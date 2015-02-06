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

public final class Utf8Util {
    private Utf8Util() {
    }

    public static int remainingUTF8Bytes(int byte1) {
        int width = getWidth((byte) byte1);
        return width - 1;

//        // See http://en.wikipedia.org/wiki/UTF-8#Description for details.
//        if ((byte1 & 0xFFFFFF80) == 0) { // 1 byte
//            return 0;
//        }
//        else if ((byte1 & 0xFFFFF800) == 0) { // 2 bytes.
//            return 1;
//        }
//        else if ((byte1 & 0xFFFF0000) == 0) { // 3 bytes.
//            return 2;
//        }
//        else if ((byte1 & 0xFF200000) == 0) { // 4 bytes.
//            return 3;
//        }
//
//        throw new IllegalStateException(format("Invalid UTF-8 sequence leader byte: 0x%02X", byte1));

//        switch (~highestOneBit(~byte1 & 0xff) & 0xff) {
//        case 0x7F:
//            // \u0000 - \u007F
//            return 0;
//        case 0xDF:
//            // \u0080 - \u07FF
//            return 1;
//        case 0xEF:
//            // \u0800 - \uFFFF
//            return 2;
//        case 0xF7:
//            // \u10000 - \u1FFFFF
//            return 3;
//        default:
//            throw new IllegalArgumentException(format("Invalid UTF-8 sequence leader byte: 0x%02X", byte1));
//        }
    }

    public static boolean isValidUTF8(byte[] input) {
        for (int index = 0; index < input.length; index++) {
            try {
                index += getNumberOfBytesInChar(input[index]);
            }
            catch (IllegalStateException ex) {
                return false;
            }
        }

        return true;
    }

    private static int getNumberOfBytesInChar(int byte1) {
        return getWidth((byte) byte1);
//        // See http://en.wikipedia.org/wiki/UTF-8#Description for details.
//        if ((byte1 & 0xFFFFFF80) == 0) { // 1 byte
//            return 1;
//        }
//        else if ((byte1 & 0xFFFFF800) == 0) { // 2 bytes.
//            return 2;
//        }
//        else if ((byte1 & 0xFFFF0000) == 0) { // 3 bytes.
//            return 3;
//        }
//        else if ((byte1 & 0xFF200000) == 0) { // 4 bytes.
//            return 4;
//        }
//
//        throw new IllegalStateException(format("Invalid UTF-8 sequence leader byte: 0x%02X", byte1));

        //      switch (~highestOneBit(~byte1 & 0xff) & 0xff) {
//      case 0x7F:
//          // \u0000 - \u007F
//          return 1;
//      case 0xDF:
//          // \u0080 - \u07FF
//          return 2;
//      case 0xEF:
//          // \u0800 - \uFFFF
//          return 3;
//      case 0xF7:
//          // \u10000 - \u1FFFFF
//          return 4;
//      default:
//          throw new IllegalArgumentException(format("Invalid UTF-8 sequence leader byte: 0x%02x", byte1));
//      }

    }

    public static int lengthUTF8Int(int value) {
        switch (highestOneBit(value)) {
        case 0x00:
        case 0x01:
        case 0x02:
        case 0x04:
        case 0x08:
        case 0x10:
        case 0x20:
        case 0x40:
            return 1;
        case 0x80:
        case 0x100:
        case 0x200:
        case 0x400:
        case 0x800:
            return 2;
        case 0x1000:
        case 0x2000:
        case 0x4000:
        case 0x8000:
        case 0x10000:
            return 3;
        case 0x20000:
        case 0x40000:
        case 0x80000:
        case 0x100000:
        case 0x200000:
            return 4;
//        case 0x400000:
//        case 0x800000:
//        case 0x1000000:
//        case 0x2000000:
//        case 0x4000000:
//            return 5;
//        case 0x8000000:
//        case 0x10000000:
//        case 0x20000000:
//        case 0x40000000:
//        case 0x80000000:
//            return 6;
        default:
            throw new IllegalArgumentException(Integer.toString(value));
        }
    }

    public static int getWidth(byte leadingByte) {
        if ((leadingByte & 0x80) == 0) {
            return 1;
        }

        for (byte i = 0; i < 7; i++) {
            int bitMask = 1 << (7 - i);

            if ((leadingByte & bitMask) != 0) {
                continue;
            }
            else {
                if (i >= 1 && i <= 6) {
                    return i;
                }

                throw new IllegalStateException(String.format("Invalid UTF-8 sequence leader byte: 0x%02x", leadingByte));
            }
        }

        throw new IllegalStateException(String.format("Invalid UTF-8 sequence leader byte: 0x%02x", leadingByte));
    }
}
