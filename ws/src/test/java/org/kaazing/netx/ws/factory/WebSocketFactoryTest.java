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
package org.kaazing.netx.ws.factory;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.Test;
import org.kaazing.netx.ws.WebSocket;
import org.kaazing.netx.ws.WebSocketFactory;

public class WebSocketFactoryTest {
    public WebSocketFactoryTest() {
    }

    @Test
    public void testWebSocketCreation() throws URISyntaxException {
        WebSocketFactory factory = WebSocketFactory.newInstance();
        WebSocket ws = factory.createWebSocket(URI.create("ws://echo.websocket.org"));
    }
}
