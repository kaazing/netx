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

package org.kaazing.netx.http.bridge.internal;

import static java.lang.String.format;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import org.kaazing.netx.http.internal.HttpOriginSecuritySpi;

public final class HttpOriginSecurityImpl extends HttpOriginSecuritySpi {

    @Override
    protected HttpURLConnection openConnection0(URL url) throws IOException {
        URL connectionURL = new URL(url.toString());

        String connectionHost = connectionURL.getHost();
        int connectionPort = connectionURL.getPort();
        if (connectionPort == -1) {
            connectionPort = connectionURL.getDefaultPort();
        }

        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkConnect(connectionHost, connectionPort);
        }

        return (HttpURLConnection) connectionURL.openConnection();
    }

    @Override
    protected Socket createSocket0(URL url) throws IOException {
        String protocol = url.getProtocol();
        String host = url.getHost();
        int port = url.getPort();
        if (port == -1) {
            port = url.getDefaultPort();
        }

        if ("http".equalsIgnoreCase(protocol)) {
            SocketFactory socketFactory = SocketFactory.getDefault();
            return socketFactory.createSocket(host, port);
        }
        else if ("https".equalsIgnoreCase(protocol)) {
            SocketFactory socketFactory = SSLSocketFactory.getDefault();
            return socketFactory.createSocket(host, port);
        }
        else {
            throw new IllegalStateException(format("Unexpected protocol: %s", protocol));
        }

    }

}
