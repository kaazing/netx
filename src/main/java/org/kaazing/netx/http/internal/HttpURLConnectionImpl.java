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

import org.kaazing.netx.http.HttpURLConnection;

final class HttpURLConnectionImpl extends HttpURLConnection {

    private static final String HEADER_UPGRADE = "Upgrade";

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
        InputStream input = handler.getInputStream();
        switch (responseCode) {
        case HTTP_MOVED_PERM:
        case HTTP_MOVED_TEMP:
        case HTTP_SEE_OTHER:
            // TODO: check maximum number of redirects
            if (getInstanceFollowRedirects()) {
                input = processRedirect(input);
            }
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
