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
import java.util.List;
import java.util.ListIterator;

import org.kaazing.netx.ws.internal.WsURLConnectionImpl;
import org.kaazing.netx.ws.internal.ext.frame.Close;
import org.kaazing.netx.ws.internal.ext.frame.Data;
import org.kaazing.netx.ws.internal.ext.frame.Ping;
import org.kaazing.netx.ws.internal.ext.frame.Pong;

public abstract class WebSocketContext {
    protected final WsURLConnectionImpl connection;
    private final ListIterator<WebSocketExtensionHooks> iterator;

    public WebSocketContext(WsURLConnectionImpl connection, List<WebSocketExtensionHooks> extensionHooks) {
        this.connection = connection;
        this.iterator = extensionHooks.listIterator();
    }

    public WebSocketExtensionHooks nextExtensionHooks() {
        if (iterator.hasNext()) {
            return iterator.next();
        }

        return null;
    }

    public void doNextBinaryFrameReceivedHook(Data frame) throws IOException {
        WebSocketExtensionHooks next = nextExtensionHooks();
        if (next != null) {
            next.whenBinaryFrameReceived.apply(this, frame);
        }

        return;
    }

    public void doNextCloseFrameReceivedHook(Close frame) throws IOException {
        WebSocketExtensionHooks next = nextExtensionHooks();
        if (next != null) {
            next.whenCloseFrameReceived.apply(this, frame);
        }

        return;
    }

    public void doNextPingFrameReceivedHook(Ping frame) throws IOException {
        WebSocketExtensionHooks next = nextExtensionHooks();
        if (next != null) {
            next.whenPingFrameReceived.apply(this, frame);
        }

        return;
    }

    public void doNextPongFrameReceivedHook(Pong frame) throws IOException {
        WebSocketExtensionHooks next = nextExtensionHooks();
        if (next != null) {
            next.whenPongFrameReceived.apply(this, frame);
        }

        return;
    }

    public void doNextTextFrameReceivedHook(Data frame) throws IOException {
        WebSocketExtensionHooks next = nextExtensionHooks();
        if (next != null) {
            next.whenTextFrameReceived.apply(this, frame);
        }

        return;
    }

    public void doNextBinaryFrameSendHook(Data frame) throws IOException {
        WebSocketExtensionHooks next = nextExtensionHooks();
        if (next != null) {
            next.whenBinaryFrameSend.apply(this, frame);
        }

        return;
    }

    public void doNextCloseFrameSendHook(Close frame) throws IOException {
        WebSocketExtensionHooks next = nextExtensionHooks();
        if (next != null) {
            next.whenCloseFrameSend.apply(this, frame);
        }

        return;
    }

    public void doNextPongFrameSendHook(Pong frame) throws IOException {
        WebSocketExtensionHooks next = nextExtensionHooks();
        if (next != null) {
            next.whenPongFrameSend.apply(this, frame);
        }

        return;
    }

    public void doNextTextFrameSendHook(Data frame) throws IOException {
        WebSocketExtensionHooks next = nextExtensionHooks();
        if (next != null) {
            next.whenTextFrameSend.apply(this, frame);
        }

        return;
    }

    public void sendBinaryFrame(ByteBuffer payload) throws IOException {
        // ### TODO
    }

    public void sendClosedFrame(int code, byte[] reason) throws IOException {
        // ### TODO
    }

    public void sendPongFrame(ByteBuffer payload) throws IOException {
        // ### TODO
    }

    public void sendTextFrame(CharBuffer payload) throws IOException {
        // ### TODO
    }

}
