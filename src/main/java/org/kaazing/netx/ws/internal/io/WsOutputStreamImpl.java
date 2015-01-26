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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

public final class WsOutputStreamImpl extends FilterOutputStream {

    public WsOutputStreamImpl(OutputStream out) {
        super(out);
    }

    @Override
    public void write(int b) throws IOException {
        write (new byte[] { (byte) b });
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(0x82);

        switch (highestOneBit(len)) {
        case 0x0000:
        case 0x0001:
        case 0x0002:
        case 0x0004:
        case 0x0008:
        case 0x0010:
        case 0x0020:
            out.write(0x80 | len);
            break;
        case 0x0040:
            switch (len) {
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
                out.write(0x80 | len);
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
            out.write((len >> 8) & 0xff);
            out.write((len >> 0) & 0xff);
            break;
        default:
            // 65536+
            out.write(0x80 | 127);
            out.write((len >> 24) & 0xff);
            out.write((len >> 16) & 0xff);
            out.write((len >> 8) & 0xff);
            out.write((len >> 0) & 0xff);
            break;
        }

        // hoist and re-use a SecureRandom instead
        Random random = new Random();
        byte[] mask = new byte[4];
        random.nextBytes(mask);
        out.write(mask);

        byte[] masked = new byte[len];
        for (int i = 0; i < len; i++) {
            int ioff = off + i;
            masked[i] = (byte) (b[ioff] ^ mask[i % mask.length]);
        }

        out.write(masked);
    }

}
