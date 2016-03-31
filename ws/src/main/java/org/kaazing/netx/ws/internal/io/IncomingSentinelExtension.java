/**
 * Copyright 2007-2015, Kaazing Corporation. All rights reserved.
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
package org.kaazing.netx.ws.internal.io;

import org.kaazing.netx.ws.internal.ext.WebSocketExtensionSpi;
import org.kaazing.netx.ws.internal.ext.flyweight.Opcode;
import org.kaazing.netx.ws.internal.ext.function.WebSocketFrameConsumer;

public final class IncomingSentinelExtension extends WebSocketExtensionSpi {
    private final WebSocketFrameConsumer defaultOnBinaryReceived;
    private final WebSocketFrameConsumer defaultOnContinuationReceived;
    private final WebSocketFrameConsumer defaultOnCloseReceived;
    private final WebSocketFrameConsumer defaultOnPingReceived;
    private final WebSocketFrameConsumer defaultOnPongReceived;
    private final WebSocketFrameConsumer defaultOnTextReceived;

    public IncomingSentinelExtension() {
        this.defaultOnBinaryReceived = super.onBinaryReceived;
        this.defaultOnContinuationReceived = super.onContinuationReceived;
        this.defaultOnCloseReceived = super.onCloseReceived;
        this.defaultOnPingReceived = super.onPingReceived;
        this.defaultOnPongReceived = super.onPongReceived;
        this.defaultOnTextReceived = super.onTextReceived;
    }

    public void setTerminalConsumer(WebSocketFrameConsumer terminalConsumer, Opcode opcode) {
        restoreConsumers();

        switch (opcode) {
        case BINARY:
            super.onBinaryReceived = terminalConsumer;
            break;
        case CONTINUATION:
            super.onContinuationReceived = terminalConsumer;
            break;
        case CLOSE:
            super.onCloseReceived = terminalConsumer;
            break;
        case PING:
            super.onPingReceived = terminalConsumer;
            break;
        case PONG:
            super.onPongReceived = terminalConsumer;
            break;
        case TEXT:
            super.onTextReceived = terminalConsumer;
            break;
        }
    }

    private void restoreConsumers() {
        super.onBinaryReceived = this.defaultOnBinaryReceived;
        super.onContinuationReceived = this.defaultOnContinuationReceived;
        super.onCloseReceived = this.defaultOnCloseReceived;
        super.onPingReceived = this.defaultOnPingReceived;
        super.onPongReceived = this.defaultOnPongReceived;
        super.onTextReceived = this.defaultOnTextReceived;
    }
}
