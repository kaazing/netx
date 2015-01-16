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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.net.URL;
import java.security.Permission;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.kaazing.netx.http.HttpURLConnection;

abstract class HttpURLConnectionImpl extends HttpURLConnection {

    protected java.net.HttpURLConnection connection;
    private final Map<String, List<String>> requestHeaders;

    public HttpURLConnectionImpl(URL url) {
        super(url);
        requestHeaders = new LinkedHashMap<String, List<String>>();
    }

    protected HttpURLConnectionImpl(URL url, java.net.HttpURLConnection connection) {
        super(url);
        this.connection = connection;
        requestHeaders = new LinkedHashMap<String, List<String>>();
    }

    @Override
    public void connect() throws IOException {
        openConnection();
    }

    @Override
    public void disconnect() {
        connection(false).disconnect();
    }

    @Override
    public boolean usingProxy() {
        return connection(false).usingProxy();
    }

    @Override
    public String getHeaderFieldKey(int n) {
        return connection(true).getHeaderFieldKey(n);
    }

    @Override
    public void setFixedLengthStreamingMode(int contentLength) {
        connection(false).setFixedLengthStreamingMode(contentLength);
    }

    @Override
    public void setFixedLengthStreamingMode(long contentLength) {
        connection(false).setFixedLengthStreamingMode(contentLength);
    }

    @Override
    public void setChunkedStreamingMode(int chunklen) {
        connection(false).setChunkedStreamingMode(chunklen);
    }

    @Override
    public String getHeaderField(int n) {
        return connection(true).getHeaderField(n);
    }

    @Override
    public void setInstanceFollowRedirects(boolean followRedirects) {
        connection(false).setInstanceFollowRedirects(followRedirects);
    }

    @Override
    public boolean getInstanceFollowRedirects() {
        return connection(false).getInstanceFollowRedirects();
    }

    @Override
    public void setRequestMethod(String method) throws ProtocolException {
        connection(false).setRequestMethod(method);
    }

    @Override
    public String getRequestMethod() {
        return connection(false).getRequestMethod();
    }

    @Override
    public int getResponseCode() throws IOException {
        return connection(true).getResponseCode();
    }

    @Override
    public String getResponseMessage() throws IOException {
        return connection(true).getResponseMessage();
    }

    @Override
    public long getHeaderFieldDate(String name, long Default) {
        return connection(true).getHeaderFieldDate(name, Default);
    }

    @Override
    public Permission getPermission() throws IOException {
        return connection(false).getPermission();
    }

    @Override
    public InputStream getErrorStream() {
        return connection(false).getErrorStream();
    }

    @Override
    public void setConnectTimeout(int timeout) {
        connection(false).setConnectTimeout(timeout);
    }

    @Override
    public int getConnectTimeout() {
        return connection(false).getConnectTimeout();
    }

    @Override
    public void setReadTimeout(int timeout) {
        connection(false).setReadTimeout(timeout);
    }

    @Override
    public int getReadTimeout() {
        return connection(false).getReadTimeout();
    }

    @Override
    public URL getURL() {
        return connection(false).getURL();
    }

    @Override
    public int getContentLength() {
        return connection(true).getContentLength();
    }

    @Override
    public long getContentLengthLong() {
        return connection(true).getContentLengthLong();
    }

    @Override
    public String getContentType() {
        return connection(true).getContentType();
    }

    @Override
    public String getContentEncoding() {
        return connection(true).getContentEncoding();
    }

    @Override
    public long getExpiration() {
        return connection(true).getExpiration();
    }

    @Override
    public long getDate() {
        return connection(true).getDate();
    }

    @Override
    public long getLastModified() {
        return connection(true).getLastModified();
    }

    @Override
    public String getHeaderField(String name) {
        return connection(true).getHeaderField(name);
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
        return connection(true).getHeaderFields();
    }

    @Override
    public int getHeaderFieldInt(String name, int Default) {
        return connection(true).getHeaderFieldInt(name, Default);
    }

    @Override
    public long getHeaderFieldLong(String name, long Default) {
        return connection(true).getHeaderFieldLong(name, Default);
    }

    @Override
    public Object getContent() throws IOException {
        return connection(true).getContent();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Object getContent(Class[] classes) throws IOException {
        return connection(true).getContent(classes);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return connection(false).getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return connection(true).getOutputStream();
    }

    @Override
    public String toString() {
        return connection(false).toString();
    }

    @Override
    public void setDoInput(boolean doinput) {
        connection(false).setDoInput(doinput);
    }

    @Override
    public boolean getDoInput() {
        return connection(false).getDoInput();
    }

    @Override
    public void setDoOutput(boolean dooutput) {
        connection(false).setDoOutput(dooutput);
    }

    @Override
    public boolean getDoOutput() {
        return connection(false).getDoOutput();
    }

    @Override
    public void setAllowUserInteraction(boolean allowuserinteraction) {
        connection(false).setAllowUserInteraction(allowuserinteraction);
    }

    @Override
    public boolean getAllowUserInteraction() {
        return connection(false).getAllowUserInteraction();
    }

    @Override
    public void setUseCaches(boolean usecaches) {
        connection(false).setUseCaches(usecaches);
    }

    @Override
    public boolean getUseCaches() {
        return connection(false).getUseCaches();
    }

    @Override
    public void setIfModifiedSince(long ifmodifiedsince) {
        connection(false).setIfModifiedSince(ifmodifiedsince);
    }

    @Override
    public long getIfModifiedSince() {
        return connection(false).getIfModifiedSince();
    }

    @Override
    public boolean getDefaultUseCaches() {
        return connection(false).getDefaultUseCaches();
    }

    @Override
    public void setDefaultUseCaches(boolean defaultusecaches) {
        connection(false).setDefaultUseCaches(defaultusecaches);
    }

    @Override
    public void setRequestProperty(String key, String value) {
        connection(false).setRequestProperty(key, value);
        List<String> values = requestHeaders.get(key);
        if (values == null) {
            values = new LinkedList<String>();
            requestHeaders.put(key, values);
        }
        else {
            values.clear();
        }
        values.add(value);
    }

    @Override
    public void addRequestProperty(String key, String value) {
        connection(false).addRequestProperty(key, value);
    }

    @Override
    public String getRequestProperty(String key) {
        return connection(false).getRequestProperty(key);
    }

    @Override
    public Map<String, List<String>> getRequestProperties() {
        return connection(false).getRequestProperties();
    }

    protected Map<String, List<String>> getRequestHeaders() {
        return requestHeaders;
    }

    protected int getResponseCode0() throws IOException {
        return super.getResponseCode();
    }

    protected String getResponseMessage0() throws IOException {
        return super.getResponseMessage();
    }

    protected java.net.HttpURLConnection connection(boolean input) {

        try {
            openConnection();

            if (input) {
                getInputStream();
            }
        }
        catch (IOException e) {
            throw new IllegalStateException(e);
        }

        return connection;
    }

    private void openConnection() throws IOException {
        if (connection == null) {
            URL delegateURL = new URL(url.toString());
            connection = (java.net.HttpURLConnection) delegateURL.openConnection();
        }
    }

}
