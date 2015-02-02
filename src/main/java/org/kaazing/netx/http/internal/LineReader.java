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

package org.kaazing.netx.http.internal;

import static java.lang.Integer.highestOneBit;
import static java.lang.String.format;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

public final class LineReader extends Reader {
    private static final byte CR = '\r';
    private static final byte LF = '\n';

    private final InputStream in;
    private int remaining;
    private int charBytes;

    public LineReader(InputStream in) {
        super(in);
        this.in = in;
    }

    @Override
    public void close() throws IOException {
        // no-op -- to address warnings in Eclipse
    }

    @Override
    public int read() throws IOException {
        char[] buf = new char[1];
        read(buf, 0, 1);
        return buf[0];
    }

    @Override
    public int read(char[] cbuf) throws IOException {
        return read(cbuf, 0, cbuf.length);
    }

    @Override
    public int read(char[] cbuf, int offset, int length) throws IOException {
        if (length == 0) {
            return 0;
        }

        if ((offset < 0) || ((offset + length) > cbuf.length) || (length < 0)) {
            throw new IndexOutOfBoundsException();
        }

        int mark = offset;
        int b = -1;

        do {
            // ### TODO: If it is just ASCII, then this should be enough. Check whether we want a UTF8LineReader that can be used
            // for other purposes later.
            //
            //  b = in.read();
            // if (b == -1) {
            //     break;
            // }
            // cbuf[offset++] = (char) b;

            while (remaining > 0) {
                // Read the remaining bytes of a multi-byte character. These bytes could be in two successive TCP fragments.
                b = in.read();
                if (b == -1) {
                    return offset - mark;
                }

                switch (remaining) {
                case 3:
                case 2:
                    charBytes = (charBytes << 6) | (b & 0x3F);
                    remaining--;
                    break;
                case 1:
                    cbuf[offset++] = (char) ((charBytes << 6) | (b & 0x3F));
                    remaining--;
                    charBytes = 0;
                    break;
                case 0:
                    break;
                }
            }

            b = in.read();
            if (b == -1) {
                break;
            }


            // Check if the byte read is the first of a multi-byte character.
            remaining = remainingUTF8Bytes(b);

            switch (remaining) {
            case 0:
                // ASCII char.
                cbuf[offset++] = (char) (b & 0x7F);
                break;
            case 1:
                charBytes = b & 0x1F;
                break;
            case 2:
                charBytes = b & 0x0F;
                break;
            case 3:
                charBytes = b & 0x07;
                break;
            default:
                throw new IOException("Invalid UTF-8 byte sequence. UTF-8 char cannot span for more than 4 bytes.");
            }
        } while ((offset - mark) < length);

        if ((offset - mark) == 0) {
            return -1;
        }

        return offset - mark;
    }

    public String readLine() throws IOException {
        char ch;

        StringBuilder builder = new StringBuilder("");

        do {
            ch = (char) read();
            if ((ch == -1) || (ch == LF)) {
                if (builder.length() == 0) {
                    return "";
                }

                return builder.toString();
            }

            if (ch != CR) {
                builder.append(ch);
            }
        } while (true);
    }

    private static int remainingUTF8Bytes(int byte1) {
        // See http://en.wikipedia.org/wiki/UTF-8#Description for details.

        switch (~highestOneBit(~byte1 & 0xff) & 0xff) {
        case 0x7F:
            // \u0000 - \u007F
            return 0;
        case 0xDF:
            // \u0080 - \u07FF
            return 1;
        case 0xEF:
            // \u0800 - \uFFFF
            return 2;
        case 0xF7:
            // \u10000 - \u1FFFFF
            return 3;
        default:
            throw new IllegalArgumentException(format("Invalid UTF-8 sequence leader byte: %02x", byte1));
        }
    }

}
