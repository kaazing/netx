
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
import static org.kaazing.netx.ws.internal.WebSocketState.CLOSE_FRAME_RECEIVED;
import static org.kaazing.netx.ws.internal.WebSocketState.OPEN;
import static org.kaazing.netx.ws.internal.WebSocketState.START;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVED_BINARY_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVED_CLOSE_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVED_PING_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVED_PONG_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVED_TEXT_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.RECEIVED_UPGRADE_RESPONSE;
import static org.kaazing.netx.ws.internal.WebSocketTransition.SEND_BINARY_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.SEND_CLOSE_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.SEND_PONG_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.SEND_TEXT_FRAME;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Collections;
import java.util.List;

import org.kaazing.netx.ws.internal.ext.WebSocketExtensionHooks;

public class WebSocketStateMachine {
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
        stateMachine[OPEN.ordinal()][SEND_PONG_FRAME.ordinal()] = OPEN;
        stateMachine[OPEN.ordinal()][RECEIVED_PONG_FRAME.ordinal()] = OPEN;

        stateMachine[OPEN.ordinal()][RECEIVED_CLOSE_FRAME.ordinal()] = CLOSE_FRAME_RECEIVED;
        stateMachine[CLOSE_FRAME_RECEIVED.ordinal()][SEND_CLOSE_FRAME.ordinal()] = CLOSED;
        stateMachine[OPEN.ordinal()][SEND_CLOSE_FRAME.ordinal()] = CLOSED;

        stateMachine[OPEN.ordinal()][RECEIVED_BINARY_FRAME.ordinal()] = OPEN;
        stateMachine[OPEN.ordinal()][SEND_BINARY_FRAME.ordinal()] = OPEN;
        stateMachine[OPEN.ordinal()][RECEIVED_TEXT_FRAME.ordinal()] = OPEN;
        stateMachine[OPEN.ordinal()][SEND_TEXT_FRAME.ordinal()] = OPEN;

        STATE_MACHINE = stateMachine;
    }

    private final List<WebSocketExtensionHooks> extensionsHooks;

    public WebSocketStateMachine() {
        this.extensionsHooks = Collections.emptyList();
    }

    public WebSocketStateMachine(List<WebSocketExtensionHooks> extensionHooks) {
        this.extensionsHooks = extensionHooks;
    }

    public void start(WsURLConnectionImpl connection) {
        connection.setWebSocketState(WebSocketState.START);
    }


    public ByteBuffer receivedBinaryFrame(WsURLConnectionImpl connection, ByteBuffer payload) {
        WebSocketState state = connection.getWebSocketState();
        ByteBuffer transformedPayload = payload;

        switch (state) {
        case OPEN:
            transition(connection, WebSocketTransition.RECEIVED_BINARY_FRAME);

            if (extensionsHooks != null) {
                for (WebSocketExtensionHooks hooks : extensionsHooks) {
                    transformedPayload = hooks.whenBinaryFrameReceived.apply(connection, transformedPayload);
                }
            }
            break;
        default:
            throw new IllegalStateException(format("Invalid state %s to be receiving a BINARY frame", state));
        }

        return transformedPayload;
    }

    public ByteBuffer receivedCloseFrame(WsURLConnectionImpl connection, ByteBuffer payload) {
        WebSocketState state = connection.getWebSocketState();
        ByteBuffer transformedPayload = payload;

        switch (state) {
        case OPEN:
            transition(connection, WebSocketTransition.RECEIVED_CLOSE_FRAME);

            if (extensionsHooks != null) {
                for (WebSocketExtensionHooks hooks : extensionsHooks) {
                    transformedPayload = hooks.whenCloseFrameReceived.apply(connection, transformedPayload);
                }
            }
            break;
        default:
            throw new IllegalStateException(format("Invalid state %s to be receiving a CLOSE frame", state));
        }

        return transformedPayload;
    }

    public ByteBuffer receivedPingFrame(WsURLConnectionImpl connection, ByteBuffer payload) {
        WebSocketState state = connection.getWebSocketState();
        ByteBuffer transformedPayload = payload;

        switch (state) {
        case OPEN:
            transition(connection, WebSocketTransition.RECEIVED_PING_FRAME);

            if (extensionsHooks != null) {
                for (WebSocketExtensionHooks hooks : extensionsHooks) {
                    transformedPayload = hooks.whenPingFrameReceived.apply(connection, transformedPayload);
                }
            }
            break;
        default:
            throw new IllegalStateException(format("Invalid state %s to be receiving a PING frame", state));
        }

        return transformedPayload;
    }

    public ByteBuffer receivedPongFrame(WsURLConnectionImpl connection, ByteBuffer payload) {
        WebSocketState state = connection.getWebSocketState();
        ByteBuffer transformedPayload = payload;

        switch (state) {
        case OPEN:
            transition(connection, WebSocketTransition.RECEIVED_PONG_FRAME);

            if (extensionsHooks != null) {
                for (WebSocketExtensionHooks hooks : extensionsHooks) {
                    transformedPayload = hooks.whenPongFrameReceived.apply(connection, transformedPayload);
                }
            }
            break;
        default:
            throw new IllegalStateException(format("Invalid state %s to be receiving a PONG frame", state));
        }

        return transformedPayload;
    }

    public CharBuffer receivedTextFrame(WsURLConnectionImpl connection, CharBuffer payload) {
        WebSocketState state = connection.getWebSocketState();
        CharBuffer transformedPayload = payload;

        switch (state) {
        case OPEN:
            transition(connection, WebSocketTransition.RECEIVED_TEXT_FRAME);

            if (extensionsHooks != null) {
                for (WebSocketExtensionHooks hooks : extensionsHooks) {
                    transformedPayload = hooks.whenTextFrameReceived.apply(connection, transformedPayload);
                }
            }
            break;
        default:
            throw new IllegalStateException(format("Invalid state %s to be receiving a TEXT frame", state));
        }

        return transformedPayload;
    }

    public ByteBuffer sendBinaryFrame(WsURLConnectionImpl connection, ByteBuffer payload) {
        WebSocketState state = connection.getWebSocketState();
        ByteBuffer transformedPayload = payload;

        switch (state) {
        case OPEN:
            transition(connection, WebSocketTransition.SEND_BINARY_FRAME);

            if (extensionsHooks != null) {
                for (WebSocketExtensionHooks hooks : extensionsHooks) {
                    transformedPayload = hooks.whenBinaryFrameIsBeingSent.apply(connection, transformedPayload);
                }
            }
            break;
        default:
            throw new IllegalStateException(format("Invalid state %s to be sending a BINARY frame", state));
        }

        return transformedPayload;
    }

    public ByteBuffer sendCloseFrame(WsURLConnectionImpl connection, ByteBuffer payload) {
        WebSocketState state = connection.getWebSocketState();
        ByteBuffer transformedPayload = payload;

        switch (state) {
        case OPEN:
        case CLOSE_FRAME_RECEIVED:
            // Do not transition to CLOSED state at this point as we still haven't yet sent the CLOSE frame.
            if (extensionsHooks != null) {
                for (WebSocketExtensionHooks hooks : extensionsHooks) {
                    transformedPayload = hooks.whenCloseFrameIsBeingSent.apply(connection, transformedPayload);
                }
            }
            break;
        default:
            throw new IllegalStateException(format("Invalid state %s to be sending a CLOSE frame", state));
        }

        return transformedPayload;
    }

    public ByteBuffer sendPongFrame(WsURLConnectionImpl connection, ByteBuffer payload) {
        WebSocketState state = connection.getWebSocketState();
        ByteBuffer transformedPayload = payload;

        switch (state) {
        case OPEN:
            transition(connection, WebSocketTransition.SEND_PONG_FRAME);

            if (extensionsHooks != null) {
                for (WebSocketExtensionHooks hooks : extensionsHooks) {
                    transformedPayload = hooks.whenPongFrameIsBeingSent.apply(connection, transformedPayload);
                }
            }
            break;
        default:
            throw new IllegalStateException(format("Invalid state %s to be sending a PONG frame", state));
        }

        return transformedPayload;
    }

    public CharBuffer sendTextFrame(WsURLConnectionImpl connection, CharBuffer payload) {
        WebSocketState state = connection.getWebSocketState();
        CharBuffer transformedPayload = payload;

        switch (state) {
        case OPEN:
            transition(connection, WebSocketTransition.SEND_TEXT_FRAME);

            if (extensionsHooks != null) {
                for (WebSocketExtensionHooks hooks : extensionsHooks) {
                    transformedPayload = hooks.whenTextFrameIsBeingSent.apply(connection, transformedPayload);
                }
            }
            break;
        default:
            throw new IllegalStateException(format("Invalid state %s to be sending a TEXT frame", state));
        }

        return transformedPayload;
    }

    private static void transition(WsURLConnectionImpl connection, WebSocketTransition transition) {
        WebSocketState state = STATE_MACHINE[connection.getWebSocketState().ordinal()][transition.ordinal()];
        connection.setWebSocketState(state);
    }
}
