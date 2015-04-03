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

/**
 * Abstract class representing a WebSocket Frame as per RFC 6544.
 */
public abstract class Frame extends Flyweight {
    Frame() {
    }

    @Override
    protected Flyweight wrap(final ByteBuffer buffer, final int offset) {
        super.wrap(buffer, offset);
        return this;
    }

    /**
     * Indicates whether this is a final frame by examining the FIN bit.
     *
     * @return true if the FIN bit is set, otherwise false
     */
    public abstract boolean fin();

    /**
     * Returns the reserved flags in the higher nibble of the leading byte of a WebSocket frame.
     *
     * @return values between 0-7
     */
    public abstract int flags();

    /**
     * Returns the opcode of the WebSocket frame.
     *
     * @return OpCode
     */
    public abstract OpCode opCode();

    /**
     * Returns the length of the WebSocket frame. The maximum length of a WebSocket frame can be Integer.MAX_VALUE.
     *
     * ### TODO: long vs int.
     *
     * @return the length of the frame
     */
    public abstract int length();

    /**
     * Returns the payload's length. The maximum length of the payload can be Integer.MAX_VALUE - 10.
     *
     * ### TODO: long vs int
     *
     * @return payload's length
     */
    public abstract int payloadLength();

    /**
     * Returns the payload's offset in the underlying buffer.
     *
     * @return payload offset
     */
    public abstract int payloadOffset();
}
