/**
 * Copyright 2007-2015, Kaazing Corporation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kaazing.netx.ws.internal.io;

import static java.lang.Integer.highestOneBit;
import static java.lang.String.format;
import static org.kaazing.netx.ws.WsURLConnection.WS_ABNORMAL_CLOSE;
import static org.kaazing.netx.ws.WsURLConnection.WS_ENDPOINT_GOING_AWAY;
import static org.kaazing.netx.ws.WsURLConnection.WS_INCONSISTENT_DATA_MESSAGE_TYPE;
import static org.kaazing.netx.ws.WsURLConnection.WS_INCORRECT_MESSAGE_TYPE;
import static org.kaazing.netx.ws.WsURLConnection.WS_MESSAGE_TOO_BIG;
import static org.kaazing.netx.ws.WsURLConnection.WS_MISSING_STATUS_CODE;
import static org.kaazing.netx.ws.WsURLConnection.WS_NORMAL_CLOSE;
import static org.kaazing.netx.ws.WsURLConnection.WS_PROTOCOL_ERROR;
import static org.kaazing.netx.ws.WsURLConnection.WS_SERVER_TERMINATED_CONNECTION;
import static org.kaazing.netx.ws.WsURLConnection.WS_UNSUCCESSFUL_EXTENSION_NEGOTIATION;
import static org.kaazing.netx.ws.WsURLConnection.WS_UNSUCCESSFUL_TLS_HANDSHAKE;
import static org.kaazing.netx.ws.WsURLConnection.WS_VIOLATE_POLICY;
import static org.kaazing.netx.ws.internal.ext.flyweight.Opcode.BINARY;
import static org.kaazing.netx.ws.internal.ext.flyweight.Opcode.CLOSE;
import static org.kaazing.netx.ws.internal.ext.flyweight.Opcode.CONTINUATION;
import static org.kaazing.netx.ws.internal.ext.flyweight.Opcode.PONG;
import static org.kaazing.netx.ws.internal.ext.flyweight.Opcode.TEXT;
import static org.kaazing.netx.ws.internal.util.Utf8Util.validBytesUTF8;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.kaazing.netx.ws.internal.WsURLConnectionImpl;
import org.kaazing.netx.ws.internal.ext.WebSocketContext;
import org.kaazing.netx.ws.internal.ext.WebSocketExtensionSpi;
import org.kaazing.netx.ws.internal.ext.flyweight.ClosePayloadRO;
import org.kaazing.netx.ws.internal.ext.flyweight.Frame;
import org.kaazing.netx.ws.internal.ext.function.WebSocketFrameConsumer;

public class OutgoingSentinelExtension extends WebSocketExtensionSpi {
    private static final String MSG_CLOSE_FRAME_VIOLATION = "Protocol Violation: CLOSE Frame - Code = %d; Reason Length = %d";
    private static final String MSG_INVALID_METADATA_LENGTH = "Invalid metadata length '%d'";

    // Even though CLOSE payload length must be <= 125, we are using 150 to accommodate negative tests.
    private static final int MAX_CLOSE_PAYLOAD_LENGTH_LIMIT = 150;

    private final ClosePayloadRO closePayloadRO;
    private final ByteBuffer closePayload;
    private final ByteBuffer frameBuffer;

    public OutgoingSentinelExtension(final WsURLConnectionImpl connection) {
        this.closePayloadRO = new ClosePayloadRO();
        this.closePayload = ByteBuffer.allocate(MAX_CLOSE_PAYLOAD_LENGTH_LIMIT);
        this.frameBuffer = ByteBuffer.allocate(connection.getMaxFrameLength() + 4);

        super.onBinarySent = new WebSocketFrameConsumer() {
            @Override
            public void accept(WebSocketContext context, Frame frame) throws IOException {
                assert frame.opcode() == BINARY;
                encodeFrame(connection, frame);
            }
        };

        super.onContinuationSent = new WebSocketFrameConsumer() {
            @Override
            public void accept(WebSocketContext context, Frame frame) throws IOException {
                assert frame.opcode() == CONTINUATION;
                encodeFrame(connection, frame);
            }
        };

        super.onCloseSent = new WebSocketFrameConsumer() {
            @Override
            public void accept(WebSocketContext context, Frame frame) throws IOException {
                assert frame.opcode() == CLOSE;
                validateAndEncodeCloseFrame(connection, frame);
            }
        };

        super.onPongSent = new WebSocketFrameConsumer() {
            @Override
            public void accept(WebSocketContext context, Frame frame) throws IOException {
                assert frame.opcode() == PONG;
                encodeFrame(connection, frame);
            }
        };

        super.onTextSent = new WebSocketFrameConsumer() {
            @Override
            public void accept(WebSocketContext context, Frame frame) throws IOException {
                assert frame.opcode() == TEXT;
                encodeFrame(connection, frame);
            }
        };
    }

    private void encodeFrame(WsURLConnectionImpl connection, Frame frame) throws IOException {
        OutputStream out = connection.getTcpOutputStream();
        int offset = frame.offset();
        ByteBuffer buf = frame.buffer();
        int payloadLength = frame.payloadLength();
        int payloadOffset = frame.payloadOffset();
        int mask = 0;

        if (payloadLength > 0) {
            mask = 1 + connection.getRandom().nextInt(Integer.MAX_VALUE);
        }

        int metadataLength = payloadOffset - offset;

        switch (metadataLength) {
        case 2:
            frameBuffer.putShort(buf.getShort(offset));
            break;
        case 4:
            frameBuffer.putInt(buf.getInt(offset));
            break;
        case 10:
            frameBuffer.putShort(buf.getShort(offset));
            frameBuffer.putLong(buf.getLong(offset + 2));
            break;
        default:
            throw new IllegalStateException(format(MSG_INVALID_METADATA_LENGTH, metadataLength));
        }

        // Set the mask bit.
        byte maskByte = frameBuffer.get(1);
        frameBuffer.put(1, (byte) (maskByte | 0x80));

        encodeMaskAndPayload(buf, payloadOffset, payloadLength, mask);

        out.write(frameBuffer.array(), 0, frameBuffer.position());
        frameBuffer.rewind();
    }

    private void encodePayloadLength(int len) throws IOException {
        switch (highestOneBit(len)) {
        case 0x0000:
        case 0x0001:
        case 0x0002:
        case 0x0004:
        case 0x0008:
        case 0x0010:
        case 0x0020:
            frameBuffer.put((byte) (0x80 | len));
            break;
        case 0x0040:
            switch (len) {
            case 126:
                frameBuffer.put((byte) (0x80 | 126));
                frameBuffer.put((byte) 0x00);
                frameBuffer.put((byte) 126);
                break;
            case 127:
                frameBuffer.put((byte) (0x80 | 126));
                frameBuffer.put((byte) 0x00);
                frameBuffer.put((byte) 127);
                break;
            default:
                frameBuffer.put((byte) (0x80 | len));
                break;
            }
            break;
        case 0x0080:
        case 0x0100:
        case 0x0200:
        case 0x0400:
        case 0x0800:
        case 0x1000:
        case 0x2000:
        case 0x4000:
        case 0x8000:
            frameBuffer.put((byte) (0x80 | 126));
            frameBuffer.put((byte) ((len >> 8) & 0xff));
            frameBuffer.put((byte) ((len >> 0) & 0xff));
            break;
        default:
            // 65536+
            frameBuffer.put((byte) (0x80 | 127));

            long length = len;
            frameBuffer.put((byte) ((length >> 56) & 0xff));
            frameBuffer.put((byte) ((length >> 48) & 0xff));
            frameBuffer.put((byte) ((length >> 40) & 0xff));
            frameBuffer.put((byte) ((length >> 32) & 0xff));
            frameBuffer.put((byte) ((length >> 24) & 0xff));
            frameBuffer.put((byte) ((length >> 16) & 0xff));
            frameBuffer.put((byte) ((length >>  8) & 0xff));
            frameBuffer.put((byte) ((length >>  0) & 0xff));
            break;
        }
    }

    private void validateAndEncodeCloseFrame(WsURLConnectionImpl connection, Frame frame) throws IOException {
        OutputStream out = connection.getTcpOutputStream();
        int len = 0;
        int code = 0;
        int closeCode = 0;
        ByteBuffer buffer = frame.buffer();
        int closePayloadLen = frame.payloadLength();
        int payloadOffset = frame.payloadOffset();
        IOException exception = null;

        closePayloadRO.wrap(buffer, payloadOffset, payloadOffset + closePayloadLen);

        if (closePayloadLen >= 2) {
            closeCode = closePayloadRO.statusCode();
        }

        if (closeCode != 0) {
            switch (closeCode) {
            case WS_MISSING_STATUS_CODE:
            case WS_ABNORMAL_CLOSE:
            case WS_UNSUCCESSFUL_TLS_HANDSHAKE:
                len += 2;
                code = WS_PROTOCOL_ERROR;
                exception = new IOException(format(MSG_CLOSE_FRAME_VIOLATION, closeCode, closePayloadLen));
                break;
            case WS_NORMAL_CLOSE:
            case WS_ENDPOINT_GOING_AWAY:
            case WS_PROTOCOL_ERROR:
            case WS_INCORRECT_MESSAGE_TYPE:
            case WS_INCONSISTENT_DATA_MESSAGE_TYPE:
            case WS_VIOLATE_POLICY:
            case WS_MESSAGE_TOO_BIG:
            case WS_UNSUCCESSFUL_EXTENSION_NEGOTIATION:
            case WS_SERVER_TERMINATED_CONNECTION:
                len += 2;
                code = closeCode;
                break;
            default:
                if ((closeCode >= 3000) && (closeCode <= 4999)) {
                    len += 2;
                    code = closeCode;
                }

                throw new IOException(format("Invalid CLOSE code %d", closeCode));
            }

            if ((code != WS_PROTOCOL_ERROR) && (closePayloadLen > 2)) {
                int reasonLength = closePayloadLen - 2;

                assert reasonLength == closePayloadRO.reasonLength();

                if (reasonLength > 0) {
                    if (!validBytesUTF8(buffer, closePayloadRO.reasonOffset(), reasonLength)) {
                        code = WS_PROTOCOL_ERROR;
                        exception = new IOException(format(MSG_CLOSE_FRAME_VIOLATION, closeCode, reasonLength));
                    }
                    else if (reasonLength > 123) {
                        code = WS_PROTOCOL_ERROR;
                        exception = new IOException(format(MSG_CLOSE_FRAME_VIOLATION, closeCode, reasonLength));
                        len += reasonLength;
                    }
                    else {
                        if (code != WS_NORMAL_CLOSE) {
                            reasonLength = 0;
                        }

                        len += reasonLength;
                    }
                }
            }
        }

        frameBuffer.put((byte) 0x88);
        encodePayloadLength(len);

        if (len == 0) {
            frameBuffer.putInt(0);
        }
        else {
            assert len >= 2;

            int mask = 1 + connection.getRandom().nextInt(Integer.MAX_VALUE);
            int reasonOffset = closePayloadRO.reasonOffset();
            int roffset = reasonOffset;

            closePayload.putShort(0, (short) code);
            if (len > 2) {
                for (int i = 2; i < len; i++) {
                    closePayload.put(i, frame.buffer().get(roffset++));
                }
            }

            encodeMaskAndPayload(closePayload, 0, len, mask);
        }

        out.write(frameBuffer.array(), 0, frameBuffer.position());
        frameBuffer.rewind();

        out.flush();
        out.close();

        if (exception != null) {
            throw exception;
        }
    }

    private void encodeMaskAndPayload(ByteBuffer buffer, int offset, int length, int mask) throws IOException {
        frameBuffer.putInt(mask);

        // xor a 32bit word at a time as long as possible then do remaining 0, 1, 2 or 3 bytes.
        int i;
        for (i = 0; i + 4 < length; i += 4) {
            int val = buffer.getInt(offset + i) ^ mask;
            frameBuffer.putInt(val);
        }

        for (; i < length; i++) {
            int shiftBytes = 3 - (i & 0x03);
            byte maskByte = (byte) (mask >> (8 * shiftBytes) & 0xFF);
            byte b = (byte) (buffer.get(offset + i) ^ maskByte);

            frameBuffer.put(b);
        }
    }
}
