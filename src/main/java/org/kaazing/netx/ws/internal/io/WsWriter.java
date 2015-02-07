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

package org.kaazing.netx.ws.internal.io;

import static java.lang.Integer.highestOneBit;
import static org.kaazing.netx.ws.internal.util.Utf8Util.byteCountUTF8;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Random;

public class WsWriter extends Writer {
    private final OutputStream out;
    private final Random random;
    private final byte[] mask;

    public WsWriter(OutputStream out, Random random) {
        this.out = out;
        this.random = random;
        this.mask = new byte[4];
    }

    @Override
    public void write(char[] cbuf, int offset, int length) throws IOException {
        int byteCount = byteCountUTF8(cbuf, offset, length);

        out.write(0x81);

        switch (highestOneBit(byteCount)) {
        case 0x0000:
        case 0x0001:
        case 0x0002:
        case 0x0004:
        case 0x0008:
        case 0x0010:
        case 0x0020:
            out.write(0x80 | byteCount);
            break;
        case 0x0040:
            switch (length) {
            case 126:
                out.write(0x80 | 126);
                out.write(0x00);
                out.write(126);
                break;
            case 127:
                out.write(0x80 | 126);
                out.write(0x00);
                out.write(127);
                break;
            default:
                out.write(0x80 | byteCount);
                break;
            }
            break;
        case 0x0080:
        case 0x0100:
        case 0x0200:
        case 0x0400:
        case 0x0800:
        case 0x1000:
        case 0x2000:
        case 0x4000:
        case 0x8000:
            out.write(0x80 | 126);
            out.write((length >> 8) & 0xff);
            out.write((length >> 0) & 0xff);
            break;
        default:
            // 65536+
            out.write(0x80 | 127);

            long lengthL = byteCount;
            out.write((int) ((lengthL >> 56) & 0xff));
            out.write((int) ((lengthL >> 48) & 0xff));
            out.write((int) ((lengthL >> 40) & 0xff));
            out.write((int) ((lengthL >> 32) & 0xff));
            out.write((int) ((lengthL >> 24) & 0xff));
            out.write((int) ((lengthL >> 16) & 0xff));
            out.write((int) ((lengthL >> 8) & 0xff));
            out.write((int) ((lengthL >> 0) & 0xff));
            break;
        }

        // Create a section of the buf that is to be written.
        char[] arr = new char[length];
        for (int i = 0; i < length; i++) {
            arr[i] = cbuf[offset + i];
        }

        // Create the masking key.
        random.nextBytes(mask);
        out.write(mask);

        // ### TODO: Convert the char[] to UTF-8 byte[] payload instead of creating a String.
        byte[] bytes = String.valueOf(arr).getBytes("UTF-8");

        // Mask the payload.

        byte[] masked = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            int ioff = offset + i;
            masked[i] = (byte) (bytes[ioff] ^ mask[i % mask.length]);
        }

        out.write(masked);
        out.flush();
    }

    @Override
    public void flush() throws IOException {
        // No-op
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
