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
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Arrays.fill;
import static java.util.Collections.unmodifiableMap;

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

final class HttpUpgradeURLConnectionImpl extends HttpURLConnectionImpl {

    private static enum State { INITIAL, HANDSHAKE_SENT, HANDSHAKE_RECEIVED }

    private static final Pattern PATTERN_START = Pattern.compile("HTTP\\/1\\.1\\s+([1-5]\\d\\d)\\s+(.*)");
    private static final Pattern PATTERN_HEADER =
            Pattern.compile("([!#$%&'\\*\\+\\-\\^_`|~0-9a-zA-Z]+):\\s*([!#$%&'\\*\\+\\-\\^_`|~0-9a-zA-Z=\\\"\\s]+)");

    private State state;
    private Socket socket;
    private InputStream input;
    private OutputStream output;
    private Map<String, List<String>> responseHeaders;
    private static final Pattern PATTERN_BASIC_CHALLENGE = Pattern.compile("Basic(?: realm=\"([^\"]+)\")?");

    public HttpUpgradeURLConnectionImpl(URL url, HttpURLConnection connection) {
        super(url, connection);
        state = State.INITIAL;
    }

    @Override
    public void connect() throws IOException {
        switch (state) {
        case INITIAL:
            // TODO: support Proxy (instance proxy, or system proxy)

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

            String method = getRequestMethod();
            Map<String, List<String>> headers = getRequestHeaders();

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
    public boolean usingProxy() {
        return false;
    }

    @Override
    public int getResponseCode() throws IOException {
        return super.getResponseCode0();
    }

    @Override
    public String getResponseMessage() throws IOException {
        return super.getResponseMessage0();
    }

    @Override
    public String getHeaderField(String name) {
        List<String> values = responseHeaders.get(name);
        return (values != null) ? values.get(0) : null;
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
        return responseHeaders;
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
            Matcher startMatcher = PATTERN_START.matcher(start);
            if (!startMatcher.matches()) {
                throw new IllegalStateException("Bad HTTP/1.1 syntax");
            }
            responseCode = parseInt(startMatcher.group(1));
            responseMessage = startMatcher.group(2);

            boolean hasSetCookie = false;
            Map<String, List<String>> responseHeaders = new LinkedHashMap<String, List<String>>();
            for (String header = reader.readLine(); !header.isEmpty(); header = reader.readLine()) {
                Matcher headerMatcher = PATTERN_HEADER.matcher(header);
                if (!headerMatcher.matches()) {
                    throw new IllegalStateException("Bad HTTP/1.1 syntax");
                }
                String name = headerMatcher.group(1);
                String value = headerMatcher.group(2);
                // detect cookies
                if ("Set-Cookie".equalsIgnoreCase(name) || "Set-Cookie2".equalsIgnoreCase(name)) {
                    hasSetCookie = true;
                }
                else {
                    List<String> values = responseHeaders.get(name);
                    if (values == null) {
                        values = new LinkedList<String>();
                        responseHeaders.put(name, values);
                    }
                    values.add(value);
                }
            }
            if (hasSetCookie) {
                CookieHandler handler = CookieHandler.getDefault();
                if (handler == null) {
                    handler = new CookieManager();
                    CookieHandler.setDefault(handler);
                }
                handler.put(URI.create(url.toString()), responseHeaders);
            }
            this.responseHeaders = unmodifiableMap(responseHeaders);

            state = State.HANDSHAKE_RECEIVED;

            switch (responseCode) {
            case HTTP_SWITCHING_PROTOCOLS:
                break;
            case HTTP_MOVED_PERM:
            case HTTP_MOVED_TEMP:
            case HTTP_SEE_OTHER:
                // 3xx (note HttpRedirectPolicy is higher level)
                if (getInstanceFollowRedirects()) {
                    String location = getHeaderField("Location");
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

    private void processChallenges() throws IOException {
        List<String> challenges = responseHeaders.get("WWW-Authenticate");
        if (challenges != null) {
            for (String challenge : challenges) {
                // TODO: digest, negotiate (!)
                Matcher matcher = PATTERN_BASIC_CHALLENGE.matcher(challenge);
                if (matcher.matches()) {
                    String realm = matcher.group(1);
                    PasswordAuthentication authentication = requestPasswordAuthentication(url, realm, "Basic");
                    if (authentication == null) {
                        // TODO: align behavior with HTTP stack when Basic challenge has null authentication
                        //       should we throw an exception?
                        throw new IllegalStateException(format("Upgrade failed (%d)", responseCode));
                    }

                    assert authentication != null;
                    state = State.INITIAL;
                    String username = authentication.getUserName();
                    char[] password = authentication.getPassword();
                    fill(password, '0');
                    byte[] credentials = format("%s:%s", username, password).getBytes(US_ASCII);
                    String authorization = new String(Base64.encode(credentials), US_ASCII);
                    setRequestProperty("Authorization", format("Basic %s", authorization));

                    // trigger next request
                    getInputStream();
                }
            }
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

    private static PasswordAuthentication requestPasswordAuthentication(URL url, String realm, String scheme) {
        String host = url.getHost();
        int port = url.getPort();
        String protocol = url.getProtocol();

        return Authenticator.requestPasswordAuthentication(host, null, port, protocol, realm, scheme);
    }

}
