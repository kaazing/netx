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
package org.kaazing.netx.ws.internal.ext.frame;

import static java.lang.String.format;

public enum OpCode {
    BINARY, CLOSE, CONTINUATION, PING, PONG, TEXT;

    public static OpCode fromInt(int value) {
        switch (value) {
        case 0x00:
            return CONTINUATION;
        case 0x01:
            return TEXT;
        case 0x02:
            return BINARY;
        case 0x08:
            return CLOSE;
        case 0x09:
            return PING;
        case 0x0A:
            return PONG;
        default:
            throw new ProtocolException(format("Unrecognized WebSocket OpCode %x", value));
        }
    };

    public static int toInt(OpCode value) {
        switch (value) {
        case CONTINUATION:
            return 0x00;
        case TEXT:
            return 0x01;
        case BINARY:
            return 0x02;
        case CLOSE:
            return 0x08;
        case PING:
            return 0x09;
        case PONG:
            return 0x0A;
        default:
            throw new ProtocolException(format("Unrecognised OpCode %s", value));
        }
    };
}
