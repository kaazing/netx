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

import java.nio.ByteBuffer;

public class ClosePayloadRO extends ClosePayload {
    private int limit;

    public ClosePayloadRO() {
    }

    @Override
    public int statusCode() {
        checkBuffer(buffer());

        int payloadLength = limit() - offset();

        if (payloadLength >= 2) {
            int code = ((buffer().get(offset()) & 0xFF) << 8) | (buffer().get(offset() + 1) & 0xFF);
            return code;
        }

        return 0;
    }

    @Override
    public int reasonOffset() {
        checkBuffer(buffer());

        int payloadLength = limit() - offset();

        if (payloadLength > 2) {
            return offset() + 2;
        }

        return -1;
    }

    @Override
    public int reasonLength() {
        checkBuffer(buffer());

        int payloadLength = limit() - offset();
        if (payloadLength > 2) {
            return payloadLength - 2;
        }

        return 0;
    }

    @Override
    public int limit() {
        return limit;
    }

    public ClosePayloadRO wrap(ByteBuffer buffer, int offset, int limit) {
        super.wrap(buffer, offset);
        this.limit = limit;
        return this;
    }

    private void checkBuffer(ByteBuffer buffer) {
        if (buffer == null) {
            throw new IllegalStateException("Flyweight has not been wrapped/populated yet with a ByteBuffer.");
        }
    }

//    private int reasonGet(byte[] buf, int offset, int length) {
//        if (buf == null) {
//            throw new NullPointerException("Null buffer passed in");
//        }
//        else if ((offset < 0) || (length < 0) || (offset + length > buf.length)) {
//            throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, offset + length, buf.length));
//        }
//
//        checkBuffer();
//
//        if (reasonLength() == 0) {
//            return 0;
//        }
//
//        int min = Math.min(reasonLength(), length);
//
//        if (delegate.masked()) {
//
//            int maskIndex = delegate.maskOffset();
//            int reasonOffset = reasonOffset();
//
//            for (int i = 0; i < min; i++) {
//                // Account for 2 bytes of status code while unmasking the reason.
//                buf[offset++] = (byte) (buffer().get(reasonOffset++) ^ buffer().get(maskIndex + ((i + 2) % 4)));
//            }
//        }
//        else {
//            System.arraycopy(buffer().array(), reasonOffset(), buf, offset, min);
//        }
//
//        return min;
//    }
}
