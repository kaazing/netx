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
import static org.kaazing.netx.ws.WsURLConnection.WS_ENDPOINT_GOING_AWAY;
import static org.kaazing.netx.ws.WsURLConnection.WS_INCONSISTENT_DATA_MESSAGE_TYPE;
import static org.kaazing.netx.ws.WsURLConnection.WS_INCORRECT_MESSAGE_TYPE;
import static org.kaazing.netx.ws.WsURLConnection.WS_MESSAGE_TOO_BIG;
import static org.kaazing.netx.ws.WsURLConnection.WS_NORMAL_CLOSE;
import static org.kaazing.netx.ws.WsURLConnection.WS_PROTOCOL_ERROR;
import static org.kaazing.netx.ws.WsURLConnection.WS_SERVER_TERMINATED_CONNECTION;
import static org.kaazing.netx.ws.WsURLConnection.WS_UNSUCCESSFUL_EXTENSION_NEGOTIATION;
import static org.kaazing.netx.ws.WsURLConnection.WS_UNSUCCESSFUL_TLS_HANDSHAKE;
import static org.kaazing.netx.ws.WsURLConnection.WS_VIOLATE_POLICY;
import static org.kaazing.netx.ws.internal.WebSocketState.CLOSED;
import static org.kaazing.netx.ws.internal.WebSocketState.OPEN;
import static org.kaazing.netx.ws.internal.WebSocketTransition.SEND_BINARY_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.SEND_CLOSE_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.SEND_PONG_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.SEND_TEXT_FRAME;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import org.kaazing.netx.ws.internal.ext.WebSocketContext;
import org.kaazing.netx.ws.internal.ext.WebSocketExtensionSpi;
import org.kaazing.netx.ws.internal.ext.frame.Close;
import org.kaazing.netx.ws.internal.ext.frame.Data;
import org.kaazing.netx.ws.internal.ext.frame.Frame;
import org.kaazing.netx.ws.internal.ext.frame.Frame.Payload;
import org.kaazing.netx.ws.internal.ext.frame.FrameFactory;
import org.kaazing.netx.ws.internal.ext.frame.Pong;
import org.kaazing.netx.ws.internal.ext.function.WebSocketFrameConsumer;
import org.kaazing.netx.ws.internal.util.FrameUtil;

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

    private final FrameFactory frameFactory;
    private final byte[] mask;

    public WebSocketOutputStateMachine() {
        this.frameFactory = FrameFactory.newInstance(8192);
        this.mask = new byte[4];
    }

    public void start(WsURLConnectionImpl connection) {
        connection.setOutputState(WebSocketState.START);
    }

    public void processBinary(final WsURLConnectionImpl connection, final Data dataFrame)
            throws IOException {
        final AtomicBoolean hookExercised = new AtomicBoolean(false);
        WebSocketExtensionSpi sentinel = new WebSocketExtensionSpi() {
            {
                onBinaryFrameSent = new WebSocketFrameConsumer() {
                    @Override
                    public void accept(WebSocketContext context, Frame frame) throws IOException {
                        if (hookExercised.compareAndSet(false, true)) {
                            Data sourceFrame = (Data) frame;
                            if (sourceFrame == dataFrame) {
                                return;
                            }

                            FrameUtil.copy(sourceFrame, dataFrame);
                        }
                    }
                };
            }
        };

        WebSocketContext context = connection.getContext(sentinel, false);
        WebSocketState state = connection.getOutputState();

        switch (state) {
        case OPEN:
            transition(connection, WebSocketTransition.SEND_BINARY_FRAME);
            context.onBinarySent(dataFrame);
            break;
        default:
            throw new IllegalStateException(format("Invalid state %s to be sending a BINARY frame", state));
        }

        if (!hookExercised.get()) {
            // One of the extensions may have decided to short-circuit and not let the BINARY frame propagate to the sentinel
            // hook. In such a case, we should not render the frame on the wire.
            return;
        }

        OutputStream out = connection.getTcpOutputStream();
        out.write(0x82);

        encodePayloadLength(out, dataFrame.getLength());
        connection.getRandom().nextBytes(mask);
        out.write(mask);

        Payload payload = dataFrame.getPayload();
        int payloadOffset = payload.offset();
        for (int i = 0; i < dataFrame.getLength(); i++) {
            byte b = (byte) (payload.buffer().get(payloadOffset++) ^ mask[i % mask.length]);
            out.write(b);
        }
    }

    public void processClose(final WsURLConnectionImpl connection, final Close closeFrame)
            throws IOException {
        final AtomicBoolean hookExercised = new AtomicBoolean(false);
        WebSocketExtensionSpi sentinel = new WebSocketExtensionSpi() {
            {
                onCloseFrameSent = new WebSocketFrameConsumer() {
                    @Override
                    public void accept(WebSocketContext context, Frame frame) throws IOException {
                        if (hookExercised.compareAndSet(false, true)) {
                            Close sourceFrame = (Close) frame;
                            if (sourceFrame == closeFrame) {
                                return;
                            }

                            FrameUtil.copy(sourceFrame, closeFrame);
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
            throw new IllegalStateException(format("Invalid state %s to be sending a CLOSE frame", state));
        }

        OutputStream out = connection.getTcpOutputStream();
        if (!hookExercised.get()) {
            // One of the extensions may have decided to short-circuit and not let the CLOSE frame propagate to the sentinel
            // hook. In such a case, we should not render the frame on the wire.
            return;
        }

        int len = 0;
        Payload reasonPayload = null;
        int reasonOffset = 0;
        int closeCode = closeFrame.getStatusCode();
        int closePayloadLen = closeFrame.getLength();
        IOException exception = null;

        if (closeCode != 0) {
            switch (closeCode) {
            case WS_NORMAL_CLOSE:
            case WS_ENDPOINT_GOING_AWAY:
            case WS_PROTOCOL_ERROR:
            case WS_INCORRECT_MESSAGE_TYPE:
//            case WS_MISSING_STATUS_CODE:
            case WS_INCONSISTENT_DATA_MESSAGE_TYPE:
            case WS_VIOLATE_POLICY:
            case WS_MESSAGE_TOO_BIG:
            case WS_UNSUCCESSFUL_EXTENSION_NEGOTIATION:
            case WS_SERVER_TERMINATED_CONNECTION:
            case WS_UNSUCCESSFUL_TLS_HANDSHAKE:
                len += 2;
                break;
            default:
                if ((closeCode >= 3000) && (closeCode <= 4999)) {
                    len += 2;
                }

                throw new IOException(format("Invalid CLOSE code %d", closeCode));
            }

            if (closePayloadLen > 2) {
                reasonPayload = closeFrame.getReason();
                reasonOffset = reasonPayload.offset();

                int reasonLength = reasonPayload.limit() - reasonPayload.offset();
                if (reasonLength > 0) {
                    if (reasonLength > 123) {
                        exception = new IOException(format(MSG_CLOSE_FRAME_VIOLATION, closeCode, reasonLength));
                    }

                    len += reasonLength;
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

            connection.getRandom().nextBytes(mask);
            out.write(mask);

            for (int i = 0; i < len; i++) {
                switch (i) {
                case 0:
                    out.write((byte) (((closeCode >> 8) & 0xFF) ^ mask[i % mask.length]));
                    break;
                case 1:
                    out.write((byte) (((closeCode >> 0) & 0xFF) ^ mask[i % mask.length]));
                    break;
                default:
                    out.write((byte) (reasonPayload.buffer().get(reasonOffset++) ^ mask[i % mask.length]));
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

    public void processPong(final WsURLConnectionImpl connection, final Pong pongFrame)
            throws IOException {
        final AtomicBoolean hookExercised = new AtomicBoolean(false);
        WebSocketExtensionSpi sentinel = new WebSocketExtensionSpi() {
            {
                onPongFrameSent = new WebSocketFrameConsumer() {
                    @Override
                    public void accept(WebSocketContext context, Frame frame) throws IOException {
                        if (hookExercised.compareAndSet(false, true)) {
                            Pong sourceFrame = (Pong) frame;
                            if (sourceFrame == pongFrame) {
                                return;
                            }

                            FrameUtil.copy(sourceFrame, pongFrame);
                        }
                    }
                };
            }
        };

        WebSocketContext context = connection.getContext(sentinel, false);
        WebSocketState state = connection.getOutputState();

        switch (state) {
        case OPEN:
            transition(connection, WebSocketTransition.SEND_PONG_FRAME);
            context.onPongSent(pongFrame);
            break;
        default:
            throw new IllegalStateException(format("Invalid state %s to be sending a PONG frame", state));
        }

        if (!hookExercised.get()) {
            // One of the extensions may have decided to short-circuit and not let the PONG frame propagate to the sentinel
            // hook. In such a case, we should not render the frame on the wire.
            return;
        }

        Payload payload = pongFrame.getPayload();
        int payloadOffset = payload.offset();
        int payloadLen = payload.limit() - payload.offset();
        OutputStream out = connection.getTcpOutputStream();

        out.write(0x8A);
        encodePayloadLength(out, payloadLen);

        if (payloadLen == 0) {
            out.write(EMPTY_MASK);
            return;
        }

        connection.getRandom().nextBytes(mask);
        out.write(mask);

        for (int i = 0; i < payloadLen; i++) {
            out.write((byte) (payload.buffer().get(payloadOffset++) ^ mask[i % mask.length]));
        }
    }

    public void processText(final WsURLConnectionImpl connection, final Data dataFrame)
            throws IOException {
        final AtomicBoolean hookExercised = new AtomicBoolean(false);
        WebSocketExtensionSpi sentinel = new WebSocketExtensionSpi() {
            {
                onTextFrameSent = new WebSocketFrameConsumer() {
                    @Override
                    public void accept(WebSocketContext context, Frame frame) throws IOException {
                        if (hookExercised.compareAndSet(false, true)) {
                            Data sourceFrame = (Data) frame;
                            if (sourceFrame == dataFrame) {
                                return;
                            }

                            FrameUtil.copy(sourceFrame, dataFrame);
                        }
                    }
                };
            }
        };

        WebSocketContext context = connection.getContext(sentinel, true);
        WebSocketState state = connection.getOutputState();

        switch (state) {
        case OPEN:
            transition(connection, WebSocketTransition.SEND_TEXT_FRAME);
            context.onTextSent(dataFrame);
            break;
        default:
            throw new IllegalStateException(format("Invalid state %s to be sending a TEXT frame", state));
        }

        if (!hookExercised.get()) {
            // One of the extensions may have decided to short-circuit and not let the frame propagate to the sentinel hook. In
            // such a case, we should not render the frame on the wire.
            return;
        }

        Payload payload = dataFrame.getPayload();
        int payloadLen = dataFrame.getLength();
        int payloadOffset = payload.offset();
        OutputStream out = connection.getTcpOutputStream();

        out.write(0x81);
        encodePayloadLength(out, payloadLen);

        // Create the masking key.
        connection.getRandom().nextBytes(mask);
        out.write(mask);

        // Mask the payload and write it out.
        for (int i = 0; i < payloadLen; i++) {
            out.write((byte) (payload.buffer().get(payloadOffset++) ^ mask[i % mask.length]));
        }
    }

    public FrameFactory getFrameFactory() {
        return frameFactory;
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
