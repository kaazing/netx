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

public class ClosePayloadRW extends ClosePayload {
    private static final String MSG_INDEX_OUT_OF_BOUNDS = "offset = %d; (offset + length) = %d; buffer length = %d";

    private final HeaderRW delegate;
    private final byte[] closePayload;

    public ClosePayloadRW() {
        this.closePayload = new byte[150];
        this.delegate = new HeaderRW();
    }

    @Override
    public int statusCode() {
        validate();

        int payloadLength = delegate.payloadLength();
        if (payloadLength >= 2) {
            delegate.payloadGet(closePayload, 0, payloadLength);
            return (short) (((closePayload[0] & 0xFF) << 8) | (closePayload[1] & 0xFF));
        }

        return 0;
    }

    @Override
    public int reasonLength() {
        validate();

        int payloadLength = delegate.payloadLength();
        if (payloadLength > 2) {
            return payloadLength - 2;
        }

        return 0;
    }

    @Override
    public int reasonGet(byte[] buf, int offset, int length) {
        if (buf == null) {
            throw new NullPointerException("Null buffer passed in");
        }
        else if ((offset < 0) || (length < 0) || (offset + length > buf.length)) {
            throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, offset + length, buf.length));
        }

        validate();

        int payloadLength = delegate.payloadLength();
        if (payloadLength > 2) {
            delegate.payloadGet(closePayload, 0, payloadLength);
            int min = Math.min(payloadLength - 2, length);
            System.arraycopy(closePayload, 2, buf, offset, min);
            return min;
        }

        return 0;
    }

    @Override
    public int limit() {
        return delegate.limit();
    }

    @Override
    public ClosePayloadRW wrap(ByteBuffer buffer, int offset) {
        super.wrap(buffer, offset);
        delegate.wrap(buffer, offset);
        return this;
    }

    public void maskedPayloadPut(int code, byte[] reason, int offset, int length) {
        if (reason != null) {
            if ((offset < 0) || (length < 0) || (offset + length > reason.length)) {
                throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, offset + length, reason.length));
            }
        }

        validate();

        int payloadLen = 0;

        if (code > 0) {
            payloadLen += 2;

            if (reason != null) {
                payloadLen += length;
            }

            closePayload[0] = (byte) ((code >> 8) & 0xFF);
            closePayload[1] = (byte) (code & 0xFF);
            if ((reason != null) && (length > 0)) {
                System.arraycopy(reason, offset, closePayload, 2, length);
            }
        }

        delegate.maskedPayloadPut(closePayload, 0, payloadLen);
    }


    public void payloadPut(int code, byte[] reason, int offset, int length) {
        if (reason != null) {
            if ((offset < 0) || (length < 0) || (offset + length > reason.length)) {
                throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, offset + length, reason.length));
            }
        }

        validate();

        int payloadLen = 0;

        if (code > 0) {
            payloadLen += 2;

            if (reason != null) {
                payloadLen += length;
            }

            closePayload[0] = (byte) ((code >> 8) & 0xFF);
            closePayload[1] = (byte) (code & 0xFF);
            if ((reason != null) && (length > 0)) {
                System.arraycopy(reason, offset, closePayload, 2, length);
            }
        }

        delegate.payloadPut(closePayload, 0, payloadLen);
    }

    private void validate() {
        if ((buffer() == null) || (delegate.buffer() == null)) {
            throw new IllegalStateException("Flyweight has not been wrapped/populated yet with a ByteBuffer.");
        }

        if (delegate.opCode() != OpCode.CLOSE) {
            throw new IllegalStateException("Invalid opcode: Expected CLOSE; Actual = " + delegate.opCode());
        }
    }
}
