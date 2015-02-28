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
import static org.kaazing.netx.ws.internal.WebSocketTransition.SEND_BINARY_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.SEND_CLOSE_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.SEND_PONG_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.SEND_TEXT_FRAME;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.kaazing.netx.ws.internal.ext.WebSocketExtensionHooks;

public class WebSocketOutputStateMachine {
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

    private final List<WebSocketExtensionHooks> extensionsHooks;

    public WebSocketOutputStateMachine() {
        this.extensionsHooks = Collections.emptyList();
    }

    public WebSocketOutputStateMachine(List<WebSocketExtensionHooks> extensionHooks) {
        List<WebSocketExtensionHooks> reversedList = new ArrayList<WebSocketExtensionHooks>();
        reversedList.addAll(extensionHooks);
        Collections.reverse(reversedList);
        this.extensionsHooks = reversedList;
    }

    public void start(WsURLConnectionImpl connection) {
        connection.setWebSocketOutputState(WebSocketState.START);
    }

    public ByteBuffer sendBinaryFrame(WsURLConnectionImpl connection, byte flagsAndOpcode, ByteBuffer payload) {
        WebSocketState state = connection.getWebSocketInputState();
        ByteBuffer transformedPayload = payload;

        switch (state) {
        case OPEN:
            transition(connection, WebSocketTransition.SEND_BINARY_FRAME);

            if (extensionsHooks != null) {
                for (WebSocketExtensionHooks hooks : extensionsHooks) {
                    transformedPayload = hooks.whenBinaryFrameIsBeingSent.apply(connection, flagsAndOpcode, transformedPayload);
                }
            }
            break;
        default:
            throw new IllegalStateException(format("Invalid state %s to be sending a BINARY frame", state));
        }

        return transformedPayload;
    }

    public ByteBuffer sendCloseFrame(WsURLConnectionImpl connection, byte flagsAndOpcode, ByteBuffer payload) {
        WebSocketState state = connection.getWebSocketInputState();
        ByteBuffer transformedPayload = payload;

        switch (state) {
        case OPEN:
            // Do not transition to CLOSED state at this point as we still haven't yet sent the CLOSE frame.
            if (extensionsHooks != null) {
                for (WebSocketExtensionHooks hooks : extensionsHooks) {
                    transformedPayload = hooks.whenCloseFrameIsBeingSent.apply(connection, flagsAndOpcode, transformedPayload);
                }
            }
            break;
        default:
            throw new IllegalStateException(format("Invalid state %s to be sending a CLOSE frame", state));
        }

        return transformedPayload;
    }

    public ByteBuffer sendPongFrame(WsURLConnectionImpl connection, byte flagsAndOpcode, ByteBuffer payload) {
        WebSocketState state = connection.getWebSocketInputState();
        ByteBuffer transformedPayload = payload;

        switch (state) {
        case OPEN:
            transition(connection, WebSocketTransition.SEND_PONG_FRAME);

            if (extensionsHooks != null) {
                for (WebSocketExtensionHooks hooks : extensionsHooks) {
                    transformedPayload = hooks.whenPongFrameIsBeingSent.apply(connection, flagsAndOpcode, transformedPayload);
                }
            }
            break;
        default:
            throw new IllegalStateException(format("Invalid state %s to be sending a PONG frame", state));
        }

        return transformedPayload;
    }

    public CharBuffer sendTextFrame(WsURLConnectionImpl connection, byte flagsAndOpcode, CharBuffer payload) {
        WebSocketState state = connection.getWebSocketInputState();
        CharBuffer transformedPayload = payload;

        switch (state) {
        case OPEN:
            transition(connection, WebSocketTransition.SEND_TEXT_FRAME);

            if (extensionsHooks != null) {
                for (WebSocketExtensionHooks hooks : extensionsHooks) {
                    transformedPayload = hooks.whenTextFrameIsBeingSent.apply(connection, flagsAndOpcode, transformedPayload);
                }
            }
            break;
        default:
            throw new IllegalStateException(format("Invalid state %s to be sending a TEXT frame", state));
        }

        return transformedPayload;
    }

    private static void transition(WsURLConnectionImpl connection, WebSocketTransition transition) {
        WebSocketState state = STATE_MACHINE[connection.getWebSocketInputState().ordinal()][transition.ordinal()];
        connection.setWebSocketOutputState(state);
    }
}
