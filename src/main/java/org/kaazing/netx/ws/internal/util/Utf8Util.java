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

import static java.lang.Character.charCount;
import static java.lang.Character.codePointAt;
import static java.lang.String.format;

import java.io.IOException;

public final class Utf8Util {

    private Utf8Util() {
    }

    public static int byteCountUTF8(char[] cbuf, int offset, int length) throws IOException {
        int count = 0;
        while (offset < length) {
            int codePoint = codePointAt(cbuf, offset);
            count += byteCountUTF8(codePoint);
            offset += charCount(codePoint);
        }
        return count;
    }

    public static int byteCountUTF8(int codePoint) throws IOException {
        if ((codePoint | 0x7f) == 0x7f) {
            return 1;
        }
        else if ((codePoint | 0x07ff) == 0x07ff) {
            return 2;
        }
        else if ((codePoint | 0xffff) == 0xffff) {
            return 3;
        }
        else if ((codePoint | 0x1fffff) == 0x1fffff) {
            return 4;
        }
        else {
            throw new IOException("Invalid UTF-8 code point. UTF-8 code point cannot span for more than 4 bytes.");
        }
    }

    public static int initialDecodeUTF8(int remainingWidth, int encodedByte) throws IOException {
        switch (remainingWidth) {
        case 0:
            return encodedByte & 0x7f;
        case 1:
            return encodedByte & 0x1f;
        case 2:
            return encodedByte & 0x0f;
        case 3:
            return encodedByte & 0x07;
        default:
            throw new IOException("Invalid UTF-8 byte sequence. UTF-8 char cannot span for more than 4 bytes.");
        }
    }

    public static int remainingDecodeUTF8(int decodedBytes, int remainingWidth, int encodedByte) throws IOException {
        switch (remainingWidth) {
        case 3:
        case 2:
        case 1:
            return (decodedBytes << 6) | (encodedByte & 0x3f);
        case 0:
            return decodedBytes;
        default:
            throw new IOException("Invalid UTF-8 byte sequence. UTF-8 char cannot span for more than 4 bytes.");
        }
    }

    public static int remainingBytesUTF8(int leadingByte) {
        if ((leadingByte & 0x80) == 0) {
            return 0;
        }

        for (byte i = 0; i < 7; i++) {
            int bitMask = 1 << (7 - i);

            if ((leadingByte & bitMask) != 0) {
                continue;
            }
            else {
                switch (i) {
                case 0:
                case 7:
                    throw new IllegalStateException(format("Invalid UTF-8 sequence leader byte: 0x%02x", leadingByte));
                default:
                    return i - 1;
                }
            }
        }

        throw new IllegalStateException(String.format("Invalid UTF-8 sequence leader byte: 0x%02x", leadingByte));
    }

    public static boolean validBytesUTF8(byte[] input) {
        for (int index = 0; index < input.length;) {
            byte leadingByte = input[index++];
            if ((leadingByte & 0xc0) == 0x80) {
                return false;
            }
            int remaining = remainingBytesUTF8(leadingByte);
            switch (remaining) {
            case 0:
                break;
            default:
                while (remaining-- > 0) {
                    if ((input[index++] & 0xc0) != 0x80) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

}
