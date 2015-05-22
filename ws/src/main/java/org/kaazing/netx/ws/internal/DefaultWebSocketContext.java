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
package org.kaazing.netx.ws.internal;

import java.util.List;

import org.kaazing.netx.ws.internal.ext.WebSocketContext;
import org.kaazing.netx.ws.internal.ext.WebSocketExtensionSpi;

public class DefaultWebSocketContext extends WebSocketContext {
    private final List<WebSocketExtensionSpi> extensions;

    // To avoid an instance of ListIterator being created for every frame that is being received/send, we are maintaining
    // the index. This helps in avoiding garbage from being created unnecessarily.
    private int currentIndex;

    public DefaultWebSocketContext(WsURLConnectionImpl connection, List<WebSocketExtensionSpi> extensions) {
        super(connection);
        this.extensions = extensions;
        this.currentIndex = 0;
    }

    @Override
    public WebSocketExtensionSpi nextExtension() {
        if (currentIndex < extensions.size()) {
            return extensions.get(currentIndex++);  // extensions is an ArrayList.
        }

        return null;
    }

    public WebSocketExtensionSpi getSentinelExtension() {
        return extensions.get(extensions.size() - 1);
    }

    public void reset() {
        currentIndex = 0;
    }
}
