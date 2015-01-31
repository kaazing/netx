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

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

public final class Utf8Util {
    private Utf8Util() {
    }

    public static int remainingUTF8Bytes(int byte1) {
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

    public static boolean isValidUTF8(byte[] input) {
        CharsetDecoder cs = Charset.forName("UTF-8").newDecoder();

        try {
            cs.decode(ByteBuffer.wrap(input));
            return true;
        }
        catch (CharacterCodingException e) {
            return false;
        }
    }
}
