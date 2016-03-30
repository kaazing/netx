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

import org.kaazing.netx.URLConnectionHelper;
import org.kaazing.netx.http.HttpRedirectPolicy;
import org.kaazing.netx.http.auth.ChallengeHandler;
import org.kaazing.netx.ws.MessageReader;
import org.kaazing.netx.ws.MessageWriter;
import org.kaazing.netx.ws.WebSocket;

public class WebSocketImpl extends WebSocket {
    private final WsURLConnectionImpl connection;

    public WebSocketImpl(URI location, WebSocketExtensionFactory extensionFactory) throws URISyntaxException {
        try {
            URLConnectionHelper helper = URLConnectionHelper.newInstance();
            URL locationURL = helper.toURL(location);

            connection = (WsURLConnectionImpl) locationURL.openConnection();
        }
        catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
        catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void addEnabledExtensions(String... extensions) {
        if (extensions.length > 0) {
            connection.addEnabledExtensions(extensions);
        }
    }

    @Override
    public void close() throws IOException {
        close(0, null);
    }

    @Override
    public void close(int code) throws IOException {
        close(code, null);
    }

    @Override
    public void close(int code, String reason) throws IOException {
        connection.close(code, reason);
    }

    @Override
    public void connect() throws IOException {
        connection.connect();
    }

    @Override
    public ChallengeHandler getChallengeHandler() {
        return connection.getChallengeHandler();
    }

    @Override
    public int getConnectTimeout() {
        return connection.getConnectTimeout();
    }

    @Override
    public Collection<String> getEnabledExtensions() {
        return connection.getEnabledExtensions();
    }

    @Override
    public Collection<String> getEnabledProtocols() {
        return connection.getEnabledProtocols();
    }

    @Override
    public HttpRedirectPolicy getRedirectPolicy() {
        return connection.getRedirectPolicy();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return connection.getInputStream();
    }

    @Override
    public int getMaxFramePayloadLength() {
        return connection.getMaxFramePayloadLength();
    }

    @Override
    public MessageReader getMessageReader() throws IOException {
        return connection.getMessageReader();
    }

    @Override
    public MessageWriter getMessageWriter() throws IOException {
        return connection.getMessageWriter();
    }

    @Override
    public Collection<String> getNegotiatedExtensions() throws IOException {
        return connection.getNegotiatedExtensions();
    }

    @Override
    public String getNegotiatedProtocol() throws IOException {
        return connection.getNegotiatedProtocol();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return connection.getOutputStream();
    }

    @Override
    public Reader getReader() throws IOException {
        return connection.getReader();
    }

    @Override
    public Collection<String> getSupportedExtensions() {
        return connection.getSupportedExtensions();
    }

    @Override
    public Writer getWriter() throws IOException {
        return connection.getWriter();
    }

    @Override
    public void setChallengeHandler(ChallengeHandler challengeHandler) {
        connection.setChallengeHandler(challengeHandler);
    }

    @Override
    public void setConnectTimeout(int connectTimeout) {
        connection.setConnectTimeout(connectTimeout);
    }

    @Override
    public void setEnabledProtocols(String... protocols) {
        connection.setEnabledProtocols(protocols);
    }

    @Override
    public void setRedirectPolicy(HttpRedirectPolicy policy) {
        connection.setRedirectPolicy(policy);
    }

    @Override
    public void setMaxFramePayloadLength(int maxPayloadLength) {
        connection.setMaxFramePayloadLength(maxPayloadLength);
    }
}
