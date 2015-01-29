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
import java.io.InputStream;
import java.io.Reader;

public class WsReader extends Reader {
    private final InputStream in;
    private final byte[]      header;

    private int               headerOffset;
    private int               payloadOffset;
    private int               payloadLength;

    public WsReader(InputStream  in) throws IOException {
        this.in = in;
        this.header = new byte[10];
        this.payloadOffset = -1;
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }

        while (payloadLength == 0) {
            while (payloadOffset == -1) {
                int headerByte = in.read();
                if (headerByte == -1) {
                    return -1;
                }
                header[headerOffset++] = (byte) headerByte;
                switch (headerOffset) {
                case 1:
                    int opcode = header[0] & 0x07;
                    switch (opcode) {
                    case 0x00:
                    case 0x01:
                        break;
                    default:
                        // TODO: skip
                        throw new IOException("Non-text frame");
                    }
                    break;
                case 2:
                    boolean masked = (header[1] & 0x80) != 0x00;
                    if (masked) {
                        throw new IOException("Masked server-to-client frame");
                    }
                    switch (header[1] & 0x7f) {
                    case 126:
                    case 127:
                        break;
                    default:
                        payloadOffset = 0;
                        payloadLength = payloadLength(header);
                    }
                    break;
                case 4:
                    switch (header[1] & 0x7f) {
                    case 126:
                        payloadOffset = 0;
                        payloadLength = payloadLength(header);
                    default:
                        break;
                    }
                    break;
                case 10:
                    switch (header[1] & 0x7f) {
                    case 127:
                        payloadOffset = 0;
                        payloadLength = payloadLength(header);
                    default:
                        break;
                    }
                    break;
                }
            }
        }

        int    length = Math.min(len, payloadLength);
        byte[] bytes = new byte[length];

        int    bytesRead = in.read(bytes, 0, length);
        if (bytesRead == -1) {
            throw new IOException("End of stream");
        }

        // ### TODO: When we test with multi-byte chars, we can decide whether
        //           to convert byte[] to a UTF-8 char[] using the following line:
        // int[]  utf8buf = Utf8Util.decode(buffer);
        char[] utf8buf = new String(bytes, 0, bytesRead, "UTF-8").toCharArray();
        int    charsRead = utf8buf.length;

        assert utf8buf.length <= len;

        for (int i = 0; i < charsRead; i++) {
            cbuf[off + i] = utf8buf[i];
        }

        payloadOffset += length;

        if (payloadOffset == payloadLength) {
            headerOffset = 0;
            payloadOffset = -1;
            payloadLength = 0;
        }

        return charsRead;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    private static int payloadLength(byte[] header) {
        int length = header[1] & 0x7f;
        switch (length) {
        case 126:
            return (header[2] & 0xff) << 8 | (header[3] & 0xff);
        case 127:
            return (header[2] & 0xff) << 56 |
                   (header[3] & 0xff) << 48 |
                   (header[4] & 0xff) << 40 |
                   (header[5] & 0xff) << 32 |
                   (header[6] & 0xff) << 24 |
                   (header[7] & 0xff) << 16 |
                   (header[8] & 0xff) << 8  |
                   (header[9] & 0xff);
        default:
            return length;
        }
    }
}
