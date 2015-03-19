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
import java.util.List;
import java.util.ListIterator;

import org.kaazing.netx.ws.internal.WsURLConnectionImpl;
import org.kaazing.netx.ws.internal.ext.frame.Close;
import org.kaazing.netx.ws.internal.ext.frame.Data;
import org.kaazing.netx.ws.internal.ext.frame.Ping;
import org.kaazing.netx.ws.internal.ext.frame.Pong;

public class WebSocketContext {
    protected final WsURLConnectionImpl connection;
    private final ListIterator<WebSocketExtensionSpi> iterator;

    public WebSocketContext(WsURLConnectionImpl connection, List<WebSocketExtensionSpi> extensions) {
        this.connection = connection;
        this.iterator = extensions.listIterator();
    }

    public WebSocketExtensionSpi nextExtension() {
        if (iterator.hasNext()) {
            return iterator.next();
        }

        return null;
    }

    public void onBinaryFrameReceived(Data frame) throws IOException {
        nextExtension().onBinaryFrameReceived.apply(this, frame);
    }

    public void onCloseFrameReceived(Close frame) throws IOException {
        nextExtension().onCloseFrameReceived.apply(this, frame);
    }

    public void onPingFrameReceived(Ping frame) throws IOException {
        nextExtension().onPingFrameReceived.apply(this, frame);
    }

    public void onPongFrameReceived(Pong frame) throws IOException {
        nextExtension().onPongFrameReceived.apply(this, frame);
    }

    public void onTextFrameReceived(Data frame) throws IOException {
        nextExtension().onTextFrameReceived.apply(this, frame);
    }

    public void onBinaryFrameSent(Data frame) throws IOException {
        nextExtension().onBinaryFrameSent.apply(this, frame);
    }

    public void onCloseFrameSent(Close frame) throws IOException {
        nextExtension().onCloseFrameSent.apply(this, frame);
    }

    public void onPongFrameSent(Pong frame) throws IOException {
        nextExtension().onPongFrameSent.apply(this, frame);
    }

    public void onTextFrameSent(Data frame) throws IOException {
        nextExtension().onTextFrameSent.apply(this, frame);
    }

    public void doSendBinaryFrame(Data dataFrame) throws IOException {
        // ### TODO
    }

    public void doSendClosedFrame(Close closeFrame) throws IOException {
        // ### TODO
    }

    public void doSendPongFrame(Pong pongFrame) throws IOException {
        // ### TODO
    }

    public void doSendTextFrame(Data dataFrame) throws IOException {
        // ### TODO
    }
}
