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

import static java.lang.String.format;
import static org.kaazing.netx.http.internal.HttpRedirectPolicyUtils.shouldFollowRedirect;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kaazing.netx.http.HttpURLConnection;
import org.kaazing.netx.http.auth.ChallengeHandler;
import org.kaazing.netx.http.auth.ChallengeRequest;
import org.kaazing.netx.http.auth.ChallengeResponse;

final class HttpURLConnectionImpl extends HttpURLConnection {
    private static final Pattern PATTERN_APPLICATION_CHALLENGE = Pattern.compile("Application ([a-zA-Z_]*)\\s?(.*)");
    private static final String APPLICATION_PREFIX = "Application ";

    private static final String HEADER_UPGRADE = "Upgrade";
    private static final String HEADER_AUTHENTICATION = "WWW-Authenticate";
    private static final String HEADER_AUTHORIZATION = "Authorization";

    private final HttpHeaderFields cachedRequestProperties;
    private final HttpHeaderFields headerFields;

    private HttpURLConnectionHandler handler;

    private int connectTimeout;
    private int readTimeout;

    public HttpURLConnectionImpl(URL url) {
        super(url);
        this.cachedRequestProperties = new HttpHeaderFields();
        this.headerFields = new HttpHeaderFields();
        this.handler = new HttpURLConnectionHandler.Default(this);
    }

    @Override
    public void addRequestProperty(String key, String value) {
        super.addRequestProperty(key, value);
        cachedRequestProperties.add(key, value);
        detectHttpUpgrade(key);
    }

    @Override
    public void connect() throws IOException {
        handler.connect();
    }

    @Override
    public void disconnect() {
        handler.disconnect();
    }

    @Override
    public int getConnectTimeout() {
        return connectTimeout;
    }

    @Override
    public InputStream getErrorStream() {
        return handler.getErrorStream();
    }

    @Override
    public String getHeaderField(int n) {
        return headerFields.value(n);
    }

    @Override
    public String getHeaderFieldKey(int n) {
        return headerFields.key(n);
    }

    @Override
    public String getHeaderField(String name) {
        return headerFields.value(name);
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
        return headerFields.map();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        InputStream input = null;
        IOException exc = null;

        try {
            input = handler.getInputStream();
        }
        catch (IOException ex) {
            exc = ex;
        }

        switch (responseCode) {
        case HTTP_MOVED_PERM:
        case HTTP_MOVED_TEMP:
        case HTTP_SEE_OTHER:
            // TODO: check maximum number of redirects
            if (getInstanceFollowRedirects()) {
                input = processRedirect(input);
            }
            break;
        case HTTP_UNAUTHORIZED:
            // Note: check maximum attempts
            String challenge = getHeaderField(HEADER_AUTHENTICATION);
            if (challenge == null) {
                throw exc;
            }

            // We are only dealing with "Application *" authentication schemes
            // here. For the Default HTTP, authentication schemes such as
            // "Basic", "Digest", and "Negotiate" will be handled implicitly
            // using the system-wide Authenticator, if registered. For the
            // Upgradeable HTTP, we will handle "Basic", "Digest", and "Negotiate"
            // schemes in the corresponding handler itself.
            if (!challenge.startsWith(APPLICATION_PREFIX)) {
                throw new IOException("Invalid authentication scheme: " + challenge);
            }
            processApplicationChallenge(challenge);
            break;

        default:
            break;
        }
        return input;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return handler.getOutputStream();
    }

    @Override
    public int getReadTimeout() {
        return readTimeout;
    }

    @Override
    public void setConnectTimeout(int timeout) {
        this.connectTimeout = timeout;
    }

    @Override
    public void setReadTimeout(int timeout) {
        this.readTimeout = timeout;
    }

    @Override
    public void setRequestProperty(String key, String value) {
        super.setRequestProperty(key, value);
        cachedRequestProperties.set(key, value);
        detectHttpUpgrade(key);
    }

    @Override
    public boolean usingProxy() {
        return false;
    }

    Map<String, List<String>> getCachedRequestProperties() {
        return cachedRequestProperties.map();
    }

    int getChunkStreamingMode() {
        return chunkLength;
    }

    int getFixedLengthStreamingMode() {
        return fixedContentLength;
    }

    void setResponse(int responseCode, String responseMessage) {
        this.responseCode = responseCode;
        this.responseMessage = responseMessage;
    }

    void addHeaderField(String key, String value) {
        this.headerFields.add(key, value);
    }

    void setHeaderFields(Map<String, List<String>> headerFields) {
        this.headerFields.addAll(headerFields);
    }

    void storeCookies(CookieHandler handler) throws IOException {
        URI locationURI = URI.create(url.toString());
        handler.put(locationURI, headerFields.map());
    }

    void reset(URL url) {
        this.url = url;
        this.responseCode = -1;
        this.responseMessage = null;
        this.headerFields.clear();
        this.handler = new HttpURLConnectionHandler.Default(this);

        if (cachedRequestProperties.value(HEADER_UPGRADE) != null) {
            this.handler = new HttpURLConnectionHandler.Upgradeable(this);
        }
    }

    void processApplicationChallenge(String challenge) throws IOException {
        if (challenge == null) {
            throw new IllegalStateException("Invalid challenge");
        }

        String authScheme = null;
        Matcher matcher = PATTERN_APPLICATION_CHALLENGE.matcher(challenge);
        if (matcher.matches()) {
            authScheme = APPLICATION_PREFIX + matcher.group(1);
        }
        if ((authScheme == null) || !authScheme.startsWith(APPLICATION_PREFIX)) {
            throw new IllegalStateException("Invalid authScheme: " + authScheme);
        }

        ChallengeHandler challengeHandler = getChallengeHandler();
        if (challengeHandler == null) {
            throw new IllegalStateException("ChallengeHandler is not registered to deal with an authentication challenge");
        }

        String location = getURL().toString();
        ChallengeRequest challengeRequest = new ChallengeRequest(location, challenge);
        if (!challengeHandler.canHandle(challengeRequest)) {
            throw new IllegalStateException(format("Registered ChallengeHandler cannot handle '%s' challenges", authScheme));
        }

        ChallengeResponse challengeResponse = challengeHandler.handle(challengeRequest);
        assert challengeResponse != null;
        String credentials = new String(challengeResponse.getCredentials());
        this.setRequestProperty(HEADER_AUTHORIZATION, credentials);

        reset(getURL());

        // Trigger next request with "Authorization" header set.
         handler.getInputStream();
    }

    private void detectHttpUpgrade(String key) {
        if (HEADER_UPGRADE.equalsIgnoreCase(key)) {
            this.handler = new HttpURLConnectionHandler.Upgradeable(this);
        }
    }

    private InputStream processRedirect(InputStream input) throws IOException {

        String location = headerFields.value("Location");
        if (location == null) {
            throw new IllegalStateException(format("Redirect missing Location header (%d)", responseCode));
        }

        URL currentURL = getURL();
        URL redirectURL = new URL(currentURL, location, new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL u) throws IOException {
                return HttpURLConnectionImpl.this;
            }
        });

        if (!shouldFollowRedirect(getRedirectPolicy(), currentURL, redirectURL)) {
            return input;
        }

        handler.disconnect();

        reset(redirectURL);
        return handler.getInputStream();
    }

}
