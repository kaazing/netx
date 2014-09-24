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

package org.kaazing.net.impl;

import static java.util.Collections.singleton;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Collection;

import org.kaazing.net.URLStreamHandlerFactorySpi;

public final class TcpURLStreamHandlerFactorySpi extends URLStreamHandlerFactorySpi {

    @Override
    public Collection<String> getSupportedProtocols() {
        return singleton("tcp");
    }

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        if (!"tcp".equals(protocol)) {
            throw new IllegalArgumentException("Unsupported protocol: " + protocol);
        }
        return new TcpURLStreamHandler();
    }

    private static final class TcpURLStreamHandler extends URLStreamHandler {

        @Override
        protected URLConnection openConnection(URL location) throws IOException {
            return new TcpURLConnection(location);
        }

        private static final class TcpURLConnection extends URLConnection implements Closeable {

            private final Socket socket;
            private final InetSocketAddress endpoint;

            public TcpURLConnection(URL location) throws IOException {
                super(location);

                String protocol = location.getProtocol();
                if (!"tcp".equals(protocol)) {
                    throw new IllegalArgumentException("Unrecognized protocol: " + protocol);
                }

                String path = location.getPath();
                if (!path.isEmpty()) {
                    throw new IllegalArgumentException("Unexpected path: " + path);
                }

                String hostname = location.getHost();
                if (hostname == null || hostname.isEmpty()) {
                    throw new IllegalArgumentException("Expected hostname: " + hostname);
                }

                int port = location.getPort();
                if (port == -1) {
                    throw new IllegalArgumentException("Expected port: " + port);
                }

                socket = new Socket();
                endpoint = new InetSocketAddress(hostname, port);
            }

            @Override
            public void connect() throws IOException {
                socket.connect(endpoint);
            }

            @Override
            public void setReadTimeout(int timeout) {
                try {
                    socket.setSoTimeout(timeout);
                }
                catch (SocketException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public int getReadTimeout() {
                try {
                    return socket.getSoTimeout();
                }
                catch (SocketException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return socket.getInputStream();
            }

            @Override
            public OutputStream getOutputStream() throws IOException {
                return socket.getOutputStream();
            }

            @Override
            public void close() throws IOException {
                socket.close();
            }
        }
    }
}
