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
package org.kaazing.netx.ws.internal.ext.frame;

import java.nio.ByteBuffer;
import java.security.SecureRandom;

public final class FrameFactory extends Flyweight {

    private final Close close = new Close();
    private final Continuation continuation;
    private final Data data;
    private final Ping ping = new Ping();
    private final Pong pong = new Pong();

    private final byte[] mask;
    private final SecureRandom random;

    private FrameFactory(int maxWsMessageSize) {
        continuation = new Continuation(maxWsMessageSize);
        data = new Data(maxWsMessageSize);
        this.mask = new byte[4];
        this.random = new SecureRandom();
    }

    public static FrameFactory newInstance(int maxWsMessageSize) {
        return new FrameFactory(maxWsMessageSize);
    }

    public Frame wrap(ByteBuffer buffer, int offset) throws ProtocolException {
        Frame frame = null;
        switch(FrameUtil.getOpCode(buffer, offset)) {
        case BINARY:
            frame = data.wrap(buffer, offset);
            break;
        case CLOSE:
            frame = close.wrap(buffer, offset);
            break;
        case CONTINUATION:
            frame = continuation.wrap(buffer, offset);
            break;
        case PING:
            frame = ping.wrap(buffer, offset);
            break;
        case PONG:
            frame = pong.wrap(buffer, offset);
            break;
        case TEXT:
            frame = data.wrap(buffer, offset);
            break;
        default:
            Frame.protocolError(null);
            break;
        }
        return frame;
    }

    public Frame createFrame(OpCode opcode, boolean fin, boolean masked, byte[] payload) {
        return encode(opcode, fin, masked, payload);
    }

    private Frame encode(OpCode opcode, boolean fin, boolean masked, byte[] payload) {
        int offset = 0;
        int capacity = FrameUtil.calculateNeed(masked, payload);
        ByteBuffer buffer = ByteBuffer.wrap(new byte[capacity]);
        byte[] maskBuf = FrameUtil.EMPTY_MASK;

        if (masked) {
            random.nextBytes(mask);
            maskBuf = mask;
        }

        FrameUtil.encode(buffer, offset, opcode, fin, masked, maskBuf, payload);
        return wrap(buffer, offset);
    }
}
