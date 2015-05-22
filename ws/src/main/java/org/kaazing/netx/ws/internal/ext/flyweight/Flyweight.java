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
 * Encapsulation of basic field operations and flyweight usage pattern
 *
 * All flyweights are intended to be direct subclasses, with a wrap(final
 * DirectBuffer buffer, final int offset) method calling super.wrap(buffer,
 * offset, false) so they are immutable. A mutable flyweight should extend the
 * immutable version, and provide a wrap method that calls super.wrap(buffer,
 * offset, true). The underlying data for a flyweight is from offset() inclusive
 * to limit() exclusive.
 */
public class Flyweight {
    private int offset;
    private ByteBuffer buffer;

    /**
     * Construct a flyweight with a given byte order assumed
     *
     * @param byteOrder of the entire flyweight
     */
    public Flyweight() {
        this.offset = 0;
    }

    /**
     * @return Byte index where the data for this flyweight starts
     */
    public int offset() {
        return offset;
    }

    /**
     * @return Byte index of the byte immediately following the data for this flyweight
     */
    public int limit() {
        return offset;
    }

    public ByteBuffer buffer() {
        return buffer;
    }

    /**
     * Wrap a flyweight to use a specific buffer starting at a given offset.
     * Immutable flyweights should provide a public wrap(buffer, offset) method
     * calling super.wrap(buffer, offset, false). A mutable subclass can then
     * override this to call super.wrap(buffer, offset, true).
     *
     * @param buffer
     *            to use
     * @param offset
     *            to start at
     * @return flyweight
     */
    protected Flyweight wrap(final ByteBuffer buffer, final int offset) {
        this.buffer = buffer;
        this.offset = offset;
        return this;
    }

    /**
     * Return the 8-bit field at a given location as an unsigned integer.
     *
     * @param buffer
     *            to read from
     * @param offset
     *            to read from
     * @return short representation of the 8-bit unsigned value
     */
    public static short uint8Get(final ByteBuffer buffer, final int offset) {
        return (short) (buffer.get(offset) & 0xFF);
    }

    /**
     * Return the 16-bit field at a given location as an unsigned integer.
     *
     * @param buffer
     *            to read from
     * @param offset
     *            to read from
     * @param byteOrder
     *            to decode with
     * @return int representation of the 16-bit signed value
     */
    public static int uint16Get(final ByteBuffer buffer, final int offset) {
        return buffer.getShort(offset) & 0xFFFF;
    }

    /**
     * Return the 64-bit field at a given location as a signed integer.
     *
     * @param buffer
     *            to read from
     * @param offset
     *            to read from
     * @return long representation of the 64-bit signed value
     */
    public static long int64Get(final ByteBuffer buffer, final int offset) {
        return buffer.getLong(offset);
    }
}
