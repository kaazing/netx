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

package org.kaazing.netx.ws.internal.wsn;

import static java.lang.String.format;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.kaazing.netx.URLConnectionHelper;
import org.kaazing.netx.http.HttpURLConnection;
import org.kaazing.netx.ws.WsURLConnection;
import org.kaazing.netx.ws.internal.WebSocketExtension;
import org.kaazing.netx.ws.internal.WsURLConnectionHandler;
import org.kaazing.netx.ws.internal.WsURLConnectionImpl;
import org.kaazing.netx.ws.internal.WebSocketExtension.Parameter;
import org.kaazing.netx.ws.internal.ext.WebSocketExtensionFactorySpi;
import org.kaazing.netx.ws.internal.ext.WebSocketExtensionParameterValuesSpi;
import org.kaazing.netx.ws.internal.util.Base64Util;

public final class WsURLConnectionHandlerNative extends WsURLConnectionHandler {
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final String HEADER_CONNECTION = "Connection";
    private static final String HEADER_PROTOCOL = "WebSocket-Protocol";
    private static final String HEADER_SEC_ACCEPT = "Sec-WebSocket-Accept";
    private static final String HEADER_SEC_PROTOCOL = "Sec-WebSocket-Protocol";
    private static final String HEADER_SEC_EXTENSIONS = "Sec-WebSocket-Extensions";
    private static final String HEADER_SEC_KEY = "Sec-WebSocket-Key";
    private static final String HEADER_SEC_VERSION = "Sec-WebSocket-Version";
    private static final String HEADER_UPGRADE = "Upgrade";
    // private static final String HTTP_101_MESSAGE = "Switching Protocols";
    private static final String HTTP_101_MESSAGE = "Web Socket Protocol Handshake";


    private final WsURLConnection _connection;
    private HttpURLConnection     _delegate;

    public WsURLConnectionHandlerNative(WsURLConnection connection) {
        if (connection == null) {
            throw new NullPointerException("Null connection is passed in");
        }

        this._connection = connection;
        this._connection.setDoOutput(true);
    }

    @Override
    public void connect() throws IOException {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create(getHttpUrl());

        _delegate = (HttpURLConnection) helper.openConnection(location);
        String websocketKey = base64Encode(randomBytes(16));

        _delegate.setRequestMethod("GET");
        _delegate.setRequestProperty(HEADER_UPGRADE, "websocket");
        _delegate.setRequestProperty(HEADER_CONNECTION, "Upgrade");
        _delegate.setRequestProperty(HEADER_SEC_KEY, websocketKey);
        _delegate.setRequestProperty(HEADER_SEC_VERSION, "13");

        String enabledExtensions = getEnabledExtensionsWithParamsAsRFC3864FormattedString();
        if ((enabledExtensions != null) && (enabledExtensions.trim().length() > 0)) {
            _delegate.setRequestProperty(HEADER_SEC_EXTENSIONS, enabledExtensions);
        }

        String protocols = getEnabledProtocols();
        if (protocols != null) {
            _delegate.setRequestProperty(HEADER_SEC_PROTOCOL, protocols);
        }

        _delegate.setConnectTimeout(_connection.getConnectTimeout());
        _delegate.setReadTimeout(_connection.getReadTimeout());
        _delegate.setChallengeHandler(_connection.getChallengeHandler());
        _delegate.setRedirectPolicy(_connection.getRedirectPolicy());
        _delegate.setAllowUserInteraction(_connection.getAllowUserInteraction());
        _delegate.setDoInput(_connection.getDoInput());
        _delegate.setDoOutput(_connection.getDoOutput());
        _delegate.setIfModifiedSince(_connection.getIfModifiedSince());

        // Initiate the handshake request to the WebSocket end-point.
        int responseCode = _delegate.getResponseCode();

        // Validate the status code and the headers in the handshake response.
        if (responseCode != 101) {
            throw new IOException(format("Upgrade to WebSocket failed with response code (%d)", responseCode));
        }

        String responseMessage = _delegate.getResponseMessage();
        if (!responseMessage.equals(HTTP_101_MESSAGE)) {
            throw new IOException(format("Upgrade to WebSocket failed with invalid message '%s'", responseMessage));
        }

        String upgrade = _delegate.getHeaderField(HEADER_UPGRADE);
        if ((upgrade == null) || !upgrade.equalsIgnoreCase("websocket")) {
            throw new IOException("Upgrade failed: Invalid 'Upgrade' header " + upgrade);
        }

        String connection = _delegate.getHeaderField(HEADER_CONNECTION);
        if ((connection == null) || !connection.equalsIgnoreCase("Upgrade")) {
            throw new IOException("Upgrade failed: Invalid 'Connection' header " + connection);
        }

        String accept = _delegate.getHeaderField(HEADER_SEC_ACCEPT);
        validateAccept(accept, websocketKey);

        String protocol = _delegate.getHeaderField(HEADER_SEC_PROTOCOL);
        validateProtocol(protocol, protocols);

        String extensions = _delegate.getHeaderField(HEADER_SEC_EXTENSIONS);
        validateExtensions(extensions, getConnectionImpl().getEnabledExtensions());

        // At this point, the handshake response has been validated and the
        // connection has been upgraded. Transfer the headers to the
        // WsURLConnection object.
        Map<String, List<String>> headerFields = _delegate.getHeaderFields();
        for (Map.Entry<String, List<String>> entry : headerFields.entrySet()) {
            String       headerName = entry.getKey();
            List<String> headerValues = entry.getValue();

            for (String headerValue : headerValues) {
                if (headerName != null) {
                    _connection.addRequestProperty(headerName, headerValue);
                }
            }
        }

        // Set up the negotiated protocol, negotiated extensions, and the
        // response/status code with message.
        getConnectionImpl().setNegotiatedProtocol(protocol);
        getConnectionImpl().setNegotiatedExtensions(extensions);
        getConnectionImpl().setResponseCode(_delegate.getResponseCode());
        getConnectionImpl().setResponseMessage(_delegate.getResponseMessage());
    }

    @Override
    public void disconnect(int code, String reason) throws IOException {
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return _delegate.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return _delegate.getOutputStream();
    }

    @Override
    public InputStream getErrorStream() throws IOException {
        return _delegate.getErrorStream();
    }

    // ------------------------ Private Methods -------------------------------
    private String base64Encode(byte[] bytes) {
        return Base64Util.encode(ByteBuffer.wrap(bytes));
    }

    private String computeHashAndBase64Encode(String key) throws NoSuchAlgorithmException {
        String        input = key + WEBSOCKET_GUID;
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[]        hash = sha1.digest(input.getBytes());

        return Base64Util.encode(ByteBuffer.wrap(hash));
    }


    private String formattedExtension(String                               extensionName,
                                      WebSocketExtensionParameterValuesSpi paramValues) {
        if (extensionName == null) {
            return "";
        }

        WebSocketExtension extension = WebSocketExtension.getWebSocketExtension(extensionName);
        Collection<Parameter<?>> extnParameters = extension.getParameters();
        StringBuffer buffer = new StringBuffer(extension.name());

        // We are using extnParameters to iterate as we want the ordered list
        // of parameters.
        for (Parameter<?> param : extnParameters) {
            if (param.required()) {
                // Required parameter is not enabled/set.
                String s = String.format("Extension '%s': Required parameter "
                        + "'%s' must be set", extension.name(), param.name());
                if ((paramValues == null)
                        || (paramValues.getParameterValue(param) == null)) {
                    throw new IllegalStateException(s);
                }
            }

            if (paramValues == null) {
                // We should continue so that we can throw an exception if
                // any of the required parameters has not been set.
                continue;
            }

            Object value = paramValues.getParameterValue(param);

            if (value == null) {
                // Non-required parameter has not been set. So, let's continue
                // to the next one.
                continue;
            }

            if (param.temporal()) {
                // Temporal/transient parameters, even if they are required,
                // are not put on the wire.
                continue;
            }

            if (param.anonymous()) {
                // If parameter is anonymous, then only it's value is put
                // on the wire.
                buffer.append(";").append(value);
                continue;
            }

            // Otherwise, append the name=value pair.
            buffer.append(";").append(param.name()).append("=").append(value);
        }

        return buffer.toString();
    }

    private WsURLConnectionImpl getConnectionImpl() {
        return (WsURLConnectionImpl) _connection;
    }

    private String getEnabledProtocols() {
        Collection<String> protocols = _connection.getEnabledProtocols();

        if ((protocols == null) || protocols.isEmpty()) {
            return null;
        }

        StringBuffer buffer = new StringBuffer("");

        for (String protocol : protocols) {
            if (buffer.length() > 0) {
                buffer.append(",");
            }

            buffer.append(protocol);
        }

        return buffer.length() > 0 ? buffer.toString() : null;
    }

    private String getEnabledExtensionsWithParamsAsRFC3864FormattedString() {
        // Iterate over enabled extensions.
        StringBuffer extensionsHeader = new StringBuffer("");
        Map<String, WebSocketExtensionFactorySpi> factories = getConnectionImpl().getExtensionFactories();

        for (String extensionName : getConnectionImpl().getEnabledExtensions()) {
            WebSocketExtensionFactorySpi extensionFactory = factories.get(extensionName);
            WebSocketExtensionParameterValuesSpi paramValues = getConnectionImpl().getEnabledParameters().get(extensionName);

            // Get the RFC-3864 formatted string representation of the
            // WebSocketExtension.
            String formatted = formattedExtension(extensionName, paramValues);

            if (formatted.length() > 0) {
                if (extensionsHeader.length() > 0) {
                    // Add the ',' separator between strings representing
                    // different extensions.
                    extensionsHeader.append(",");
                }

                extensionsHeader.append(formatted);
            }
        }

        return extensionsHeader.toString();
    }

    private String getHttpUrl() {
        String scheme = getConnectionImpl().getLocation().getScheme();
        String location = getConnectionImpl().getLocation().toString();
        int    index = location.indexOf(':');
        String newScheme = null;

        if (scheme.equalsIgnoreCase("ws") || scheme.equalsIgnoreCase("wsn")) {
            newScheme = "http";
        }
        else {
            newScheme = "https";
        }

        return newScheme + location.substring(index);
    }

    private byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        Random r = new Random();
        r.nextBytes(bytes);
        return bytes;
    }

    private void validateAccept(String wsAccept, String wsKey) throws IOException {
        if (wsAccept == null) {
            String s = "Upgrade failed: Missing 'Sec-WebSocket-Accept' header in the handshake response";
            throw new IOException(s);
        }

        String hashedBase64EncodedKey = null;

        try {
            hashedBase64EncodedKey = computeHashAndBase64Encode(wsKey);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IOException("Could not compute hash", e);
        }

        if (wsAccept.equals(hashedBase64EncodedKey)) {
            return;
        }

        throw new IOException("SHA-1 hashed and Base64 encoded value of SEC_WEBSOCKET_KEY "
                    + "header in the request does not match the value of SEC_WEBSOCKET_ACCEPT header in the response");
    }

    private void validateExtensions(String             rawNegotiatedExtensionsHeader,
                                    Collection<String> enabledExtensionNames)
        throws IOException {

        if ((rawNegotiatedExtensionsHeader == null) ||
            (rawNegotiatedExtensionsHeader.trim().length() == 0)) {
            return;
        }

        String[] rawExtensions = rawNegotiatedExtensionsHeader.split(",");

        for (String rawExtension : rawExtensions) {
            String[] tokens = rawExtension.split(";");
            String   extnName = tokens[0].trim();

            if (!enabledExtensionNames.contains(extnName)) {
                String s = String.format("Extension '%s' is not an enabled " +
                        "extension so it should not be in the handshake response", extnName);
                throw new IOException(s);
            }
        }
    }

    private void validateProtocol(String protocol, String protocols)
        throws IOException {
        // It is perfectly valid according to RFC-6455 for the negotiated protocol
        // to be null.
        if (protocol != null) {
            if (protocols == null) {
                String s = "Invalid protocol '%s' included in the handshake response";
                throw new IOException(format(s, protocol));
            }

            if (protocols.indexOf(protocol) == -1) {
                String s = "Protocol '%s' in the handshake response is not in the list of protocol(s) "
                        + "'%s' that were in the handshake request";
                throw new IOException(format(s, protocol, protocols));
            }
        }
    }
}
