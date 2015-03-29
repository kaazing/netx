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

public class FrameRO extends Frame {
    private static final String MSG_INDEX_OUT_OF_BOUNDS = "offset = %d; (offset + length) = %d; buffer length = %d";

    private static final byte FIN_MASK = (byte) 0x80;
    private static final byte OP_CODE_MASK = 0x0F;
    private static final byte MASKED_MASK = (byte) 0x80;
    private static final byte LENGTH_BYTE_1_MASK = 0x7F;

    private static final int LENGTH_OFFSET = 1;
    private static final int MASK_OFFSET = 1;

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
            return -1;
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
    public int payloadGet(byte[] buf, int offset, int length) {
        if (buf == null) {
            throw new NullPointerException("Null buffer passed in");
        }
        else if ((offset < 0) || (length < 0) || (offset + length > buf.length)) {
            throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, offset + length, buf.length));
        }

        int min = Math.min(payloadLength(), length);

        if (masked()) {
            byte[] maskBuf = new byte[4];
            int maskIndex = maskOffset();
            int dataIndex = payloadOffset();

            for (int i = 0; i < maskBuf.length; i++) {
                maskBuf[i] = buffer().get(maskIndex++);
            }

            for (int i = 0; i < min; i++) {
                buf[i] = (byte) (buffer().get(dataIndex++) ^ maskBuf[i % maskBuf.length]);
            }
        }
        else {
            int dataIndex = payloadOffset();
            for (int i = 0; i < min; i++) {
                buf[i] = buffer().get(dataIndex++);
            }
        }

        return min;
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
    public FrameRO wrap(ByteBuffer buffer, int offset) {
        super.wrap(buffer, offset);
        return this;
    }

    private static void checkBuffer(ByteBuffer buffer) {
        if (buffer == null) {
            throw new IllegalStateException("Flyweight has not been wrapped/populated yet with a ByteBuffer.");
        }
    }

}
