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

import static java.lang.String.format;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.CharBuffer;

public final class LineReader extends Reader {
    private static final byte CR = '\r';
    private static final byte LF = '\n';

    private final InputStream in;

    public LineReader(InputStream in) {
        super(in);
        this.in = in;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    @Override
    public void mark(int readLimit) {
        in.mark(readLimit);
    }

    @Override
    public boolean markSupported() {
        return in.markSupported();
    }

    @Override
    public int read() throws IOException {
        return in.read();
    }

    @Override
    public int read(char[] cbuf) throws IOException {
        return read(cbuf, 0, cbuf.length);
    }

    @Override
    public int read(char[] cbuf, int offset, int length) throws IOException {
        if ((offset < 0) || ((offset + length) > cbuf.length) || (length < 0)) {
            throw new IndexOutOfBoundsException();
        }

        if (length == 0) {
            return 0;
        }

        int mark = offset;

        do {
            int b = in.read();
            if (b == -1) {
                break;
            }
            cbuf[offset++] = (char) b;

        } while ((offset - mark) < length);

        if ((offset - mark) == 0) {
            return -1;
        }

        return offset - mark;
    }

    @Override
    public int read(CharBuffer target) {
        throw new UnsupportedOperationException("Unsupported operation");
    }

    @Override
    public boolean ready() throws IOException {
        return in.available() > 0;
    }

    @Override
    public void reset() throws IOException {
        in.reset();
    }

    @Override
    public long skip(long n) {
        throw new UnsupportedOperationException("Unsupported operation");
    }

    public String readLine() throws IOException {
        StringBuilder builder = new StringBuilder();

        do {
            int ch = in.read();
            if (ch == -1) {
                if (builder.length() == 0) {
                    return null;
                }

                return builder.toString();
            }

            if ((ch & 0x80) != 0) {
                throw new IOException(format("Invalid ASCII character: '%c'", ch));
            }

            if (ch == LF) {
                return builder.toString();
            }

            if (ch != CR) {
                builder.append((char) ch);
            }
        } while (true);
    }
}
