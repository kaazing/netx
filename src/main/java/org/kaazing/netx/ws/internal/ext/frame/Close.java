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

import org.kaazing.netx.ws.internal.ext.agrona.DirectBuffer;
import org.kaazing.netx.ws.internal.util.Utf8Util;

public class Close extends Control {
    private final Payload reason = new Payload();

    Close() {

    }

    public Close wrap(DirectBuffer buffer, int offset) throws ProtocolException {
        wrap(buffer, offset, false);
        return this;
    }

    public int getStatusCode() {
        if (getLength() < 2)
        {
            return 1005; // RFC 6455 section 7.4.1
        }
        int status = uint16Get(getPayload().buffer(), getPayload().offset());
        validateStatusCode(status);
        return status;
    }

    @Override
    public int getLength() {
        int length = super.getLength();
        if (length == 1)
        {
            protocolError("Invalid Close frame, payload length cannot be 1");
        }
        return length;
    }

    public Payload getReason() {
        return getReason(false);
    }

    @Override
    protected Close wrap(final DirectBuffer buffer, final int offset, boolean mutable) {
        super.wrap(buffer, offset, mutable);
        reason.wrap(null, offset, 0, mutable);
        return this;
    }

    Payload getReason(boolean mutable) {
        Payload payload = getPayload();
        if (getLength() < 2) {
            // return empty reason (TODO: fix this for mutable case)
            return reason.wrap(payload.buffer(), payload.offset(), payload.offset(), false);
        }
        if (reason.buffer() != null) {
            return reason;
        }
        reason.wrap(payload.buffer(), payload.offset() + 2, payload.limit(), mutable);
        if (!Utf8Util.validBytesUTF8(reason.buffer(), reason.offset(), reason.limit())) {
            protocolError("Invalid UTF-8 byte");
        }
        return reason;
    }

    private static void validateStatusCode(int status) {
        if (status < 999 || status == 1005 || (status > 1014 && status < 3000) || status > 4999)
        {
            protocolError(format("Invalid Close frame status code %d", status));
        }
    }
}
