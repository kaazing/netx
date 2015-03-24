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
import org.kaazing.netx.ws.internal.ext.flyweight.Close;
import org.kaazing.netx.ws.internal.ext.flyweight.Data;
import org.kaazing.netx.ws.internal.ext.flyweight.Ping;
import org.kaazing.netx.ws.internal.ext.flyweight.Pong;

public class WebSocketContext {
    protected final WsURLConnectionImpl connection;
    private final ListIterator<WebSocketExtensionSpi> iterator;
    private String errorMessage;

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

    public String getErrorMessage() {
        return errorMessage;
    }

    public void onError(String message) throws IOException {
        this.errorMessage = message;
        nextExtension().onError.accept(this);
    }

    public void onBinaryReceived(Data frame) throws IOException {
        nextExtension().onBinaryFrameReceived.accept(this, frame);
    }

    public void onCloseReceived(Close frame) throws IOException {
        nextExtension().onCloseFrameReceived.accept(this, frame);
    }

    public void onPingReceived(Ping frame) throws IOException {
        nextExtension().onPingFrameReceived.accept(this, frame);
    }

    public void onPongReceived(Pong frame) throws IOException {
        nextExtension().onPongFrameReceived.accept(this, frame);
    }

    public void onTextReceived(Data frame) throws IOException {
        nextExtension().onTextFrameReceived.accept(this, frame);
    }

    public void onBinarySent(Data frame) throws IOException {
        nextExtension().onBinaryFrameSent.accept(this, frame);
    }

    public void onCloseSent(Close frame) throws IOException {
        nextExtension().onCloseFrameSent.accept(this, frame);
    }

    public void onPongSent(Pong frame) throws IOException {
        nextExtension().onPongFrameSent.accept(this, frame);
    }

    public void onTextSent(Data frame) throws IOException {
        nextExtension().onTextFrameSent.accept(this, frame);
    }

    public void doSendBinary(Data dataFrame) throws IOException {
        connection.getOutputStateMachine().processBinary(connection, dataFrame);
    }

    public void doSendClose(Close closeFrame) throws IOException {
        connection.getOutputStateMachine().processClose(connection, closeFrame);
    }

    public void doSendPong(Pong pongFrame) throws IOException {
        connection.getOutputStateMachine().processPong(connection, pongFrame);
    }

    public void doSendText(Data dataFrame) throws IOException {
        connection.getOutputStateMachine().processText(connection, dataFrame);
    }
}
