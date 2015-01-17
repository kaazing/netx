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
import static java.nio.charset.StandardCharsets.US_ASCII;
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
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class HttpURLConnectionHandler  {

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

        private HttpURLConnection bundled;
        private InputStream input;

        public Default(HttpURLConnectionImpl connection) {
            super(connection);
        }

        @Override
        public void connect() throws IOException {
            bundled().connect();
        }

        @Override
        public void disconnect() {
            if (bundled != null) {
                bundled.disconnect();
            }
        }

        @Override
        public InputStream getInputStream() throws IOException {
            if (input == null) {
                HttpURLConnection bundled = bundled();
                try {
                    input = bundled.getInputStream();
                }
                finally {
                    HttpHeaderFields headerFields = connection.getHttpHeaderFields();
                    headerFields.addAll(bundled.getHeaderFields());
                }
            }
            return input;
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return bundled().getOutputStream();
        }

        @Override
        public InputStream getErrorStream() {
            return (bundled != null) ? bundled.getErrorStream() : null;
        }

        private HttpURLConnection bundled() throws IOException {
            if (bundled == null) {
                HttpURLConnection bundled = (HttpURLConnection) new URL(connection.getURL().toString()).openConnection();
                bundled.setAllowUserInteraction(connection.getAllowUserInteraction());
                bundled.setConnectTimeout(connection.getConnectTimeout());
                bundled.setDoInput(connection.getDoInput());
                bundled.setDoOutput(connection.getDoOutput());
                bundled.setIfModifiedSince(connection.getIfModifiedSince());
                bundled.setInstanceFollowRedirects(connection.getInstanceFollowRedirects());
                bundled.setReadTimeout(connection.getReadTimeout());
                bundled.setRequestMethod(connection.getRequestMethod());

                int chunkLength = connection.getChunkStreamingMode();
                int fixedContentLength = connection.getFixedLengthStreamingMode();
                long fixedContentLengthLong = connection.getFixedLengthStreamingModeLong();
                if (chunkLength != -1) {
                    bundled.setChunkedStreamingMode(chunkLength);
                }
                else if (fixedContentLength != -1) {
                    bundled.setFixedLengthStreamingMode(fixedContentLength);
                }
                else if (fixedContentLengthLong != -1L) {
                    bundled.setFixedLengthStreamingMode(fixedContentLengthLong);
                }

                Map<String, List<String>> requestProperties = connection.getRequestProperties(true);
                for (Map.Entry<String, List<String>> entry : requestProperties.entrySet()) {
                    String key = entry.getKey();
                    List<String> values = entry.getValue();
                    for (String value : values) {
                        bundled.addRequestProperty(key, value);
                    }
                }

                bundled.setUseCaches(connection.getUseCaches());

                this.bundled = bundled;
            }
            return bundled;
        }
    }

    static class Upgradeable extends HttpURLConnectionHandler {


        private static final Pattern PATTERN_START = Pattern.compile("HTTP\\/1\\.1\\s+([1-5]\\d\\d)\\s+(.*)");
        private static final Pattern PATTERN_BASIC_CHALLENGE = Pattern.compile("Basic(?: realm=\"([^\"]+)\")?");

        private static enum State { INITIAL, HANDSHAKE_SENT, HANDSHAKE_RECEIVED }

        private State state;
        private Socket socket;
        private InputStream input;
        private OutputStream output;
        private InputStream error;

        public Upgradeable(HttpURLConnectionImpl connection) {
            super(connection);
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
                    throw new IllegalStateException("TODO: default HTTP(S) port");
                }

                SecurityManager security = System.getSecurityManager();
                if (security != null) {
                    security.checkConnect(host, port);
                }

                socket = new Socket(host, port);
                output = socket.getOutputStream();

                String method = connection.getRequestMethod();
                Map<String, List<String>> headers = connection.getRequestProperties(true);

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
        }

        @Override
        public InputStream getInputStream() throws IOException {
            // ensure connected
            connect();

            switch (state) {
            case HANDSHAKE_SENT:
                input = socket.getInputStream();
                HttpHeaderFields headerFields = connection.getHttpHeaderFields();

                BufferedReader reader = new BufferedReader(new InputStreamReader(input, "US-ASCII"));
                String start = reader.readLine();
                headerFields.add(null, start);

                Matcher startMatcher = PATTERN_START.matcher(start);
                if (!startMatcher.matches()) {
                    throw new IllegalStateException("Bad HTTP/1.1 syntax");
                }
                int responseCode = parseInt(startMatcher.group(1));

                Map<String, List<String>> cookies = null;
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
                    else {
                        headerFields.add(name, value);
                    }
                }

                if (cookies != null && !cookies.isEmpty()) {
                    CookieHandler handler = CookieHandler.getDefault();
                    if (handler == null) {
                        handler = new CookieManager();
                        CookieHandler.setDefault(handler);
                    }
                    URI locationURI = URI.create(connection.getURL().toString());
                    handler.put(locationURI, headerFields.map());
                }

                state = State.HANDSHAKE_RECEIVED;

                switch (responseCode) {
                case HTTP_SWITCHING_PROTOCOLS:
                    break;
                case HTTP_MOVED_PERM:
                case HTTP_MOVED_TEMP:
                case HTTP_SEE_OTHER:
                    // 3xx (note HttpRedirectPolicy is higher level)
                    if (connection.getInstanceFollowRedirects()) {
                        String location = headerFields.value("Location");
                        if (location == null) {
                            throw new IllegalStateException(format("Redirect missing Location header (%d)", responseCode));
                        }
                        // TODO: repeat request at new Location
                        // Note: check maximum redirects
                        throw new UnsupportedOperationException("Redirect not yet supported");
                    }
                    break;
                case HTTP_UNAUTHORIZED:
                    // Note: check maximum redirects
                    processChallenges();
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

        private void processChallenges() throws IOException {
            HttpHeaderFields headerFields = connection.getHttpHeaderFields();
            List<String> challenges = headerFields.map().get("WWW-Authenticate");
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

                        // trigger next request
                        getInputStream();
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
