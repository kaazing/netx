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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;

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

    public WebSocketOutputStateMachine() {
    }

    public void start(WsURLConnectionImpl connection) {
        connection.setOutputState(WebSocketState.START);
    }

    public ByteBuffer sendBinaryFrame(DefaultWebSocketContext context, byte flagsAndOpcode, ByteBuffer payload)
            throws IOException {
        WebSocketState state = context.getConnection().getInputState();

        switch (state) {
        case OPEN:
            transition(context.getConnection(), WebSocketTransition.SEND_BINARY_FRAME);
            return context.doNextBinaryFrameIsBeingSentHook(flagsAndOpcode, payload);
        default:
            throw new IllegalStateException(format("Invalid state %s to be sending a BINARY frame", state));
        }
    }

    public ByteBuffer sendCloseFrame(DefaultWebSocketContext context, byte flagsAndOpcode, ByteBuffer payload)
            throws IOException {
        WebSocketState state = context.getConnection().getInputState();

        switch (state) {
        case OPEN:
            // Do not transition to CLOSED state at this point as we still haven't yet sent the CLOSE frame.
            return context.doNextCloseFrameIsBeingSentHook(flagsAndOpcode, payload);
        default:
            throw new IllegalStateException(format("Invalid state %s to be sending a CLOSE frame", state));
        }
    }

    public ByteBuffer sendPongFrame(DefaultWebSocketContext context, byte flagsAndOpcode, ByteBuffer payload)
            throws IOException {
        WebSocketState state = context.getConnection().getInputState();

        switch (state) {
        case OPEN:
            transition(context.getConnection(), WebSocketTransition.SEND_PONG_FRAME);
            return context.doNextPongFrameIsBeingSentHook(flagsAndOpcode, payload);
        default:
            throw new IllegalStateException(format("Invalid state %s to be sending a PONG frame", state));
        }
    }

    public CharBuffer sendTextFrame(DefaultWebSocketContext context, byte flagsAndOpcode, CharBuffer payload)
            throws IOException {
        WebSocketState state = context.getConnection().getInputState();

        switch (state) {
        case OPEN:
            transition(context.getConnection(), WebSocketTransition.SEND_TEXT_FRAME);
            return context.doNextTextFrameIsBeingSentHook(flagsAndOpcode, payload);
        default:
            throw new IllegalStateException(format("Invalid state %s to be sending a TEXT frame", state));
        }
    }

    private static void transition(WsURLConnectionImpl connection, WebSocketTransition transition) {
        WebSocketState state = STATE_MACHINE[connection.getInputState().ordinal()][transition.ordinal()];
        connection.setOutputState(state);
    }
}
