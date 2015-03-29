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
import org.kaazing.netx.ws.internal.ext.flyweight.Frame;

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

    public void onBinaryReceived(Frame frame) throws IOException {
        nextExtension().onBinaryReceived.accept(this, frame);
    }

    public void onCloseReceived(Frame frame) throws IOException {
        nextExtension().onCloseReceived.accept(this, frame);
    }

    public void onContinuationReceived(Frame frame) throws IOException {
        nextExtension().onContinuationReceived.accept(this, frame);
    }

    public void onPingReceived(Frame frame) throws IOException {
        nextExtension().onPingReceived.accept(this, frame);
    }

    public void onPongReceived(Frame frame) throws IOException {
        nextExtension().onPongReceived.accept(this, frame);
    }

    public void onTextReceived(Frame frame) throws IOException {
        nextExtension().onTextReceived.accept(this, frame);
    }

    public void onBinarySent(Frame frame) throws IOException {
        nextExtension().onBinarySent.accept(this, frame);
    }

    public void onCloseSent(Frame frame) throws IOException {
        nextExtension().onCloseSent.accept(this, frame);
    }

    public void onContinuationSent(Frame frame) throws IOException {
        nextExtension().onContinuationSent.accept(this, frame);
    }

    public void onPongSent(Frame frame) throws IOException {
        nextExtension().onPongSent.accept(this, frame);
    }

    public void onTextSent(Frame frame) throws IOException {
        nextExtension().onTextSent.accept(this, frame);
    }

    public void doSendBinary(Frame dataFrame) throws IOException {
        connection.getOutputStateMachine().processBinary(connection, dataFrame);
    }

    public void doSendClose(Frame closeFrame) throws IOException {
        connection.getOutputStateMachine().processClose(connection, closeFrame);
    }

    public void doSendContinuation(Frame dataFrame) throws IOException {
        connection.getOutputStateMachine().processContinuation(connection, dataFrame);
    }

    public void doSendPong(Frame pongFrame) throws IOException {
        connection.getOutputStateMachine().processPong(connection, pongFrame);
    }

    public void doSendText(Frame dataFrame) throws IOException {
        connection.getOutputStateMachine().processText(connection, dataFrame);
    }
}
