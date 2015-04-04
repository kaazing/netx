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
import static org.kaazing.netx.ws.internal.WebSocketTransition.ERROR;
import static org.kaazing.netx.ws.internal.WebSocketTransition.SEND_BINARY_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.SEND_CLOSE_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.SEND_CONTINUATION_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.SEND_PONG_FRAME;
import static org.kaazing.netx.ws.internal.WebSocketTransition.SEND_TEXT_FRAME;

import java.io.IOException;

import org.kaazing.netx.ws.internal.ext.WebSocketContext;
import org.kaazing.netx.ws.internal.ext.WebSocketExtensionSpi;
import org.kaazing.netx.ws.internal.ext.flyweight.Frame;
import org.kaazing.netx.ws.internal.ext.flyweight.OpCode;

public final class WebSocketOutputStateMachine {
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

        stateMachine[OPEN.ordinal()][SEND_PONG_FRAME.ordinal()] = OPEN;
        stateMachine[OPEN.ordinal()][SEND_CLOSE_FRAME.ordinal()] = CLOSED;
        stateMachine[OPEN.ordinal()][SEND_BINARY_FRAME.ordinal()] = OPEN;
        stateMachine[OPEN.ordinal()][SEND_CONTINUATION_FRAME.ordinal()] = OPEN;
        stateMachine[OPEN.ordinal()][SEND_TEXT_FRAME.ordinal()] = OPEN;

        STATE_MACHINE = stateMachine;
    }

    private static final WebSocketOutputStateMachine INSTANCE = new WebSocketOutputStateMachine();

    private WebSocketOutputStateMachine() {
    }

    public static WebSocketOutputStateMachine instance() {
        return INSTANCE;
    }

    public void start(WsURLConnectionImpl connection) {
        connection.setOutputState(WebSocketState.START);
    }

    public void processFrame(final WsURLConnectionImpl connection,
                             final Frame frame,
                             final WebSocketExtensionSpi sentinel) throws IOException {
        WebSocketContext context = connection.getContext(sentinel, true);
        WebSocketState state = connection.getOutputState();
        OpCode opcode = frame.opCode();

        switch (state) {
        case OPEN:
            switch (opcode) {
            case BINARY:
                transition(connection, SEND_BINARY_FRAME);
                context.onBinarySent(frame);
                break;
            case CLOSE:
                context.onCloseSent(frame);
                break;
            case CONTINUATION:
                transition(connection, SEND_CONTINUATION_FRAME);
                context.onContinuationSent(frame);
                break;
            case PONG:
                transition(connection, SEND_PONG_FRAME);
                context.onPongSent(frame);
                break;
            case TEXT:
                transition(connection, SEND_TEXT_FRAME);
                context.onTextSent(frame);
                break;
            default:
                break;
            }
            break;
        default:
            transition(connection, ERROR);
            context.onError(format("Invalid state %s to be sending a %s frame", state, opcode));
        }
    }

    private static void transition(WsURLConnectionImpl connection, WebSocketTransition transition) {
        WebSocketState state = STATE_MACHINE[connection.getOutputState().ordinal()][transition.ordinal()];
        connection.setOutputState(state);
    }
}
