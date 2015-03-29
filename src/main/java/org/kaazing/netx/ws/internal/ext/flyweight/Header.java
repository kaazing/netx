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

public abstract class Header extends Frame {

    public abstract boolean fin();

    public abstract int flags();

    public abstract OpCode opCode();

    public abstract int length();   // Only handing frames of size < Integer.MAX_VALUE

    public abstract int mask();

    public abstract boolean masked();

    public abstract int maskOffset();

    public abstract int payloadLength();  // Only handing payloads of size < Integer.MAX_VALUE - 10

    /**
     * Populates the passed in buffer with unmasked payload.
     *
     * @param buf
     * @param offset
     * @param length
     * @return number of payload bytes in the buf
     */
    public abstract int payloadGet(byte[] buf, int offset, int length);

    public abstract int payloadOffset();
}
