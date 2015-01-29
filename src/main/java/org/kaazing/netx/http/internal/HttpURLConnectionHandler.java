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

package org.kaazing.netx.http.internal;

import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.net.HttpURLConnection.HTTP_MOVED_PERM;
import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;
import static java.net.HttpURLConnection.HTTP_SEE_OTHER;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static java.util.Arrays.fill;
import static org.kaazing.netx.http.HttpURLConnection.HTTP_SWITCHING_PROTOCOLS;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class HttpURLConnectionHandler {

    private static final Charset US_ASCII = Charset.forName("US-ASCII");

    protected final HttpURLConnectionImpl connection;

    protected HttpURLConnectionHandler(HttpURLConnectionImpl connection) {
        this.connection = connection;
    }

    public abstract void connect() throws IOException;

    public abstract void disconnect();

    public abstract InputStream getInputStream() throws IOException;

    public abstract OutputStream getOutputStream() throws IOException;

    public abstract InputStream getErrorStream();

    static class Default extends HttpURLConnectionHandler {

        private final HttpOriginSecuritySpi security;

        private HttpURLConnection delegate;
        private InputStream input;

        public Default(HttpURLConnectionImpl connection) {
            super(connection);
            security = HttpOriginSecuritySpi.newInstance();
        }

        @Override
        public void connect() throws IOException {
            delegate().connect();
        }

        @Override
        public void disconnect() {
            input = null;
            if (delegate != null) {
                delegate.disconnect();
                delegate = null;
            }
        }

        @Override
        public InputStream getInputStream() throws IOException {
            if (input == null) {
                HttpURLConnection delegate = delegate();
                try {
                    input = delegate.getInputStream();
                }
                finally {
                    connection.setHeaderFields(delegate.getHeaderFields());
                    connection.setResponse(delegate.getResponseCode(), delegate.getResponseMessage());
                }
            }
            return input;
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return delegate().getOutputStream();
        }

        @Override
        public InputStream getErrorStream() {
            return (delegate != null) ? delegate.getErrorStream() : null;
        }

        private HttpURLConnection delegate() throws IOException {
            if (this.delegate == null) {
                HttpURLConnection delegate = security.openConnection(connection.getURL());
                delegate.setAllowUserInteraction(connection.getAllowUserInteraction());
                delegate.setConnectTimeout(connection.getConnectTimeout());
                delegate.setDoInput(connection.getDoInput());
                delegate.setDoOutput(connection.getDoOutput());
                delegate.setIfModifiedSince(connection.getIfModifiedSince());
                delegate.setReadTimeout(connection.getReadTimeout());
                delegate.setRequestMethod(connection.getRequestMethod());

                int chunkLength = connection.getChunkStreamingMode();
                int fixedContentLength = connection.getFixedLengthStreamingMode();
                if (chunkLength != -1) {
                    delegate.setChunkedStreamingMode(chunkLength);
                }
                else if (fixedContentLength != -1) {
                    delegate.setFixedLengthStreamingMode(fixedContentLength);
                }

                Map<String, List<String>> requestProperties = connection.getCachedRequestProperties();
                for (Map.Entry<String, List<String>> entry : requestProperties.entrySet()) {
                    String key = entry.getKey();
                    List<String> values = entry.getValue();
                    for (String value : values) {
                        delegate.addRequestProperty(key, value);
                    }
                }

                delegate.setUseCaches(connection.getUseCaches());

                // auto-redirect disabled to apply redirect policy
                delegate.setInstanceFollowRedirects(false);

                this.delegate = delegate;
            }
            return delegate;
        }
    }

    static class Upgradeable extends HttpURLConnectionHandler {

        private static final Pattern PATTERN_START = Pattern.compile("HTTP\\/1\\.1\\s+([1-5]\\d\\d)\\s+(.*)");
        private static final Pattern PATTERN_BASIC_CHALLENGE = Pattern.compile("Basic(?: realm=\"([^\"]+)\")?");
        private static final Pattern PATTERN_APPLICATION_CHALLENGE = Pattern.compile("Application ([a-zA-Z_]*)\\s?(.*)");

        private static enum State { INITIAL, HANDSHAKE_SENT, HANDSHAKE_RECEIVED }

        private final HttpOriginSecuritySpi security;

        private State state;
        private Socket socket;
        private InputStream input;
        private OutputStream output;
        private InputStream error;

        public Upgradeable(HttpURLConnectionImpl connection) {
            super(connection);
            security = HttpOriginSecuritySpi.newInstance();
            state = State.INITIAL;
        }

        @Override
        public void connect() throws IOException {
            switch (state) {
            case INITIAL:
                // TODO: support Proxy (instance proxy, or system proxy)

                URL url = connection.getURL();
                String host = url.getHost();
                int port = url.getPort();
                if (port == -1) {
                    port = url.getDefaultPort();
                }

                socket = security.createSocket(url);
                output = socket.getOutputStream();

                String method = connection.getRequestMethod();
                Map<String, List<String>> headers = connection.getCachedRequestProperties();

                Writer writer = new OutputStreamWriter(output, US_ASCII);
                writer.write(format("%s %s HTTP/1.1\r\n", method, url.getFile()));
                writer.write(format("Host: %s:%s\r\n", host, port));
                for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                    String headerName = entry.getKey();
                    List<String> headerValues = entry.getValue();
                    for (String headerValue: headerValues) {
                        writer.write(format("%s: %s\r\n", headerName, headerValue));
                    }
                }

                CookieHandler handler = CookieHandler.getDefault();
                if (handler != null) {
                    Map<String, List<String>> cookieHeaders = handler.get(URI.create(url.toString()), headers);
                    for (Map.Entry<String, List<String>> entry : cookieHeaders.entrySet()) {
                        String headerName = entry.getKey();
                        List<String> headerValues = entry.getValue();
                        for (String headerValue: headerValues) {
                            writer.write(format("%s: %s\r\n", headerName, headerValue));
                        }
                    }
                }

                writer.write("\r\n");
                writer.flush();

                state = State.HANDSHAKE_SENT;
                break;
            default:
                break;
            }
        }

        @Override
        public void disconnect() {
            try {
                socket.close();
            }
            catch (IOException e) {
                // ignore
            }
            state = State.INITIAL;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            // ensure connected
            connect();

            switch (state) {
            case HANDSHAKE_SENT:
                input = socket.getInputStream();

                BufferedReader reader = new BufferedReader(new InputStreamReader(input, "US-ASCII"));
                String start = reader.readLine();
                connection.addHeaderField(null, start);

                if (start == null) {
                    throw new IllegalStateException("Bad HTTP/1.1 syntax");
                }
                Matcher startMatcher = PATTERN_START.matcher(start);
                if (!startMatcher.matches()) {
                    throw new IllegalStateException("Bad HTTP/1.1 syntax");
                }
                int responseCode = parseInt(startMatcher.group(1));
                String responseMessage = startMatcher.group(2);
                connection.setResponse(responseCode, responseMessage);

                Map<String, List<String>> cookies = null;
                List<String> challenges = null;
                for (String header = reader.readLine(); !header.isEmpty(); header = reader.readLine()) {
                    int colonAt = header.indexOf(':');
                    if (colonAt == -1) {
                        throw new IllegalStateException("Bad HTTP/1.1 syntax");
                    }
                    String name = header.substring(0, colonAt).trim();
                    String value = header.substring(colonAt + 1).trim();
                    // detect cookies
                    if ("Set-Cookie".equalsIgnoreCase(name) || "Set-Cookie2".equalsIgnoreCase(name)) {
                        if (cookies == null) {
                            cookies = new LinkedHashMap<String, List<String>>();
                        }
                        List<String> values = cookies.get(name);
                        if (values == null) {
                            values = new LinkedList<String>();
                            cookies.put(name, values);
                        }
                        values.add(value);
                    }
                    else if ("WWW-Authenticate".equalsIgnoreCase(name)) {
                        if (challenges == null) {
                            challenges = new LinkedList<String>();
                        }
                        challenges.add(value);
                    }
                    else {
                        connection.addHeaderField(name, value);
                    }
                }

                if (cookies != null && !cookies.isEmpty()) {
                    CookieHandler handler = CookieHandler.getDefault();
                    if (handler != null) {
                        connection.storeCookies(handler);
                    }
                }

                state = State.HANDSHAKE_RECEIVED;

                switch (responseCode) {
                case HTTP_SWITCHING_PROTOCOLS:
                case HTTP_MOVED_PERM:
                case HTTP_MOVED_TEMP:
                case HTTP_SEE_OTHER:
                    break;
                case HTTP_UNAUTHORIZED:
                    // Note: check maximum attempts
                    processChallenges(challenges);
                    break;
                default:
                    throw new IllegalStateException(format("Upgrade failed (%d)", responseCode));
                }

                return input;
            case HANDSHAKE_RECEIVED:
                return input;
            default:
                throw new IllegalStateException(format("Unexpeced state: %s", state));
            }
        }

        @Override
        public OutputStream getOutputStream() throws IOException {

            // ensure read/write connected
            getInputStream();

            switch (state) {
            case HANDSHAKE_RECEIVED:
                return output;
            default:
                throw new IllegalStateException(format("Unexpeced state: %s", state));
            }

        }

        @Override
        public InputStream getErrorStream() {
            return error;
        }

        private void processChallenges(List<String> challenges) throws IOException {
            if (challenges != null) {
                for (String challenge : challenges) {
                    // TODO: digest, negotiate (!)
                    Matcher matcher = PATTERN_BASIC_CHALLENGE.matcher(challenge);
                    if (matcher.matches()) {
                        String realm = matcher.group(1);
                        URL url = connection.getURL();
                        PasswordAuthentication authentication = requestPasswordAuthentication(url, realm, "Basic");
                        if (authentication == null) {
                            // TODO: align behavior with HTTP stack when Basic challenge has null authentication
                            //       should we throw an exception?
                            throw new IllegalStateException("Basic Challenge Failed");
                        }

                        assert authentication != null;
                        state = State.INITIAL;
                        String username = authentication.getUserName();
                        char[] password = authentication.getPassword();
                        fill(password, '0');
                        byte[] credentials = format("%s:%s", username, password).getBytes(US_ASCII);
                        String authorization = new String(Base64.encode(credentials), US_ASCII);
                        connection.setRequestProperty("Authorization", format("Basic %s", authorization));

                        // Trigger next request with "Authorization" header set.
                        getInputStream();
                    }
                    else if (PATTERN_APPLICATION_CHALLENGE.matcher(challenge).matches()) {
                        state = State.INITIAL;

                        // Deal with "Application *" authentication schemes and
                        // trigger the next request after setting the
                        // "Authorization" header.
                        connection.processApplicationChallenge(challenge);
                    }
                }
            }
        }

        private static PasswordAuthentication requestPasswordAuthentication(URL url, String realm, String scheme) {
            String host = url.getHost();
            int port = url.getPort();
            String protocol = url.getProtocol();

            return Authenticator.requestPasswordAuthentication(host, null, port, protocol, realm, scheme);
        }
    }
}
