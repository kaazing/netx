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

import static java.lang.String.format;
import static java.util.EnumSet.allOf;
import static org.kaazing.netx.ws.WsURLConnection.WS_ABNORMAL_CLOSE;
import static org.kaazing.netx.ws.WsURLConnection.WS_MISSING_STATUS_CODE;
import static org.kaazing.netx.ws.WsURLConnection.WS_NORMAL_CLOSE;
import static org.kaazing.netx.ws.WsURLConnection.WS_PROTOCOL_ERROR;
import static org.kaazing.netx.ws.WsURLConnection.WS_UNSUCCESSFUL_TLS_HANDSHAKE;
import static org.kaazing.netx.ws.internal.WebSocketState.CLOSED;
import static org.kaazing.netx.ws.internal.WebSocketState.OPEN;
import static org.kaazing.netx.ws.internal.WebSocketState.START;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVED_BINARY_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVED_CLOSE_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVED_PING_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVED_PONG_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVED_TEXT_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVED_UPGRADE_RESPONSE;
import static org.kaazing.netx.ws.internal.util.FrameUtil.putLengthAndMaskBit;
import static org.kaazing.netx.ws.internal.util.Utf8Util.validBytesUTF8;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.kaazing.netx.ws.internal.ext.WebSocketContext;
import org.kaazing.netx.ws.internal.ext.WebSocketExtensionSpi;
import org.kaazing.netx.ws.internal.ext.frame.Close;
import org.kaazing.netx.ws.internal.ext.frame.Data;
import org.kaazing.netx.ws.internal.ext.frame.Frame;
import org.kaazing.netx.ws.internal.ext.frame.Frame.Payload;
import org.kaazing.netx.ws.internal.ext.frame.FrameFactory;
import org.kaazing.netx.ws.internal.ext.frame.Ping;
import org.kaazing.netx.ws.internal.ext.frame.Pong;
import org.kaazing.netx.ws.internal.ext.function.WebSocketFrameConsumer;
import org.kaazing.netx.ws.internal.util.FrameUtil;

public class WebSocketInputStateMachine {
    private static final String MSG_CLOSE_FRAME_VIOLATION = "Protocol Violation: CLOSE Frame - Code = %d; Reason Length = %d";
    private static final WebSocketState[][] STATE_MACHINE;
    private static final int MAX_COMMAND_FRAME_PAYLOAD = 125;

    static {
        int stateCount = WebSocketState.values().length;
        int transitionCount = WebSocketTransition.values().length;

        WebSocketState[][] stateMachine = new WebSocketState[stateCount][transitionCount];
        for (WebSocketState state : allOf(WebSocketState.class)) {
            for (WebSocketTransition transition : allOf(WebSocketTransition.class)) {
                stateMachine[state.ordinal()][transition.ordinal()] = CLOSED;
            }
        }

        stateMachine[START.ordinal()][RECEIVED_UPGRADE_RESPONSE.ordinal()] = OPEN;

        stateMachine[OPEN.ordinal()][RECEIVED_PING_FRAME.ordinal()] = OPEN;
        stateMachine[OPEN.ordinal()][RECEIVED_PONG_FRAME.ordinal()] = OPEN;
        stateMachine[OPEN.ordinal()][RECEIVED_CLOSE_FRAME.ordinal()] = OPEN;
        stateMachine[OPEN.ordinal()][RECEIVED_BINARY_FRAME.ordinal()] = OPEN;
        stateMachine[OPEN.ordinal()][RECEIVED_TEXT_FRAME.ordinal()] = OPEN;

        STATE_MACHINE = stateMachine;
    }

    private final FrameFactory frameFactory;

    public WebSocketInputStateMachine() {
        frameFactory = FrameFactory.newInstance(8192);
    }

    public void start(WsURLConnectionImpl connection) {
        connection.setInputState(WebSocketState.START);
    }

    public void processBinary(final WsURLConnectionImpl connection, final Data dataFrame)
            throws IOException {
        final AtomicBoolean hookExercised = new AtomicBoolean(false);

        WebSocketExtensionSpi sentinel = new WebSocketExtensionSpi() {
            {
                onBinaryFrameReceived = new WebSocketFrameConsumer() {
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
        WebSocketState state = connection.getInputState();

        switch (state) {
        case OPEN:
            transition(connection, WebSocketTransition.RECEIVED_BINARY_FRAME);
            context.onBinaryReceived(dataFrame);
            break;
        default:
            throw new IllegalStateException(format("Invalid state %s to be receiving a BINARY frame", state));
        }

        if (!hookExercised.get()) {
            // One of the extensions may have decided to short-circuit and not let the BINARY frame propagate to the sentinel
            // hook. In such a case, we should set the payload length of the frame to zero.
            putLengthAndMaskBit(dataFrame.buffer(), dataFrame.offset() + 1, 0, dataFrame.isMasked());
        }
    }

    public void processClose(final WsURLConnectionImpl connection, final Close closeFrame)
            throws IOException {
        final AtomicBoolean hookExercised = new AtomicBoolean(false);
        WebSocketExtensionSpi sentinel = new WebSocketExtensionSpi() {
            {
                onCloseFrameReceived = new WebSocketFrameConsumer() {
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
        WebSocketState state = connection.getInputState();

        switch (state) {
        case OPEN:
            transition(connection, WebSocketTransition.RECEIVED_CLOSE_FRAME);
            context.onCloseReceived(closeFrame);
            break;
        default:
            throw new IllegalStateException(format("Invalid state %s to be receiving a CLOSE frame", state));
        }

        if (!hookExercised.get()) {
            // One of the extensions may have decided to short-circuit and not let the CLOSE frame propagate to the sentinel
            // hook. In such a case, we just bail and do not send a CLOSE to the server.
            return;
        }

        int closePayloadLength = closeFrame.getLength();
        int code = 0;
        int closeCodeRO = 0;
        int reasonOffset = 0;
        int reasonLength = 0;

        if (closePayloadLength >= 2) {
            code = closeFrame.getStatusCode();
            Payload reasonPayload = closeFrame.getReason();
            closeCodeRO = code;

            switch (code) {
            case WS_MISSING_STATUS_CODE:
            case WS_ABNORMAL_CLOSE:
            case WS_UNSUCCESSFUL_TLS_HANDSHAKE:
                code = WS_PROTOCOL_ERROR;
                break;
            default:
                break;
            }

            if (reasonPayload != null) {
                reasonOffset = reasonPayload.offset();
                reasonLength = closePayloadLength - 2;

                if (closePayloadLength > 2) {
                    if ((closePayloadLength > MAX_COMMAND_FRAME_PAYLOAD) ||
                        !validBytesUTF8(reasonPayload.buffer(), reasonOffset, reasonLength)) {
                        code = WS_PROTOCOL_ERROR;
                    }

                    if (code != WS_NORMAL_CLOSE) {
                        reasonLength = 0;
                    }
                }
            }
        }

        // Use the output state machine to send CLOSE.
        connection.sendClose(code, closeFrame.buffer().array(), reasonOffset, reasonLength);

        if (code == WS_PROTOCOL_ERROR) {
            throw new IOException(format(MSG_CLOSE_FRAME_VIOLATION, closeCodeRO, reasonLength));
        }

        return;
    }

    public void processPing(final WsURLConnectionImpl connection, final Ping pingFrame)
            throws IOException {
        final AtomicReference<Ping> reference = new AtomicReference<Ping>(pingFrame);
        final AtomicBoolean hookExercised = new AtomicBoolean(false);

        WebSocketExtensionSpi sentinel = new WebSocketExtensionSpi() {
            {
                onPingFrameReceived = new WebSocketFrameConsumer() {
                    @Override
                    public void accept(WebSocketContext context, Frame frame) throws IOException {
                        reference.set((Ping) frame);
                        hookExercised.set(true);
                    }
                };
            }
        };
        WebSocketContext context = connection.getContext(sentinel, false);
        WebSocketState state = connection.getInputState();

        switch (state) {
        case OPEN:
            transition(connection, WebSocketTransition.RECEIVED_PING_FRAME);
            context.onPingReceived(pingFrame);
            break;
        default:
            throw new IllegalStateException(format("Invalid state %s to be receiving a PING frame", state));
        }

        if (!hookExercised.get()) {
            // One of the extensions may have decided to short-circuit and not let the PING frame propagate to the sentinel
            // hook. In such a case, we just bail and do not send PONG.
            return;
        }

        // Use output state-machine to send PONG.
        Ping transformedFrame = reference.get();
        Payload pingPayload = transformedFrame.getPayload();
        connection.sendPong(pingPayload.buffer().array(), pingPayload.offset(), pingPayload.limit() - pingPayload.offset());
    }

    public void processPong(final WsURLConnectionImpl connection, final Pong pongFrame)
            throws IOException {
        WebSocketExtensionSpi sentinel = new WebSocketExtensionSpi() {
            {
                onPongFrameReceived = new WebSocketFrameConsumer() {
                    @Override
                    public void accept(WebSocketContext context, Frame frame) throws IOException {
                    }
                };
            }
        };

        WebSocketContext context = connection.getContext(sentinel, false);
        WebSocketState state = connection.getInputState();

        switch (state) {
        case OPEN:
            transition(connection, WebSocketTransition.RECEIVED_PONG_FRAME);
            context.onPongReceived(pongFrame);
            break;
        default:
            throw new IllegalStateException(format("Invalid state %s to be receiving a PONG frame", state));
        }
    }

    public void processText(final WsURLConnectionImpl connection, final Data dataFrame)
            throws IOException {
        final AtomicBoolean hookExercised = new AtomicBoolean(false);

        WebSocketExtensionSpi sentinel = new WebSocketExtensionSpi() {
            {
                onTextFrameReceived = new WebSocketFrameConsumer() {
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
        WebSocketState state = connection.getInputState();

        switch (state) {
        case OPEN:
            transition(connection, WebSocketTransition.RECEIVED_PONG_FRAME);
            context.onTextReceived(dataFrame);
            break;
        default:
            throw new IllegalStateException(format("Invalid state %s to be receiving a TEXT frame", state));
        }

        if (!hookExercised.get()) {
            // One of the extensions may have decided to short-circuit and not let the TEXT frame propagate to the sentinel
            // hook. In such a case, we should set the payload length of the frame to zero.
            putLengthAndMaskBit(dataFrame.buffer(), dataFrame.offset() + 1, 0, dataFrame.isMasked());
        }
    }

    public FrameFactory getFrameFactory() {
        return frameFactory;
    }

    private static void transition(WsURLConnectionImpl connection, WebSocketTransition transition) {
        WebSocketState state = STATE_MACHINE[connection.getInputState().ordinal()][transition.ordinal()];
        connection.setInputState(state);
    }
}
