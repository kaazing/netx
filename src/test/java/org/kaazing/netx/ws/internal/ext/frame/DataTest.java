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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.kaazing.netx.ws.internal.ext.agrona.BitUtil.fromHex;

import java.nio.ByteBuffer;

import org.junit.Test;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theory;
import org.kaazing.netx.ws.internal.ext.agrona.BitUtil;
import org.kaazing.netx.ws.internal.ext.frame.Frame.Payload;

public class DataTest extends FrameTest
{
    enum Fin
    {
        SET, UNSET;
    }

    @DataPoint
    public static final Fin FIN_SET = Fin.SET;

    @DataPoint
    public static final Fin FIN_UNSET = Fin.UNSET;

    @Theory
    public void shouldDecodeTextWithEmptyPayload(int offset, boolean masked, Fin fin) throws Exception
    {
        buffer.putBytes(offset, fromHex(fin == Fin.SET ? "81" : "01"));
        putLengthMaskAndHexPayload(buffer, offset + 1, null, masked);
        Frame frame = frameFactory.wrap(buffer, offset);
        assertEquals(OpCode.TEXT, frame.getOpCode());
        Data data = (Data) frame;
        Payload payload = frame.getPayload();
        assertEquals(payload.offset(), payload.limit());
        assertEquals(0, data.getLength());
        assertEquals(fin == Fin.SET, data.isFin());
    }

    @Theory
    public void shouldDecodeTextWithValidPayload(int offset, boolean masked, Fin fin) throws Exception
    {
        buffer.putBytes(offset, fromHex(fin == Fin.SET ? "81" : "01"));
        ByteBuffer bytes = ByteBuffer.allocate(1000);
        bytes.put("e acute (0xE9 or 0x11101001): ".getBytes(UTF_8));
        bytes.put((byte) 0xC3).put((byte) 0xA9);
        bytes.put(", Euro sign: ".getBytes(UTF_8));
        bytes.put(fromHex("e282ac"));
        bytes.put(", Hwair: ".getBytes(UTF_8));
        bytes.put(fromHex("f0908d88"));
        bytes.limit(bytes.position());
        bytes.position(0);
        byte[] inputPayload = new byte[bytes.remaining()];
        bytes.get(inputPayload);
        putLengthMaskAndPayload(buffer, offset + 1, inputPayload, masked);
        Frame frame = frameFactory.wrap(buffer, offset);
        assertEquals(OpCode.TEXT, frame.getOpCode());
        Payload payload = frame.getPayload();
        byte[] payloadBytes = new byte[payload.limit() - payload.offset()];
        payload.buffer().getBytes(payload.offset(), payloadBytes);
        assertArrayEquals(inputPayload, payloadBytes);
        Data data = (Data) frame;
        assertEquals(inputPayload.length, data.getLength());
        assertEquals(fin == Fin.SET, data.isFin());
    }

    @Theory
    public void shouldDecodeTextWithIncompleteUTF8(int offset, boolean masked, Fin fin) throws Exception
    {
        buffer.putBytes(offset, fromHex(fin == Fin.SET ? "81" : "01"));
        ByteBuffer bytes = ByteBuffer.allocate(1000);
        bytes.put("e acute (0xE9 or 0x11101001): ".getBytes(UTF_8));
        bytes.put((byte) 0xC3).put((byte) 0xA9);
        bytes.put(", Euro sign: ".getBytes(UTF_8));
        bytes.put(fromHex("e282ac"));
        bytes.put(", Hwair (first 2 bytes only): ".getBytes(UTF_8));
        bytes.put(fromHex("f090")); // missing 8d88
        bytes.limit(bytes.position());
        bytes.position(0);
        byte[] inputPayload = new byte[bytes.remaining()];
        bytes.get(inputPayload);
        putLengthMaskAndPayload(buffer, offset + 1, inputPayload, masked);
        Frame frame = frameFactory.wrap(buffer, offset);
        assertEquals(OpCode.TEXT, frame.getOpCode());
        Payload payload = frame.getPayload();
        byte[] payloadBytes = new byte[payload.limit() - payload.offset()];
        payload.buffer().getBytes(payload.offset(), payloadBytes);
        assertArrayEquals(inputPayload, payloadBytes);
        Data data = (Data) frame;
        assertEquals(inputPayload.length, data.getLength());
        assertEquals(fin == Fin.SET, data.isFin());
    }

    @Theory
    public void shouldRejectTextExceedingMaximumLength(int offset, boolean masked, Fin fin) throws Exception
    {
        buffer.putBytes(offset, fromHex(fin == Fin.SET ? "81" : "01"));
        ByteBuffer bytes = ByteBuffer.allocate(1000);
        bytes.put("e acute (0xE9 or 0x11101001): ".getBytes(UTF_8));
        bytes.put((byte) 0xC31).put((byte) 0xA9);
        bytes.put(", invalid: ".getBytes(UTF_8));
        bytes.put(fromHex("ff"));
        bytes.put(", Euro sign: ".getBytes(UTF_8));
        bytes.put(fromHex("e282ac"));
        bytes.limit(bytes.position());
        bytes.position(0);
        byte[] inputPayload = "abcdefghijklmnopqrstuvwxyz1234567890".getBytes(UTF_8);
        bytes.get(inputPayload);
        putLengthMaskAndPayload(buffer, offset + 1, inputPayload, masked);
        int wsMaxMessageSize = 30;
        try
        {
            FrameFactory.newInstance(wsMaxMessageSize).wrap(buffer, offset);
        }
        catch (ProtocolException e)
        {
            return;
        }
        fail("Exception was not thrown");
    }

    @Theory
    public void shouldDecodeBinaryWithEmptyPayload(int offset, boolean masked, Fin fin) throws Exception
    {
        buffer.putBytes(offset, fromHex(fin == Fin.SET ? "82" : "02"));
        putLengthMaskAndHexPayload(buffer, offset + 1, null, masked);
        Frame frame = frameFactory.wrap(buffer, offset);
        assertEquals(OpCode.BINARY, frame.getOpCode());
        Data data = (Data) frame;
        Payload payload = frame.getPayload();
        assertEquals(payload.offset(), payload.limit());
        assertEquals(0, data.getLength());
        assertEquals(fin == Fin.SET, data.isFin());
    }

    @Test
    public void shouldUnmask0Remaining() throws Exception
    {
        buffer.putBytes(0, fromHex("82")); // fin, binary
        buffer.putBytes(1, fromHex("84")); // masked, length=4
        buffer.putBytes(2, fromHex("01020384")); // mask
        buffer.putBytes(6, fromHex("FF00FF00")); // masked payload
        Frame frame = frameFactory.wrap(buffer, 0);
        assertEquals(OpCode.BINARY, frame.getOpCode());
        Payload payload = frame.getPayload();
        byte[] payloadBytes = new byte[payload.limit() - payload.offset()];
        payload.buffer().getBytes(payload.offset(), payloadBytes);
        assertArrayEquals(fromHex("FE02FC84"), payloadBytes);
        Data data = (Data) frame;
        assertEquals(4, data.getLength());
        assertTrue(data.isFin());
    }

    @Test
    public void shouldUnmask1Remaining() throws Exception
    {
        buffer.putBytes(0, fromHex("82")); // fin, binary
        buffer.putBytes(1, fromHex("85")); // masked, length=5
        buffer.putBytes(2, fromHex("01020384")); // mask
        buffer.putBytes(6, fromHex("FF00FF00FE")); // masked payload
        Frame frame = frameFactory.wrap(buffer, 0);
        assertEquals(OpCode.BINARY, frame.getOpCode());
        Payload payload = frame.getPayload();
        byte[] payloadBytes = new byte[payload.limit() - payload.offset()];
        payload.buffer().getBytes(payload.offset(), payloadBytes);
        assertArrayEquals(fromHex("FE02FC84FF"), payloadBytes);
        Data data = (Data) frame;
        assertEquals(5, data.getLength());
        assertTrue(data.isFin());
    }

    @Test
    public void shouldUnmask2Remaining() throws Exception
    {
        buffer.putBytes(0, fromHex("82")); // fin, binary
        buffer.putBytes(1, fromHex("86")); // masked, length=6
        buffer.putBytes(2, fromHex("01020384")); // mask
        buffer.putBytes(6, fromHex("FF00FF00FE65")); // masked payload
        Frame frame = frameFactory.wrap(buffer, 0);
        assertEquals(OpCode.BINARY, frame.getOpCode());
        Payload payload = frame.getPayload();
        byte[] payloadBytes = new byte[payload.limit() - payload.offset()];
        payload.buffer().getBytes(payload.offset(), payloadBytes);
        assertArrayEquals(fromHex("FE02FC84FF67"), payloadBytes);
        Data data = (Data) frame;
        assertEquals(6, data.getLength());
        assertTrue(data.isFin());
    }

    @Test
    public void shouldUnmask3Remaining() throws Exception
    {
        buffer.putBytes(0, fromHex("82")); // fin, binary
        buffer.putBytes(1, fromHex("87")); // masked, length=7
        buffer.putBytes(2, fromHex("01020384")); // mask
        buffer.putBytes(6, fromHex("FF00FF00FE6596")); // masked payload
        Frame frame = frameFactory.wrap(buffer, 0);
        assertEquals(OpCode.BINARY, frame.getOpCode());
        Payload payload = frame.getPayload();
        byte[] payloadBytes = new byte[payload.limit() - payload.offset()];
        payload.buffer().getBytes(payload.offset(), payloadBytes);
        assertArrayEquals(fromHex("FE02FC84FF6795"), payloadBytes);
        Data data = (Data) frame;
        assertEquals(7, data.getLength());
        assertTrue(data.isFin());
    }

    @Theory
    public void shouldDecodeBinaryWithPayload(int offset, boolean masked, Fin fin) throws Exception
    {
        buffer.putBytes(offset, fromHex(fin == Fin.SET ? "82" : "02"));
        byte[] inputPayload = new byte[5000];
        inputPayload[12] = (byte)0xff;
        putLengthMaskAndPayload(buffer, offset + 1, inputPayload, masked);
        Frame frame = frameFactory.wrap(buffer, offset);
        assertEquals(OpCode.BINARY, frame.getOpCode());
        Payload payload = frame.getPayload();
        byte[] payloadBytes = new byte[payload.limit() - payload.offset()];
        payload.buffer().getBytes(payload.offset(), payloadBytes);
        assertArrayEquals(inputPayload, payloadBytes);
        Data data = (Data) frame;
        assertEquals(inputPayload.length, data.getLength());
        assertEquals(fin == Fin.SET, data.isFin());
    }

    @Theory
    public void shouldRejectBinaryExceedingMaximumLength(int offset, boolean masked, Fin fin) throws Exception
    {
        buffer.putBytes(offset, BitUtil.fromHex(fin == Fin.SET ? "82" : "02"));
        byte[] inputPayload = new byte[5001];
        inputPayload[12] = (byte)0xff;
        putLengthMaskAndPayload(buffer, offset + 1, inputPayload, masked);
        int wsMaxMessageSize = 5000;
        try
        {
            FrameFactory.newInstance(wsMaxMessageSize).wrap(buffer, offset);
        }
        catch (ProtocolException e)
        {
            return;
        }
        fail("Exception was not thrown");
    }

}
