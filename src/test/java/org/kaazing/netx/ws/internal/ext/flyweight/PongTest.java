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
import static org.kaazing.netx.ws.internal.ext.flyweight.Opcode.PONG;

import java.nio.ByteBuffer;

import org.junit.experimental.theories.Theory;

public class PongTest extends FrameTest {

    @Theory
    public void shouldDecodeWithEmptyPayload(int offset) throws Exception {
        FrameRW pongFrame = new FrameRW().wrap(buffer, offset);

        pongFrame.fin(true);
        pongFrame.opcode(PONG);
        pongFrame.payloadPut((ByteBuffer) null, offset, 0);

        assertEquals(Opcode.PONG, pongFrame.opcode());
        assertEquals(0, pongFrame.payloadLength());
        assertEquals(true, pongFrame.fin());
    }

    @Theory
    public void shouldDecodeWithPayload(int offset) throws Exception {
        FrameRW pongFrame = new FrameRW().wrap(buffer, offset);
        byte[] inputBytes = fromHex("03e8ff01");

        pongFrame.fin(true);
        pongFrame.opcode(PONG);
        pongFrame.payloadPut(inputBytes, 0, inputBytes.length);

        assertEquals(Opcode.PONG, pongFrame.opcode());
        assertEquals(inputBytes.length, pongFrame.payloadLength());
        assertEquals(true, pongFrame.fin());

        int payloadOffset = pongFrame.payloadOffset();
        int payloadLength = pongFrame.payloadLength();
        byte[] payloadBytes = new byte[payloadLength];

        for (int i = 0; i < payloadLength; i++) {
            payloadBytes[i] = pongFrame.buffer().get(payloadOffset++);
        }

        assertArrayEquals(inputBytes, payloadBytes);
    }
}
