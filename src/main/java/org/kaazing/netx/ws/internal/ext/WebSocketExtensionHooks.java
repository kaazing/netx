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
package org.kaazing.netx.ws.internal.ext;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;

import org.kaazing.netx.ws.internal.ext.function.WebSocketFrameSupplier;
import org.kaazing.netx.ws.internal.ext.function.WebSocketSupplier;

public abstract class WebSocketExtensionHooks {
    public WebSocketSupplier whenInitialized =
            new WebSocketSupplier() {

        @Override
        public void apply(WebSocketContext context) {
            return;
        }
    };

    public WebSocketSupplier whenError =
            new WebSocketSupplier() {

        @Override
        public void apply(WebSocketContext context) {
            return;
        }
    };

    public WebSocketFrameSupplier<ByteBuffer> whenPingFrameReceived =
            new WebSocketFrameSupplier<ByteBuffer>() {

        @Override
        public ByteBuffer apply(WebSocketContext context, byte flagsAndOpcode, ByteBuffer payload) throws IOException {
            return payload;
        }
    };

    public WebSocketFrameSupplier<ByteBuffer> whenPongFrameReceived =
            new WebSocketFrameSupplier<ByteBuffer>() {

        @Override
        public ByteBuffer apply(WebSocketContext context, byte flagsAndOpcode, ByteBuffer payload) throws IOException {
            return payload;
        }
    };

    public WebSocketFrameSupplier<ByteBuffer> whenPongFrameIsBeingSent =
            new WebSocketFrameSupplier<ByteBuffer>() {

        @Override
        public ByteBuffer apply(WebSocketContext context, byte flagsAndOpcode, ByteBuffer payload) throws IOException {
            return payload;
        }
    };

    public WebSocketFrameSupplier<ByteBuffer> whenCloseFrameReceived =
            new WebSocketFrameSupplier<ByteBuffer>() {

        @Override
        public ByteBuffer apply(WebSocketContext context, byte flagsAndOpcode, ByteBuffer payload) throws IOException {
            return payload;
        }
    };

    public WebSocketFrameSupplier<ByteBuffer> whenCloseFrameIsBeingSent =
            new WebSocketFrameSupplier<ByteBuffer>() {

        @Override
        public ByteBuffer apply(WebSocketContext context, byte flagsAndOpcode, ByteBuffer payload) throws IOException {
            return payload;
        }
    };

    public WebSocketFrameSupplier<ByteBuffer> whenBinaryFrameReceived =
            new WebSocketFrameSupplier<ByteBuffer>() {

        @Override
        public ByteBuffer apply(WebSocketContext context, byte flagsAndOpcode, ByteBuffer payload) throws IOException {
            return payload;
        }
    };

    public WebSocketFrameSupplier<ByteBuffer> whenBinaryFrameIsBeingSent =
            new WebSocketFrameSupplier<ByteBuffer>() {

        @Override
        public ByteBuffer apply(WebSocketContext context, byte flagsAndOpcode, ByteBuffer payload) throws IOException {
            return payload;
        }
    };

    public WebSocketFrameSupplier<CharBuffer> whenTextFrameReceived =
            new WebSocketFrameSupplier<CharBuffer>() {

        @Override
        public CharBuffer apply(WebSocketContext context, byte flagsAndOpcode, CharBuffer payload) throws IOException {
            return payload;
        }
    };

    public WebSocketFrameSupplier<CharBuffer> whenTextFrameIsBeingSent =
            new WebSocketFrameSupplier<CharBuffer>() {

        @Override
        public CharBuffer apply(WebSocketContext context, byte flagsAndOpcode, CharBuffer payload) throws IOException {
            return payload;
        }
    };
}
