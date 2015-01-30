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

import static java.util.Collections.unmodifiableMap;
import static java.util.ServiceLoader.load;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;


public final class WebSocketExtensionFactory {

    private final Map<String, WebSocketExtensionFactorySpi> factoriesRO;

    /**
     * Creates and returns the singleton{@link WebSocketExtensionSpi} instance for the
     * extension that this factory is responsible for. The parameters for the
     * extension are specified so that the formatted string that can be put on
     * the wire can be supplied by the extension implementor.
     *
     * @param name          the extension name
     * @param parameters    the extension parameters
     *
     * @return WebSocketExtension   the parameterized extension
     */
    public WebSocketExtensionSpi createExtension(String name, WebSocketExtensionParameterValues parameters) {
        WebSocketExtensionFactorySpi factory = factoriesRO.get(name);
        if (factory == null) {
            throw new IllegalArgumentException("Unsupported extension: " + name);
        }
        return factory.createExtension(parameters);
    }

    public Collection<String> getExtensionNames() {
        return factoriesRO.keySet();
    }

    public static WebSocketExtensionFactory newInstance() {
        ServiceLoader<WebSocketExtensionFactorySpi> services = load(WebSocketExtensionFactorySpi.class);
        return newInstance(services);
    }

    public static WebSocketExtensionFactory newInstance(ClassLoader cl) {
        ServiceLoader<WebSocketExtensionFactorySpi> services = load(WebSocketExtensionFactorySpi.class, cl);
        return newInstance(services);
    }

    private WebSocketExtensionFactory(Map<String, WebSocketExtensionFactorySpi> factories) {
        this.factoriesRO = factories;
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
