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
    public WebSocketSupplier<WsURLConnection> whenInitialized;
    public WebSocketSupplier<WsURLConnection> whenError;

    public WebSocketFrameSupplier<WsURLConnection, ByteBuffer> whenPingFrameReceived;
    public WebSocketFrameSupplier<WsURLConnection, ByteBuffer> whenPongFrameReceived;
    public WebSocketFrameSupplier<WsURLConnection, ByteBuffer> whenPongFrameIsBeingSent;

    public WebSocketFrameSupplier<WsURLConnection, ByteBuffer> whenCloseFrameReceived;
    public WebSocketFrameSupplier<WsURLConnection, ByteBuffer> whenCloseFrameIsBeingSent;

    public WebSocketFrameSupplier<WsURLConnection, ByteBuffer> whenBinaryFrameReceived;
    public WebSocketFrameSupplier<WsURLConnection, ByteBuffer> whenBinaryFrameIsBeingSent;

    public WebSocketFrameSupplier<WsURLConnection, CharBuffer> whenTextFrameReceived;
    public WebSocketFrameSupplier<WsURLConnection, CharBuffer> whenTextFrameIsBeingSent;
}
