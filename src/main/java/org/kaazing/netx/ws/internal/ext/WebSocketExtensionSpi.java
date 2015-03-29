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

import org.kaazing.netx.ws.internal.ext.flyweight.Frame;
import org.kaazing.netx.ws.internal.ext.function.WebSocketConsumer;
import org.kaazing.netx.ws.internal.ext.function.WebSocketFrameConsumer;

/**
 * {@link WebSocketExtensionSpi} is part of <i>Service Provider Interface</i> <em>(SPI)</em> for extension developers.
 * <p>
 * Developing an extension involves implementing:
 * <UL>
 *   <LI> a sub-class of {@link WebSocketExtensionFactorySpi}
 *   <LI> a sub-class of {@link WebSocketExtensionSpi}
 * </UL>
 * <p>
 * When an enabled extension is successfully negotiated, an instance of this class is created using the corresponding
 * {@link WebSocketExtensionFactorySpi} that is registered through META-INF/services. This class is used to instantiate the
 * hooks that can be exercised as the state machine transitions from one state to another while
 * handling the WebSocket traffic. Based on the functionality of the extension, the developer can decide which hooks to code.
 */
public abstract class WebSocketExtensionSpi {

    public WebSocketConsumer onInitialized = new WebSocketConsumer() {

        @Override
        public void accept(WebSocketContext context) {
            return;
        }
    };

    public WebSocketConsumer onError = new WebSocketConsumer() {

        @Override
        public void accept(WebSocketContext context) {
            return;
        }
    };

    public WebSocketFrameConsumer onBinaryReceived = new WebSocketFrameConsumer() {

        @Override
        public void accept(WebSocketContext context, Frame frame) throws IOException {
            context.onBinaryReceived(frame);
        }
    };

    public WebSocketFrameConsumer onBinarySent = new WebSocketFrameConsumer() {

        @Override
        public void accept(WebSocketContext context, Frame frame) throws IOException {
            context.onBinarySent(frame);
        }
    };

    public WebSocketFrameConsumer onContinuationReceived = new WebSocketFrameConsumer() {

        @Override
        public void accept(WebSocketContext context, Frame frame) throws IOException {
            context.onContinuationReceived(frame);
        }
    };

    public WebSocketFrameConsumer onContinuationSent = new WebSocketFrameConsumer() {

        @Override
        public void accept(WebSocketContext context, Frame frame) throws IOException {
            context.onContinuationSent(frame);
        }
    };

    public WebSocketFrameConsumer onCloseReceived = new WebSocketFrameConsumer() {

        @Override
        public void accept(WebSocketContext context, Frame frame) throws IOException {
            context.onCloseReceived(frame);
        }
    };

    public WebSocketFrameConsumer onCloseSent = new WebSocketFrameConsumer() {

        @Override
        public void accept(WebSocketContext context, Frame frame) throws IOException {
            context.onCloseSent(frame);
        }
    };

    public WebSocketFrameConsumer onPingReceived = new WebSocketFrameConsumer() {

        @Override
        public void accept(WebSocketContext context, Frame frame) throws IOException {
            context.onPingReceived(frame);
        }
    };

    public WebSocketFrameConsumer onPongReceived = new WebSocketFrameConsumer() {

        @Override
        public void accept(WebSocketContext context, Frame frame) throws IOException {
            context.onPongReceived(frame);
        }
    };

    public WebSocketFrameConsumer onPongSent = new WebSocketFrameConsumer() {

        @Override
        public void accept(WebSocketContext context, Frame frame) throws IOException {
            context.onPongSent(frame);
        }
    };

    public WebSocketFrameConsumer onTextReceived = new WebSocketFrameConsumer() {

        @Override
        public void accept(WebSocketContext context, Frame frame) throws IOException {
            context.onTextReceived(frame);
        }
    };

    public WebSocketFrameConsumer onTextSent = new WebSocketFrameConsumer() {

        @Override
        public void accept(WebSocketContext context, Frame frame) throws IOException {
            context.onTextSent(frame);
        }
    };
}
