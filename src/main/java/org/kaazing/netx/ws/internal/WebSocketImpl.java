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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.kaazing.netx.URLConnectionHelper;
import org.kaazing.netx.http.HttpRedirectPolicy;
import org.kaazing.netx.http.auth.ChallengeHandler;
import org.kaazing.netx.ws.WebSocketMessageReader;
import org.kaazing.netx.ws.WebSocketMessageWriter;
import org.kaazing.netx.ws.WsURLConnection;
import org.kaazing.netx.ws.WebSocketExtension.Parameter;

public class WebSocketImpl extends WebSocket {
    private WsURLConnection   _connection;

    /**
     * Creates a WebSocket that opens up a full-duplex connection to the target
     * location on a supported WebSocket provider. Call connect() to establish
     * the location after adding event listeners.
     *
     * @param location        URI of the WebSocket service for the connection
     * @throws Exception      if connection could not be established
     */
    public WebSocketImpl(URI  location) throws URISyntaxException {
        this(location, Collections.<String, WsExtensionParameterValuesSpiImpl>emptyMap());
    }

    public WebSocketImpl(URI                                            location,
                         Map<String, WsExtensionParameterValuesSpiImpl> enabledParameters)
           throws URISyntaxException {
        try {
            URLConnectionHelper helper = URLConnectionHelper.newInstance();
            URL locationURL = helper.toURL(location);

            _connection = (WsURLConnection) locationURL.openConnection();
            ((WsURLConnectionImpl) _connection).setEnabledParameters(enabledParameters);
        }
        catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
        catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }


    @Override
    public synchronized void close() throws IOException {
        close(0, null);
    }

    @Override
    public synchronized void close(int code) throws IOException {
        close(code, null);
    }

    @Override
    public synchronized void close(int code, String reason) throws IOException {
        _connection.close(code, reason);
    }

    @Override
    public void connect() throws IOException {
        _connection.connect();
    }

    @Override
    public ChallengeHandler getChallengeHandler() {
        return _connection.getChallengeHandler();
    }

    @Override
    public int getConnectTimeout() {
        return _connection.getConnectTimeout();
    }

    @Override
    public Collection<String> getEnabledExtensions() {
        return _connection.getEnabledExtensions();
    }

    @Override
    public <T> T getEnabledParameter(Parameter<T> parameter) {
        return _connection.getEnabledParameter(parameter);
    }

    @Override
    public Collection<String> getEnabledProtocols() {
        return _connection.getEnabledProtocols();
    }

    @Override
    public HttpRedirectPolicy getRedirectPolicy() {
        return _connection.getRedirectPolicy();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return _connection.getInputStream();
    }

    @Override
    public WebSocketMessageReader getMessageReader() throws IOException {
        return _connection.getMessageReader();
    }

    @Override
    public WebSocketMessageWriter getMessageWriter() throws IOException {
        return _connection.getMessageWriter();
    }

    @Override
    public Collection<String> getNegotiatedExtensions() {
        return _connection.getNegotiatedExtensions();
    }

    @Override
    public <T> T getNegotiatedParameter(Parameter<T> parameter) {
        return _connection.getNegotiatedParameter(parameter);
    }

    @Override
    public String getNegotiatedProtocol() {
        return _connection.getNegotiatedProtocol();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return _connection.getOutputStream();
    }

    @Override
    public Reader getReader() throws IOException {
        return _connection.getReader();
    }

    @Override
    public Collection<String> getSupportedExtensions() {
        return _connection.getSupportedExtensions();
    }

    @Override
    public Writer getWriter() throws IOException {
        return _connection.getWriter();
    }

    @Override
    public void setChallengeHandler(ChallengeHandler challengeHandler) {
        _connection.setChallengeHandler(challengeHandler);
    }

    @Override
    public void setConnectTimeout(int connectTimeout) {
        _connection.setConnectTimeout(connectTimeout);
    }

    @Override
    public void setEnabledExtensions(Collection<String> extensions) {
        _connection.setEnabledExtensions(extensions);
    }

    @Override
    public <T> void setEnabledParameter(Parameter<T> parameter, T value) {
        _connection.setEnabledParameter(parameter, value);
    }

    @Override
    public void setEnabledProtocols(Collection<String> protocols) {
        _connection.setEnabledProtocols(protocols);
    }

    @Override
    public void setRedirectPolicy(HttpRedirectPolicy policy) {
        _connection.setRedirectPolicy(policy);
    }
}
