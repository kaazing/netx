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

package org.kaazing.netx.ws.internal.wse;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import java.util.Collection;

import org.kaazing.netx.ws.WsURLConnection;
import org.kaazing.netx.ws.spi.WebSocketConnectionStrategySpi;
import org.kaazing.netx.ws.spi.WebSocketHandlerSpi;

public final class WebSocketEmulatedConnectionStrategy extends WebSocketConnectionStrategySpi {
    private static final Collection<String> _supportedStrategies = unmodifiableList(asList("ws", "wss", "wse", "wse+ssl"));

    public WebSocketEmulatedConnectionStrategy() {

    }

    @Override
    public Collection<String> getSupportedStrategies() {
        return _supportedStrategies;
    }

    @Override
    public WebSocketHandlerSpi createHandler(WsURLConnection conn) {
        return null;
    }
}