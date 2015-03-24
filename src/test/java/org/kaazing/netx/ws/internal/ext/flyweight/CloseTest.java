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
import static org.junit.Assert.fail;
import static org.kaazing.netx.ws.internal.ext.flyweight.FrameTestUtil.fromHex;
import static org.kaazing.netx.ws.internal.ext.flyweight.FrameTestUtil.toHex;

import org.junit.Ignore;
import org.junit.experimental.theories.Theory;
import org.kaazing.netx.ws.internal.ext.flyweight.Frame.Payload;
import org.kaazing.netx.ws.internal.util.FrameUtil;

public class CloseTest extends FrameTest {

    @Theory
    public void shouldDecodeWithEmptyPayload(int offset, boolean masked) throws Exception {
        FrameUtil.putBytes(buffer, offset, fromHex("88"));
        putLengthMaskAndHexPayload(buffer, offset + 1, null, masked);
        Frame frame = frameFactory.wrap(buffer, offset);
        assertEquals(OpCode.CLOSE, frame.getOpCode());
        Payload payload = frame.getPayload();
        assertEquals(payload.offset(), payload.limit());
        Close close = (Close) frame;
        assertEquals(0, close.getLength());
//        assertEquals(1005, close.getStatusCode());
        Payload reason = close.getReason();
        assertEquals(reason.offset(), reason.limit());
    }

    @Theory
    public void shouldDecodeWithStatusCode1000(int offset, boolean masked) throws Exception {
        FrameUtil.putBytes(buffer, offset, fromHex("88"));
        putLengthMaskAndHexPayload(buffer, offset + 1, "03e8", masked);
        Frame frame = frameFactory.wrap(buffer, offset);
        assertEquals(OpCode.CLOSE, frame.getOpCode());
        byte[] payloadBytes = new byte[2];
        Payload payload = frame.getPayload();
        FrameUtil.getBytes(payload.buffer(), payload.offset(), payloadBytes);
        assertArrayEquals(fromHex("03e8"), payloadBytes);
        Close close = (Close) frame;
        assertEquals(2, close.getLength());
        assertEquals(1000, close.getStatusCode());
        Payload reason = close.getReason();
        assertEquals(reason.offset(), reason.limit());
    }

    @Theory
    public void shouldDecodeWithStatusCodeAndReason(int offset, boolean masked) throws Exception {
        FrameUtil.putBytes(buffer, offset, fromHex("88"));
        String reasonString = "Something bad happened";
        putLengthMaskAndHexPayload(buffer, offset + 1, "0" + Integer.toHexString(1001) + toHex(reasonString.getBytes(UTF_8)),
                masked);
        Frame frame = frameFactory.wrap(buffer, offset);
        assertEquals(OpCode.CLOSE, frame.getOpCode());
        assertEquals(frame.offset(), offset);
        assertEquals(offset + 2 + (masked ? 4 : 0), frame.getLength(), frame.limit());
        byte[] payloadBytes = new byte[2];
        Payload payload = frame.getPayload();
        FrameUtil.getBytes(payload.buffer(), payload.offset(), payloadBytes);
        Close close = (Close) frame;
        assertEquals(2 + reasonString.length(), close.getLength());
        assertEquals(1001, close.getStatusCode());
        Payload reason = close.getReason();
        assertEquals(reasonString.length(), reason.limit() - reason.offset());
        String reasonResult = new String(reason.buffer().array(), reason.offset(), reason.limit() - reason.offset());
        assertEquals(reasonString, reasonResult);
    }

    @Theory
    @Ignore
    public void shouldRejectCloseFrameWithFinNotSet(int offset, boolean masked) throws Exception {
        FrameUtil.putBytes(buffer, offset, fromHex("08"));
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
    public void shouldRejectCloseFrameWithLength1(int offset, boolean masked) throws Exception {
        FrameUtil.putBytes(buffer, offset, fromHex("88"));
        putLengthMaskAndHexPayload(buffer, offset + 1, "01", masked);
        try {
            Close frame = (Close) frameFactory.wrap(buffer, offset);
            frame.getLength();
        } catch (Exception e) {
            System.out.println(e);
            return;
        }
        fail("Exception exception was not thrown");
    }

    @Theory
    @Ignore
    public void shouldRejectCloseFrameWithLengthOver125(int offset, boolean masked) throws Exception {
        FrameUtil.putBytes(buffer, offset, fromHex("88"));
        putLengthAndMaskBit(buffer, offset + 1, 126, masked);
        try {
            Close frame = (Close) frameFactory.wrap(buffer, offset);
            frame.getPayload();
        } catch (Exception e) {
            System.out.println(e);
            return;
        }
        fail("Exception exception was not thrown");
    }

    @Theory
    @Ignore
    public void shouldRejectCloseFrameWithStatusCode1023(int offset, boolean masked) throws Exception {
        FrameUtil.putBytes(buffer, offset, fromHex("88"));
        putLengthMaskAndHexPayload(buffer, offset + 1, "0" + Integer.toHexString(1023), masked);
        Close close = (Close) frameFactory.wrap(buffer, offset);
        try {
            close.getStatusCode();
        } catch (Exception e) {
            System.out.println(e);
            return;
        }
        fail("Exception exception was not thrown");
    }

    @Theory
    @Ignore
    public void shouldRejectCloseFrameWithStatusCodeFFFF(int offset, boolean masked) throws Exception {
        FrameUtil.putBytes(buffer, offset, fromHex("88"));
        putLengthMaskAndHexPayload(buffer, offset + 1, "ffff", masked);
        Close close = (Close) frameFactory.wrap(buffer, offset);
        try {
            close.getStatusCode();
        } catch (Exception e) {
            System.out.println(e);
            return;
        }
        fail("Exception exception was not thrown");
    }

    @Theory
    @Ignore
    public void shouldRejectCloseFrameWithReasonNotValidUTF8(int offset, boolean masked) throws Exception {
        String validMultibyteCharEuroSign = "e282ac";
        String invalidUTF8 = toHex("valid text".getBytes(UTF_8)) + validMultibyteCharEuroSign + "ff";
        FrameUtil.putBytes(buffer, offset, fromHex("88"));
        putLengthMaskAndHexPayload(buffer, offset + 1, "03ff" + invalidUTF8, masked);
        Close frame = (Close) frameFactory.wrap(buffer, offset);
        try {
            frame.getReason();
        } catch (Exception e) {
            return;
        }
        fail("Exception was not thrown");
    }
}
