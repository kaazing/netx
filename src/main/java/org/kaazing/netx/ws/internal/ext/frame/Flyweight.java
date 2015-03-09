/*
 * Copyright 2014 Kaazing Corporation, All rights reserved.
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

import static java.nio.ByteOrder.BIG_ENDIAN;

import java.nio.ByteOrder;

import org.kaazing.netx.ws.internal.ext.agrona.DirectBuffer;
import org.kaazing.netx.ws.internal.ext.agrona.MutableDirectBuffer;

/**
 * Encapsulation of basic field operations and flyweight usage pattern
 *
 * All flyweights are intended to be direct subclasses, with
 * a wrap(final DirectBuffer buffer, final int offset) method calling
 * super.wrap(buffer, offset, false) so they are immutable.
 * A mutable flyweight should extend the immutable version, and provide
 * a wrap method that calls super.wrap(buffer, offset, true).
 * The underlying data for a flyweight is from offset() inclusive to limit() exclusive.
 */
public class Flyweight
{
    private int offset;
    private Storage storage;

    /**
     * Construct a flyweight with a given byte order assumed
     *
     * @param byteOrder of the entire flyweight
     */
    public Flyweight()
    {
        this.offset = 0;
    }

    /**
     * @return Byte index where the data for this flyweight starts
     */
    public int offset()
    {
        return offset;
    }

    /**
     * @return Byte index of the byte immediately following the data for this flyweight
     */
    public int limit()
    {
        return offset;
    }

    public DirectBuffer buffer()
    {
        return storage.buffer();
    }

    public MutableDirectBuffer mutableBuffer()
    {
        return storage.mutableBuffer();
    }

    /**
     * Wrap a flyweight to use a specific buffer starting at a given offset.
     * Immutable flyweights should provide a public wrap(buffer, offset) method
     * calling super.wrap(buffer, offset, false). A mutable subclass can then
     * override this to call super.wrap(buffer, offset, true).
     * @param buffer to use
     * @param offset to start at
     * @param whether the flyweight is to be mutable
     * @return flyweight
     */
    protected Flyweight wrap(final DirectBuffer buffer, final int offset, boolean mutable)
    {
        this.storage = mutable ? new MutableStorage((MutableDirectBuffer)buffer) : new ImmutableStorage(buffer);
        this.offset = offset;
        return this;
    }

    /**
     * Return the 32-bit field at a given location as an float.
     *
     * @param buffer to read from
     * @param offset to read from
     * @return float representation of the 32-bit field
     */
    public static float floatGet(final DirectBuffer buffer, final int offset)
    {
        return buffer.getFloat(offset, BIG_ENDIAN);
    }

    /**
     * Encode the given float value as a 32-bit field at the given location.
     *
     * @param buffer to write from
     * @param offset to write at
     * @param byteOrder to encode with
     * @param value to encode represented as a 32-bit field
     */
    public static void floatPut(
        final MutableDirectBuffer buffer, final int offset, final float value)
    {
        buffer.putFloat(offset, value, BIG_ENDIAN);
    }

    /**
     * Return the 64-bit field at a given location as an double.
     *
     * @param buffer to read from
     * @param offset to read from
     * @param byteOrder to decode with
     * @return double representation of the 64-bit field
     */
    public static double doubleGet(final DirectBuffer buffer, final int offset)
    {
        return buffer.getDouble(offset, BIG_ENDIAN);
    }

    /**
     * Encode the given double value as a 64-bit field at the given location.
     *
     * @param buffer to write from
     * @param offset to write at
     * @param byteOrder to encode with
     * @param value to encode represented as a 64-bit field
     */
    public static void doublePut(
        final MutableDirectBuffer buffer, final int offset, final double value)
    {
        buffer.putDouble(offset, value, BIG_ENDIAN);
    }

    /**
     * Return the 8-bit field at a given location as an unsigned integer.
     *
     * @param buffer to read from
     * @param offset to read from
     * @return short representation of the 8-bit unsigned value
     */
    public static short uint8Get(final DirectBuffer buffer, final int offset)
    {
        return (short)(buffer.getByte(offset) & 0xFF);
    }

    /**
     * Encode a given value as an 8-bit unsigned integer at a given location.
     *
     * @param buffer to write to
     * @param offset to write at
     * @param value to encode represented as a short
     */
    public static void uint8Put(final MutableDirectBuffer buffer, final int offset, final short value)
    {
        buffer.putByte(offset, (byte)value);
    }

    /**
     * Return the 8-bit field at a given location as a signed integer.
     *
     * @param buffer to read from
     * @param offset to read from
     * @return byte representation of the 8-bit signed value
     */
    public static byte int8Get(final DirectBuffer buffer, final int offset)
    {
        return buffer.getByte(offset);
    }

    /**
     * Encode a given value as an 8-bit signed integer at a given location.
     *
     * @param buffer to write to
     * @param offset to write at
     * @param value to encode represented as a byte
     */
    public static void int8Put(final MutableDirectBuffer buffer, final int offset, final byte value)
    {
        buffer.putByte(offset, value);
    }

    /**
     * Return the 16-bit field at a given location as an unsigned integer.
     *
     * @param buffer to read from
     * @param offset to read from
     * @param byteOrder to decode with
     * @return int representation of the 16-bit signed value
     */
    public static int uint16Get(final DirectBuffer buffer, final int offset)
    {
        return buffer.getShort(offset, BIG_ENDIAN) & 0xFFFF;
    }

    /**
     * Encode a given value as an 16-bit unsigned integer at a given location.
     *
     * @param buffer to write to
     * @param offset to write at
     * @param value to encode represented as an int
     */
    public static void uint16Put(final MutableDirectBuffer buffer, final int offset, final int value)
    {
        buffer.putShort(offset, (short)value, BIG_ENDIAN);
    }

    /**
     * Return the 16-bit field at a given location as a signed integer.
     *
     * @param buffer to read from
     * @param offset to read from
     * @param byteOrder to decode with
     * @return short representation of the 16-bit signed value
     */
    public static short int16Get(final DirectBuffer buffer, final int offset, final ByteOrder byteOrder)
    {
        return buffer.getShort(offset, byteOrder);
    }

    /**
     * Encode a given value as an 16-bit signed integer at a given location.
     *
     * @param buffer to write to
     * @param offset to write at
     * @param value to encode represented as a short
     * @param byteOrder to encode with
     */
    public static void int16Put(final MutableDirectBuffer buffer, final int offset, final short value)
    {
        buffer.putShort(offset, value, BIG_ENDIAN);
    }

    /**
     * Return the 32-bit field at a given location as an unsigned integer.
     *
     * @param buffer to read from
     * @param offset to read from
     * @param byteOrder to decode with
     * @return long representation of the 32-bit signed value
     */
    public static long uint32Get(final DirectBuffer buffer, final int offset)
    {
        return buffer.getInt(offset, BIG_ENDIAN) & 0xFFFFFFFFL;
    }

    /**
     * Encode a given value as an 32-bit unsigned integer at a given location.
     *
     * @param buffer to write to
     * @param offset to write at
     * @param value to encode represented as an long
     */
    public static void uint32Put(final MutableDirectBuffer buffer, final int offset, final long value)
    {
        buffer.putInt(offset, (int)value, BIG_ENDIAN);
    }

    /**
     * Return the 32-bit field at a given location as a signed integer.
     *
     * @param buffer to read from
     * @param offset to read from
     * @return int representation of the 32-bit signed value
     */
    public static int int32Get(final DirectBuffer buffer, final int offset)
    {
        return buffer.getInt(offset, BIG_ENDIAN);
    }

    /**
     * Encode a given value as an 32-bit signed integer at a given location.
     *
     * @param buffer to write to
     * @param offset to write at
     * @param value to encode represented as a int
     */
    public static void int32Put(final MutableDirectBuffer buffer, final int offset, final int value)
    {
        buffer.putInt(offset, value, BIG_ENDIAN);
    }

    /**
     * Return the 64-bit field at a given location as a signed integer.
     *
     * @param buffer to read from
     * @param offset to read from
     * @return long representation of the 64-bit signed value
     */
    public static long int64Get(final DirectBuffer buffer, final int offset)
    {
        return buffer.getLong(offset, BIG_ENDIAN);
    }

    /**
     * Encode a given value as an 64-bit signed integer at a given location.
     *
     * @param buffer to write to
     * @param offset to write at
     * @param byteOrder to encode with
     * @param value to encode represented as a long
     */
    public static void int64Put(final MutableDirectBuffer buffer, final int offset, final long value,
                                final ByteOrder byteOrder)
    {
        buffer.putLong(offset, value, byteOrder);
    }

    /**
     * Is a bit set at a given index.
     *
     * @param buffer to read from.
     * @param offset of the beginning byte
     * @param bitIndex bit index to read
     * @return true if the bit is set otherwise false.
     */
    public static boolean bitSet(final MutableDirectBuffer buffer, final int offset, final int bitIndex)
    {
        return 0 != (buffer.getByte(offset) & (1 << bitIndex));
    }

    /**
     * Set a bit on or off at a given index.
     *
     * @param buffer to write the bit too.
     * @param offset of the beginning byte.
     * @param bitIndex bit index to set.
     * @param switchOn true sets bit to 1 and false sets it to 0.
     */
    public static void bitSet(
        final MutableDirectBuffer buffer, final int offset, final int bitIndex, final boolean switchOn)
    {
        byte bits = buffer.getByte(offset);
        bits = (byte)((switchOn ? bits | (1 << bitIndex) : bits & ~(1 << bitIndex)));
        buffer.putByte(offset, bits);
    }

    private interface Storage
    {
        DirectBuffer buffer();

        MutableDirectBuffer mutableBuffer();
    }

    private static final class ImmutableStorage implements Storage
    {
        DirectBuffer buffer;

        ImmutableStorage(DirectBuffer buffer)
        {
            this.buffer = buffer;
        }

        @Override
        public DirectBuffer buffer()
        {
            return buffer;
        }

        @Override
        public MutableDirectBuffer mutableBuffer()
        {
            throw new UnsupportedOperationException("Flyweight is immutable");
        }
    }

    private static final class MutableStorage implements Storage
    {
        MutableDirectBuffer buffer;

        MutableStorage(MutableDirectBuffer buffer)
        {
            this.buffer = buffer;
        }

        @Override
        public DirectBuffer buffer()
        {
            return buffer;
        }

        @Override
        public MutableDirectBuffer mutableBuffer()
        {
            return buffer;
        }
    }


}