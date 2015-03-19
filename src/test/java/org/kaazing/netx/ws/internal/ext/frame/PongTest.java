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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.kaazing.netx.ws.internal.ext.frame.FrameTestUtil.fromHex;

import org.junit.Ignore;
import org.junit.experimental.theories.Theory;
import org.kaazing.netx.ws.internal.ext.frame.Frame.Payload;
import org.kaazing.netx.ws.internal.util.FrameUtil;

public class PongTest extends FrameTest {

    @Theory
    public void shouldDecodeWithEmptyPayload(int offset, boolean masked) throws Exception {
        FrameUtil.putBytes(buffer, offset, fromHex("8a"));
        putLengthMaskAndHexPayload(buffer, offset + 1, null, masked);
        Frame frame = frameFactory.wrap(buffer, offset);
        assertEquals(OpCode.PONG, frame.getOpCode());
        Payload payload = frame.getPayload();
        assertEquals(payload.offset(), payload.limit());
        Pong pong = (Pong) frame;
        assertEquals(0, pong.getLength());
    }

    @Theory
    public void shouldDecodeWithPayload(int offset, boolean masked) throws Exception {
        FrameUtil.putBytes(buffer, offset, fromHex("8a"));
        byte[] inputBytes = fromHex("03e8ff01");
        putLengthMaskAndPayload(buffer, offset + 1, inputBytes, masked);
        Frame frame = frameFactory.wrap(buffer, offset);
        assertEquals(OpCode.PONG, frame.getOpCode());
        byte[] payloadBytes = new byte[inputBytes.length];
        Payload payload = frame.getPayload();
        FrameUtil.getBytes(payload.buffer(), payload.offset(), payloadBytes);
        assertArrayEquals(inputBytes, payloadBytes);
        Pong pong = (Pong) frame;
        assertEquals(payloadBytes.length, pong.getLength());
        assertEquals(inputBytes.length, payload.limit() - payload.offset());
    }

    @Theory
    @Ignore
    public void shouldRejectPongFrameWithFinNotSet(int offset, boolean masked) throws Exception {
        FrameUtil.putBytes(buffer, offset, fromHex("0a"));
        putLengthMaskAndHexPayload(buffer, offset + 1, null, masked);
        try {
            frameFactory.wrap(buffer, offset);
        } catch (Exception e) {
            System.out.println(e);
            return;
        }
        fail("Exception exception was not thrown");
    }

    @Theory
    @Ignore
    public void shouldRejectPongFrameWithLengthOver125(int offset, boolean masked) throws Exception {
        FrameUtil.putBytes(buffer, offset, fromHex("8a"));
        putLengthAndMaskBit(buffer, offset + 1, 126, masked);
        try {
            frameFactory.wrap(buffer, offset);
        } catch (Exception e) {
            System.out.println(e);
            return;
        }
        fail("Exception exception was not thrown");
    }

}
