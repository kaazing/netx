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
package org.kaazing.netx.ws.internal.ext.flyweight;

import static java.lang.String.format;

import java.nio.ByteBuffer;

public class FrameRW extends Frame {
    private static final String MSG_INDEX_OUT_OF_BOUNDS = "offset = %d; (offset + length) = %d; buffer length = %d";
    public static final byte[] EMPTY_MASK = new byte[] {0x00, 0x00, 0x00, 0x00};

    private static final byte FIN_MASK = (byte) 0x80;
    private static final byte OP_CODE_MASK = 0x0F;
    private static final byte LENGTH_BYTE_1_MASK = 0x7F;

    private static final int LENGTH_OFFSET = 1;

    public FrameRW() {
    }

    @Override
    public boolean fin() {
        checkBuffer(buffer());
        return (uint8Get(buffer(), offset()) & FIN_MASK) != 0;
    }

    @Override
    public int flags() {
        checkBuffer(buffer());

        byte leadByte = (byte) uint8Get(buffer(), offset());
        return (leadByte & 0x70) >> 4;
    }

    @Override
    public int length() {
        checkBuffer(buffer());
        return limit() - offset();
    }

    @Override
    public int limit() {
        checkBuffer(buffer());
        return payloadOffset() + payloadLength();
    }

    @Override
    public OpCode opCode() {
        checkBuffer(buffer());

        short byte0 = uint8Get(buffer(), offset());
        return OpCode.fromInt(byte0 & OP_CODE_MASK);
    }

    @Override
    public int payloadLength() {
        checkBuffer(buffer());
        int length = uint8Get(buffer(), offset() + LENGTH_OFFSET) & LENGTH_BYTE_1_MASK;

        switch (length) {
        case 126:
            return uint16Get(buffer(), offset() + LENGTH_OFFSET + 1);
        case 127:
            return (int) int64Get(buffer(), offset() + LENGTH_OFFSET + 1);
        default:
            return length;
        }
    }

    @Override
    public int payloadOffset() {
        checkBuffer(buffer());

        int index = offset() + LENGTH_OFFSET;
        int lengthByte1 = uint8Get(buffer(), index) & LENGTH_BYTE_1_MASK;
        index += 1;

        switch (lengthByte1) {
        case 126:
            index += 2;
            break;
        case 127:
            index += 8;
            break;
        default:
            break;
        }

        return index;
    }

    @Override
    public FrameRW wrap(ByteBuffer buffer, int offset) {
        super.wrap(buffer, offset);
        return this;
    }

    // Mutators

    /**
     * Sets the FIN bit in the lead byte of the frame. If the FIN bit is set, then this is the final frame of the message.
     *
     * @param fin   true if this is the final frame, otherwise false
     */
    public void fin(boolean fin) {
        checkBuffer(buffer());

        byte leadByte = (byte) Flyweight.uint8Get(buffer(), offset());
        leadByte = (byte) (leadByte | (fin ? 0x80 : 0x00));
        buffer().put(offset(), leadByte);
    }

    /**
     * Sets the opcode in the lead byte of the frame.
     *
     * @param opcode   OpCode
     */
    public void opCode(OpCode opcode) {
        checkBuffer(buffer());

        byte leadByte = (byte) Flyweight.uint8Get(buffer(), offset());
        leadByte = (byte) (leadByte & 0xF0); // Clear the current opcode before setting the new one.
        leadByte |= OpCode.toInt(opcode);
        buffer().put(offset(), leadByte);
    }

    /**
     * Puts the specified payload into the buffer without any masking.
     *
     * @param buf      source byte[]
     * @param offset   offset into the passed in byte[]
     * @param length   number of bytes in the specified byte[] to be used as payload
     */
    public void payloadPut(byte[] buf, int offset, int length) {
        if (buf != null) {
            if ((offset < 0) || (length < 0) || (offset + length > buf.length)) {
                throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, offset + length, buf.length));
            }
        }

        if (buf == null) {
            length = 0;
        }

        checkBuffer(buffer());
        payloadLength(length, false);

        if (buf != null) {
            System.arraycopy(buf, offset, buffer().array(), payloadOffset(), length);
        }
    }

    /**
     * Puts the specified payload into the buffer without any masking.
     *
     * @param buf      source ByteBuffer
     * @param offset   offset into the passed in ByteBuffer
     * @param length   number of bytes in the specified ByteBuffer to be used as payload
     */
    public void payloadPut(ByteBuffer buf, int offset, int length) {
        if (buf != null) {
            if ((offset < 0) || (length < 0) || (offset + length > buf.capacity())) {
                throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, offset + length, buf.capacity()));
            }
        }

        if (buf == null) {
            length = 0;
        }

        checkBuffer(buffer());
        payloadLength(length, false);

        int dataOffset = payloadOffset();

        // Not using System.arraycopy() as the passed in ByteBuffer could be read-only and using buf.array() to get to the
        // byte[] will result in an exception.
        for (int i = 0; i < length; i++) {
            buffer().put(dataOffset++, buf.get(offset++));
        }
    }

    private void payloadLength(long payloadLength, boolean masked) {
        checkBuffer(buffer());

        int lengthPosition = offset() + LENGTH_OFFSET;

        if (payloadLength < 126) {
            buffer().put(lengthPosition, (byte) (payloadLength | (masked ? 0x80 : 0x00)));
        }
        else if (payloadLength <= 0xFFFF) {
            buffer().put(lengthPosition, (byte) (126 | (masked ? 0x80 : 0x00)));
            buffer().put(lengthPosition + 1, (byte) (payloadLength >> 8 & 0xFF));
            buffer().put(lengthPosition + 2, (byte) (payloadLength >> 0 & 0xFF));
        }
        else {
            buffer().put(lengthPosition, (byte) (127 | (masked ? 0x80 : 0x00)));
            buffer().put(lengthPosition + 1, (byte) (payloadLength >> 56 & 0xFF));
            buffer().put(lengthPosition + 2, (byte) (payloadLength >> 48 & 0xFF));
            buffer().put(lengthPosition + 3, (byte) (payloadLength >> 40 & 0xFF));
            buffer().put(lengthPosition + 4, (byte) (payloadLength >> 32 & 0xFF));
            buffer().put(lengthPosition + 5, (byte) (payloadLength >> 24 & 0xFF));
            buffer().put(lengthPosition + 6, (byte) (payloadLength >> 16 & 0xFF));
            buffer().put(lengthPosition + 7, (byte) (payloadLength >> 8 & 0xFF));
            buffer().put(lengthPosition + 8, (byte) (payloadLength >> 0 & 0xFF));
        }
    }

    private static void checkBuffer(ByteBuffer buffer) {
        if (buffer == null) {
            throw new IllegalStateException("Flyweight has not been wrapped/populated yet with a ByteBuffer.");
        }
    }
}
