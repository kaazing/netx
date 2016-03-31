/**
 * Copyright 2007-2015, Kaazing Corporation. All rights reserved.
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
import static org.kaazing.netx.ws.internal.ext.flyweight.FrameTestUtil.fromHex;
import static org.kaazing.netx.ws.internal.ext.flyweight.Opcode.BINARY;
import static org.kaazing.netx.ws.internal.ext.flyweight.Opcode.CONTINUATION;
import static org.kaazing.netx.ws.internal.ext.flyweight.Opcode.TEXT;

import java.nio.ByteBuffer;

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
    public void shouldDecodeTextWithEmptyPayload(int offset, Fin fin) throws Exception {
        FrameRW textFrame = new FrameRW().wrap(buffer, offset);

        textFrame.fin((fin == Fin.SET) ? true : false);
        textFrame.opcode(TEXT);
        textFrame.payloadPut((ByteBuffer) null, offset, 0);

        assertEquals(Opcode.TEXT, textFrame.opcode());
        assertEquals(0, textFrame.payloadLength());
        assertEquals(fin == Fin.SET, textFrame.fin());
    }

    @Theory
    public void shouldDecodeTextWithValidPayload(int offset, Fin fin) throws Exception {
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

        textFrame.fin((fin == Fin.SET) ? true : false);
        textFrame.opcode(TEXT);
        textFrame.payloadPut(inputPayload, 0, inputPayload.length);

        assertEquals(Opcode.TEXT, textFrame.opcode());
        assertEquals(inputPayload.length, textFrame.payloadLength());
        assertEquals(fin == Fin.SET, textFrame.fin());

        int payloadOffset = textFrame.payloadOffset();
        int payloadLength = textFrame.payloadLength();
        byte[] payloadBytes = new byte[payloadLength];

        for (int i = 0; i < payloadLength; i++) {
            payloadBytes[i] = textFrame.buffer().get(payloadOffset++);
        }
        assertArrayEquals(inputPayload, payloadBytes);
    }

    @Theory
    public void shouldDecodeTextWithIncompleteUTF8(int offset, Fin fin) throws Exception {
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

        textFrame.fin((fin == Fin.SET) ? true : false);
        textFrame.opcode(TEXT);
        textFrame.payloadPut(inputPayload, 0, inputPayload.length);

        assertEquals(Opcode.TEXT, textFrame.opcode());
        assertEquals(inputPayload.length, textFrame.payloadLength());
        assertEquals(fin == Fin.SET, textFrame.fin());

        int payloadOffset = textFrame.payloadOffset();
        int payloadLength = textFrame.payloadLength();
        byte[] payloadBytes = new byte[payloadLength];

        for (int i = 0; i < payloadLength; i++) {
            payloadBytes[i] = textFrame.buffer().get(payloadOffset++);
        }
        assertArrayEquals(inputPayload, payloadBytes);
    }

    @Theory
    public void shouldDecodeBinaryWithEmptyPayload(int offset, Fin fin) throws Exception {
        FrameRW binaryFrame = new FrameRW().wrap(buffer, offset);

        binaryFrame.fin((fin == Fin.SET) ? true : false);
        binaryFrame.opcode(BINARY);
        binaryFrame.payloadPut((ByteBuffer) null, offset, 0);

        assertEquals(Opcode.BINARY, binaryFrame.opcode());
        assertEquals(0, binaryFrame.payloadLength());
        assertEquals(fin == Fin.SET, binaryFrame.fin());
    }

    @Theory
    public void shouldDecodeBinaryWithPayload(int offset, Fin fin) throws Exception {
        FrameRW binaryFrame = new FrameRW().wrap(buffer, offset);
        byte[] inputPayload = new byte[5000];
        inputPayload[12] = (byte) 0xff;

        binaryFrame.fin((fin == Fin.SET) ? true : false);
        binaryFrame.opcode(CONTINUATION);
        binaryFrame.payloadPut(inputPayload, 0, inputPayload.length);

        assertEquals(Opcode.CONTINUATION, binaryFrame.opcode());
        assertEquals(inputPayload.length, binaryFrame.payloadLength());
        assertEquals(fin == Fin.SET, binaryFrame.fin());

        int payloadOffset = binaryFrame.payloadOffset();
        int payloadLength = binaryFrame.payloadLength();
        byte[] payloadBytes = new byte[payloadLength];

        for (int i = 0; i < payloadLength; i++) {
            payloadBytes[i] = binaryFrame.buffer().get(payloadOffset++);
        }
        assertArrayEquals(inputPayload, payloadBytes);
    }
}
