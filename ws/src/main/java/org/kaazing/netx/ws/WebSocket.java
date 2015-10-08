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
package org.kaazing.netx.ws;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Collection;

import org.kaazing.netx.http.HttpRedirectPolicy;
import org.kaazing.netx.http.auth.ChallengeHandler;


/**
 * WebSocket provides bi-directional communications for text and binary
 * messaging via the Kaazing Gateway.
 * <p>
 * Refer to {@link http://www.w3.org/TR/websockets/} for the published standard
 * W3C WebSocket API specification.
 * <p>
 * Instances of {@link WebSocket} can be created using
 * {@link WebSocketFactory#createWebSocket(java.net.URI, String...)} API.
 */
public abstract class WebSocket implements Closeable {
    /**
     * Uses the specified comma(,) separated string as a list of enabled extensions that would be negotiated with the server
     * during the opening handshake. The HTTP request header format of specified string is shown below:
     *
     * {@code}
     *      extension-name1[;param11=value11;param12;param13=value13, extension-name2;param21=value21;..]
     * {@code}
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
     * Disconnects with the server with code. This is a blocking call that returns only when the shutdown is complete. An
     * IllegalArgumentException is thrown if the code isn't 1000 or out of 3000 - 4999 range.
     *
     * @param code                         the error code for closing
     * @throws IOException                 if the disconnect did not succeed
     */
    public abstract void close(int code) throws IOException;

    /**
     * Disconnects with the server with code and reason. This is a blocking call that returns only when the shutdown is complete.
     *
     * @param code                         the error code for closing
     * @param reason                       the reason for closing
     * @throws IOException                 if the disconnect did not succeed OR if the code isn't 1000 or out of range
     *                                     3000 - 4999 OR if the reason is more than 123 bytes
     */
    public abstract void close(int code, String reason) throws IOException;

    /**
     * Connects with the server using an end-point. This is a blocking call. The thread invoking this method will be blocked
     * till a successful connection is established. If the request is not upgraded as per RFC-6455, then IOException is thrown.
     *
     * @throws IOException  if the connection cannot be established or the upgrade is not successful
     */
    public abstract void connect() throws IOException;

    /**
     * Gets the {@link ChallengeHandler} that is used during authentication both at the connect-time as well as at subsequent
     * revalidation-time that occurs at regular intervals.
     *
     * @return ChallengeHandler
     */
    public abstract ChallengeHandler getChallengeHandler();

    /**
     * Gets the connect timeout in milliseconds. The timeout will expire if there is no exchange of packets(for example,
     * 100% packet loss) while establishing the connection. A timeout value of zero indicates no timeout. Default connect
     * timeout is zero.
     *
     * @return connect timeout value in milliseconds
     */
    public abstract int getConnectTimeout();

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
     * Returns an {@link InputStream} to stream <b>binary</b> data. {@link InputStream} is typically used when the incoming
     * data is only binary and the application does not care about the message boundaries. The read() methods on the
     * {@link InputStream} will block till the message arrives. The {@link InputStream} must be used to only stream
     * <b>binary</b> data.
     *
     * @return InputStream    to stream binary data
     * @throws IOException if an I/O error occurs while creating the input stream or connection is closed or a text message is
     *                     received
     */
    public abstract InputStream getInputStream() throws IOException;

    /**
     * Returns the maximum message length that this connection will support. The default maximum message length is 8192 bytes.
     *
     * @return maximum message length for the connection
     */
    public abstract int getMaxFramePayloadLength();

    /**
     * Returns a {@link MessageReader} to receive messages. The {@link MessageReader} is used to when the incoming messages
     * are either binary or text. {@link MessageReader} has the APIs to received messages that fit in a single WebSocket frame
     * as well as messages that span across multiple WebSocket frames.
     *
     * @return MessageReader    to receive or stream messages
     * @throws IOException if an I/O error occurs while creating the input stream or connection is closed or a text message is
     *                     received
     */
    public abstract MessageReader getMessageReader() throws IOException;

    /**
     * Returns a {@link MessageWriter} to send messages that fit in a single WebSocket frame as well as messages that span
     * across multiple WebSocket frames.
     * <p>
     * @return MessageWriter  to send text messages
     * @throws IOException    if an I/O error occurs when creating the writer or the connection is closed
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
    public abstract OutputStream getOutputStream() throws IOException;

    /**
     * Returns a {@link Reader} to stream <b>text</b> data. {@link Reader} is typically used when the incoming
     * data is only text and the application does not care about the message boundaries. The read() methods on the
     * {@link Reader} will block till the message arrives. The {@link Reader} must be used to only stream <b>text</b> data.
     * <p>
     * If the Reader is used to receive <b>binary</b> messages, then an IOException is thrown.
     * <p>
     * @return Reader         used to stream text messages
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
     * Returns a {@link Writer} to send <b>text</b> messages from this connection.
     * <p>
     * @return Writer         used to send text messages from this connection
     * @throws IOException    if an I/O error occurs when creating the writer or the connection is closed
     */
    public abstract Writer getWriter() throws IOException;

    /**
     * Sets the {@link ChallengeHandler} that is used during authentication both at the connect-time as well as at subsequent
     * revalidation-time that occurs at regular intervals.
     *
     * @param challengeHandler   ChallengeHandler used for authentication
     */
    public abstract void setChallengeHandler(ChallengeHandler challengeHandler);

    /**
     * Sets the connect timeout in milliseconds. The timeout will expire if there is no exchange of packets(for example,
     * 100% packet loss) while establishing the connection. A timeout value of zero indicates no timeout. An
     * IllegalStateException is thrown  if the connect timeout is being set after the connection has been established.
     * An IllegalArgumentException is thrown if connectTimeout is negative.
     *
     * @param connectTimeout    timeout value in milliseconds
     */
    public abstract void setConnectTimeout(int connectTimeout);

    /**
     * Registers the protocols to be negotiated with the server during the handshake. This method must be invoked before
     * the {@link #connect()} method is called. Invoking this method clears previously enabled extensions.
     * <p>
     * If this method is invoked after a connection has been successfully established, an IllegalStateException is thrown.
     * <p>
     * @param protocols  protocols to be negotiated with the server during the opening handshake
     */
    public abstract void setEnabledProtocols(String... protocols);

    /**
     * Sets the maximum payload length for a single WebSocket frame that this connection can handle. This method must be invoked
     * before {@link #connect} is called. The maximum payload length can be
     * {@link WsURLConnection#MAX_FRAME_PAYLOAD_LENGTH_LIMIT}.
     * <p>
     * If this method is invoked after a connection has been successfully established, an IllegalStateException is thrown.
     * If the maxFramePayloadLength <= 0 or maxFramePayloadLength > {@link WsURLConnection#MAX_FRAME_PAYLOAD_LENGTH_LIMIT}, an
     * IllegalArgumentException is thrown.
     * <p>
     * @param maxFramePayloadLength  maximum frame payload length for the connection
     */
    public abstract void setMaxFramePayloadLength(int maxFramePayloadLength);

    /**
     * Sets {@link HttpRedirectPolicy} indicating the policy for following HTTP redirects (3xx).
     *
     * @param policy the redirect policy applied to HTTP redirect responses
     */
    public abstract void setRedirectPolicy(HttpRedirectPolicy policy);
}
