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

import static java.lang.Integer.highestOneBit;
import static java.lang.String.format;

public final class Utf8Util {
    private Utf8Util() {
    }

    public static int[] decode(byte[] source) {
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
