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
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVED_BINARY_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVED_CLOSE_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVED_PING_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVED_PONG_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVED_TEXT_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVED_UPGRADE_RESPONSE;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Collections;
import java.util.List;

import org.kaazing.netx.ws.internal.ext.WebSocketExtensionHooks;

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
        }

        stateMachine[START.ordinal()][RECEIVED_UPGRADE_RESPONSE.ordinal()] = OPEN;

        stateMachine[OPEN.ordinal()][RECEIVED_PING_FRAME.ordinal()] = OPEN;
        stateMachine[OPEN.ordinal()][RECEIVED_PONG_FRAME.ordinal()] = OPEN;
        stateMachine[OPEN.ordinal()][RECEIVED_CLOSE_FRAME.ordinal()] = OPEN;
        stateMachine[OPEN.ordinal()][RECEIVED_BINARY_FRAME.ordinal()] = OPEN;
        stateMachine[OPEN.ordinal()][RECEIVED_TEXT_FRAME.ordinal()] = OPEN;

        STATE_MACHINE = stateMachine;
    }

    private final List<WebSocketExtensionHooks> extensionsHooks;

    public WebSocketInputStateMachine() {
        this.extensionsHooks = Collections.emptyList();
    }

    public WebSocketInputStateMachine(List<WebSocketExtensionHooks> extensionHooks) {
        this.extensionsHooks = extensionHooks;
    }

    public void start(WsURLConnectionImpl connection) {
        connection.setWebSocketInputState(WebSocketState.START);
    }


    public ByteBuffer receivedBinaryFrame(WsURLConnectionImpl connection, byte flagsAndOpcode, ByteBuffer payload) {
        WebSocketState state = connection.getWebSocketInputState();
        ByteBuffer transformedPayload = payload;

        switch (state) {
        case OPEN:
            transition(connection, WebSocketTransition.RECEIVED_BINARY_FRAME);

            if (extensionsHooks != null) {
                for (WebSocketExtensionHooks hooks : extensionsHooks) {
                    transformedPayload = hooks.whenBinaryFrameReceived.apply(connection, flagsAndOpcode, transformedPayload);
                }
            }
            break;
        default:
            throw new IllegalStateException(format("Invalid state %s to be receiving a BINARY frame", state));
        }

        return transformedPayload;
    }

    public ByteBuffer receivedCloseFrame(WsURLConnectionImpl connection, byte flagsAndOpcode, ByteBuffer payload) {
        WebSocketState state = connection.getWebSocketInputState();
        ByteBuffer transformedPayload = payload;

        switch (state) {
        case OPEN:
            transition(connection, WebSocketTransition.RECEIVED_CLOSE_FRAME);

            if (extensionsHooks != null) {
                for (WebSocketExtensionHooks hooks : extensionsHooks) {
                    transformedPayload = hooks.whenCloseFrameReceived.apply(connection, flagsAndOpcode, transformedPayload);
                }
            }
            break;
        default:
            throw new IllegalStateException(format("Invalid state %s to be receiving a CLOSE frame", state));
        }

        return transformedPayload;
    }

    public ByteBuffer receivedPingFrame(WsURLConnectionImpl connection, byte flagsAndOpcode, ByteBuffer payload) {
        WebSocketState state = connection.getWebSocketInputState();
        ByteBuffer transformedPayload = payload;

        switch (state) {
        case OPEN:
            transition(connection, WebSocketTransition.RECEIVED_PING_FRAME);

            if (extensionsHooks != null) {
                for (WebSocketExtensionHooks hooks : extensionsHooks) {
                    transformedPayload = hooks.whenPingFrameReceived.apply(connection, flagsAndOpcode, transformedPayload);
                }
            }
            break;
        default:
            throw new IllegalStateException(format("Invalid state %s to be receiving a PING frame", state));
        }

        return transformedPayload;
    }

    public ByteBuffer receivedPongFrame(WsURLConnectionImpl connection, byte flagsAndOpcode, ByteBuffer payload) {
        WebSocketState state = connection.getWebSocketInputState();
        ByteBuffer transformedPayload = payload;

        switch (state) {
        case OPEN:
            transition(connection, WebSocketTransition.RECEIVED_PONG_FRAME);

            if (extensionsHooks != null) {
                for (WebSocketExtensionHooks hooks : extensionsHooks) {
                    transformedPayload = hooks.whenPongFrameReceived.apply(connection, flagsAndOpcode, transformedPayload);
                }
            }
            break;
        default:
            throw new IllegalStateException(format("Invalid state %s to be receiving a PONG frame", state));
        }

        return transformedPayload;
    }

    public CharBuffer receivedTextFrame(WsURLConnectionImpl connection, byte flagsAndOpcode, CharBuffer payload) {
        WebSocketState state = connection.getWebSocketInputState();
        CharBuffer transformedPayload = payload;

        switch (state) {
        case OPEN:
            transition(connection, WebSocketTransition.RECEIVED_TEXT_FRAME);

            if (extensionsHooks != null) {
                for (WebSocketExtensionHooks hooks : extensionsHooks) {
                    transformedPayload = hooks.whenTextFrameReceived.apply(connection, flagsAndOpcode, transformedPayload);
                }
            }
            break;
        default:
            throw new IllegalStateException(format("Invalid state %s to be receiving a TEXT frame", state));
        }

        return transformedPayload;
    }

    private static void transition(WsURLConnectionImpl connection, WebSocketTransition transition) {
        WebSocketState state = STATE_MACHINE[connection.getWebSocketInputState().ordinal()][transition.ordinal()];
        connection.setWebSocketInputState(state);
    }
}
