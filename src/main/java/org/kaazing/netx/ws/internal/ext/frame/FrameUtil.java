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

public final class FrameUtil {
    public static final byte[] EMPTY_MASK = new byte[] {0x00, 0x00, 0x00, 0x00};

    private FrameUtil() {

    }

    public static int calculateNeed(boolean masked, byte[] payload) {
        int capacity = 2; // opcode, length

        if (payload == null) {
            if (!masked) {
                return capacity;
            }

            return capacity + 4;
        }

        if (payload.length < 126) {
            return capacity;
        }
        else if (payload.length <= 0xFFFF) {
            capacity += 2;
        }
        else {
            capacity += 8;
        }

        if (masked) {
            capacity += 4;
        }

        capacity += payload.length;
        return capacity;
    }

    public static OpCode getOpCode(ByteBuffer buffer, int offset) {
        short byte0 = buffer.get(offset);
        return OpCode.fromInt(byte0 & 0x0F);
    }

    public static void encode(
            ByteBuffer buffer,
            int offset,
            OpCode opcode,
            boolean fin,
            boolean masked,
            byte[] mask,
            byte[] payload) {

        int length = payload == null ? 0 : payload.length;
        int need = FrameUtil.calculateNeed(masked, payload);
        int capacity = buffer.limit() - offset;

        if (need > capacity) {
            final String msg = String.format("need=%d is beyond capacity=%d", need, capacity);
            throw new IndexOutOfBoundsException(msg);
        }
        offset += FrameUtil.putFinAndOpCode(buffer, offset, opcode, fin);
        offset += FrameUtil.putLengthAndMaskBit(buffer, offset, length, masked);
        offset += FrameUtil.putMaskAndPayload(buffer, offset, masked, mask, payload);
    }

    public static void getBytes(ByteBuffer buffer, int offset, byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = buffer.get(offset + i);
        }
    }

    public static void putBytes(ByteBuffer buffer, int offset, byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            buffer.put(offset + i, bytes[i]);
        }
    }

    private static int putFinAndOpCode(ByteBuffer buffer, int offset, OpCode opcode, boolean fin) {
        byte byte0 = (byte) (OpCode.toInt(opcode) | (fin ? 0x80 : 0x00));
        buffer.put(offset, byte0);
        return 1;
    }

    private static int putLengthAndMaskBit(ByteBuffer buffer, int offset, int length, boolean masked) {
        if (length < 126) {
            buffer.put(offset, (byte) (length | (masked ? 0x80 : 0x00)));
            return 1;
        }
        else if (length <= 0xFFFF) {
            buffer.put(offset, (byte) (126 | (masked ? 0x80 : 0x00)));
            buffer.put(offset + 1, (byte) (length >> 8 & 0xFF));
            buffer.put(offset + 2, (byte) (length >> 0 & 0xFF));
            return 3;
        }
        else {
            buffer.put(offset, (byte) (127 | (masked ? 0x80 : 0x00)));
            buffer.put(offset + 1, (byte) (length >> 56 & 0xFF));
            buffer.put(offset + 2, (byte) (length >> 48 & 0xFF));
            buffer.put(offset + 3, (byte) (length >> 40 & 0xFF));
            buffer.put(offset + 4, (byte) (length >> 32 & 0xFF));
            buffer.put(offset + 5, (byte) (length >> 24 & 0xFF));
            buffer.put(offset + 6, (byte) (length >> 16 & 0xFF));
            buffer.put(offset + 7, (byte) (length >> 8 & 0xFF));
            buffer.put(offset + 8, (byte) (length >> 0 & 0xFF));
            return 9;
        }
    }

    private static int putMaskAndPayload(ByteBuffer buffer, int offset, boolean masked, byte[] mask, byte[] payload) {
        if (payload == null) {
            if (masked) {
                FrameUtil.putBytes(buffer, offset, EMPTY_MASK);
                return offset + 4;
            }
            return offset;
        }

        if (masked) {
            for (int i = 0; i < mask.length; i++) {
                buffer.put(offset++, mask[i]);
            }

            for (int i = 0; i < payload.length; i++) {
                buffer.put(offset++, (byte) (payload[i] ^ mask[i % mask.length]));
            }
        }
        else {
            for (int i = 0; i < payload.length; i++) {
                buffer.put(offset++, payload[i]);
            }
        }

        return offset;
    }
}
