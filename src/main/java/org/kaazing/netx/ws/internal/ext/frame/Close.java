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

public class Close extends Frame {
    private final Payload reason = new Payload();

    Close() {

    }

    @Override
    public Close wrap(ByteBuffer buffer, int offset) {
        super.wrap(buffer, offset);
        reason.wrap(null, offset, 0);
        return this;
    }

    public int getStatusCode() {
        int status = uint16Get(getPayload().buffer(), getPayload().offset());
        return status;
    }

    public Payload getReason() {
        Payload payload = getPayload();
        if (getLength() < 2) {
            // return empty reason (TODO: fix this for mutable case)
            return reason.wrap(payload.buffer(), payload.offset(), payload.offset());
        }
        if (reason.buffer() != null) {
            return reason;
        }
        reason.wrap(payload.buffer(), payload.offset() + 2, payload.limit());
        return reason;
    }
}
