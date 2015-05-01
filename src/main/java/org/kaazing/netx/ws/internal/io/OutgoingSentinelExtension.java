/*
 * Copyright 2014, Kaazing Corporation. All rights reserved.
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

    private static final byte[] EMPTY_MASK = new byte[] {0x00, 0x00, 0x00, 0x00};

    private final ClosePayloadRO closePayload;
    private final byte[] mask;

    public OutgoingSentinelExtension(final WsURLConnectionImpl connection) {
        this.closePayload = new ClosePayloadRO();
        this.mask = new byte[4];

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
        byte[] maskBuf = EMPTY_MASK;

        if (payloadLength > 0) {
            createMask(connection, mask);
            maskBuf = mask;
        }
        out.write(buf.get(offset));
        encodePayloadLength(out, payloadLength);

        out.write(maskBuf);

        for (int i = 0; i < payloadLength; i++) {
            out.write((byte) (buf.get(payloadOffset++) ^ maskBuf[i % maskBuf.length]));
        }
    }

    private void encodePayloadLength(OutputStream out, int len) throws IOException {
        switch (highestOneBit(len)) {
        case 0x0000:
        case 0x0001:
        case 0x0002:
        case 0x0004:
        case 0x0008:
        case 0x0010:
        case 0x0020:
            out.write(0x80 | len);
            break;
        case 0x0040:
            switch (len) {
            case 126:
                out.write(0x80 | 126);
                out.write(0x00);
                out.write(126);
                break;
            case 127:
                out.write(0x80 | 126);
                out.write(0x00);
                out.write(127);
                break;
            default:
                out.write(0x80 | len);
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
            out.write(0x80 | 126);
            out.write((len >> 8) & 0xff);
            out.write((len >> 0) & 0xff);
            break;
        default:
            // 65536+
            out.write(0x80 | 127);

            long length = len;
            out.write((int) ((length >> 56) & 0xff));
            out.write((int) ((length >> 48) & 0xff));
            out.write((int) ((length >> 40) & 0xff));
            out.write((int) ((length >> 32) & 0xff));
            out.write((int) ((length >> 24) & 0xff));
            out.write((int) ((length >> 16) & 0xff));
            out.write((int) ((length >> 8) & 0xff));
            out.write((int) ((length >> 0) & 0xff));
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

        closePayload.wrap(buffer, payloadOffset, payloadOffset + closePayloadLen);

        if (closePayloadLen >= 2) {
            closeCode = closePayload.statusCode();
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

                assert reasonLength == closePayload.reasonLength();

                if (reasonLength > 0) {
                    if (!validBytesUTF8(buffer, closePayload.reasonOffset(), reasonLength)) {
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

        out.write(0x88);
        encodePayloadLength(out, len);
        if (len == 0) {
            out.write(EMPTY_MASK);
        }
        else {
            assert len >= 2;

            int reasonOffset = closePayload.reasonOffset();

            createMask(connection, mask);
            out.write(mask);

            for (int i = 0; i < len; i++) {
                switch (i) {
                case 0:
                    out.write((byte) (((code >> 8) & 0xFF) ^ mask[i % mask.length]));
                    break;
                case 1:
                    out.write((byte) (((code >> 0) & 0xFF) ^ mask[i % mask.length]));
                    break;
                default:
                    out.write((byte) (frame.buffer().get(reasonOffset++) ^ mask[i % mask.length]));
                    break;
                }
            }

            out.flush();
            out.close();

            if (exception != null) {
                throw exception;
            }
        }
    }

    private void createMask(WsURLConnectionImpl connection, byte[] maskBuf) {
        int m = 1 + connection.getRandom().nextInt(Integer.MAX_VALUE);

        maskBuf[0] = (byte) ((m >> 24) & 0xFF);
        maskBuf[1] = (byte) ((m >> 16) & 0xFF);
        maskBuf[2] = (byte) ((m >> 8) & 0xFF);
        maskBuf[3] = (byte) ((m >> 0) & 0xFF);
    }
}
