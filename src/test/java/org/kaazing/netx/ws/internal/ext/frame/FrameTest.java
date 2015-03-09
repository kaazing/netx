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

import static org.kaazing.netx.ws.internal.ext.frame.FrameTestUtil.fromHex;

import java.nio.ByteBuffer;
import java.util.Random;

import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import org.kaazing.netx.ws.internal.ext.frame.DataTest.Fin;

@RunWith(Theories.class)
public class FrameTest {
    private static final int BUFFER_CAPACITY = 64 * 1024;
    private static final int WS_MAX_MESSAGE_SIZE = 20 * 1024;

    @DataPoint
    public static final int ZERO_OFFSET = 0;

    @DataPoint
    public static final int NON_ZERO_OFFSET = new Random().nextInt(BUFFER_CAPACITY - WS_MAX_MESSAGE_SIZE);

    @DataPoint
    public static final boolean MASKED = true;

    @DataPoint
    public static final boolean UNMASKED = false;

    protected final ByteBuffer buffer = ByteBuffer.wrap(new byte[BUFFER_CAPACITY]);
    protected final FrameFactory frameFactory = FrameFactory.newInstance(WS_MAX_MESSAGE_SIZE);

    protected static void putLengthMaskAndHexPayload(ByteBuffer buffer, int offset, String hexPayload, boolean masked) {
        byte[] unmasked = hexPayload == null ? new byte[0] : fromHex(hexPayload);
        putLengthMaskAndPayload(buffer, offset, unmasked, masked);
    }

    protected static void putLengthMaskAndPayload(ByteBuffer buffer, int offset, byte[] unmaskedPayload, boolean masked) {
        offset += putLengthAndMaskBit(buffer, offset, unmaskedPayload.length, masked);
        if (!masked) {
            for (int i = 0; i < unmaskedPayload.length; i++) {
                buffer.put(offset + i, unmaskedPayload[i]);
            }
            return;
        }
        int mask = new Random().nextInt(Integer.MAX_VALUE);
        buffer.putInt(offset, mask);
        offset += 4;
        for (int i = 0; i < unmaskedPayload.length; i++) {
            byte maskByte = (byte) (mask >> (8 * (3 - i % 4)) & 0x000000FF);
            buffer.put(offset + i, (byte) (unmaskedPayload[i] ^ maskByte));
        }
    }

    protected static int putLengthAndMaskBit(ByteBuffer buffer, int offset, int length, boolean masked) {
        if (length < 126) {
            buffer.put(offset, (byte) (length | (masked ? 0x80 : 0x00)));
            return 1;
        } else if (length <= 0xFFFF) {
            buffer.put(offset, (byte) (126 | (masked ? 0x80 : 0x00)));
            buffer.put(offset + 1, (byte) (length >> 8 & 0xFF));
            buffer.put(offset + 2, (byte) (length & 0xFF));
            return 3;
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Theory
    public void dummyTest(int offset, boolean masked, Fin fin) throws Exception {
    }
}
