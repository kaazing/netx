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
import java.security.SecureRandom;

public class FrameRW extends Frame {
    private static final String MSG_INDEX_OUT_OF_BOUNDS = "offset = %d; (offset + length) = %d; buffer length = %d";
    public static final byte[] EMPTY_MASK = new byte[] {0x00, 0x00, 0x00, 0x00};

    private static final byte FIN_MASK = (byte) 0x80;
    private static final byte OP_CODE_MASK = 0x0F;
    private static final byte MASKED_MASK = (byte) 0x80;
    private static final byte LENGTH_BYTE_1_MASK = 0x7F;

    private static final int LENGTH_OFFSET = 1;
    private static final int MASK_OFFSET = 1;

    private final byte[] mask;
    private final SecureRandom random;

    public FrameRW() {
        this.mask = new byte[4];
        this.random = new SecureRandom();
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
    public int mask() {
        checkBuffer(buffer());

        if (!masked()) {
            return -1;
        }

        return buffer().getInt(maskOffset());
    }

    @Override
    public boolean masked() {
        checkBuffer(buffer());
        return (uint8Get(buffer(), offset() + MASK_OFFSET) & MASKED_MASK) != 0;
    }

    @Override
    public int maskOffset() {
        checkBuffer(buffer());

        if (!masked()) {
            return payloadOffset();
        }

        return payloadOffset() - 4;
    }

    @Override
    public OpCode opCode() {
        checkBuffer(buffer());

        short byte0 = uint8Get(buffer(), offset());
        return OpCode.fromInt(byte0 & OP_CODE_MASK);
    }

    @Override
    public int payloadGet(byte[] buf, int offset, int length) {
        if (buf == null) {
            throw new NullPointerException("Null buffer passed in");
        }
        else if ((offset < 0) || (length < 0) || (offset + length > buf.length)) {
            throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, offset + length, buf.length));
        }

        int min = Math.min(payloadLength(), length);

        if (masked()) {

            int maskIndex = maskOffset();
            int dataIndex = payloadOffset();

            for (int i = 0; i < mask.length; i++) {
                mask[i] = buffer().get(maskIndex++);
            }

            for (int i = 0; i < min; i++) {
                buf[offset++] = (byte) (buffer().get(dataIndex++) ^ mask[i % mask.length]);
            }
        }
        else {
            System.arraycopy(buffer().array(), payloadOffset(), buf, offset, min);
        }

        return min;
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

        if (masked()) {
            index += 4;
        }
        return index;
    }

    @Override
    public FrameRW wrap(ByteBuffer buffer, int offset) {
        super.wrap(buffer, offset);
        return this;
    }

    // Mutators

    public void opCodeAndFin(OpCode opcode, boolean fin) {
        checkBuffer(buffer());

        byte leadByte = (byte) (OpCode.toInt(opcode) | (fin ? 0x80 : 0x00));
        buffer().put(offset(), leadByte);
    }

    /**
     * Puts the specified payload along with the mask bytes into the buffer. The mask-bit is lit up and the payload is
     * masked using the mask bytes.
     *
     * @param buf
     * @param offset
     * @param length
     */
    public void maskedPayloadPut(byte[] buf, int offset, int length) {
        if (buf != null) {
            if ((offset < 0) || (length < 0) || (offset + length > buf.length)) {
                throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, offset + length, buf.length));
            }
        }

        checkBuffer(buffer());

        if (buf == null) {
            length = 0;
        }

        payloadLength(length, true);

        byte[] maskBuf = EMPTY_MASK;
        int dataIndex = payloadOffset();

        if ((buf != null) && (length > 0)) {
            random.nextBytes(mask);
            maskBuf = mask;
        }

        putBytes(buffer(), maskOffset(), maskBuf);

        for (int i = 0; i < length; i++) {
            buffer().put(dataIndex++, (byte) (buf[offset++] ^ maskBuf[i % maskBuf.length]));
        }
    }

    /**
     * Puts the specified payload along with the mask-bytes into the buffer. The mask-bit is lit up and the payload is
     * masked using the mask-bytes.
     *
     * @param buf
     * @param offset
     * @param length
     */
    public void maskedPayloadPut(ByteBuffer buf, int offset, int length) {
        if (buf != null) {
            if ((offset < 0) || (length < 0) || (offset + length > buf.limit())) {
                throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, offset + length, buf.limit()));
            }
        }

        if (buf == null) {
            length = 0;
        }

        checkBuffer(buffer());
        payloadLength(length, true);

        byte[] maskBuf = EMPTY_MASK;
        int dataIndex = payloadOffset();

        if ((buf != null) && (length > 0)) {
            random.nextBytes(mask);
            maskBuf = mask;
        }

        putBytes(buffer(), maskOffset(), maskBuf);

        for (int i = 0; i < length; i++) {
            buffer().put(dataIndex++, (byte) (buf.get(offset++) ^ maskBuf[i % maskBuf.length]));
        }
    }

    /**
     * Puts the specified payload into the buffer without masking.
     *
     * @param buf
     * @param offset
     * @param length
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
     * Puts the specified payload into the buffer without masking.
     *
     * @param buf
     * @param offset
     * @param length
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

    private static void putBytes(ByteBuffer buffer, int offset, byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            buffer.put(offset + i, bytes[i]);
        }
    }
}
