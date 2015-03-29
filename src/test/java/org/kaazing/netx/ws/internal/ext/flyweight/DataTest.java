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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.kaazing.netx.ws.internal.ext.flyweight.FrameTestUtil.fromHex;

import java.nio.ByteBuffer;

import org.junit.Test;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theory;

public class DataTest extends FrameTest {
    enum Fin {
        SET, UNSET;
    }

    @DataPoint
    public static final Fin FIN_SET = Fin.SET;

    @DataPoint
    public static final Fin FIN_UNSET = Fin.UNSET;

    @Theory
    public void shouldDecodeTextWithEmptyPayload(int offset, boolean masked, Fin fin) throws Exception {
        FrameRW textFrame = new FrameRW().wrap(buffer, offset);
        byte[] payloadBytes = new byte[10];

        textFrame.opCodeAndFin(OpCode.TEXT, fin == Fin.SET ? true : false);

        if (masked) {
            textFrame.maskedPayloadPut((ByteBuffer) null, offset, 0);
        }
        else {
            textFrame.payloadPut((ByteBuffer) null, offset, 0);
        }

        int bytes = textFrame.payloadGet(payloadBytes, 0, payloadBytes.length);

        assertEquals(OpCode.TEXT, textFrame.opCode());
        assertEquals(0, textFrame.payloadLength());
        assertEquals(fin == Fin.SET, textFrame.fin());
        assertEquals(masked, textFrame.masked());
        assertEquals(0, bytes);
    }

    @Theory
    public void shouldDecodeTextWithValidPayload(int offset, boolean masked, Fin fin) throws Exception {
        FrameRW textFrame = new FrameRW().wrap(buffer, offset);
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
        byte[] payloadBytes = new byte[inputPayload.length];

        textFrame.opCodeAndFin(OpCode.TEXT, fin == Fin.SET ? true : false);

        if (masked) {
            textFrame.maskedPayloadPut(inputPayload, 0, inputPayload.length);
        }
        else {
            textFrame.payloadPut(inputPayload, 0, inputPayload.length);
        }

        int numBytes = textFrame.payloadGet(payloadBytes, 0, payloadBytes.length);

        assertEquals(OpCode.TEXT, textFrame.opCode());
        assertEquals(inputPayload.length, textFrame.payloadLength());
        assertEquals(fin == Fin.SET, textFrame.fin());
        assertArrayEquals(inputPayload, payloadBytes);
        assertEquals(masked, textFrame.masked());
        assertEquals(inputPayload.length, numBytes);
    }

    @Theory
    public void shouldDecodeTextWithIncompleteUTF8(int offset, boolean masked, Fin fin) throws Exception {
        FrameRW textFrame = new FrameRW().wrap(buffer, offset);
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
        byte[] payloadBytes = new byte[inputPayload.length];

        textFrame.opCodeAndFin(OpCode.TEXT, fin == Fin.SET ? true : false);

        if (masked) {
            textFrame.maskedPayloadPut(inputPayload, 0, inputPayload.length);
        }
        else {
            textFrame.payloadPut(inputPayload, 0, inputPayload.length);
        }

        int numBytes = textFrame.payloadGet(payloadBytes, 0, payloadBytes.length);

        assertEquals(OpCode.TEXT, textFrame.opCode());
        assertEquals(inputPayload.length, textFrame.payloadLength());
        assertEquals(fin == Fin.SET, textFrame.fin());
        assertArrayEquals(inputPayload, payloadBytes);
        assertEquals(masked, textFrame.masked());
        assertEquals(inputPayload.length, numBytes);
    }

    @Theory
    public void shouldDecodeBinaryWithEmptyPayload(int offset, boolean masked, Fin fin) throws Exception {
        FrameRW binaryFrame = new FrameRW().wrap(buffer, offset);
        byte[] payloadBytes = new byte[10];

        binaryFrame.opCodeAndFin(OpCode.BINARY, fin == Fin.SET ? true : false);

        if (masked) {
            binaryFrame.maskedPayloadPut((ByteBuffer) null, offset, 0);
        }
        else {
            binaryFrame.payloadPut((ByteBuffer) null, offset, 0);
        }

        int bytes = binaryFrame.payloadGet(payloadBytes, 0, payloadBytes.length);

        assertEquals(OpCode.BINARY, binaryFrame.opCode());
        assertEquals(0, binaryFrame.payloadLength());
        assertEquals(fin == Fin.SET, binaryFrame.fin());
        assertEquals(masked, binaryFrame.masked());
        assertEquals(0, bytes);
    }

    @Test
    public void shouldUnmask0Remaining() throws Exception {
        putBytes(buffer, 0, fromHex("82")); // fin, binary
        putBytes(buffer, 1, fromHex("84")); // masked, length=4
        putBytes(buffer, 2, fromHex("01020384")); // mask
        putBytes(buffer, 6, fromHex("FF00FF00")); // masked payload

        FrameRW binaryFrame = new FrameRW().wrap(buffer, 0);
        byte[] payloadBytes = new byte[10];
        int numBytes = binaryFrame.payloadGet(payloadBytes, 0, payloadBytes.length);
        byte[] bytes = new byte[numBytes];

        System.arraycopy(payloadBytes, 0, bytes, 0, numBytes);

        assertEquals(OpCode.BINARY, binaryFrame.opCode());
        assertEquals(4, binaryFrame.payloadLength());
        assertTrue(binaryFrame.fin());
        assertTrue(binaryFrame.masked());
        assertArrayEquals(fromHex("FE02FC84"), bytes);
        assertEquals(4, numBytes);
    }

    @Test
    public void shouldUnmask1Remaining() throws Exception {
        putBytes(buffer, 0, fromHex("82")); // fin, binary
        putBytes(buffer, 1, fromHex("85")); // masked, length=5
        putBytes(buffer, 2, fromHex("01020384")); // mask
        putBytes(buffer, 6, fromHex("FF00FF00FE")); // masked payload

        FrameRW binaryFrame = new FrameRW().wrap(buffer, 0);
        byte[] payloadBytes = new byte[10];
        int numBytes = binaryFrame.payloadGet(payloadBytes, 0, payloadBytes.length);
        byte[] bytes = new byte[numBytes];

        System.arraycopy(payloadBytes, 0, bytes, 0, numBytes);

        assertEquals(OpCode.BINARY, binaryFrame.opCode());
        assertEquals(5, binaryFrame.payloadLength());
        assertTrue(binaryFrame.fin());
        assertTrue(binaryFrame.masked());
        assertArrayEquals(fromHex("FE02FC84FF"), bytes);
        assertEquals(5, numBytes);
    }

    @Test
    public void shouldUnmask2Remaining() throws Exception {
        putBytes(buffer, 0, fromHex("82")); // fin, binary
        putBytes(buffer, 1, fromHex("86")); // masked, length=6
        putBytes(buffer, 2, fromHex("01020384")); // mask
        putBytes(buffer, 6, fromHex("FF00FF00FE65")); // masked payload

        FrameRW binaryFrame = new FrameRW().wrap(buffer, 0);
        byte[] payloadBytes = new byte[10];
        int numBytes = binaryFrame.payloadGet(payloadBytes, 0, payloadBytes.length);
        byte[] bytes = new byte[numBytes];

        System.arraycopy(payloadBytes, 0, bytes, 0, numBytes);

        assertEquals(OpCode.BINARY, binaryFrame.opCode());
        assertEquals(6, binaryFrame.payloadLength());
        assertTrue(binaryFrame.fin());
        assertTrue(binaryFrame.masked());
        assertArrayEquals(fromHex("FE02FC84FF67"), bytes);
        assertEquals(6, numBytes);
    }

    @Test
    public void shouldUnmask3Remaining() throws Exception {
        putBytes(buffer, 0, fromHex("82")); // fin, binary
        putBytes(buffer, 1, fromHex("87")); // masked, length=6
        putBytes(buffer, 2, fromHex("01020384")); // mask
        putBytes(buffer, 6, fromHex("FF00FF00FE6596")); // masked payload

        FrameRW binaryFrame = new FrameRW().wrap(buffer, 0);
        byte[] payloadBytes = new byte[10];
        int numBytes = binaryFrame.payloadGet(payloadBytes, 0, payloadBytes.length);
        byte[] bytes = new byte[numBytes];

        System.arraycopy(payloadBytes, 0, bytes, 0, numBytes);

        assertEquals(OpCode.BINARY, binaryFrame.opCode());
        assertEquals(7, binaryFrame.payloadLength());
        assertTrue(binaryFrame.fin());
        assertTrue(binaryFrame.masked());
        assertArrayEquals(fromHex("FE02FC84FF6795"), bytes);
        assertEquals(7, numBytes);
    }

    @Theory
    public void shouldDecodeBinaryWithPayload(int offset, boolean masked, Fin fin) throws Exception {
        FrameRW binaryFrame = new FrameRW().wrap(buffer, offset);
        byte[] inputPayload = new byte[5000];
        byte[] payloadBytes = new byte[inputPayload.length];

        inputPayload[12] = (byte) 0xff;

        binaryFrame.opCodeAndFin(OpCode.CONTINUATION, (fin == Fin.SET) ? true : false);

        if (masked) {
            binaryFrame.maskedPayloadPut(inputPayload, 0, inputPayload.length);
        }
        else {
            binaryFrame.payloadPut(inputPayload, 0, inputPayload.length);
        }

        int numBytes = binaryFrame.payloadGet(payloadBytes, 0, payloadBytes.length);

        assertEquals(OpCode.CONTINUATION, binaryFrame.opCode());
        assertEquals(inputPayload.length, binaryFrame.payloadLength());
        assertArrayEquals(inputPayload, payloadBytes);
        assertEquals(fin == Fin.SET, binaryFrame.fin());
        assertEquals(masked, binaryFrame.masked());
        assertEquals(inputPayload.length, numBytes);
    }

    private static void putBytes(ByteBuffer buffer, int offset, byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            buffer.put(offset + i, bytes[i]);
        }
    }
}
