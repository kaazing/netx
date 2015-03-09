package org.kaazing.netx.ws.internal.ext.frame;

import static java.nio.ByteOrder.BIG_ENDIAN;
import static org.kaazing.netx.ws.internal.ext.agrona.BitUtil.fromHex;

import java.util.Random;

import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.runner.RunWith;
import org.kaazing.netx.ws.internal.ext.agrona.MutableDirectBuffer;
import org.kaazing.netx.ws.internal.ext.agrona.UnsafeBuffer;

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

    protected final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[BUFFER_CAPACITY]);
    protected final FrameFactory frameFactory = FrameFactory.newInstance(WS_MAX_MESSAGE_SIZE);

    protected static void putLengthMaskAndHexPayload(MutableDirectBuffer buffer,
                                                  int offset,
                                                  String hexPayload,
                                                  boolean masked) {
        byte[] unmasked = hexPayload == null ? new byte[0] : fromHex(hexPayload);
        putLengthMaskAndPayload(buffer, offset, unmasked, masked);
    }


    protected static void putLengthMaskAndPayload(MutableDirectBuffer buffer,
                                                  int offset,
                                                  byte[] unmaskedPayload,
                                                  boolean masked) {
        offset += putLengthAndMaskBit(buffer, offset, unmaskedPayload.length, masked);
        if (!masked)
        {
            buffer.putBytes(offset, unmaskedPayload);
            return;
        }
        int mask = new Random().nextInt(Integer.MAX_VALUE);
        buffer.putInt(offset, mask, BIG_ENDIAN);
        offset += 4;
        for (int i = 0; i < unmaskedPayload.length; i++)
        {
            byte maskByte = (byte) (mask >> (8 * (3 - i % 4)) & 0x000000FF);
            buffer.putByte(offset + i, (byte) (unmaskedPayload[i] ^ maskByte));
        }
    }

    protected static int putLengthAndMaskBit(MutableDirectBuffer buffer, int offset, int length, boolean masked) {
        if (length < 126)
        {
            buffer.putByte(offset, (byte) (length | (masked ? 0x80 : 0x00)));
            return 1;
        }
        else if (length <= 0xFFFF)
        {
            buffer.putByte(offset, (byte) (126 | (masked ? 0x80 : 0x00)));
            buffer.putByte(offset + 1, (byte) (length >> 8 & 0xFF));
            buffer.putByte(offset + 2, (byte) (length & 0xFF));
            return 3;
        }
        else
        {
            throw new UnsupportedOperationException();
        }
    }

}
