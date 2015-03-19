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

import org.kaazing.netx.ws.WebSocketExtension;
import org.kaazing.netx.ws.internal.ext.frame.Close;
import org.kaazing.netx.ws.internal.ext.frame.Data;
import org.kaazing.netx.ws.internal.ext.frame.Frame;
import org.kaazing.netx.ws.internal.ext.frame.Ping;
import org.kaazing.netx.ws.internal.ext.frame.Pong;
import org.kaazing.netx.ws.internal.ext.function.WebSocketFrameSupplier;
import org.kaazing.netx.ws.internal.ext.function.WebSocketSupplier;

/**
 * WebSocketExtensionSpi is part of <i>Service Provider Interface</i> <em>(SPI)</em> for extension developers.
 * <p>
 * Developing an extension involves the following:
 * <UL>
 *   <LI> a sub-class of {@link WebSocketExtensionFactorySpi}
 *   <LI> a sub-class of {@link WebSocketExtensionSpi}
 *   <LI> a sub-class of {@link WebSocketExtension} with {@link Parameter}s defined as constants
 * </UL>
 * <p>
 * When an enabled extension is successfully negotiated, an instance of this class is created using the corresponding
 * {@link WebSocketExtensionFactorySpi} that is registered through META-INF/services. This class is used to instantiate the
 * hooks that can be exercised as the state machine transitions from one state to another while
 * handling the WebSocket traffic. Based on the functionality of the extension, the developer can decide which hooks to code.
 */
public abstract class WebSocketExtensionSpi {

    public WebSocketSupplier onInitialized = new WebSocketSupplier() {

        @Override
        public void apply(WebSocketContext context) {
            return;
        }
    };

    public WebSocketSupplier onError = new WebSocketSupplier() {

        @Override
        public void apply(WebSocketContext context) {
            return;
        }
    };

    public WebSocketFrameSupplier onBinaryFrameReceived = new WebSocketFrameSupplier() {

        @Override
        public void apply(WebSocketContext context, Frame frame) throws IOException {
            context.onBinaryFrameReceived((Data) frame);
        }
    };

    public WebSocketFrameSupplier onBinaryFrameSent = new WebSocketFrameSupplier() {

        @Override
        public void apply(WebSocketContext context, Frame frame) throws IOException {
            context.onBinaryFrameSent((Data) frame);
        }
    };

    public WebSocketFrameSupplier onCloseFrameReceived = new WebSocketFrameSupplier() {

        @Override
        public void apply(WebSocketContext context, Frame frame) throws IOException {
            context.onCloseFrameReceived((Close) frame);
        }
    };

    public WebSocketFrameSupplier onCloseFrameSent = new WebSocketFrameSupplier() {

        @Override
        public void apply(WebSocketContext context, Frame frame) throws IOException {
            context.onCloseFrameSent((Close) frame);
        }
    };

    public WebSocketFrameSupplier onPingFrameReceived = new WebSocketFrameSupplier() {

        @Override
        public void apply(WebSocketContext context, Frame frame) throws IOException {
            context.onPingFrameReceived((Ping) frame);
        }
    };

    public WebSocketFrameSupplier onPongFrameReceived = new WebSocketFrameSupplier() {

        @Override
        public void apply(WebSocketContext context, Frame frame) throws IOException {
            context.onPongFrameReceived((Pong) frame);
        }
    };

    public WebSocketFrameSupplier onPongFrameSent = new WebSocketFrameSupplier() {

        @Override
        public void apply(WebSocketContext context, Frame frame) throws IOException {
            context.onPongFrameSent((Pong) frame);
        }
    };

    public WebSocketFrameSupplier onTextFrameReceived = new WebSocketFrameSupplier() {

        @Override
        public void apply(WebSocketContext context, Frame frame) throws IOException {
            context.onTextFrameReceived((Data) frame);
        }
    };

    public WebSocketFrameSupplier onTextFrameSent = new WebSocketFrameSupplier() {

        @Override
        public void apply(WebSocketContext context, Frame frame) throws IOException {
            context.onTextFrameSent((Data) frame);
        }
    };
}
