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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;

import org.kaazing.netx.ws.WsURLConnection;
import org.kaazing.netx.ws.internal.ext.function.WebSocketFrameSupplier;
import org.kaazing.netx.ws.internal.ext.function.WebSocketSupplier;

public abstract class WebSocketExtensionHooks {
    public WebSocketSupplier<WsURLConnection> whenInitialized =
            new WebSocketSupplier<WsURLConnection>() {

        @Override
        public void apply(WsURLConnection connection) {
            return;
        }
    };

    public WebSocketSupplier<WsURLConnection> whenError =
            new WebSocketSupplier<WsURLConnection>() {

        @Override
        public void apply(WsURLConnection connection) {
            return;
        }
    };

    public WebSocketFrameSupplier<WsURLConnection, ByteBuffer> whenPingFrameReceived =
            new WebSocketFrameSupplier<WsURLConnection, ByteBuffer>() {

        @Override
        public ByteBuffer apply(WsURLConnection connection, ByteBuffer payload) {
            return payload;
        }
    };

    public WebSocketFrameSupplier<WsURLConnection, ByteBuffer> whenPongFrameReceived =
            new WebSocketFrameSupplier<WsURLConnection, ByteBuffer>() {

        @Override
        public ByteBuffer apply(WsURLConnection connection, ByteBuffer payload) {
            return payload;
        }
    };

    public WebSocketFrameSupplier<WsURLConnection, ByteBuffer> whenPongFrameIsBeingSent =
            new WebSocketFrameSupplier<WsURLConnection, ByteBuffer>() {

        @Override
        public ByteBuffer apply(WsURLConnection connection, ByteBuffer payload) {
            return payload;
        }
    };

    public WebSocketFrameSupplier<WsURLConnection, ByteBuffer> whenCloseFrameReceived =
            new WebSocketFrameSupplier<WsURLConnection, ByteBuffer>() {

        @Override
        public ByteBuffer apply(WsURLConnection connection, ByteBuffer payload) {
            return payload;
        }
    };

    public WebSocketFrameSupplier<WsURLConnection, ByteBuffer> whenCloseFrameIsBeingSent =
            new WebSocketFrameSupplier<WsURLConnection, ByteBuffer>() {

        @Override
        public ByteBuffer apply(WsURLConnection connection, ByteBuffer payload) {
            return payload;
        }
    };

    public WebSocketFrameSupplier<WsURLConnection, ByteBuffer> whenBinaryFrameReceived =
            new WebSocketFrameSupplier<WsURLConnection, ByteBuffer>() {

        @Override
        public ByteBuffer apply(WsURLConnection connection, ByteBuffer payload) {
            return payload;
        }
    };

    public WebSocketFrameSupplier<WsURLConnection, ByteBuffer> whenBinaryFrameIsBeingSent =
            new WebSocketFrameSupplier<WsURLConnection, ByteBuffer>() {

        @Override
        public ByteBuffer apply(WsURLConnection connection, ByteBuffer payload) {
            return payload;
        }
    };

    public WebSocketFrameSupplier<WsURLConnection, CharBuffer> whenTextFrameReceived =
            new WebSocketFrameSupplier<WsURLConnection, CharBuffer>() {

        @Override
        public CharBuffer apply(WsURLConnection connection, CharBuffer payload) {
            return payload;
        }
    };

    public WebSocketFrameSupplier<WsURLConnection, CharBuffer> whenTextFrameIsBeingSent =
            new WebSocketFrameSupplier<WsURLConnection, CharBuffer>() {

        @Override
        public CharBuffer apply(WsURLConnection connection, CharBuffer payload) {
            return payload;
        }
    };
}
