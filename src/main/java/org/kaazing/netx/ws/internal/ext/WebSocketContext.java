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

    public ByteBuffer doNextBinaryFrameReceivedHook(byte flagsAndOpcode, ByteBuffer payload) throws IOException {
        WebSocketExtensionHooks next = nextExtensionHooks();
        if (next != null) {
            return next.whenBinaryFrameReceived.apply(this, flagsAndOpcode, payload);
        }

        return payload;
    }

    public ByteBuffer doNextCloseFrameReceivedHook(byte flagsAndOpcode, ByteBuffer payload) throws IOException {
        WebSocketExtensionHooks next = nextExtensionHooks();
        if (next != null) {
            return next.whenCloseFrameReceived.apply(this, flagsAndOpcode, payload);
        }

        return payload;
    }

    public ByteBuffer doNextPingFrameReceivedHook(byte flagsAndOpcode, ByteBuffer payload) throws IOException {
        WebSocketExtensionHooks next = nextExtensionHooks();
        if (next != null) {
            return next.whenPingFrameReceived.apply(this, flagsAndOpcode, payload);
        }

        return payload;
    }

    public ByteBuffer doNextPongFrameReceivedHook(byte flagsAndOpcode, ByteBuffer payload) throws IOException {
        WebSocketExtensionHooks next = nextExtensionHooks();
        if (next != null) {
            return next.whenPongFrameReceived.apply(this, flagsAndOpcode, payload);
        }

        return payload;
    }

    public CharBuffer doNextTextFrameReceivedHook(byte flagsAndOpcode, CharBuffer payload) throws IOException {
        WebSocketExtensionHooks next = nextExtensionHooks();
        if (next != null) {
            return next.whenTextFrameReceived.apply(this, flagsAndOpcode, payload);
        }

        return payload;
    }

    public ByteBuffer doNextBinaryFrameIsBeingSentHook(byte flagsAndOpcode, ByteBuffer payload) throws IOException {
        WebSocketExtensionHooks next = nextExtensionHooks();
        if (next != null) {
            return next.whenBinaryFrameIsBeingSent.apply(this, flagsAndOpcode, payload);
        }

        return payload;
    }

    public ByteBuffer doNextCloseFrameIsBeingSentHook(byte flagsAndOpcode, ByteBuffer payload) throws IOException {
        WebSocketExtensionHooks next = nextExtensionHooks();
        if (next != null) {
            return next.whenCloseFrameIsBeingSent.apply(this, flagsAndOpcode, payload);
        }

        return payload;
    }

    public ByteBuffer doNextPongFrameIsBeingSentHook(byte flagsAndOpcode, ByteBuffer payload) throws IOException {
        WebSocketExtensionHooks next = nextExtensionHooks();
        if (next != null) {
            return next.whenPongFrameIsBeingSent.apply(this, flagsAndOpcode, payload);
        }

        return payload;
    }

    public CharBuffer doNextTextFrameIsBeingSentHook(byte flagsAndOpcode, CharBuffer payload) throws IOException {
        WebSocketExtensionHooks next = nextExtensionHooks();
        if (next != null) {
            return next.whenTextFrameIsBeingSent.apply(this, flagsAndOpcode, payload);
        }

        return payload;
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
