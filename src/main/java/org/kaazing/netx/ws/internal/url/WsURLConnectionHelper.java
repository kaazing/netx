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

package org.kaazing.netx.ws.internal.url;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;

import java.io.IOException;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import org.kaazing.netx.URLConnectionHelperSpi;
import org.kaazing.netx.ws.internal.WsURLConnectionImpl;
import org.kaazing.netx.ws.internal.ext.WebSocketExtensionFactorySpi;

public class WsURLConnectionHelper extends URLConnectionHelperSpi {
    private static final Collection<String> SUPPORTED_PROTOCOLS = unmodifiableList(asList("ws", "wse", "wsn"));
    private static final Map<String, WebSocketExtensionFactorySpi>  _extensionFactories;

    static {
        Class<WebSocketExtensionFactorySpi> clazz = WebSocketExtensionFactorySpi.class;
        ServiceLoader<WebSocketExtensionFactorySpi> loader = ServiceLoader.load(clazz);
        Map<String, WebSocketExtensionFactorySpi> factories = new HashMap<String, WebSocketExtensionFactorySpi>();

        for (WebSocketExtensionFactorySpi factory: loader) {
            String extensionName = factory.getExtensionName();

            if (extensionName != null)
            {
                factories.put(extensionName, factory);
            }
        }
        _extensionFactories = unmodifiableMap(factories);
    }

    @Override
    public Collection<String> getSupportedProtocols() {
        return SUPPORTED_PROTOCOLS;
    }

    @Override
    public URLConnection openConnection(URI location) throws IOException {
        assert SUPPORTED_PROTOCOLS.contains(location.getScheme());
        return new WsURLConnectionImpl(location.toURL(), _extensionFactories);
    }

    @Override
    public URLStreamHandler newStreamHandler() throws IOException {
        return new WsURLStreamHandlerImpl(_extensionFactories);
    }


    // ----------------- Package Private Methods -----------------------------
    Map<String, WebSocketExtensionFactorySpi> getExtensionFactories() {
        return _extensionFactories != null ? _extensionFactories :
                                             Collections.<String, WebSocketExtensionFactorySpi>emptyMap();
    }



    // ----------------- Private Methods -------------------------------------
}
