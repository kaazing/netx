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

import java.nio.ByteBuffer;

import org.junit.experimental.theories.Theory;

public class CloseTest extends FrameTest {

    @Theory
    public void shouldDecodeWithEmptyPayload(int offset, boolean masked) throws Exception {
        HeaderRW closeFrame = new HeaderRW().wrap(buffer, offset);
        byte[] payloadBytes = new byte[10];

        closeFrame.opCodeAndFin(OpCode.CLOSE, true);

        if (masked) {
            closeFrame.maskedPayloadPut((ByteBuffer) null, offset, 0);
        }
        else {
            closeFrame.payloadPut((ByteBuffer) null, offset, 0);
        }

        int numBytes = closeFrame.payloadGet(payloadBytes, 0, payloadBytes.length);

        assertEquals(OpCode.CLOSE, closeFrame.opCode());
        assertEquals(0, closeFrame.payloadLength());
        assertTrue(closeFrame.fin());
        assertEquals(masked, closeFrame.masked());
        assertEquals(0, numBytes);
    }

    @Theory
    public void shouldDecodeWithStatusCode1000(int offset, boolean masked) throws Exception {
        HeaderRW closeFrame = new HeaderRW().wrap(buffer, offset);
        byte[] inputPayload = new byte[] { 0x03, (byte) 0xe8 };
        byte[] payloadBytes = new byte[inputPayload.length];

        closeFrame.opCodeAndFin(OpCode.CLOSE, true);

        if (masked) {
            closeFrame.maskedPayloadPut(inputPayload, 0, inputPayload.length);
        }
        else {
            closeFrame.payloadPut(inputPayload, 0, inputPayload.length);
        }

        int numBytes = closeFrame.payloadGet(payloadBytes, 0, payloadBytes.length);

        assertEquals(OpCode.CLOSE, closeFrame.opCode());
        assertEquals(2, closeFrame.payloadLength());
        assertTrue(closeFrame.fin());
        assertEquals(masked, closeFrame.masked());
        assertEquals(2, numBytes);

        ClosePayloadRW closePayload = new ClosePayloadRW();
        closePayload.wrap(buffer, offset);

        assertEquals(1000, closePayload.statusCode());
        assertEquals(0, closePayload.reasonLength());
    }

    @Theory
    public void shouldDecodeWithStatusCodeAndReason(int offset, boolean masked) throws Exception {
        HeaderRW closeFrame = new HeaderRW().wrap(buffer, offset);
        int statusCode = 1001;
        byte[] reason = "Something bad happened".getBytes(UTF_8);
        ClosePayloadRW closePayload = new ClosePayloadRW();
        closePayload.wrap(buffer, offset);

        byte[] payloadBytes = new byte[2 + reason.length];

        closeFrame.opCodeAndFin(OpCode.CLOSE, true);

        if (masked) {
            closePayload.maskedPayloadPut(statusCode, reason, 0, reason.length);
        }
        else {
            closePayload.payloadPut(statusCode, reason, 0, reason.length);
        }

        int numBytes = closeFrame.payloadGet(payloadBytes, 0, payloadBytes.length);

        assertEquals(OpCode.CLOSE, closeFrame.opCode());
        assertEquals(2 + reason.length, closeFrame.payloadLength());
        assertTrue(closeFrame.fin());
        assertEquals(masked, closeFrame.masked());
        assertEquals(2 + reason.length, numBytes);

        byte[] actualReason = new byte[reason.length];
        closePayload.reasonGet(actualReason, 0, actualReason.length);

        assertEquals(1001, closePayload.statusCode());
        assertEquals(reason.length, closePayload.reasonLength());
        assertArrayEquals(reason, actualReason);
    }
}
