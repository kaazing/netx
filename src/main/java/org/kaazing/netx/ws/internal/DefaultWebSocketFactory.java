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

import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableMap;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;

import org.kaazing.netx.http.HttpRedirectPolicy;
import org.kaazing.netx.http.auth.ChallengeHandler;
import org.kaazing.netx.ws.internal.WebSocketExtension.Parameter;
import org.kaazing.netx.ws.internal.ext.WebSocketExtensionFactorySpi;

public final class DefaultWebSocketFactory extends WebSocketFactory {
    private static final Map<String, WebSocketExtensionFactorySpi>  extensionFactories;

    private final Map<String, WebSocketExtensionParameterValues> enabledExtensions;
    private final Map<String, WebSocketExtensionParameterValues> enabledExtensionsRO;
    private final Collection<String>  supportedExtensions;

    private HttpRedirectPolicy        redirectPolicy;
    private ChallengeHandler          challengeHandler;
    private int                       connectTimeout; // milliseconds

    static {
        Class<WebSocketExtensionFactorySpi> clazz = WebSocketExtensionFactorySpi.class;
        ServiceLoader<WebSocketExtensionFactorySpi> loader = ServiceLoader.load(clazz);
        Map<String, WebSocketExtensionFactorySpi> factories = new HashMap<String, WebSocketExtensionFactorySpi>();

        for (WebSocketExtensionFactorySpi factory: loader) {
            String extensionName = factory.getExtensionName();

            if (extensionName != null) {
                factories.put(extensionName, factory);
            }
        }
        extensionFactories = unmodifiableMap(factories);
    }

    public DefaultWebSocketFactory() {
        this.enabledExtensions = new LinkedHashMap<String, WebSocketExtensionParameterValues>();
        this.enabledExtensionsRO = unmodifiableMap(enabledExtensions);

        this.supportedExtensions = new HashSet<String>();
        this.supportedExtensions.addAll(extensionFactories.keySet());

        this.redirectPolicy = HttpRedirectPolicy.ORIGIN;

    }

    @Override
    public WebSocket createWebSocket(URI location)
            throws URISyntaxException {
        return createWebSocket(location, (String[]) null);
    }

    @Override
    public WebSocket createWebSocket(URI location, String... protocols)
            throws URISyntaxException {
        Collection<String> enabledProtocols = null;

        // Clone enabled protocols maintained at the WebSocketFactory level to
        // pass into the WebSocket instance.
        if (protocols != null) {
            enabledProtocols = new HashSet<String>(Arrays.asList(protocols));
        }

        // Clone the map of default parameters maintained at the
        // WebSocketFactory level to pass into the WebSocket instance.
        Map<String, WebSocketExtensionParameterValues> enabledExtns =
                      new HashMap<String, WebSocketExtensionParameterValues>();
        enabledExtns.putAll(this.enabledExtensions);

        // Create a WebSocket instance that inherits the enabled protocols,
        // enabled extensions, enabled parameters, the HttpRedirectOption,
        // the extension factories(ie. the supported extensions).
        WebSocketImpl   ws = new WebSocketImpl(location, enabledExtns);
        ws.setRedirectPolicy(redirectPolicy);
        ws.setEnabledProtocols(enabledProtocols);
        ws.setChallengeHandler(challengeHandler);
        ws.setConnectTimeout(connectTimeout);

        return ws;
    }

    @Override
    public int getDefaultConnectTimeout() {
       return this.connectTimeout;
    }

    @Override
    public ChallengeHandler getDefaultChallengeHandler() {
        return this.challengeHandler;
    }

    @Override
    public Collection<String> getDefaultEnabledExtensions() {
        return this.enabledExtensionsRO.keySet();
    }

    @Override
    public HttpRedirectPolicy getDefaultRedirectPolicy() {
        return redirectPolicy;
    }

    @Override
    public <T> T getDefaultEnabledParameter(Parameter<T> parameter) {
        WebSocketExtension extension = parameter.extension();
        WebSocketExtensionParameterValues enabledParameterValues = enabledExtensions.get(extension.name());
        return (enabledParameterValues != null) ? enabledParameterValues.getParameterValue(parameter) : null;

    }

    @Override
    public Collection<String> getSupportedExtensions() {
        return (this.supportedExtensions == null) ? Collections.<String>emptySet() :
                                                unmodifiableCollection(this.supportedExtensions);
    }

    @Override
    public void setDefaultChallengeHandler(ChallengeHandler challengeHandler) {
        this.challengeHandler = challengeHandler;
    }

    @Override
    public void setDefaultConnectTimeout(int connectTimeout) {
       this.connectTimeout = connectTimeout;
    }

    @Override
    public void setDefaultEnabledExtensions(Map<String, WebSocketExtensionParameterValues> enabledExtensions) {
        this.enabledExtensions.clear();
        this.enabledExtensions.putAll(enabledExtensions);
    }

    @Override
    public void setDefaultRedirectPolicy(HttpRedirectPolicy redirectPolicy) {
        this.redirectPolicy = redirectPolicy;
    }

}
