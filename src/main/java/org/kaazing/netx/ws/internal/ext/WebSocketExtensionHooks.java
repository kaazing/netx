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

import org.kaazing.netx.ws.internal.ext.frame.Close;
import org.kaazing.netx.ws.internal.ext.frame.Data;
import org.kaazing.netx.ws.internal.ext.frame.Frame;
import org.kaazing.netx.ws.internal.ext.frame.Ping;
import org.kaazing.netx.ws.internal.ext.frame.Pong;
import org.kaazing.netx.ws.internal.ext.function.WebSocketFrameSupplier;
import org.kaazing.netx.ws.internal.ext.function.WebSocketSupplier;

public abstract class WebSocketExtensionHooks {
    public WebSocketSupplier whenInitialized = new WebSocketSupplier() {

        @Override
        public void apply(WebSocketContext context) {
            return;
        }
    };

    public WebSocketSupplier whenError = new WebSocketSupplier() {

        @Override
        public void apply(WebSocketContext context) {
            return;
        }
    };

    public WebSocketFrameSupplier whenBinaryFrameReceived = new WebSocketFrameSupplier() {

        @Override
        public void apply(WebSocketContext context, Frame frame) throws IOException {
            context.doNextBinaryFrameReceivedHook((Data) frame);
        }
    };

    public WebSocketFrameSupplier whenBinaryFrameSend = new WebSocketFrameSupplier() {

        @Override
        public void apply(WebSocketContext context, Frame frame) throws IOException {
            context.doNextBinaryFrameSendHook((Data) frame);
        }
    };

    public WebSocketFrameSupplier whenCloseFrameReceived = new WebSocketFrameSupplier() {

        @Override
        public void apply(WebSocketContext context, Frame frame) throws IOException {
            context.doNextCloseFrameReceivedHook((Close) frame);
        }
    };

    public WebSocketFrameSupplier whenCloseFrameSend = new WebSocketFrameSupplier() {

        @Override
        public void apply(WebSocketContext context, Frame frame) throws IOException {
            context.doNextCloseFrameSendHook((Close) frame);
        }
    };

    public WebSocketFrameSupplier whenPingFrameReceived = new WebSocketFrameSupplier() {

        @Override
        public void apply(WebSocketContext context, Frame frame) throws IOException {
            context.doNextPingFrameReceivedHook((Ping) frame);
        }
    };

    public WebSocketFrameSupplier whenPongFrameReceived = new WebSocketFrameSupplier() {

        @Override
        public void apply(WebSocketContext context, Frame frame) throws IOException {
            context.doNextPongFrameReceivedHook((Pong) frame);
        }
    };

    public WebSocketFrameSupplier whenPongFrameSend = new WebSocketFrameSupplier() {

        @Override
        public void apply(WebSocketContext context, Frame frame) throws IOException {
            context.doNextPongFrameSendHook((Pong) frame);
        }
    };

    public WebSocketFrameSupplier whenTextFrameReceived = new WebSocketFrameSupplier() {

        @Override
        public void apply(WebSocketContext context, Frame frame) throws IOException {
            context.doNextTextFrameReceivedHook((Data) frame);
        }
    };

    public WebSocketFrameSupplier whenTextFrameSend = new WebSocketFrameSupplier() {

        @Override
        public void apply(WebSocketContext context, Frame frame) throws IOException {
            context.doNextTextFrameSendHook((Data) frame);
        }
    };
}
