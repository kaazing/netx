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

import org.kaazing.netx.ws.internal.ext.agrona.DirectBuffer;
import org.kaazing.netx.ws.internal.ext.agrona.MutableDirectBuffer;

public final class FrameUtils {
    public static final byte[] EMPTY_MASK = new byte[] {0x00, 0x00, 0x00, 0x00};

    private FrameUtils() {

    }

    public static int calculateCapacity(boolean masked, byte[] payload) {
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

    public static OpCode getOpCode(DirectBuffer buffer, int offset) {
        short byte0 = buffer.getByte(offset);
        return OpCode.fromInt(byte0 & 0x0F);
    }

    public static void encode(
            MutableDirectBuffer buffer,
            int offset,
            OpCode opcode,
            boolean fin,
            boolean masked,
            byte[] mask,
            byte[] payload) {

        int length = payload == null ? 0 : payload.length;
        int capacity = FrameUtils.calculateCapacity(masked, payload);

        buffer.checkLimit(capacity);

        offset += FrameUtils.putFinAndOpCode(buffer, offset, opcode, fin);
        offset += FrameUtils.putLengthAndMaskBit(buffer, offset, length, masked);
        offset += FrameUtils.putMaskAndPayload(buffer, offset, masked, mask, payload);
    }

    private static int putFinAndOpCode(MutableDirectBuffer buffer, int offset, OpCode opcode, boolean fin) {
        byte byte0 = (byte) (OpCode.toInt(opcode) | (fin ? 0x80 : 0x00));
        buffer.putByte(offset, byte0);
        return 1;
    }

    private static int putLengthAndMaskBit(MutableDirectBuffer buffer, int offset, int length, boolean masked) {
        if (length < 126) {
            buffer.putByte(offset, (byte) (length | (masked ? 0x80 : 0x00)));
            return 1;
        }
        else if (length <= 0xFFFF) {
            buffer.putByte(offset, (byte) (126 | (masked ? 0x80 : 0x00)));
            buffer.putByte(offset + 1, (byte) (length >> 8 & 0xFF));
            buffer.putByte(offset + 2, (byte) (length >> 0 & 0xFF));
            return 3;
        }
        else {
            buffer.putByte(offset, (byte) (127 | (masked ? 0x80 : 0x00)));
            buffer.putByte(offset + 1, (byte) (length >> 56 & 0xFF));
            buffer.putByte(offset + 2, (byte) (length >> 48 & 0xFF));
            buffer.putByte(offset + 3, (byte) (length >> 40 & 0xFF));
            buffer.putByte(offset + 4, (byte) (length >> 32 & 0xFF));
            buffer.putByte(offset + 5, (byte) (length >> 24 & 0xFF));
            buffer.putByte(offset + 6, (byte) (length >> 16 & 0xFF));
            buffer.putByte(offset + 7, (byte) (length >> 8 & 0xFF));
            buffer.putByte(offset + 8, (byte) (length >> 0 & 0xFF));
            return 9;
        }
    }

    private static int putMaskAndPayload(MutableDirectBuffer buffer, int offset, boolean masked, byte[] mask, byte[] payload) {
        if (payload == null) {
            if (masked) {
                buffer.putBytes(offset, EMPTY_MASK);
            }
            return offset + 4;
        }

        if (masked) {
            for (int i = 0; i < mask.length; i++) {
                buffer.putByte(offset++, mask[i]);
            }

            for (int i = 0; i < payload.length; i++) {
                buffer.putByte(offset++, (byte) (payload[i] ^ mask[i % mask.length]));
            }
        }
        else {
            for (int i = 0; i < payload.length; i++) {
                buffer.putByte(offset++, payload[i]);
            }
        }

        return offset;
    }
}
