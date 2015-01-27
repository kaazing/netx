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
import static java.lang.String.format;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

public class WsReaderImpl extends Reader {
    private final InputStream in;
    private final byte[]      header;

    private int               headerOffset;
    private int               payloadOffset;
    private int               payloadLength;

    public WsReaderImpl(InputStream  in) throws IOException {
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
                        throw new IOException("Non-binary frame");
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
        byte[] payload = new byte[length];
        int    numBytes = in.read(payload, 0, length);

        if (numBytes == -1) {
            throw new IOException("End of stream");
        }

        int[]  utf8Decoded = decodeUTF8(payload);
        int    numChars = utf8Decoded.length;

        assert utf8Decoded.length <= len;

        for (int i = 0; i < numChars; i++) {
            cbuf[off + i] = (char) utf8Decoded[i];
        }

        payloadOffset += length;

        if (payloadOffset == payloadLength) {
            headerOffset = 0;
            payloadOffset = -1;
            payloadLength = 0;
        }

        return numChars;
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

    private static int[] decodeUTF8(byte[] source) {
        int   srcOffset = 0;
        int   destOffset = 0;
        int   capacity = calculateCapacity(source);
        int[] decoded = new int[capacity];

        while (srcOffset < source.length) {
            int byte1 = source[srcOffset++];
            int byte2;
            int byte3;
            int byte4;
            int byte5;
            int byte6;

            // positional value of highest zero bit indicates decode strategy
            // see http://en.wikipedia.org/wiki/UTF-8#Description
            switch (~highestOneBit(~byte1 & 0xff) & 0xff) {
            case 0x7F:
                // \u0000 - \u007F
                byte1 &= 0x7F;
                decoded[destOffset++] = byte1 &= 0x7F;
                break;
            case 0xDF:
                // \u0080 - \u07FF
                byte1 &= 0x1F;
                byte2 = source[srcOffset++] & 0x3F;
                decoded[destOffset++] = (byte1 << 6) | byte2;
                break;
            case 0xEF:
                // \u0800 - \uFFFF
                byte1 &= 0x0F;
                byte2 = source[srcOffset++] & 0x3F;
                byte3 = source[srcOffset++] & 0x3F;
                decoded[destOffset++] = (byte1 << 12) | (byte2 << 6) | byte3;
                break;
            case 0xF7:
                // \u10000 - \u1FFFFF
                byte1 &= 0x07;
                byte2 = source[srcOffset++] & 0x3F;
                byte3 = source[srcOffset++] & 0x3F;
                byte4 = source[srcOffset++] & 0x3F;
                decoded[destOffset++] = (byte1 << 18) | (byte2 << 12) | (byte3 << 6) | byte4;
                break;
            case 0xFB:
                // \u200000 - \u3FFFFFF
                byte1 &= 0x03;
                byte2 = source[srcOffset++] & 0x3F;
                byte3 = source[srcOffset++] & 0x3F;
                byte4 = source[srcOffset++] & 0x3F;
                byte5 = source[srcOffset++] & 0x3F;
                decoded[destOffset++] = (byte1 << 24) | (byte2 << 18) | (byte3 << 12) | (byte4 << 6) | byte5;
                break;
            case 0xFD:
                // \u4000000 - \u7FFFFFFF
                byte1 &= 0x03;
                byte2 = source[srcOffset++] & 0x3F;
                byte3 = source[srcOffset++] & 0x3F;
                byte4 = source[srcOffset++] & 0x3F;
                byte5 = source[srcOffset++] & 0x3F;
                byte6 = source[srcOffset++] & 0x3F;
                decoded[destOffset++] = (byte1 << 30) | (byte2 << 24) | (byte3 << 18) | (byte4 << 12) | (byte5 << 6) | byte6;
            default:
                throw new IllegalArgumentException(format("Invalid UTF-8 sequence leader byte: %02x", byte1));
            }
        }

        return decoded;
    }

    private static int calculateCapacity(byte[] buf) {
        int offset = 0;
        int capacity = 0;

        while (offset < buf.length) {
            int byte1 = buf[offset];

            // positional value of highest zero bit indicates decode strategy
            // see http://en.wikipedia.org/wiki/UTF-8#Description
            switch (~highestOneBit(~byte1 & 0xff) & 0xff) {
            case 0x7F:   // 0b01111111
                // \u0000 - \u007F
                capacity++;
                offset++;
                break;
            case 0xDF:   // 0b11011111
                // \u0080 - \u07FF
                capacity++;
                offset += 2;
                break;
            case 0xEF:   // 0b11101111
                // \u0800 - \uFFFF
                capacity++;
                offset += 3;
                break;
            case 0xF7:   // 0b11110111
                // \u10000 - \u1FFFFF
                capacity++;
                offset += 4;
                break;
            case 0xFB:   // 0b11111011
                // \u200000 - \u3FFFFFF
                capacity++;
                offset += 5;
                break;
            case 0xFD:   // 0b11111101
                // \u4000000 - \u7FFFFFFF
                capacity++;
                offset += 6;
                break;
            default:
                throw new IllegalArgumentException(format("Invalid UTF-8 sequence leader byte: %02x", byte1));
            }
        }

        assert (offset == buf.length);
        return capacity;
    }
}
