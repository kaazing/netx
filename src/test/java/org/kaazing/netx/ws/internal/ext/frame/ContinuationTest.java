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
import static org.junit.Assert.fail;
import static org.kaazing.netx.ws.internal.ext.frame.FrameTestUtil.fromHex;

import java.nio.ByteBuffer;

import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theory;
import org.kaazing.netx.ws.internal.ext.frame.Frame.Payload;
import org.kaazing.netx.ws.internal.util.FrameUtil;

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
        FrameUtil.putBytes(buffer, offset, fromHex(fin == Fin.SET ? "80" : "00"));
        putLengthMaskAndHexPayload(buffer, offset + 1, null, masked);
        Frame frame = frameFactory.wrap(buffer, offset);
        assertEquals(OpCode.CONTINUATION, frame.getOpCode());
        Continuation continuation = (Continuation) frame;
        Payload payload = frame.getPayload();
        assertEquals(payload.offset(), payload.limit());
        assertEquals(0, continuation.getLength());
        assertEquals(fin == Fin.SET, continuation.isFin());
    }

    @Theory
    public void shouldDecodeContinuationWithUTF8Payload(int offset, boolean masked, Fin fin) throws Exception {
        FrameUtil.putBytes(buffer, offset, fromHex(fin == Fin.SET ? "80" : "00"));
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
        assertEquals(OpCode.CONTINUATION, frame.getOpCode());
        Payload payload = frame.getPayload();
        byte[] payloadBytes = new byte[payload.limit() - payload.offset()];
        FrameUtil.getBytes(payload.buffer(), payload.offset(), payloadBytes);
        assertArrayEquals(inputPayload, payloadBytes);
        Continuation continuation = (Continuation) frame;
        assertEquals(inputPayload.length, continuation.getLength());
        assertEquals(fin == Fin.SET, continuation.isFin());
    }

    @Theory
    public void shouldDecodeContinuationWithIncompleteUTF8(int offset, boolean masked, Fin fin) throws Exception {
        FrameUtil.putBytes(buffer, offset, fromHex(fin == Fin.SET ? "80" : "00"));
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
        assertEquals(OpCode.CONTINUATION, frame.getOpCode());
        Payload payload = frame.getPayload();
        byte[] payloadBytes = new byte[payload.limit() - payload.offset()];
        FrameUtil.getBytes(payload.buffer(), payload.offset(), payloadBytes);
        assertArrayEquals(inputPayload, payloadBytes);
        Continuation continuation = (Continuation) frame;
        assertEquals(inputPayload.length, continuation.getLength());
        assertEquals(fin == Fin.SET, continuation.isFin());
    }

    @Theory
    public void shouldDecodeContinuationWithBinaryPayload(int offset, boolean masked, Fin fin) throws Exception {
        FrameUtil.putBytes(buffer, offset, fromHex(fin == Fin.SET ? "80" : "00"));
        byte[] inputPayload = new byte[5000];
        inputPayload[12] = (byte) 0xff;
        putLengthMaskAndPayload(buffer, offset + 1, inputPayload, masked);
        Frame frame = frameFactory.wrap(buffer, offset);
        assertEquals(OpCode.CONTINUATION, frame.getOpCode());
        Payload payload = frame.getPayload();
        byte[] payloadBytes = new byte[payload.limit() - payload.offset()];
        FrameUtil.getBytes(payload.buffer(), payload.offset(), payloadBytes);
        assertArrayEquals(inputPayload, payloadBytes);
        Continuation continuation = (Continuation) frame;
        assertEquals(inputPayload.length, continuation.getLength());
        assertEquals(fin == Fin.SET, continuation.isFin());
    }

    @Theory
    public void shouldRejectContinuationExceedingMaximumLength(int offset, boolean masked, Fin fin) throws Exception {
        FrameUtil.putBytes(buffer, offset, fromHex(fin == Fin.SET ? "80" : "00"));
        byte[] inputPayload = new byte[5001];
        inputPayload[12] = (byte) 0xff;
        putLengthMaskAndPayload(buffer, offset + 1, inputPayload, masked);
        int wsMaxMessageSize = 5000;
        try {
            FrameFactory.newInstance(wsMaxMessageSize).wrap(buffer, offset);
        } catch (ProtocolException e) {
            return;
        }
        fail("Exception was not thrown");
    }

}
