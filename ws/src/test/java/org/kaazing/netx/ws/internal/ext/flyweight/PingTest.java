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
import static org.kaazing.netx.ws.internal.ext.flyweight.Opcode.PING;

import org.junit.experimental.theories.Theory;

public class PingTest extends FrameTest {

    @Theory
    public void shouldDecodeWithEmptyPayload(int offset) throws Exception {
        FrameRW pingFrame = new FrameRW().wrap(buffer, offset);

        pingFrame.fin(true);
        pingFrame.opcode(PING);
        pingFrame.payloadPut((byte[]) null, offset, 0);

        assertEquals(Opcode.PING, pingFrame.opcode());
        assertEquals(0, pingFrame.payloadLength());
        assertEquals(true, pingFrame.fin());

    }

    @Theory
    public void shouldDecodeWithPayload(int offset) throws Exception {
        FrameRW pingFrame = new FrameRW().wrap(buffer, offset);
        byte[] inputBytes = fromHex("03e8ff01");

        pingFrame.fin(true);
        pingFrame.opcode(PING);
        pingFrame.payloadPut(inputBytes, 0, inputBytes.length);

        assertEquals(Opcode.PING, pingFrame.opcode());
        assertEquals(inputBytes.length, pingFrame.payloadLength());
        assertEquals(true, pingFrame.fin());

        int payloadOffset = pingFrame.payloadOffset();
        int payloadLength = pingFrame.payloadLength();
        byte[] payloadBytes = new byte[payloadLength];

        for (int i = 0; i < payloadLength; i++) {
            payloadBytes[i] = pingFrame.buffer().get(payloadOffset++);
        }
        assertArrayEquals(inputBytes, payloadBytes);
    }
}
