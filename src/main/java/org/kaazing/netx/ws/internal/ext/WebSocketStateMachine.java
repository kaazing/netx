
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

package org.kaazing.netx.ws.internal.ext;

import static java.util.EnumSet.allOf;
import static org.kaazing.netx.ws.internal.ext.WebSocketState.CLOSE_FRAME_RECEIVED;
import static org.kaazing.netx.ws.internal.ext.WebSocketState.CONNECTED;
import static org.kaazing.netx.ws.internal.ext.WebSocketState.END;
import static org.kaazing.netx.ws.internal.ext.WebSocketState.PING_FRAME_RECEIVED;
import static org.kaazing.netx.ws.internal.ext.WebSocketState.START;
import static org.kaazing.netx.ws.internal.ext.WebSocketState.UPGRADE_REQUEST_SENT;
import static org.kaazing.netx.ws.internal.ext.WebSocketState.UPGRADE_RESPONSE_RECEIVED;
import static org.kaazing.netx.ws.internal.ext.WebSocketTransition.RECEIVED_BINARY_FRAME;
import static org.kaazing.netx.ws.internal.ext.WebSocketTransition.RECEIVED_CLOSE_FRAME;
import static org.kaazing.netx.ws.internal.ext.WebSocketTransition.RECEIVED_PING_FRAME;
import static org.kaazing.netx.ws.internal.ext.WebSocketTransition.RECEIVED_PONG_FRAME;
import static org.kaazing.netx.ws.internal.ext.WebSocketTransition.RECEIVED_TEXT_FRAME;
import static org.kaazing.netx.ws.internal.ext.WebSocketTransition.RECEIVED_UPGRADE_RESPONSE;
import static org.kaazing.netx.ws.internal.ext.WebSocketTransition.RECEIVED_UPGRADE_RESPONSE_NOT_VALID;
import static org.kaazing.netx.ws.internal.ext.WebSocketTransition.RECEIVED_UPGRADE_RESPONSE_VALID;
import static org.kaazing.netx.ws.internal.ext.WebSocketTransition.SENT_BINARY_FRAME;
import static org.kaazing.netx.ws.internal.ext.WebSocketTransition.SENT_CLOSE_FRAME;
import static org.kaazing.netx.ws.internal.ext.WebSocketTransition.SENT_PONG_FRAME;
import static org.kaazing.netx.ws.internal.ext.WebSocketTransition.SENT_TEXT_FRAME;
import static org.kaazing.netx.ws.internal.ext.WebSocketTransition.SENT_UPGRADE_REQUEST;

import java.util.List;

public class WebSocketStateMachine {
    private static final WebSocketState[][] STATE_MACHINE;

    static {
        int stateCount = WebSocketState.values().length;
        int transitionCount = WebSocketTransition.values().length;

        WebSocketState[][] stateMachine = new WebSocketState[stateCount][transitionCount];
        for (WebSocketState state : allOf(WebSocketState.class)) {
            for (WebSocketTransition transition : allOf(WebSocketTransition.class)) {
                stateMachine[state.ordinal()][transition.ordinal()] = WebSocketState.END;
            }
        }

        stateMachine[START.ordinal()][SENT_UPGRADE_REQUEST.ordinal()] = UPGRADE_REQUEST_SENT;
        stateMachine[UPGRADE_REQUEST_SENT.ordinal()][RECEIVED_UPGRADE_RESPONSE.ordinal()] = UPGRADE_RESPONSE_RECEIVED;
        stateMachine[UPGRADE_RESPONSE_RECEIVED.ordinal()][RECEIVED_UPGRADE_RESPONSE_VALID.ordinal()] = CONNECTED;
        stateMachine[UPGRADE_RESPONSE_RECEIVED.ordinal()][RECEIVED_UPGRADE_RESPONSE_NOT_VALID.ordinal()] = END;

        stateMachine[CONNECTED.ordinal()][RECEIVED_PING_FRAME.ordinal()] = PING_FRAME_RECEIVED;
        stateMachine[PING_FRAME_RECEIVED.ordinal()][SENT_PONG_FRAME.ordinal()] = CONNECTED;
        stateMachine[CONNECTED.ordinal()][RECEIVED_PONG_FRAME.ordinal()] = CONNECTED;

        stateMachine[CONNECTED.ordinal()][RECEIVED_CLOSE_FRAME.ordinal()] = CLOSE_FRAME_RECEIVED;
        stateMachine[CLOSE_FRAME_RECEIVED.ordinal()][SENT_CLOSE_FRAME.ordinal()] = END;
        stateMachine[CONNECTED.ordinal()][SENT_CLOSE_FRAME.ordinal()] = END;

        stateMachine[CONNECTED.ordinal()][RECEIVED_BINARY_FRAME.ordinal()] = CONNECTED;
        stateMachine[CONNECTED.ordinal()][SENT_BINARY_FRAME.ordinal()] = CONNECTED;
        stateMachine[CONNECTED.ordinal()][RECEIVED_TEXT_FRAME.ordinal()] = CONNECTED;
        stateMachine[CONNECTED.ordinal()][SENT_TEXT_FRAME.ordinal()] = CONNECTED;

        STATE_MACHINE = stateMachine;
    }

    private final List<WebSocketHooks> extensionHooks;

    public WebSocketStateMachine(List<WebSocketHooks> extensionHooks) {
        this.extensionHooks = extensionHooks;
    }
}
