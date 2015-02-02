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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

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

    public String readLine() throws IOException {
        StringBuilder builder = new StringBuilder("");

        do {
            char ch = (char) read();
            if ((ch == -1) || (ch == LF)) {
                return builder.toString();
            }

            if (ch != CR) {
                builder.append(ch);
            }
        } while (true);
    }
}
