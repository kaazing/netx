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
package org.kaazing.netx.tcp.internal;

import static java.util.Collections.singleton;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Collection;

import org.kaazing.netx.URLConnectionHelperSpi;

public final class TcpURLConnectionHelper extends URLConnectionHelperSpi {

    @Override
    public Collection<String> getSupportedProtocols() {
        return singleton("tcp");
    }

    @Override
    public URLConnection openConnection(URI location) throws IOException {
        String scheme = location.getScheme();
        if (!"tcp".equals(scheme)) {
            throw new IllegalArgumentException("Unsupported protocol: " + scheme);
        }
        return new TcpURLConnection(null, location);
    }

    @Override
    public URLStreamHandler newStreamHandler() throws IOException {
        return new TcpURLStreamHandler();
    }

    private static final class TcpURLStreamHandler extends URLStreamHandler {

        @Override
        protected URLConnection openConnection(URL location) throws IOException {
            return new TcpURLConnection(location);
        }
    }

    private static final class TcpURLConnection extends URLConnection implements Closeable {

        private final Socket socket;
        private final InetSocketAddress endpoint;

        private InputStream input;
        private OutputStream output;

        public TcpURLConnection(URL location) throws IOException {
            this(location, URI.create(location.toString()));
        }

        public TcpURLConnection(URL location, URI locationURI) throws IOException {
            super(location);

            String protocol = locationURI.getScheme();
            if (!"tcp".equals(protocol)) {
                throw new IllegalArgumentException("Unrecognized protocol: " + protocol);
            }

            String path = locationURI.getPath();
            if (!path.isEmpty()) {
                throw new IllegalArgumentException("Unexpected path: " + path);
            }

            String hostname = locationURI.getHost();
            if (hostname == null || hostname.isEmpty()) {
                throw new IllegalArgumentException("Expected hostname: " + hostname);
            }

            int port = locationURI.getPort();
            if (port == -1) {
                throw new IllegalArgumentException("Expected port: " + port);
            }

            socket = new Socket();
            endpoint = new InetSocketAddress(hostname, port);
        }

        @Override
        public void connect() throws IOException {
            socket.connect(endpoint);
            connected = true;
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
            if (input == null) {
                input = new TcpInputStream(socket);
            }
            return input;
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            if (output == null) {
                output = new TcpOutputStream(socket);
            }
            return output;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }

        private final class TcpInputStream extends InputStream {

            private final InputStream input;

            public TcpInputStream(Socket socket) throws IOException {
                this.input = socket.getInputStream();
            }

            @Override
            public int read() throws IOException {
                return input.read();
            }

            @Override
            public int read(byte[] b) throws IOException {
                return input.read(b);
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return input.read(b, off, len);
            }

            @Override
            public long skip(long n) throws IOException {
                return input.skip(n);
            }

            @Override
            public int available() throws IOException {
                return input.available();
            }

            @Override
            public void close() throws IOException {
                if (socket.isOutputShutdown()) {
                    socket.close();
                }
                else {
                    socket.shutdownInput();
                }
            }

            @Override
            public synchronized void mark(int readlimit) {
                input.mark(readlimit);
            }

            @Override
            public synchronized void reset() throws IOException {
                input.reset();
            }

            @Override
            public boolean markSupported() {
                return input.markSupported();
            }
        }

        private final class TcpOutputStream extends OutputStream {

            private final OutputStream output;

            public TcpOutputStream(Socket socket) throws IOException {
                this.output = socket.getOutputStream();
            }

            @Override
            public void write(int b) throws IOException {
                output.write(b);
            }

            @Override
            public void write(byte[] b) throws IOException {
                output.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                output.write(b, off, len);
            }

            @Override
            public void flush() throws IOException {
                output.flush();
            }

            @Override
            public void close() throws IOException {
                if (socket.isInputShutdown()) {
                    socket.close();
                }
                else {
                    socket.shutdownOutput();
                }
            }

        }
    }
}
