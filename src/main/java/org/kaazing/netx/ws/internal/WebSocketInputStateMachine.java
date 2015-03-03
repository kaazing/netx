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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;

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

    public WebSocketInputStateMachine() {
    }

    public void start(WsURLConnectionImpl connection) {
        connection.setInputState(WebSocketState.START);
    }

    public ByteBuffer receivedBinaryFrame(DefaultWebSocketContext context, byte flagsAndOpcode, ByteBuffer payload)
            throws IOException {
        WebSocketState state = context.getConnection().getInputState();

        switch (state) {
        case OPEN:
            transition(context.getConnection(), WebSocketTransition.RECEIVED_BINARY_FRAME);
            return context.doNextBinaryFrameReceivedHook(flagsAndOpcode, payload);
        default:
            throw new IllegalStateException(format("Invalid state %s to be receiving a BINARY frame", state));
        }
    }

    public ByteBuffer receivedCloseFrame(DefaultWebSocketContext context, byte flagsAndOpcode, ByteBuffer payload)
            throws IOException {
        WebSocketState state = context.getConnection().getInputState();

        switch (state) {
        case OPEN:
            transition(context.getConnection(), WebSocketTransition.RECEIVED_CLOSE_FRAME);
            return context.doNextCloseFrameReceivedHook(flagsAndOpcode, payload);
        default:
            throw new IllegalStateException(format("Invalid state %s to be receiving a CLOSE frame", state));
        }
    }

    public ByteBuffer receivedPingFrame(DefaultWebSocketContext context, byte flagsAndOpcode, ByteBuffer payload)
            throws IOException {
        WebSocketState state = context.getConnection().getInputState();

        switch (state) {
        case OPEN:
            transition(context.getConnection(), WebSocketTransition.RECEIVED_PING_FRAME);
            return context.doNextPingFrameReceivedHook(flagsAndOpcode, payload);
        default:
            throw new IllegalStateException(format("Invalid state %s to be receiving a PING frame", state));
        }
    }

    public ByteBuffer receivedPongFrame(DefaultWebSocketContext context, byte flagsAndOpcode, ByteBuffer payload)
            throws IOException {
        WebSocketState state = context.getConnection().getInputState();

        switch (state) {
        case OPEN:
            transition(context.getConnection(), WebSocketTransition.RECEIVED_PONG_FRAME);
            return context.doNextPongFrameReceivedHook(flagsAndOpcode, payload);
        default:
            throw new IllegalStateException(format("Invalid state %s to be receiving a PONG frame", state));
        }
    }

    public CharBuffer receivedTextFrame(DefaultWebSocketContext context, byte flagsAndOpcode, CharBuffer payload)
            throws IOException {
        WebSocketState state = context.getConnection().getInputState();

        switch (state) {
        case OPEN:
            transition(context.getConnection(), WebSocketTransition.RECEIVED_PONG_FRAME);
            return context.doNextTextFrameReceivedHook(flagsAndOpcode, payload);
        default:
            throw new IllegalStateException(format("Invalid state %s to be receiving a TEXT frame", state));
        }
    }

    private static void transition(WsURLConnectionImpl connection, WebSocketTransition transition) {
        WebSocketState state = STATE_MACHINE[connection.getInputState().ordinal()][transition.ordinal()];
        connection.setInputState(state);
    }
}
