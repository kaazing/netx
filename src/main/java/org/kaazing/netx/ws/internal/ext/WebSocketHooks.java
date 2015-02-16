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

import org.kaazing.netx.ws.internal.ext.function.WebSocketFrameSupplier;
import org.kaazing.netx.ws.internal.ext.function.WebSocketSupplier;

public abstract class WebSocketHooks {
    public WebSocketSupplier<WebSocketStateMachine> whenInitialized;
    public WebSocketSupplier<WebSocketStateMachine> whenError;

    public WebSocketFrameSupplier<WebSocketStateMachine, Object> whenPingFrameReceived;
    public WebSocketFrameSupplier<WebSocketStateMachine, Object> whenPongFrameReceived;
    public WebSocketFrameSupplier<WebSocketStateMachine, Object> whenPongFrameSent;

    public WebSocketFrameSupplier<WebSocketStateMachine, Object> whenCloseFrameReceived;
    public WebSocketFrameSupplier<WebSocketStateMachine, Object> whenCloseFrameSent;

    public WebSocketFrameSupplier<WebSocketStateMachine, Object> whenBinaryFrameReceived;
    public WebSocketFrameSupplier<WebSocketStateMachine, Object> whenBinaryFrameSent;

    public WebSocketFrameSupplier<WebSocketStateMachine, Object> whenTextFrameReceived;
    public WebSocketFrameSupplier<WebSocketStateMachine, Object> whenTextFrameSent;
}
