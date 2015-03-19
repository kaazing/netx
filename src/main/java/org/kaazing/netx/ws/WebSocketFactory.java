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

package org.kaazing.netx.ws;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.ServiceLoader;

import org.kaazing.netx.http.HttpRedirectPolicy;
import org.kaazing.netx.http.auth.ChallengeHandler;

/**
 * {@link WebSocketFactory} is an abstract class that can be used to create {@link WebSocket}s by specifying the end-point and
 * the enabled protocols. It may be extended to instantiate particular subclasses of {@link WebSocket} and thus provide a
 * general framework for the addition of public WebSocket-level functionality.
 * <p>
 * Using {@link WebSocketFactory} instance, application developers can specify default characteristics such as redirect policy,
 * challenge handler, etc. that will be inherited by all the {@link WebSocket} instances created from the factory. Application
 * developers can override these characteristics at the individual {@link WebSocket} level, if needed.
 */
public abstract class WebSocketFactory {

    protected WebSocketFactory() {
    }

    /**
     * Creates and returns a new instance of the default implementation of the {@link WebSocketFactory}.
     *
     * @return WebSocketFactory
     */
    public static WebSocketFactory createWebSocketFactory() {
        Class<WebSocketFactory>         clazz = WebSocketFactory.class;
        ServiceLoader<WebSocketFactory> loader = ServiceLoader.load(clazz);
        return loader.iterator().next();
    }

    /**
     * Adds the specified extension to the list of enabled extensions. The extension's toString() method should return RFC-3864
     * formatted string so that it can be sent as part of <i>Sec-Websocket-Extensions</i> header during the opening handshake.
     * All the WebSockets created using this factory will inherit the enabled extension.
     *
     * @param extension WebSocketExtension with toString() method that returns RFC-3864 formatted string
     */
    public abstract void addDefaultEnabledExtension(WebSocketExtension extension);

    /**
     * Enables the specified extensions. The enabled extensions should be a subset of the supported extensions. The specified
     * extensions(along with their parameters) are specified as the value of the <i>Sec-Websocket-Extensions</i> header
     * during the opening handshake. Invoking this method clears previously enabled extensions. All the WebSockets created
     * using this factory will inherit the enabled extensions.
     *
     * @param extensions  the format for each string that is passed in must be as per RFC-3864
     *                            extension_name[;param1=value1;param2=value2]
     */
    public abstract void addDefaultEnabledExtensions(String...extensions);

    /**
     * Clears the default enabled extensions.
     */
    public abstract void clearDefaultEnabledExtensions();

    /**
     * Creates a {@link WebSocket} to establish a full-duplex connection to the target location.
     * <p>
     * The default enabled extensions, default connection timeout, default challenge handler, default redirect policy that were
     * set on the {@link WebSocketFactory} prior to this call are inherited by the newly newly created {@link WebSocket}
     * instance.
     *
     * @param location    URI of the WebSocket service for the connection
     * @return WebSocket instance
     * @throws URISyntaxException if the URI syntax is invalid
     */
    public abstract WebSocket createWebSocket(URI   location) throws URISyntaxException;

    /**
     * Creates a {@link WebSocket} to establish a full-duplex connection to the target location with one of the specified
     * protocols on a supported WebSocket provider.
     * <p>
     * The default enabled extensions, default connection timeout, default challenge handler, default redirect policy that were
     * set on the {@link WebSocketFactory} prior to this call are inherited by the newly newly created {@link WebSocket}
     * instance.
     *
     * @param location    URI of the WebSocket service for the connection
     * @param protocols   protocols to be negotiated over the WebSocket, or
     *                    <I>null</I> for any protocol
     * @return WebSocket instance
     * @throws URISyntaxException if the URI syntax is invalid
     */
    public abstract WebSocket createWebSocket(URI       location,
                                              String... protocols)
           throws URISyntaxException;

    /**
     * Gets the default {@link ChallengeHandler} that is used during authentication both at the connect-time as well as at
     * subsequent revalidation-time that occurs at regular intervals.
     *
     * @return the default ChallengeHandler
     */
    public abstract ChallengeHandler getDefaultChallengeHandler();

    /**
     * Gets the default connect timeout in milliseconds. Default value of the default connect timeout is zero -- which means
     * no timeout.
     *
     * @return connect timeout value in milliseconds
     */
    public abstract int getDefaultConnectTimeout();

    /**
     * Gets the names of the default enabled extensions that will be inherited by all the {@link WebSocket}s created using this
     * factory. These extensions are negotiated between the client and the server during the WebSocket handshake only if all the
     * required parameters belonging to the extension have been set as enabled parameters. An empty Collection is returned if no
     * extensions have been enabled for this factory.
     *
     * @return Collection<String>     names of the enabled extensions for
     */
    public abstract Collection<String> getDefaultEnabledExtensions();

    /**
     * Returns the default {@link HttpRedirectPolicy} that was specified at on the factory. The default redirect policy
     * is {@link HttpRedirectPolicy.ORIGIN}.
     *
     * @return HttpRedirectPolicy
     */
    public abstract HttpRedirectPolicy getDefaultRedirectPolicy();

    /**
     * Returns the names of supported extensions that have been discovered. An empty Collection is returned if no extensions
     * were discovered.
     *
     * @return Collection<String>    extension names discovered for this factory
     */
    public abstract Collection<String> getSupportedExtensions();

    /**
     * Sets the default {@link ChallengeHandler} that is used during authentication both at the connect-time as well as at
     * subsequent revalidation-time that occurs at regular intervals. All the {@link WebSocket}s created using this factory
     * will inherit the default ChallengeHandler.
     *
     * @param challengeHandler   default ChallengeHandler
     */
    public abstract void setDefaultChallengeHandler(ChallengeHandler challengeHandler);

    /**
     * Sets the default connect timeout in milliseconds. The specified timeout is inherited by all the WebSocket instances that
     * are created using this WebSocketFactory instance. The timeout will expire if there is no exchange of packets(for example,
     * 100% packet loss) while establishing the connection. A timeout value of zero indicates no timeout. An
     * IllegalArgumentException is thrown if connectTimeout is negative
     *
     * @param connectTimeout    timeout value in milliseconds
     */
    public abstract void setDefaultConnectTimeout(int connectTimeout);

    /**
     * Sets the default {@link HttpRedirectPolicy} that is to be inherited by
     * all the {@link WebSocket}s created using this factory instance.
     *
     * @param policy     HttpRedirectOption
     */
    public abstract void setDefaultRedirectPolicy(HttpRedirectPolicy policy);
}
