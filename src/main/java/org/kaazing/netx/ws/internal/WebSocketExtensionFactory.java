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

import static java.util.Collections.unmodifiableMap;
import static java.util.ServiceLoader.load;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import org.kaazing.netx.ws.internal.ext.WebSocketExtensionFactorySpi;
import org.kaazing.netx.ws.internal.ext.WebSocketExtensionSpi;


public final class WebSocketExtensionFactory {
    private final Map<String, WebSocketExtensionFactorySpi> factoriesRO;

    private WebSocketExtensionFactory(Map<String, WebSocketExtensionFactorySpi> factoriesRO) {
        this.factoriesRO = factoriesRO;
    }

    /**
     * Creates and returns {@link WebSocketExtensionSpi} instance representing the extension using the registered
     * {@link WebSocketExtensionFactorySpi}.
     *
     * @param name            the extension name
     * @param formattedStr    string representation of an extension formatted as HTTP request header
     *
     * @return WebSocketExtension   the parameterized extension
     */
    public WebSocketExtensionSpi createExtension(String name, String formattedStr) throws IOException {
        WebSocketExtensionFactorySpi factory = factoriesRO.get(name);
        if (factory == null) {
            throw new IllegalArgumentException("Unsupported extension: " + name);
        }
        return factory.createExtension(formattedStr);
    }

    /**
     * Returns the names of all the supported/discovered extensions.
     *
     * @return Collection of extension names
     */
    public Collection<String> getExtensionNames() {
        return factoriesRO.keySet();
    }

    /**
     * Creates a new instance of WebSocketExtensionFactory. It uses the default {@link ClassLoader} to load
     * {@link WebSocketExtensionFactorySpi} objects that are registered using META-INF/services.
     *
     * @return WebSocketExtensionFactory
     */
    public static WebSocketExtensionFactory newInstance() {
        ServiceLoader<WebSocketExtensionFactorySpi> services = load(WebSocketExtensionFactorySpi.class);
        return newInstance(services);
    }

    /**
     * Creates a new instance of WebSocketExtensionFactory. It uses the specified {@link ClassLoader} to load
     * {@link WebSocketExtensionFactorySpi} objects that are registered using META-INF/services.
     *
     * @return WebSocketExtensionFactory
     */
    public static WebSocketExtensionFactory newInstance(ClassLoader cl) {
        ServiceLoader<WebSocketExtensionFactorySpi> services = load(WebSocketExtensionFactorySpi.class, cl);
        return newInstance(services);
    }


    private static WebSocketExtensionFactory newInstance(ServiceLoader<WebSocketExtensionFactorySpi> services) {
        Map<String, WebSocketExtensionFactorySpi> factories = new HashMap<String, WebSocketExtensionFactorySpi>();
        for (WebSocketExtensionFactorySpi service : services) {
            String extensionName = service.getExtensionName();
            factories.put(extensionName, service);
        }
        return new WebSocketExtensionFactory(unmodifiableMap(factories));
    }
}
