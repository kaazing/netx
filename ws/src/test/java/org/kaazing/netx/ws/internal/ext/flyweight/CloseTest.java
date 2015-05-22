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
import static org.kaazing.netx.ws.internal.ext.flyweight.Opcode.CLOSE;

import java.nio.ByteBuffer;

import org.junit.experimental.theories.Theory;

public class CloseTest extends FrameTest {

    @Theory
    public void shouldDecodeWithEmptyPayload(int offset) throws Exception {
        FrameRW closeFrame = new FrameRW().wrap(buffer, offset);

        closeFrame.fin(true);
        closeFrame.opcode(CLOSE);

        assertEquals(Opcode.CLOSE, closeFrame.opcode());
        assertEquals(0, closeFrame.payloadLength());
        assertTrue(closeFrame.fin());
    }

    @Theory
    public void shouldDecodeWithStatusCode1000(int offset) throws Exception {
        FrameRW closeFrame = new FrameRW().wrap(buffer, offset);
        byte[] inputPayload = new byte[] { 0x03, (byte) 0xe8 };

        closeFrame.fin(true);
        closeFrame.opcode(CLOSE);

        closeFrame.payloadPut(inputPayload, 0, inputPayload.length);

        assertEquals(Opcode.CLOSE, closeFrame.opcode());
        assertEquals(2, closeFrame.payloadLength());
        assertTrue(closeFrame.fin());

        int payloadOffset = closeFrame.payloadOffset();
        int payloadLength = closeFrame.payloadLength();
        ClosePayloadRO closePayload = new ClosePayloadRO();
        closePayload.wrap(buffer, payloadOffset, payloadOffset + payloadLength);

        assertEquals(1000, closePayload.statusCode());
        assertEquals(0, closePayload.reasonLength());
    }

    @Theory
    public void shouldDecodeWithStatusCodeAndReason(int offset) throws Exception {
        FrameRW closeFrame = new FrameRW().wrap(buffer, offset);
        int statusCode = 1001;
        byte[] reason = "Something bad happened".getBytes(UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(reason.length + 2);

        buf.putShort((short) statusCode);
        buf.put(reason);
        buf.flip();

        closeFrame.fin(true);
        closeFrame.opcode(CLOSE);
        closeFrame.payloadPut(buf, 0, reason.length + 2);

        int payloadOffset = closeFrame.payloadOffset();
        int payloadLength = closeFrame.payloadLength();

        ClosePayloadRO closePayload = new ClosePayloadRO();
        closePayload.wrap(buffer, payloadOffset, payloadOffset + payloadLength);

        assertEquals(Opcode.CLOSE, closeFrame.opcode());
        assertEquals(2 + reason.length, closeFrame.payloadLength());
        assertTrue(closeFrame.fin());

        assertEquals(1001, closePayload.statusCode());
        assertEquals(reason.length, closePayload.reasonLength());

        int reasonOffset = closePayload.reasonOffset();
        byte[] closeReason = new byte[closePayload.reasonLength()];
        for (int i = 0; i < closePayload.reasonLength(); i++) {
            closeReason[i] = closePayload.buffer().get(reasonOffset++);
        }
        assertArrayEquals(reason, closeReason);
    }
}
