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
import static org.kaazing.netx.ws.internal.ext.flyweight.FrameTestUtil.fromHex;

import java.nio.ByteBuffer;

import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theory;

public class ContinuationTest extends FrameTest {
    enum Fin {
        SET, UNSET;
    }

    @DataPoint
    public static final Fin FIN_SET = Fin.SET;

    @DataPoint
    public static final Fin FIN_UNSET = Fin.UNSET;

    @Theory
    public void shouldDecodeContinuationWithEmptyPayload(int offset, boolean masked, Fin fin) throws Exception {
        HeaderRW continuationFrame = new HeaderRW().wrap(buffer, offset);

        continuationFrame.opCodeAndFin(OpCode.CONTINUATION, (fin == Fin.SET) ? true : false);

        if (masked) {
            continuationFrame.maskedPayloadPut((ByteBuffer) null, offset, 0);
        }
        else {
            continuationFrame.payloadPut((ByteBuffer) null, offset, 0);
        }


        assertEquals(OpCode.CONTINUATION, continuationFrame.opCode());
        assertEquals(0, continuationFrame.payloadLength());
        assertEquals(fin == Fin.SET, continuationFrame.fin());
        assertEquals(masked, continuationFrame.masked());
    }

    @Theory
    public void shouldDecodeContinuationWithUTF8Payload(int offset, boolean masked, Fin fin) throws Exception {
        HeaderRW continuationFrame = new HeaderRW().wrap(buffer, offset);
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

        continuationFrame.opCodeAndFin(OpCode.CONTINUATION, (fin == Fin.SET) ? true : false);

        if (masked) {
            continuationFrame.maskedPayloadPut(inputPayload, 0, inputPayload.length);
        }
        else {
            continuationFrame.payloadPut(inputPayload, 0, inputPayload.length);
        }

        continuationFrame.payloadGet(payloadBytes, 0, payloadBytes.length);

        assertEquals(OpCode.CONTINUATION, continuationFrame.opCode());
        assertEquals(inputPayload.length, continuationFrame.payloadLength());
        assertArrayEquals(inputPayload, payloadBytes);
        assertEquals(fin == Fin.SET, continuationFrame.fin());
        assertEquals(masked, continuationFrame.masked());
    }

    @Theory
    public void shouldDecodeContinuationWithIncompleteUTF8(int offset, boolean masked, Fin fin) throws Exception {
        HeaderRW continuationFrame = new HeaderRW().wrap(buffer, offset);
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

        continuationFrame.opCodeAndFin(OpCode.CONTINUATION, (fin == Fin.SET) ? true : false);

        if (masked) {
            continuationFrame.maskedPayloadPut(inputPayload, 0, inputPayload.length);
        }
        else {
            continuationFrame.payloadPut(inputPayload, 0, inputPayload.length);
        }

        continuationFrame.payloadGet(payloadBytes, 0, payloadBytes.length);

        assertEquals(OpCode.CONTINUATION, continuationFrame.opCode());
        assertEquals(inputPayload.length, continuationFrame.payloadLength());
        assertArrayEquals(inputPayload, payloadBytes);
        assertEquals(fin == Fin.SET, continuationFrame.fin());
        assertEquals(masked, continuationFrame.masked());
    }

    @Theory
    public void shouldDecodeContinuationWithBinaryPayload(int offset, boolean masked, Fin fin) throws Exception {
        HeaderRW continuationFrame = new HeaderRW().wrap(buffer, offset);
        byte[] inputPayload = new byte[5000];
        byte[] payloadBytes = new byte[inputPayload.length];

        inputPayload[12] = (byte) 0xff;

        continuationFrame.opCodeAndFin(OpCode.CONTINUATION, (fin == Fin.SET) ? true : false);

        if (masked) {
            continuationFrame.maskedPayloadPut(inputPayload, 0, inputPayload.length);
        }
        else {
            continuationFrame.payloadPut(inputPayload, 0, inputPayload.length);
        }

        continuationFrame.payloadGet(payloadBytes, 0, payloadBytes.length);

        assertEquals(OpCode.CONTINUATION, continuationFrame.opCode());
        assertEquals(inputPayload.length, continuationFrame.payloadLength());
        assertArrayEquals(inputPayload, payloadBytes);
        assertEquals(fin == Fin.SET, continuationFrame.fin());
        assertEquals(masked, continuationFrame.masked());
    }
}
