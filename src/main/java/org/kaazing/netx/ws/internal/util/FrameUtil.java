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

package org.kaazing.netx.ws.internal.util;

import static java.lang.String.format;

import java.nio.ByteBuffer;

import org.kaazing.netx.ws.internal.ext.frame.Close;
import org.kaazing.netx.ws.internal.ext.frame.Data;
import org.kaazing.netx.ws.internal.ext.frame.Frame;
import org.kaazing.netx.ws.internal.ext.frame.OpCode;
import org.kaazing.netx.ws.internal.ext.frame.Ping;
import org.kaazing.netx.ws.internal.ext.frame.Pong;

public final class FrameUtil {
    public static final byte[] EMPTY_MASK = new byte[] {0x00, 0x00, 0x00, 0x00};

    private FrameUtil() {

    }

    public static int calculateNeed(boolean masked, long payloadLength) {
        int capacity = 1; // opcode

        if (payloadLength < 126) {
            capacity++;
        }
        else if (payloadLength <= 0xFFFF) {
            capacity += 3;
        }
        else {
            capacity += 9;
        }

        if (masked) {
            capacity += 4;
        }

        capacity += payloadLength;
        return capacity;
    }

    public static OpCode getOpCode(ByteBuffer buffer, int offset) {
        short byte0 = buffer.get(offset);
        return OpCode.fromInt(byte0 & 0x0F);
    }

    public static void copy(Frame source, Frame destination) {
        if (source.getOpCode() != destination.getOpCode()) {
            String s = format("Mismatched opcodes: source frame opcode = %s; destination frame opcode = %s",
                              source.getOpCode(), destination.getOpCode());
            throw new IllegalStateException(s);
        }

        if (source.isFin() != destination.isFin()) {
            String s = format("Mismatched FIN: source frame FIN = %s; destination frame FIN = %s",
                               source.isFin(), destination.isFin());
            throw new IllegalStateException(s);
        }

        int srcLength = source.getLength();
        boolean srcMasked = source.isMasked();
        int need = calculateNeed(srcMasked, srcLength);
        ByteBuffer destBuffer = destination.buffer();

        if (need > (destination.buffer().capacity() - destination.offset())) {
            destBuffer = ByteBuffer.wrap(new byte[need]);
            System.arraycopy(source.buffer().array(),
                             source.offset(),
                             destBuffer.array(),
                             0,
                             need);
            OpCode opcode = source.getOpCode();
            switch (opcode) {
            case BINARY:
            case TEXT:
            case CONTINUATION:
                ((Data) destination).wrap(destBuffer, 0);
                break;
            case CLOSE:
                ((Close) destination).wrap(destBuffer, 0);
                break;
            case PING:
                ((Ping) destination).wrap(destBuffer, 0);
                break;
            case PONG:
                ((Pong) destination).wrap(destBuffer, 0);
                break;
            }
            return;
        }

        System.arraycopy(source.buffer().array(), source.offset(), destBuffer.array(), destination.offset(), need);
    }

    public static void encode(
            ByteBuffer buffer,
            int offset,
            OpCode opcode,
            boolean fin,
            boolean masked,
            byte[] mask,
            byte[] payload) {

        int length = payload == null ? 0 : payload.length;
        int need = FrameUtil.calculateNeed(masked, payload.length);
        int capacity = buffer.limit() - offset;

        if (need > capacity) {
            final String msg = String.format("need=%d is beyond capacity=%d", need, capacity);
            throw new IndexOutOfBoundsException(msg);
        }
        offset += FrameUtil.putFinAndOpCode(buffer, offset, opcode, fin);
        offset += FrameUtil.putLengthAndMaskBit(buffer, offset, length, masked);
        offset += FrameUtil.putMaskAndPayload(buffer, offset, masked, mask, payload);
    }

    public static void getBytes(ByteBuffer buffer, int offset, byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = buffer.get(offset + i);
        }
    }

    public static void putBytes(ByteBuffer buffer, int offset, byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            buffer.put(offset + i, bytes[i]);
        }
    }

    public static int putFinAndOpCode(ByteBuffer buffer, int offset, OpCode opcode, boolean fin) {
        byte byte0 = (byte) (OpCode.toInt(opcode) | (fin ? 0x80 : 0x00));
        buffer.put(offset, byte0);
        return 1;
    }

    public static int putLengthAndMaskBit(ByteBuffer buffer, int offset, int length, boolean masked) {
        if (length < 126) {
            buffer.put(offset, (byte) (length | (masked ? 0x80 : 0x00)));
            return 1;
        }
        else if (length <= 0xFFFF) {
            buffer.put(offset, (byte) (126 | (masked ? 0x80 : 0x00)));
            buffer.put(offset + 1, (byte) (length >> 8 & 0xFF));
            buffer.put(offset + 2, (byte) (length >> 0 & 0xFF));
            return 3;
        }
        else {
            buffer.put(offset, (byte) (127 | (masked ? 0x80 : 0x00)));
            buffer.put(offset + 1, (byte) (length >> 56 & 0xFF));
            buffer.put(offset + 2, (byte) (length >> 48 & 0xFF));
            buffer.put(offset + 3, (byte) (length >> 40 & 0xFF));
            buffer.put(offset + 4, (byte) (length >> 32 & 0xFF));
            buffer.put(offset + 5, (byte) (length >> 24 & 0xFF));
            buffer.put(offset + 6, (byte) (length >> 16 & 0xFF));
            buffer.put(offset + 7, (byte) (length >> 8 & 0xFF));
            buffer.put(offset + 8, (byte) (length >> 0 & 0xFF));
            return 9;
        }
    }

//    private static int putMaskAndPayload(ByteBuffer source,
//                                        int sourceOffset,
//                                        int sourcePayloadLength,
//                                        boolean masked,
//                                        byte[] mask,
//                                        ByteBuffer destination,
//                                        int destinationOffset) {
//        if ((source == null) || (sourcePayloadLength == 0)) {
//            if (masked) {
//                putBytes(destination, destinationOffset, EMPTY_MASK);
//                return 4;
//            }
//            return 0;
//        }
//
//        int mark = destinationOffset;
//
//        if (masked) {
//            for (int i = 0; i < mask.length; i++) {
//                destination.put(destinationOffset++, mask[i]);
//            }
//
//            for (int i = sourceOffset; i < sourcePayloadLength; i++) {
//                destination.put(destinationOffset++, (byte) (source.get(i) ^ mask[i % mask.length]));
//            }
//        }
//        else {
//            for (int i = sourceOffset; i < sourcePayloadLength; i++) {
//                destination.put(destinationOffset++, source.get(i));
//            }
//        }
//
//        return destinationOffset - mark;
//    }

    public static int putMaskAndPayload(ByteBuffer buffer, int offset, boolean masked, byte[] mask, byte[] payload) {
        if ((payload == null) || (payload.length == 0)) {
            if (masked) {
                putBytes(buffer, offset, EMPTY_MASK);
                return 4;
            }
            return 0;
        }

        int mark = offset;

        if (masked) {
            for (int i = 0; i < mask.length; i++) {
                buffer.put(offset++, mask[i]);
            }

            for (int i = 0; i < payload.length; i++) {
                buffer.put(offset++, (byte) (payload[i] ^ mask[i % mask.length]));
            }
        }
        else {
            for (int i = 0; i < payload.length; i++) {
                buffer.put(offset++, payload[i]);
            }
        }

        return offset - mark;
    }

    public static int putMaskAndPayload(ByteBuffer buffer,
                                        int offset,
                                        boolean masked,
                                        byte[] mask,
                                        byte[] payload,
                                        int payloadOffset,
                                        long payloadLength) {
        if ((payload == null) || (payload.length == 0) || (payloadLength == 0)) {
            if (masked) {
                putBytes(buffer, offset, EMPTY_MASK);
                return 4;
            }
            return 0;
        }

        int mark = offset;

        if (masked) {
            for (int i = 0; i < mask.length; i++) {
                buffer.put(offset++, mask[i]);
            }

            for (int i = 0; i < payloadLength; i++) {
                buffer.put(offset++, (byte) (payload[payloadOffset++] ^ mask[i % mask.length]));
            }
        }
        else {
            for (int i = 0; i < payloadLength; i++) {
                buffer.put(offset++, payload[payloadOffset++]);
            }
        }

        return offset - mark;
    }

}
