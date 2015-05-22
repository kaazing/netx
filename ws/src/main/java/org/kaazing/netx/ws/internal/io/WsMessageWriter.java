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

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

import org.kaazing.netx.ws.internal.WsURLConnectionImpl;

public class WsMessageWriter extends MessageWriter {
    private final OutputStream out;
    private final Writer writer;

    public WsMessageWriter(WsURLConnectionImpl connection) throws IOException {
        this.out = connection.getOutputStream();
        this.writer = connection.getWriter();
    }

    public void close() throws IOException {
        writer.close();
        out.close();
    }

    @Override
    public void write(char[] buf) throws IOException {
        write(buf, 0, buf.length);
    }

    @Override
    public void write(char[] buf, int off, int len) throws IOException {
        writer.write(buf, off, len);
    }

    @Override
    public void write(byte[] buf) throws IOException {
        write(buf, 0, buf.length);
    }

    @Override
    public void write(byte[] buf, int offset, int len) throws IOException {
        out.write(buf, offset, len);
    }
}
