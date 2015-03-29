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

package org.kaazing.netx.ws.internal;

import static java.lang.Integer.highestOneBit;
import static java.lang.String.format;
import static java.util.EnumSet.allOf;
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
import static org.kaazing.netx.ws.internal.WebSocketState.CLOSED;
import static org.kaazing.netx.ws.internal.WebSocketState.OPEN;
import static org.kaazing.netx.ws.internal.WebSocketTransition.ERROR;
import static org.kaazing.netx.ws.internal.WebSocketTransition.SEND_BINARY_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.SEND_CLOSE_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.SEND_PONG_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.SEND_TEXT_FRAME;
import static org.kaazing.netx.ws.internal.ext.flyweight.OpCode.BINARY;
import static org.kaazing.netx.ws.internal.ext.flyweight.OpCode.CONTINUATION;
import static org.kaazing.netx.ws.internal.ext.flyweight.OpCode.PONG;
import static org.kaazing.netx.ws.internal.ext.flyweight.OpCode.TEXT;
import static org.kaazing.netx.ws.internal.util.Utf8Util.validBytesUTF8;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.kaazing.netx.ws.internal.ext.WebSocketContext;
import org.kaazing.netx.ws.internal.ext.WebSocketExtensionSpi;
import org.kaazing.netx.ws.internal.ext.flyweight.ClosePayloadRW;
import org.kaazing.netx.ws.internal.ext.flyweight.Frame;
import org.kaazing.netx.ws.internal.ext.flyweight.Header;
import org.kaazing.netx.ws.internal.ext.function.WebSocketFrameConsumer;

public class WebSocketOutputStateMachine {
    private static final String MSG_CLOSE_FRAME_VIOLATION = "Protocol Violation: CLOSE Frame - Code = %d; Reason Length = %d";
    private static final byte[] EMPTY_MASK = new byte[] {0x00, 0x00, 0x00, 0x00};
    private static final WebSocketState[][] STATE_MACHINE;

    static {
        int stateCount = WebSocketState.values().length;
        int transitionCount = WebSocketTransition.values().length;

        WebSocketState[][] stateMachine = new WebSocketState[stateCount][transitionCount];
        for (WebSocketState state : allOf(WebSocketState.class)) {
            for (WebSocketTransition transition : allOf(WebSocketTransition.class)) {
                stateMachine[state.ordinal()][transition.ordinal()] = CLOSED;
            }
        }

        stateMachine[OPEN.ordinal()][SEND_PONG_FRAME.ordinal()] = OPEN;
        stateMachine[OPEN.ordinal()][SEND_CLOSE_FRAME.ordinal()] = CLOSED;
        stateMachine[OPEN.ordinal()][SEND_BINARY_FRAME.ordinal()] = OPEN;
        stateMachine[OPEN.ordinal()][SEND_TEXT_FRAME.ordinal()] = OPEN;

        STATE_MACHINE = stateMachine;
    }

    private final byte[] mask;
    private final ClosePayloadRW closePayload;
    private final byte[] closeReason;

    public WebSocketOutputStateMachine() {
        this.mask = new byte[4];
        closePayload = new ClosePayloadRW();
        closeReason = new byte[150];
    }

    public void start(WsURLConnectionImpl connection) {
        connection.setOutputState(WebSocketState.START);
    }

    public void processBinary(final WsURLConnectionImpl connection, final Header dataFrame)
            throws IOException {
        WebSocketExtensionSpi sentinel = new WebSocketExtensionSpi() {
            {
                onBinarySent = new WebSocketFrameConsumer() {
                    @Override
                    public void accept(WebSocketContext context, Frame frame) throws IOException {
                        Header transformedFrame = (Header) frame;
                        OutputStream out = connection.getTcpOutputStream();

                        assert transformedFrame.opCode() == BINARY;
                        assert transformedFrame.masked();
                        // If we want to support sending fragmented frames, then we should delete the next line.
                        assert transformedFrame.fin();

                        int length = transformedFrame.length();
                        ByteBuffer buf = transformedFrame.buffer();
                        int offset = transformedFrame.offset();

                        for (int i = 0; i < length; i++) {
                            out.write(buf.get(offset++));
                        }
                    }
                };
            }
        };

        WebSocketContext context = connection.getContext(sentinel, false);
        WebSocketState state = connection.getOutputState();

        switch (state) {
        case OPEN:
            transition(connection, SEND_BINARY_FRAME);
            context.onBinarySent(dataFrame);
            break;
        default:
            transition(connection, ERROR);
            context.onError(format("Invalid state %s to be sending a BINARY frame", state));
        }
    }

    public void processContinuation(final WsURLConnectionImpl connection, final Header dataFrame)
            throws IOException {
        WebSocketExtensionSpi sentinel = new WebSocketExtensionSpi() {
            {
                onBinarySent = new WebSocketFrameConsumer() {
                    @Override
                    public void accept(WebSocketContext context, Frame frame) throws IOException {
                        Header transformedFrame = (Header) frame;
                        OutputStream out = connection.getTcpOutputStream();

                        assert transformedFrame.opCode() == CONTINUATION;
                        assert transformedFrame.masked();
                        assert !transformedFrame.fin();

                        int length = transformedFrame.length();
                        ByteBuffer buf = transformedFrame.buffer();
                        int offset = transformedFrame.offset();

                        for (int i = 0; i < length; i++) {
                            out.write(buf.get(offset++));
                        }
                    }
                };
            }
        };

        WebSocketContext context = connection.getContext(sentinel, false);
        WebSocketState state = connection.getOutputState();

        switch (state) {
        case OPEN:
            transition(connection, WebSocketTransition.SEND_CONTINUATION_FRAME);
            context.onBinarySent(dataFrame);
            break;
        default:
            transition(connection, ERROR);
            context.onError(format("Invalid state %s to be sending a BINARY frame", state));
        }
    }

    public void processClose(final WsURLConnectionImpl connection, final Header closeFrame)
            throws IOException {
        WebSocketExtensionSpi sentinel = new WebSocketExtensionSpi() {
            {
                onCloseSent = new WebSocketFrameConsumer() {
                    @Override
                    public void accept(WebSocketContext context, Frame frame) throws IOException {
                        Header transformedFrame = (Header) frame;
                        OutputStream out = connection.getTcpOutputStream();
                        int len = 0;
                        int code = 0;
                        int closeCode = 0;
                        int closePayloadLen = transformedFrame.payloadLength();
                        IOException exception = null;

                        closePayload.wrap(transformedFrame.buffer(), transformedFrame.offset());

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

                                if (reasonLength > 0) {
                                    closePayload.reasonGet(closeReason, 0, reasonLength);
                                    if (!validBytesUTF8(ByteBuffer.wrap(closeReason, 0, reasonLength), 0, reasonLength)) {
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

                            int reasonOffset = 0;

                            connection.getRandom().nextBytes(mask);
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
                                    out.write((byte) (closeReason[reasonOffset++] ^ mask[i % mask.length]));
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
                };
            }
        };

        WebSocketContext context = connection.getContext(sentinel, false);
        WebSocketState state = connection.getOutputState();

        switch (state) {
        case OPEN:
            // Do not transition to CLOSED state at this point as we still haven't yet sent the CLOSE frame.
            context.onCloseSent(closeFrame);
            break;
        default:
            transition(connection, ERROR);
            context.onError(format("Invalid state %s to be sending a CLOSE frame", state));
        }

    }

    public void processPong(final WsURLConnectionImpl connection, final Header pongFrame)
            throws IOException {
        WebSocketExtensionSpi sentinel = new WebSocketExtensionSpi() {
            {
                onPongSent = new WebSocketFrameConsumer() {
                    @Override
                    public void accept(WebSocketContext context, Frame frame) throws IOException {
                        Header transformedFrame = (Header) frame;
                        OutputStream out = connection.getTcpOutputStream();

                        assert transformedFrame.opCode() == PONG;
                        assert transformedFrame.masked();
                        assert transformedFrame.fin();

                        int length = transformedFrame.length();
                        ByteBuffer buf = transformedFrame.buffer();
                        int offset = transformedFrame.offset();

                        for (int i = 0; i < length; i++) {
                            out.write(buf.get(offset++));
                        }
                    }
                };
            }
        };

        WebSocketContext context = connection.getContext(sentinel, false);
        WebSocketState state = connection.getOutputState();

        switch (state) {
        case OPEN:
            transition(connection, SEND_PONG_FRAME);
            context.onPongSent(pongFrame);
            break;
        default:
            transition(connection, ERROR);
            context.onError(format("Invalid state %s to be sending a PONG frame", state));
        }
    }

    public void processText(final WsURLConnectionImpl connection, final Header dataFrame)
            throws IOException {
        WebSocketExtensionSpi sentinel = new WebSocketExtensionSpi() {
            {
                onTextSent = new WebSocketFrameConsumer() {
                    @Override
                    public void accept(WebSocketContext context, Frame frame) throws IOException {
                        Header transformedFrame = (Header) frame;

                        OutputStream out = connection.getTcpOutputStream();

                        assert transformedFrame.opCode() == TEXT;
                        assert transformedFrame.masked();
                        // If we want to support sending fragmented frames, then we should delete the next line.
                        assert transformedFrame.fin();

                        int length = transformedFrame.length();
                        ByteBuffer buf = transformedFrame.buffer();
                        int offset = transformedFrame.offset();

                        for (int i = 0; i < length; i++) {
                            out.write(buf.get(offset++));
                        }
                    }
                };
            }
        };

        WebSocketContext context = connection.getContext(sentinel, true);
        WebSocketState state = connection.getOutputState();

        switch (state) {
        case OPEN:
            transition(connection, SEND_TEXT_FRAME);
            context.onTextSent(dataFrame);
            break;
        default:
            transition(connection, ERROR);
            context.onError(format("Invalid state %s to be sending a TEXT frame", state));
        }
    }

    private static void transition(WsURLConnectionImpl connection, WebSocketTransition transition) {
        WebSocketState state = STATE_MACHINE[connection.getOutputState().ordinal()][transition.ordinal()];
        connection.setOutputState(state);
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
}
