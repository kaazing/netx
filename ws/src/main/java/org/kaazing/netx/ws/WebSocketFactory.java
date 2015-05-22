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

import static java.lang.String.format;
import static java.util.Collections.unmodifiableList;
import static org.kaazing.netx.ws.WsURLConnection.MAX_MESSAGE_LENGTH_LIMIT;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.kaazing.netx.http.HttpRedirectPolicy;
import org.kaazing.netx.http.auth.ChallengeHandler;
import org.kaazing.netx.ws.internal.WebSocketExtensionFactory;
import org.kaazing.netx.ws.internal.WebSocketImpl;

/**
 * {@link WebSocketFactory} is an abstract class that can be used to create {@link WebSocket}s by specifying the end-point and
 * the enabled protocols. It may be extended to instantiate particular subclasses of {@link WebSocket} and thus provide a
 * general framework for the addition of public WebSocket-level functionality.
 * <p>
 * Using {@link WebSocketFactory} instance, application developers can specify default characteristics such as redirect policy,
 * challenge handler, etc. that will be inherited by all the {@link WebSocket} instances created from the factory. Application
 * developers can override these characteristics at the individual {@link WebSocket} level, if needed.
 */
public final class WebSocketFactory {
    private static final int DEFAULT_MAX_PAYLOAD_LENGTH = 8192;

    private final List<String> defaultEnabledExtensions;
    private final List<String> defaultEnabledExtensionsRO;
    private final WebSocketExtensionFactory extensionFactory;

    private HttpRedirectPolicy defaultRedirectPolicy;
    private ChallengeHandler defaultChallengeHandler;
    private int defaultConnectTimeout; // milliseconds
    private int defaultMaxMessageLength;

    private WebSocketFactory(WebSocketExtensionFactory extensionFactory) {
        this.defaultEnabledExtensions = new ArrayList<String>();
        this.defaultEnabledExtensionsRO = unmodifiableList(defaultEnabledExtensions);
        this.extensionFactory = extensionFactory;
        this.defaultRedirectPolicy = HttpRedirectPolicy.ORIGIN;
        this.defaultMaxMessageLength = DEFAULT_MAX_PAYLOAD_LENGTH;
    }

    /**
     * Creates and returns a new instance of the {@link WebSocketFactory}.
     *
     * @return WebSocketFactory
     */
    public static WebSocketFactory newInstance() {
        return new WebSocketFactory(WebSocketExtensionFactory.newInstance());
    }

    /**
     * Creates and returns a new instance of the {@link WebSocketFactory}.
     *
     * @param cl the service discovery class loader for extension factories
     * @return WebSocketFactory
     */
    public static WebSocketFactory newInstance(ClassLoader cl) {
        return new WebSocketFactory(WebSocketExtensionFactory.newInstance(cl));
    }

    /**
     * Uses the specified comma(,) separated string as a list of enabled extensions that would be negotiated with the server
     * during the opening handshake.  The HTTP request header format of specified string is shown below:
     *
     * {@code}
     *      extension-name1[;param11=value11;param12;param13=value13, extension-name2;param21=value21;..]
     * {@code}
     *
     * All the WebSockets created using this factory will inherit the enabled extensions.
     *
     * @param extensions comma(,) separated string representation of multiple extensions
     */
    public void addDefaultEnabledExtensions(String... extensions) {
        if (extensions == null) {
            throw new NullPointerException("Null extensions passed in");
        }

        if (extensions.length == 0) {
            throw new IllegalArgumentException("No extensions specified to be enabled");
        }

        defaultEnabledExtensions.clear();

        for (String extension : extensions) {
            defaultEnabledExtensions.add(extension);
        }
    }

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
    public WebSocket createWebSocket(URI location)
            throws URISyntaxException {
        return createWebSocket(location, (String[]) null);
    }

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
    public WebSocket createWebSocket(URI location, String... protocols)
            throws URISyntaxException {
        // Create a WebSocket instance that inherits the enabled protocols,
        // enabled extensions, enabled parameters, the HttpRedirectOption,
        // the extension factories(ie. the supported extensions).
        WebSocketImpl   ws = new WebSocketImpl(location, extensionFactory);
        ws.setRedirectPolicy(defaultRedirectPolicy);
        ws.setEnabledProtocols(protocols);
        ws.setChallengeHandler(defaultChallengeHandler);
        ws.setConnectTimeout(defaultConnectTimeout);
        ws.addEnabledExtensions(defaultEnabledExtensions.toArray(new String[defaultEnabledExtensions.size()]));

        return ws;
    }

    /**
     * Gets the default {@link ChallengeHandler} that is used during authentication both at the connect-time as well as at
     * subsequent revalidation-time that occurs at regular intervals.
     *
     * @return the default ChallengeHandler
     */
    public ChallengeHandler getDefaultChallengeHandler() {
        return defaultChallengeHandler;
    }

    /**
     * Gets the default connect timeout in milliseconds. Default value of the default connect timeout is zero -- which means
     * no timeout.
     *
     * @return connect timeout value in milliseconds
     */
    public int getDefaultConnectTimeout() {
        return defaultConnectTimeout;
     }

    /**
     * Gets the default enabled extensions that will be inherited by all the {@link WebSocket}s created using this
     * factory. These extensions are negotiated between the client and the server during the WebSocket handshake only if all the
     * required parameters belonging to the extension have been set as enabled parameters. An empty Collection is returned if no
     * extensions have been enabled for this factory.
     *
     * @return Collection<String>     list of enabled extensions
     */
    public Collection<String> getDefaultEnabledExtensions() {
        return defaultEnabledExtensionsRO;
    }

    /**
     * Returns the maximum message length that this connection will support. The default maximum message length is 8192 bytes.
     *
     * @return maximum message length for the connection
     */
    public int getDefaultMaxMessageLength() {
        return defaultMaxMessageLength;
    }

    /**
     * Returns the default {@link HttpRedirectPolicy} that was specified at on the factory. The default redirect policy
     * is {@link HttpRedirectPolicy.ORIGIN}.
     *
     * @return HttpRedirectPolicy
     */
    public HttpRedirectPolicy getDefaultRedirectPolicy() {
        return defaultRedirectPolicy;
    }

    /**
     * Returns the names of supported extensions that have been discovered. An empty Collection is returned if no extensions
     * were discovered.
     *
     * @return Collection<String>    extension names discovered for this factory
     */
    public Collection<String> getSupportedExtensions() {
        return extensionFactory.getExtensionNames();
    }

    /**
     * Sets the default {@link ChallengeHandler} that is used during authentication both at the connect-time as well as at
     * subsequent revalidation-time that occurs at regular intervals. All the {@link WebSocket}s created using this factory
     * will inherit the default ChallengeHandler.
     *
     * @param challengeHandler   default ChallengeHandler
     */
    public void setDefaultChallengeHandler(ChallengeHandler challengeHandler) {
        this.defaultChallengeHandler = challengeHandler;
    }

    /**
     * Sets the default connect timeout in milliseconds. The specified timeout is inherited by all the WebSocket instances that
     * are created using this WebSocketFactory instance. The timeout will expire if there is no exchange of packets(for example,
     * 100% packet loss) while establishing the connection. A timeout value of zero indicates no timeout. An
     * IllegalArgumentException is thrown if connectTimeout is negative
     *
     * @param connectTimeout    timeout value in milliseconds
     */
    public void setDefaultConnectTimeout(int connectTimeout) {
        this.defaultConnectTimeout = connectTimeout;
     }

    /**
     * Sets the maximum message length that is inherited by all the @{link WebSocket}s created using this factory.
     * The maximum message length can be {@link WsURLConnection#MAX_MESSAGE_LENGTH_LIMIT}.
     * <p>
     * If this method is invoked after a connection has been successfully established, an IllegalStateException is thrown.
     * If the maxMessageLength <= 0 or maxMessageLength > {@link WsURLConnection#MAX_MESSAGE_LENGTH_LIMIT}, an
     * IllegalArgumentException is thrown.
     * <p>
     * @param maxMessageLength  maximum message length for the connection
     */
    public void setMaxMessageLength(int maxMessageLength) {
        if (maxMessageLength > MAX_MESSAGE_LENGTH_LIMIT) {
            throw new IllegalArgumentException(format("Maximim message length must not exceed %d", MAX_MESSAGE_LENGTH_LIMIT));
        }

        if (maxMessageLength <= 0) {
            throw new IllegalArgumentException("Maximum message length must be positive integer value");
        }

        this.defaultMaxMessageLength = maxMessageLength;
    }

    /**
     * Sets the default {@link HttpRedirectPolicy} that is to be inherited by all the {@link WebSocket}s created using this
     * factory instance.
     *
     * @param redirectPolicy     HttpRedirectPolicy to be enforced during redirects
     */
    public void setDefaultRedirectPolicy(HttpRedirectPolicy redirectPolicy) {
        this.defaultRedirectPolicy = redirectPolicy;
    }
}
