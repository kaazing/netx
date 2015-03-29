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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.kaazing.netx.ws.internal.ext.flyweight.FrameTestUtil.fromHex;

import org.junit.experimental.theories.Theory;

public class PingTest extends FrameTest {

    @Theory
    public void shouldDecodeWithEmptyPayload(int offset, boolean masked) throws Exception {
        FrameRW pingFrame = new FrameRW().wrap(buffer, offset);

        pingFrame.opCodeAndFin(OpCode.PING, true);

        if (masked) {
            pingFrame.maskedPayloadPut((byte[]) null, offset, 0);
        }
        else {
            pingFrame.payloadPut((byte[]) null, offset, 0);
        }

        assertEquals(OpCode.PING, pingFrame.opCode());
        assertEquals(0, pingFrame.payloadLength());
        assertEquals(true, pingFrame.fin());
        assertEquals(masked, pingFrame.masked());
    }

    @Theory
    public void shouldDecodeWithPayload(int offset, boolean masked) throws Exception {
        FrameRW pingFrame = new FrameRW().wrap(buffer, offset);
        byte[] inputBytes = fromHex("03e8ff01");
        byte[] payload = new byte[inputBytes.length];

        pingFrame.opCodeAndFin(OpCode.PING, true);

        if (masked) {
            pingFrame.maskedPayloadPut(inputBytes, 0, inputBytes.length);
        }
        else {
            pingFrame.payloadPut(inputBytes, 0, inputBytes.length);
        }

        pingFrame.payloadGet(payload, 0, payload.length);

        assertEquals(OpCode.PING, pingFrame.opCode());
        assertEquals(inputBytes.length, pingFrame.payloadLength());
        assertArrayEquals(inputBytes, payload);
        assertEquals(true, pingFrame.fin());
        assertEquals(masked, pingFrame.masked());
    }
}
