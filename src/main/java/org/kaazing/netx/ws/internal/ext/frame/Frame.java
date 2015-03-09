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

import java.security.SecureRandom;

import org.kaazing.netx.ws.internal.ext.agrona.DirectBuffer;
import org.kaazing.netx.ws.internal.ext.agrona.UnsafeBuffer;


public abstract class Frame extends Flyweight {
    private static final byte FIN_MASK = (byte) 0x80;
    private static final byte OP_CODE_MASK = 0x0F;
    private static final byte MASKED_MASK = (byte) 0x80;
    private static final byte LENGTH_BYTE_1_MASK = 0x7F;

    private static final int LENGTH_OFFSET = 1;
    private static final int MASK_OFFSET = 1;

    private UnsafeBuffer unmaskedPayload;
    private final Payload payload;
    private final byte[] mask;
    private final SecureRandom random;

    Frame() {
        payload = new Payload();
        this.mask = new byte[4];
        this.random = new SecureRandom();
    }

    protected abstract int getMaxPayloadLength();

    @Override
    public int limit() {
        return getDataOffset() + getLength();
    }

    @Override
    protected Flyweight wrap(final DirectBuffer buffer, final int offset, final boolean mutable) {
        super.wrap(buffer, offset, mutable);
        validateLength();
        payload.wrap(null, offset, 0, mutable);
        return this;
    }

    public void encode(OpCode opcode, boolean fin, boolean masked, byte[] payload) {
        int capacity = FrameUtils.calculateCapacity(masked, payload);
        byte[] maskBuf = FrameUtils.EMPTY_MASK;

        buffer().checkLimit(capacity);

        if (masked) {
            random.nextBytes(mask);
            maskBuf = mask;
        }

        FrameUtils.encode(super.mutableBuffer(), offset(), opcode, fin, masked, maskBuf, payload);
    }

    public int getDataOffset() {
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

        if (isMasked()) {
            index += 4;
        }
        return index;
    }

    public int getLength() {
        int length = uint8Get(buffer(), offset() + LENGTH_OFFSET) & LENGTH_BYTE_1_MASK;

        switch (length)
        {
        case 126:
            return uint16Get(buffer(), offset() + LENGTH_OFFSET + 1);
        case 127:
            return (int) int64Get(buffer(), offset() + LENGTH_OFFSET + 1);
        default:
            return length;
        }
    }

    public int getMaskOffset() {
        if (!isMasked()) {
            return -1;
        }

        int len = getLength();
        if (len == 0) {
            return 2;
        }

        return getDataOffset() - 4;
    }

    public OpCode getOpCode() {
        short byte0 = uint8Get(buffer(), offset());
        return OpCode.fromInt(byte0 & OP_CODE_MASK);
    }

    public Payload getPayload() {
        return getPayload(false);
    }

    public Payload getPayload(boolean mutable) {
        if (payload.buffer() != null) {
            return payload;
        }

        if (!isMasked()) {
            payload.wrap(buffer(), getDataOffset(), limit(), false);
        }
        else {
            int len = getLength();
            if (len == 0) {
                payload.wrap(buffer(),  getDataOffset(),  getDataOffset(), false);
            }
            else {
                int maskOff = getMaskOffset();
                for (int i = 0; i < mask.length; i++) {
                    mask[i] = (byte) uint8Get(buffer(), maskOff + i);
                }

                if (unmaskedPayload == null) {
                    unmaskedPayload = new UnsafeBuffer(new byte[getMaxPayloadLength()]);
                }

                for (int i = 0; i < len; i++) {
                    byte b = (byte) (uint8Get(buffer(), getDataOffset() + i) ^ mask[i % mask.length]);
                    uint8Put(unmaskedPayload, i, b);
                }

                payload.wrap(unmaskedPayload, 0, len, false);
            }
        }
        return payload;
    }

    public boolean isFin() {
        return (uint8Get(buffer(), offset()) & FIN_MASK) != 0;
    }

    public boolean isMasked() {
        return (uint8Get(buffer(), offset() + MASK_OFFSET) & MASKED_MASK) != 0;
    }

    public static class Payload extends Flyweight {
        private int limit;

        protected Payload wrap(DirectBuffer buffer, int offset, int limit, boolean mutable) {
            super.wrap(buffer, offset, mutable);
            this.limit = limit;
            return this;
        }

        @Override
        public int limit() {
            return limit;
        }
    }

    static void protocolError(String message) throws ProtocolException
    {
        throw new ProtocolException(message);
    }

    private void validateLength()
    {
        if (getLength() > getMaxPayloadLength())
        {
            protocolError(format("%s frame payload exceeds %d bytes", getOpCode(), getMaxPayloadLength()));
        }
    }
}
