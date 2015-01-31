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
 * A URLConnection with support for WebSocket RFC-6455
 * <p>
 * Each WsURLConnection provides bi-directional communications for text and
 * binary messaging via the Kaazing Gateway.
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
public abstract class WsURLConnection extends URLConnection {

    /**
     * Creates a new {@code WsURLConnection}.
     *
     * @param url the location for this connection
     */
    protected WsURLConnection(URL url) {
        super(url);
    }

    /**
     * Disconnects with the server. This is a blocking call that returns only
     * when the shutdown is complete.
     *
     * @throws IOException    if the disconnect did not succeed
     */
    public abstract void close() throws IOException;

    /**
     * Disconnects with the server with code. This is a blocking
     * call that returns only when the shutdown is complete.
     *
     * @param code                         the error code for closing
     * @throws IOException                 if the disconnect did not succeed
     * @throws IllegalArgumentException    if the code isn't 1000 or out of
     *                                     range 3000 - 4999.
     */
    public abstract void close(int code) throws IOException, IllegalArgumentException;

    /**
     * Disconnects with the server with code and reason. This is a blocking
     * call that returns only when the shutdown is complete.
     *
     * @param code                         the error code for closing
     * @param reason                       the reason for closing
     * @throws IOException                 if the disconnect did not succeed
     * @throws IllegalArgumentException    if the code isn't 1000 or out of
     *                                     range 3000 - 4999 OR if the reason
     *                                     is more than 123 bytes
     */
    public abstract void close(int code, String reason) throws IOException, IllegalArgumentException;

    /**
     * Connects with the server using an end-point. This is a blocking call. The
     * thread invoking this method will be blocked till a successful connection
     * is established. If the connection cannot be established, then an
     * IOException is thrown and the thread is unblocked.
     *
     * @throws IOException    if the connection cannot be established
     */
    @Override
    public abstract void connect() throws IOException;

    /**
     * Gets the {@link ChallengeHandler} that is used during authentication
     * both at the connect-time as well as at subsequent revalidation-time that
     * occurs at regular intervals.
     *
     * @return ChallengeHandler
     */
    public abstract ChallengeHandler getChallengeHandler();

    /**
     * Gets the names of all the protocols that are enabled for this
     * connection. Returns an empty Collection if protocols are not enabled.
     *
     * @return the protocols enabled for this connection
     */
    public abstract Collection<String> getEnabledProtocols();

    /**
     * Returns {@link HttpRedirectPolicy} indicating the policy for
     * following  HTTP redirects (3xx).
     *
     * @return  the redirect policy
     */
    public abstract HttpRedirectPolicy getRedirectPolicy();

    /**
     * Returns the {@link InputStream} to receive <b>binary</b> messages. The
     * methods on {@link InputStream} will block till the message arrives. The
     * {@link InputStream} must be used to only receive <b>binary</b>
     * messages.
     * <p>
     * An IOException is thrown if this method is invoked when the connection
     * has not been established. Receiving a text message using the
     * {@link InputStream} will result in an IOException.
     * <p>
     * Once the connection is closed, a new {@link InputStream} should be
     * obtained using this method after the connection has been established.
     * Using the old InputStream will result in an IOException.
     * <p>
     * @return InputStream    to receive binary messages
     * @throws IOException    if the method is invoked before the connection is
     *                        successfully opened; if a text message is being
     *                        read using the InputStream
     */
    @Override
    public abstract InputStream getInputStream() throws IOException;

    /**
     * Returns a {@link MessageReader} that can be used to receive
     * <b>binary</b> and <b>text</b> messages based on the
     * {@link MessageType}.
     * <p>
     * If this method is invoked before a connection is established successfully,
     * then an IOException is thrown.
     * <p>
     * Once the connection is closed, a new {@link MessageReader}
     * should be obtained using this method after the connection has been
     * established. Using the old WebSocketMessageReader will result in an
     * IOException.
     * <p>
     * @return WebSocketMessageReader   to receive binary and text messages
     * @throws IOException       if invoked before the connection is opened
     */
    public abstract MessageReader getMessageReader() throws IOException;

    /**
     * Returns a {@link MessageWriter} that can be used to send
     * <b>binary</b> and <b>text</b> messages.
     * <p>
     * If this method is invoked before a connection is established
     * successfully, then an IOException is thrown.
     * <p>
     * Once the connection is closed, a new {@link MessageWriter}
     * should be obtained using this method after the connection has been
     * established. Using the old WebSocketMessageWriter will result in an
     * IOException.
     * <p>
     * @return WebSocketMessageWriter   to send binary and text messages
     * @throws IOException       if invoked before the connection is opened
     */
    public abstract MessageWriter getMessageWriter() throws IOException;

    /**
     * Gets the protocol that the client and the server have successfully
     * negotiated.
     *
     * @return protocol  negotiated by the client and the server
     * @throws IOException  if an I/O error occurs while negotiating the WebSocket sub-protocol
     */
    public abstract String getNegotiatedProtocol() throws IOException;

    /**
     * Returns the {@link OutputStream} to send <b>binary</b> messages. The
     * message is put on the wire only when {@link OutputStream#flush()} is
     * invoked.
     * <p>
     * If this method is invoked before {@link #connect()} is complete, an
     * IOException is thrown.
     * <p>
     * Once the connection is closed, a new {@link OutputStream} should
     * be obtained using this method after the connection has been
     * established. Using the old OutputStream will result in IOException.
     * <p>
     * @return OutputStream    to send binary messages
     * @throws IOException     if the method is invoked before the connection is
     *                         successfully opened
     */
    @Override
    public abstract OutputStream getOutputStream() throws IOException;

    /**
     * Returns a {@link Reader} to receive <b>text</b> messages from this
     * connection. This method should be used to only to receive <b>text</b>
     * messages. Methods on {@link Reader} will block till a message arrives.
     * <p>
     * If the Reader is used to receive <b>binary</b> messages, then an
     * IOException is thrown.
     * <p>
     * If this method is invoked before a connection is established
     * successfully, then an IOException is thrown.
     * <p>
     * Once the connection is closed, a new {@link Reader} should be obtained
     * using this method after the connection has been established. Using the
     * old Reader will result in an IOException.
     * <p>
     * @return Reader         used to receive text messages from this connection
     * @throws IOException    if the method is invoked before the connection is
     *                        successfully opened
     */
    public abstract Reader getReader() throws IOException;

    /**
     * Returns a {@link Writer} to send <b>text</b> messages from this
     * connection. The message is put on the wire only when
     * {@link Writer#flush()} is invoked.
     * <p>
     * An IOException is thrown if this method is invoked when the connection
     * has not been established.
     * <p>
     * Once the connection is closed, a new {@link Writer} should be obtained
     * using this method after the connection has been established. Using the
     * old Writer will result in an IOException.
     * <p>
     * @return Writer          used to send text messages from this connection
     * @throws IOException     if the method is invoked before the connection is
     *                         successfully opened
     */
    public abstract Writer getWriter() throws IOException;

    /**
     * Sets the {@link ChallengeHandler} that is used during authentication
     * both at the connect-time as well as at subsequent revalidation-time that
     * occurs at regular intervals.
     *
     * @param challengeHandler   the security challenge handler used for authentication
     */
    public abstract void setChallengeHandler(ChallengeHandler challengeHandler);

    /**
     * Registers the protocols to be negotiated with the server during the
     * handshake. This method must be invoked before {@link #connect()} is
     * called.
     * <p>
     * If this method is invoked after a connection has been successfully
     * established, an IllegalStateException is thrown.
     * <p>
     * @param protocols  the list of protocols to be negotiated with the server during the WebSocket handshake
     * @throws IllegalStateException   if this method is invoked after connect()
     */
    public abstract void setEnabledProtocols(Collection<String> protocols) throws IllegalStateException;

    /**
     * Sets {@link HttpRedirectPolicy} indicating the policy for
     * following  HTTP redirects (3xx).
     *
     * @param policy the redirect policy applied to HTTP redirect responses
     */
    public abstract void setRedirectPolicy(HttpRedirectPolicy policy);
}
