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

import static java.lang.String.format;
import static org.kaazing.netx.ws.internal.util.FrameUtil.EMPTY_MASK;
import static org.kaazing.netx.ws.internal.util.FrameUtil.calculateNeed;
import static org.kaazing.netx.ws.internal.util.FrameUtil.getOpCode;
import static org.kaazing.netx.ws.internal.util.FrameUtil.putFinAndOpCode;
import static org.kaazing.netx.ws.internal.util.FrameUtil.putLengthAndMaskBit;
import static org.kaazing.netx.ws.internal.util.FrameUtil.putMaskAndPayload;

import java.nio.ByteBuffer;
import java.security.SecureRandom;

public final class FrameFactory extends Flyweight {
    private static final int MAX_COMMAND_FRAME_PAYLOAD = 125;

    private final Close close = new Close();
    private final Continuation continuation;
    private final Data data;
    private final Ping ping = new Ping();
    private final Pong pong = new Pong();
    private final int maxMessageSize;

    private final byte[] mask;
    private final SecureRandom random;

    private FrameFactory(int maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
        this.mask = new byte[4];
        this.random = new SecureRandom();
        this.continuation = new Continuation(maxMessageSize);
        this.data = new Data(maxMessageSize);

        byte[] closeBuf = new byte[131];
        closeBuf[0] = (byte) (0x80 | OpCode.toInt(OpCode.CLOSE));
        close.wrap(ByteBuffer.wrap(closeBuf), 0);

        byte[] pingBuf = new byte[131];
        pingBuf[0] = (byte) (0x80 | OpCode.toInt(OpCode.PING));
        ping.wrap(ByteBuffer.wrap(pingBuf), 0);

        byte[] pongBuf = new byte[131];
        pongBuf[0] = (byte) (0x80 | OpCode.toInt(OpCode.PONG));
        pong.wrap(ByteBuffer.wrap(pongBuf), 0);

        byte[] continationBuf = new byte[maxMessageSize];
        continuation.wrap(ByteBuffer.wrap(continationBuf), 0);

        byte[] dataBuf = new byte[maxMessageSize];
        data.wrap(ByteBuffer.wrap(dataBuf), 0);
    }

    public static FrameFactory newInstance(int maxMessageSize) {
        return new FrameFactory(maxMessageSize);
    }

    public Frame wrap(ByteBuffer buffer, int offset) {
        Frame frame = null;
        OpCode opcode = getOpCode(buffer, offset);

        switch(opcode) {
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
            throw new IllegalStateException(format("Protocol Violation: Invalid opcode: %s", opcode));
        }
        return frame;
    }

    public Frame getFrame(OpCode opcode, boolean fin, boolean masked, long payloadLength) {
        Frame frame = null;

        switch (opcode) {
        case BINARY:
        case TEXT:
            ensureCapacity(data, masked, payloadLength, maxMessageSize);
            frame = data;
            break;
        case CLOSE:
            ensureCapacity(close, masked, payloadLength, MAX_COMMAND_FRAME_PAYLOAD);
            frame = close;
            break;
        case CONTINUATION:
            ensureCapacity(continuation, masked, payloadLength, maxMessageSize);
            frame = continuation;
            break;
        case PING:
            ensureCapacity(ping, masked, payloadLength, MAX_COMMAND_FRAME_PAYLOAD);
            frame = ping;
            break;
        case PONG:
            ensureCapacity(pong, masked, payloadLength, MAX_COMMAND_FRAME_PAYLOAD);
            frame = pong;
            break;
        default:
            throw new IllegalStateException(format("Protocol Violation: Invalid opcode: %s", opcode));
        }

        putFinAndOpCode(frame.buffer(), frame.offset(), opcode, fin);
        putLengthAndMaskBit(frame.buffer(), frame.offset() + 1, (int) payloadLength, masked);
        return frame;
    }

    public Frame getFrame(OpCode opcode, boolean fin, boolean masked, byte[] payload, int offset, long length) {
        Frame frame = getFrame(opcode, fin, masked, (payload == null) ? 0 : length);
        byte[] maskBuf = EMPTY_MASK;

        if (masked) {
            random.nextBytes(mask);
            maskBuf = mask;
        }

        putMaskAndPayload(frame.buffer(), frame.getMaskOffset(), masked, maskBuf, payload, offset, length);
        return frame;
    }

    private void ensureCapacity(Frame frame, boolean masked, long payloadLength, long maxPayloadLength) {
        int need = calculateNeed(masked, payloadLength);
        ByteBuffer buf = frame.buffer();

        if (buf == null) {
            buf = ByteBuffer.allocate((int) Math.max(need, maxPayloadLength));
            frame.wrap(buf, 0, false);
            return;
        }

        int size = buf.capacity();
        if (need > size) {
            buf = ByteBuffer.allocate(need);
            System.arraycopy(frame.buffer().array(), frame.offset(), buf.array(), 0, size);
            frame.wrap(buf, 0, false);
        }
    }
}
