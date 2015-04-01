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
import static org.kaazing.netx.ws.internal.WebSocketState.CLOSED;
import static org.kaazing.netx.ws.internal.WebSocketState.OPEN;
import static org.kaazing.netx.ws.internal.WebSocketState.START;
import static org.kaazing.netx.ws.internal.WebSocketTransition.ERROR;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVE_BINARY_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVE_CLOSE_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVE_CONTINUATION_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVE_PING_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVE_PONG_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVE_TEXT_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVE_UPGRADE_RESPONSE;

import java.io.IOException;

import org.kaazing.netx.ws.internal.ext.WebSocketContext;
import org.kaazing.netx.ws.internal.ext.WebSocketExtensionSpi;
import org.kaazing.netx.ws.internal.ext.flyweight.Frame;
import org.kaazing.netx.ws.internal.ext.function.WebSocketFrameConsumer;

public final class WebSocketInputStateMachine {
    private static final WebSocketState[][] STATE_MACHINE;

    static {
        int stateCount = WebSocketState.values().length;
        int transitionCount = WebSocketTransition.values().length;

        WebSocketState[][] stateMachine = new WebSocketState[stateCount][transitionCount];
        for (WebSocketState state : allOf(WebSocketState.class)) {
            for (WebSocketTransition transition : allOf(WebSocketTransition.class)) {
                stateMachine[state.ordinal()][transition.ordinal()] = CLOSED;
            }

            stateMachine[state.ordinal()][ERROR.ordinal()] = CLOSED;
        }

        stateMachine[START.ordinal()][RECEIVE_UPGRADE_RESPONSE.ordinal()] = OPEN;

        stateMachine[OPEN.ordinal()][RECEIVE_PING_FRAME.ordinal()] = OPEN;
        stateMachine[OPEN.ordinal()][RECEIVE_PONG_FRAME.ordinal()] = OPEN;
        stateMachine[OPEN.ordinal()][RECEIVE_CONTINUATION_FRAME.ordinal()] = OPEN;
        stateMachine[OPEN.ordinal()][RECEIVE_CLOSE_FRAME.ordinal()] = OPEN;
        stateMachine[OPEN.ordinal()][RECEIVE_BINARY_FRAME.ordinal()] = OPEN;
        stateMachine[OPEN.ordinal()][RECEIVE_TEXT_FRAME.ordinal()] = OPEN;


        STATE_MACHINE = stateMachine;
    }

    private static final WebSocketInputStateMachine INSTANCE = new WebSocketInputStateMachine();

    private WebSocketInputStateMachine() {
    }

    public static WebSocketInputStateMachine instance() {
        return INSTANCE;
    }

    public void start(WsURLConnectionImpl connection) {
        connection.setInputState(START);
    }

    public void processBinary(final WsURLConnectionImpl connection,
                              final Frame frame,
                              final WebSocketFrameConsumer terminalConsumer) throws IOException {
        WebSocketExtensionSpi sentinel = new WebSocketExtensionSpi() {
            {
                onBinaryReceived = terminalConsumer;
            }
        };
        WebSocketContext context = connection.getContext(sentinel, false);
        WebSocketState state = connection.getInputState();

        switch (state) {
        case OPEN:
            transition(connection, RECEIVE_BINARY_FRAME);
            context.onBinaryReceived(frame);
            break;
        default:
            transition(connection, ERROR);
            context.onError(format("Invalid state %s to be receiving a BINARY frame", state));
        }
    }

    public void processClose(final WsURLConnectionImpl connection,
                             final Frame frame,
                             final WebSocketFrameConsumer terminalConsumer) throws IOException {
        WebSocketExtensionSpi sentinel = new WebSocketExtensionSpi() {
            {
                onCloseReceived = terminalConsumer;
            }
        };

        WebSocketContext context = connection.getContext(sentinel, false);
        WebSocketState state = connection.getInputState();

        switch (state) {
        case OPEN:
            transition(connection, RECEIVE_CLOSE_FRAME);
            context.onCloseReceived(frame);
            break;
        default:
            transition(connection, ERROR);
            context.onError(format("Invalid state %s to be receiving a CLOSE frame", state));
        }
    }

    public void processContinuation(final WsURLConnectionImpl connection,
                             final Frame frame,
                             final WebSocketFrameConsumer terminalConsumer) throws IOException {
        WebSocketExtensionSpi sentinel = new WebSocketExtensionSpi() {
            {
                onContinuationReceived = terminalConsumer;
            }
        };

        WebSocketContext context = connection.getContext(sentinel, false);
        WebSocketState state = connection.getInputState();

        switch (state) {
        case OPEN:
            transition(connection, RECEIVE_CONTINUATION_FRAME);
            context.onContinuationReceived(frame);
            break;
        default:
            transition(connection, ERROR);
            context.onError(format("Invalid state %s to be receiving a CONTINUATION frame", state));
        }
    }

    public void processPing(final WsURLConnectionImpl connection,
                            final Frame frame,
                            final WebSocketFrameConsumer terminalConsumer) throws IOException {
        WebSocketExtensionSpi sentinel = new WebSocketExtensionSpi() {
            {
                onPingReceived = terminalConsumer;
            }
        };
        WebSocketContext context = connection.getContext(sentinel, false);
        WebSocketState state = connection.getInputState();

        switch (state) {
        case OPEN:
            transition(connection, RECEIVE_PING_FRAME);
            context.onPingReceived(frame);
            break;
        default:
            transition(connection, ERROR);
            context.onError(format("Invalid state %s to be receiving a PING frame", state));
        }
    }

    public void processPong(final WsURLConnectionImpl connection,
                            final Frame frame,
                            final WebSocketFrameConsumer terminalConsumer) throws IOException {
        WebSocketExtensionSpi sentinel = new WebSocketExtensionSpi() {
            {
                onPongReceived = terminalConsumer;
            }
        };

        WebSocketContext context = connection.getContext(sentinel, false);
        WebSocketState state = connection.getInputState();

        switch (state) {
        case OPEN:
            transition(connection, RECEIVE_PONG_FRAME);
            context.onPongReceived(frame);
            break;
        default:
            transition(connection, ERROR);
            context.onError(format("Invalid state %s to be receiving a PONG frame", state));
        }
    }

    public void processText(final WsURLConnectionImpl connection,
                            final Frame frame,
                            final WebSocketFrameConsumer terminalConsumer) throws IOException {
        WebSocketExtensionSpi sentinel = new WebSocketExtensionSpi() {
            {
                onTextReceived = terminalConsumer;
            }
        };
        WebSocketContext context = connection.getContext(sentinel, false);
        WebSocketState state = connection.getInputState();

        switch (state) {
        case OPEN:
            transition(connection, RECEIVE_TEXT_FRAME);
            context.onTextReceived(frame);
            break;
        default:
            transition(connection, ERROR);
            context.onError(format("Invalid state %s to be receiving a TEXT frame", state));
        }
    }

    private static void transition(WsURLConnectionImpl connection, WebSocketTransition transition) {
        WebSocketState state = STATE_MACHINE[connection.getInputState().ordinal()][transition.ordinal()];
        connection.setInputState(state);
    }
}
