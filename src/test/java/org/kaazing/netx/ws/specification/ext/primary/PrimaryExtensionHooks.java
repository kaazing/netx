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
package org.kaazing.netx.ws.specification.ext.primary;

import java.io.IOException;
import java.nio.CharBuffer;

import org.kaazing.netx.ws.internal.ext.WebSocketContext;
import org.kaazing.netx.ws.internal.ext.WebSocketExtensionHooks;
import org.kaazing.netx.ws.internal.ext.function.WebSocketFrameSupplier;

public class PrimaryExtensionHooks extends WebSocketExtensionHooks {
    {
        whenTextFrameReceived = new WebSocketFrameSupplier<CharBuffer>() {

            @Override
            public CharBuffer apply(WebSocketContext context, byte flagsAndOpcode, CharBuffer payload) throws IOException {
                String str = "Hello, " + payload.toString();
                char[] cbuf = str.toCharArray();
                CharBuffer transformedPayload = CharBuffer.wrap(cbuf);
                return context.doNextTextFrameReceivedHook(flagsAndOpcode, transformedPayload);
            }
        };

        whenTextFrameIsBeingSent = new WebSocketFrameSupplier<CharBuffer>() {

            @Override
            public CharBuffer apply(WebSocketContext context, byte flagsAndOpcode, CharBuffer payload) throws IOException {
                String str = payload.toString();
                if (str.startsWith("Hello, ")) {
                    str = str.substring("Hello,  ".length() - 1);
                }
                CharBuffer transformedPayload = CharBuffer.wrap(str.toCharArray());
                return context.doNextTextFrameIsBeingSentHook(flagsAndOpcode, transformedPayload);
            }
        };
    }
}
