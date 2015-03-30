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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collection;

import org.kaazing.netx.http.HttpRedirectPolicy;
import org.kaazing.netx.http.auth.ChallengeHandler;

/**
 * A URLConnection with support for WebSocket RFC-6455 specification.
 * <p>
 * Each WsURLConnection provides bi-directional communications for text and binary messaging.
 * <p>
 * An instance of {@link WsURLConnection} is created as shown below:
 * <pre>
 * {@code
 *     URLConnectionHelper helper = URLConnectionHelper.newInstance();
 *     URL location = helper.toURL(URI.create("ws://<hostname>:<port>/<serviceName>"));
 *     WsURLConnection wsConnection = (WsURLConnection) location.openConnection();
 * }
 * </pre>
 */
public abstract class WsURLConnection extends URLConnection implements Closeable {
    /**
     * Connection has been closed normally.
     */
    public static final int WS_NORMAL_CLOSE = 1000;

    /**
     * End-point is going away.
     */
    public static final int WS_ENDPOINT_GOING_AWAY = 1001;

    /**
     * Connection terminated due to protocol error.
     */
    public static final int WS_PROTOCOL_ERROR = 1002;

    /**
     * Connection terminated due to incorrect message type.
     */
    public static final int WS_INCORRECT_MESSAGE_TYPE = 1003;

    /**
     * Reserved for future use.
     */
    public static final int WS_FUTURE_USE = 1004;

    /**
     * No status code was present.
     */
    public static final int WS_MISSING_STATUS_CODE = 1005;

    /**
     * Connection was closed abnormally, e.g., without sending or receiving a Close control frame.
     */
    public static final int WS_ABNORMAL_CLOSE = 1006;

    /**
     * Connection terminated due to inconsistency between the data and the message type.
     */
    public static final int WS_INCONSISTENT_DATA_MESSAGE_TYPE = 1007;

    /**
     * Connection terminated as the received a message violates the policy.
     */
    public static final int WS_VIOLATE_POLICY = 1008;

    /**
     * Connection terminated as the received message is too big to process.
     */
    public static final int WS_MESSAGE_TOO_BIG = 1009;

    /**
     * Connection terminated by the client because an extension could not be negotiated with the server during the handshake.
     */
    public static final int WS_UNSUCCESSFUL_EXTENSION_NEGOTIATION = 1010;

    /**
     * Connection terminated by the server because of an unexpected condition.
     */
    public static final int WS_SERVER_TERMINATED_CONNECTION = 1011;

    /**
     * Connection was closed due to a failure to perform a TLS handshake.
     */
    public static final int WS_UNSUCCESSFUL_TLS_HANDSHAKE = 1015;


    /**
     * Creates a new {@code WsURLConnection}.
     *
     * @param url the location for this connection
     */
    protected WsURLConnection(URL url) {
        super(url);
    }

    /**
     * Uses the specified comma(,) separated string as a list of enabled extensions that would be negotiated with the server
     * during the opening handshake. The HTTP request header format of specified string is shown below:
     *
     * {@code}
     *      extension-name1[;param11=value11;param12;param13=value13, extension-name2;param21=value21;..]
     * {@code}
     *
     *
     * @param extensions comma(,) separated string representation of multiple extensions
     */
    public abstract void addEnabledExtensions(String... extensions);

    /**
     * Disconnects with the server. This is a blocking call that returns only when the shutdown is complete.
     *
     * @throws IOException    if the disconnect did not succeed
     */
    @Override
    public abstract void close() throws IOException;

    /**
     * Disconnects with the server with code. This is a blocking call that returns only when the shutdown is complete.
     *
     * @param code                         the error code for closing
     * @throws IOException                 if the disconnect did not succeed
     * @throws IllegalArgumentException    if the code isn't 1000 or out of
     *                                     range 3000 - 4999.
     */
    public abstract void close(int code) throws IOException, IllegalArgumentException;

    /**
     * Disconnects with the server with code and reason. This is a blocking call that returns only when the shutdown is complete.
     *
     * @param code                         the error code for closing
     * @param reason                       the reason for closing
     * @throws IOException                 if the disconnect did not succeed
     * @throws IllegalArgumentException    if the code isn't 1000 or out of range 3000 - 4999 OR if the reason is more than
     *                                     123 bytes
     */
    public abstract void close(int code, String reason) throws IOException, IllegalArgumentException;

    /**
     * Connects with the server using an end-point. This is a blocking call. The thread invoking this method will be blocked
     * till a successful connection is established. If the request is not upgraded as per RFC-6455, then IOException is thrown.
     *
     * @throws IOException  if the connection cannot be established or the upgrade is not successful
     */
    @Override
    public abstract void connect() throws IOException;

    /**
     * Gets the {@link ChallengeHandler} that is used during authentication both at the connect-time as well as at subsequent
     * revalidation-time that occurs at regular intervals.
     *
     * @return ChallengeHandler
     */
    public abstract ChallengeHandler getChallengeHandler();

    /**
     * Gets the extensions that have been enabled for this connection. The enabled extensions are negotiated
     * between the client and the server during the handshake. The names of the negotiated extensions can be obtained using
     * {@link #getNegotiatedExtensions()} API. An empty Collection is returned if no extensions have been enabled for
     * this connection. The enabled extensions will be a subset of the supported extensions.
     *
     * @return Collection<String>     enabled extensions for this connection
     */
    public abstract Collection<String> getEnabledExtensions();

    /**
     * Gets the names of all the protocols that are enabled for this connection. Returns an empty Collection if protocols are
     * not enabled.
     *
     * @return the protocols enabled for this connection
     */
    public abstract Collection<String> getEnabledProtocols();

    /**
     * Returns the {@link InputStream} to receive <b>binary</b> messages. The methods on {@link InputStream} will block till the
     * message arrives. The {@link InputStream} must be used to only receive <b>binary</b> messages.
     *
     * @return InputStream    to receive binary messages
     * @throws IOException if an I/O error occurs while creating the input stream or connection is closed or a text message is
     *                     received
     */
    @Override
    public abstract InputStream getInputStream() throws IOException;

    /**
     * Returns a {@link MessageReader} that can be used to receive <b>binary</b> and/or <b>text</b> messages.
     * <p>
     * @return WebSocketMessageReader   to receive binary and text messages
     * @throws IOException if an I/O error occurs when connecting or connection is closed
     */
    public abstract MessageReader getMessageReader() throws IOException;

    /**
     * Returns a {@link MessageWriter} that can be used to send <b>binary</b> and/or <b>text</b> messages.
     *
     * @return WebSocketMessageWriter   to send binary and/or text messages
     * @throws IOException if an I/O error occurs when connecting or connection is closed
     */
    public abstract MessageWriter getMessageWriter() throws IOException;

    /**
     * Gets names of all the enabled extensions that have been successfully negotiated between the client and the server during
     * the initial handshake.
     * <p>
     * Returns an empty Collection if no extensions were negotiated between the client and the server. The negotiated extensions
     * will be a subset of the enabled extensions.
     * <p>
     * @return Collection<String>   successfully negotiated using this connection
     * @throws IOException if an I/O error occurs when negotiating the extension or connection is closed
     */
    public abstract Collection<String> getNegotiatedExtensions() throws IOException;

    /**
     * Gets the protocol that the client and the server have successfully negotiated.
     *
     * @return protocol  negotiated by the client and the server
     * @throws IOException  if an I/O error occurs while negotiating the WebSocket sub-protocol
     */
    public abstract String getNegotiatedProtocol() throws IOException;

    /**
     * Returns the {@link OutputStream} to send <b>binary</b> messages. The message is put on the wire only when the application
     * invokes {@link OutputStream#flush()} method.
     * <p>
     * @return OutputStream   to send binary messages
     * @throws IOException    if an I/O error occurs when creating the writer or the connection is closed
     */
    @Override
    public abstract OutputStream getOutputStream() throws IOException;

    /**
     * Returns a {@link Reader} to receive <b>text</b> messages from this connection. This method should be used to only to
     * receive <b>text</b> messages. Methods on {@link Reader} will block till a message arrives.
     * <p>
     * If the Reader is used to receive <b>binary</b> messages, then an IOException is thrown.
     * <p>
     * @return Reader         used to receive text messages from this connection
     * @throws IOException    if an I/O error occurs when creating the reader or the connection is closed or a binary message
     *                        is received
     */
    public abstract Reader getReader() throws IOException;

    /**
     * Returns {@link HttpRedirectPolicy} indicating the policy for following  HTTP redirects (3xx).
     *
     * @return  the redirect policy
     */
    public abstract HttpRedirectPolicy getRedirectPolicy();

    /**
     * Returns the names of extensions that have been discovered for this connection. An empty Collection is returned if no
     * extensions were discovered for this connection.
     *
     * @return Collection<String>    extension names discovered for this connection
     */
    public abstract Collection<String> getSupportedExtensions();

    /**
     * Returns a {@link Writer} to send <b>text</b> messages from this connection. The message is put on the wire only when
     * {@link Writer#flush()} is invoked.
     * <p>
     * @return Writer         used to send text messages from this connection
     * @throws IOException    if an I/O error occurs when creating the writer or the connection is closed
     */
    public abstract Writer getWriter() throws IOException;

    /**
     * Sets the {@link ChallengeHandler} that is used during authentication both at the connect-time as well as at subsequent
     * revalidation-time that occurs at regular intervals. This method must be invoked before {@link #connect} is called.
     *
     * @param challengeHandler   the security challenge handler used for authentication
     */
    public abstract void setChallengeHandler(ChallengeHandler challengeHandler);

    /**
     * Registers the protocols to be negotiated with the server during the handshake. This method must be invoked before
     * {@link #connect()} is called.
     * <p>
     * If this method is invoked after a connection has been successfully established, an IllegalStateException is thrown.
     * <p>
     * @param protocols  the list of protocols to be negotiated with the server during the WebSocket handshake
     * @throws IllegalStateException   if this method is invoked after connect()
     */
    public abstract void setEnabledProtocols(String... protocols) throws IllegalStateException;

    /**
     * Sets {@link HttpRedirectPolicy} indicating the policy for following HTTP redirects (3xx).
     *
     * @param policy the redirect policy applied to HTTP redirect responses
     */
    public abstract void setRedirectPolicy(HttpRedirectPolicy policy);
}
