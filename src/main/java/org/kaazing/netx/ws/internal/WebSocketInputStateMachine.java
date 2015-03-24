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
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVED_BINARY_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVED_CLOSE_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVED_PING_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVED_PONG_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVED_TEXT_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVED_UPGRADE_RESPONSE;

import java.io.IOException;

import org.kaazing.netx.ws.internal.ext.WebSocketContext;
import org.kaazing.netx.ws.internal.ext.WebSocketExtensionSpi;
import org.kaazing.netx.ws.internal.ext.flyweight.Close;
import org.kaazing.netx.ws.internal.ext.flyweight.Data;
import org.kaazing.netx.ws.internal.ext.flyweight.FrameFactory;
import org.kaazing.netx.ws.internal.ext.flyweight.Ping;
import org.kaazing.netx.ws.internal.ext.flyweight.Pong;
import org.kaazing.netx.ws.internal.ext.function.WebSocketFrameConsumer;

public class WebSocketInputStateMachine {
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

    public void processBinary(final WsURLConnectionImpl connection,
                              final Data dataFrame,
                              final WebSocketFrameConsumer terminalConsumer)
            throws IOException {
        WebSocketExtensionSpi sentinel = new WebSocketExtensionSpi() {
            {
                onBinaryFrameReceived = terminalConsumer;
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
            transition(connection, WebSocketTransition.ERROR);
            context.onError(format("Invalid state %s to be receiving a BINARY frame", state));
        }
    }

    public void processClose(final WsURLConnectionImpl connection,
                             final Close closeFrame,
                             final WebSocketFrameConsumer terminalConsumer) throws IOException {
        WebSocketExtensionSpi sentinel = new WebSocketExtensionSpi() {
            {
                onCloseFrameReceived = terminalConsumer;
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
            transition(connection, WebSocketTransition.ERROR);
            context.onError(format("Invalid state %s to be receiving a CLOSE frame", state));
        }
    }

    public void processPing(final WsURLConnectionImpl connection,
                            final Ping pingFrame,
                            final WebSocketFrameConsumer terminalConsumer) throws IOException {
        WebSocketExtensionSpi sentinel = new WebSocketExtensionSpi() {
            {
                onPingFrameReceived = terminalConsumer;
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
            transition(connection, WebSocketTransition.ERROR);
            context.onError(format("Invalid state %s to be receiving a PING frame", state));
        }
    }

    public void processPong(final WsURLConnectionImpl connection,
                            final Pong pongFrame,
                            final WebSocketFrameConsumer terminalConsumer) throws IOException {
        WebSocketExtensionSpi sentinel = new WebSocketExtensionSpi() {
            {
                onPongFrameReceived = terminalConsumer;
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
            transition(connection, WebSocketTransition.ERROR);
            context.onError(format("Invalid state %s to be receiving a PONG frame", state));
        }
    }

    public void processText(final WsURLConnectionImpl connection,
                            final Data dataFrame,
                            final WebSocketFrameConsumer terminalConsumer) throws IOException {
        WebSocketExtensionSpi sentinel = new WebSocketExtensionSpi() {
            {
                onTextFrameReceived = terminalConsumer;
            }
        };
        WebSocketContext context = connection.getContext(sentinel, false);
        WebSocketState state = connection.getInputState();

        switch (state) {
        case OPEN:
            transition(connection, WebSocketTransition.RECEIVED_TEXT_FRAME);
            context.onTextReceived(dataFrame);
            break;
        default:
            transition(connection, WebSocketTransition.ERROR);
            context.onError(format("Invalid state %s to be receiving a TEXT frame", state));
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
