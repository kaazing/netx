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

package org.kaazing.net.bbosh.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.List;

import org.kaazing.net.bbosh.BBoshStrategy;
import org.kaazing.net.bbosh.BBoshURLConnection;

final class BBoshURLConnectionImpl extends BBoshURLConnection {

    private static final int INITIALIZED = 0;
    private static final int CONNECTED = 1;
    private static final int CLOSED = 2;

    private final BBoshConnectionFactory connectionFactory;

    private BBoshConnection connection;
    private int status;

    BBoshURLConnectionImpl(URL url, String httpScheme) throws IOException {
        super(url);
        URL factoryURL = new URL(httpScheme, url.getHost(), url.getPort(), url.getFile());
        connectionFactory = new BBoshConnectionFactory(factoryURL);
    }

    @Override
    public void connect() throws IOException {
        switch (status) {
        case INITIALIZED:
            doConnect();
            break;
        case CONNECTED:
            throw new IOException("Already connected");
        case CLOSED:
            throw new IOException("Already closed");
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        doConnectWhenInitialized();
        return connection.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        doConnectWhenInitialized();
        return connection.getOutputStream();
    }

    @Override
    public void close() throws IOException {
        doConnectWhenInitialized();
        switch (status) {
        case CONNECTED:
            doClose();
            break;
        case CLOSED:
            throw new IOException("Already closed");
        }
    }

    private void doConnectWhenInitialized() throws IOException {
        switch (status) {
        case INITIALIZED:
            doConnect();
            break;
        case CONNECTED:
            break;
        case CLOSED:
            throw new IOException("Already closed");
        }
    }

    private void doConnect() throws IOException {
        List<BBoshStrategy> strategies = getSupportedStrategies();
        int timeout = getConnectTimeout();
        connection = connectionFactory.createConnection(strategies, timeout);
        status = CONNECTED;
    }

    private void doClose() throws IOException {
        connection.close();
        status = CLOSED;
    }
}