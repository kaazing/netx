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

package org.kaazing.netx.ws.internal;

import static java.lang.String.format;
import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableMap;
import static java.util.logging.Logger.getLogger;
import static org.kaazing.netx.http.HttpURLConnection.HTTP_SWITCHING_PROTOCOLS;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kaazing.netx.URLConnectionHelper;
import org.kaazing.netx.http.HttpRedirectPolicy;
import org.kaazing.netx.http.HttpURLConnection;
import org.kaazing.netx.http.auth.ChallengeHandler;
import org.kaazing.netx.ws.MessageReader;
import org.kaazing.netx.ws.MessageWriter;
import org.kaazing.netx.ws.WsURLConnection;
import org.kaazing.netx.ws.internal.WebSocketExtension.Parameter;
import org.kaazing.netx.ws.internal.ext.WebSocketExtensionFactory;
import org.kaazing.netx.ws.internal.ext.WebSocketExtensionParameterValues;
import org.kaazing.netx.ws.internal.io.WsInputStreamImpl;
import org.kaazing.netx.ws.internal.io.WsMessageReaderImpl;
import org.kaazing.netx.ws.internal.io.WsMessageWriterImpl;
import org.kaazing.netx.ws.internal.io.WsOutputStreamImpl;
import org.kaazing.netx.ws.internal.io.WsReaderImpl;
import org.kaazing.netx.ws.internal.io.WsWriterImpl;
import org.kaazing.netx.ws.internal.util.Base64Util;

public final class WsURLConnectionImpl extends WsURLConnection {

    private static final Set<Parameter<?>> EMPTY_PARAMETERS = Collections.emptySet();

    private static final Logger LOG = getLogger(WsURLConnection.class.getPackage().getName());

    private static final String HEADER_CONNECTION = "Connection";
    private static final String HEADER_SEC_WEBSOCKET_ACCEPT = "Sec-WebSocket-Accept";
    private static final String HEADER_SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";
    private static final String HEADER_SEC_WEBSOCKET_EXTENSIONS = "Sec-WebSocket-Extensions";
    private static final String HEADER_SEC_WEBSOCKET_KEY = "Sec-WebSocket-Key";
    private static final String HEADER_SEC_WEBSOCKET_VERSION = "Sec-WebSocket-Version";
    private static final String HEADER_UPGRADE = "Upgrade";

    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final Random random;
    private final WebSocketExtensionFactory extensionFactory;
    private final HttpURLConnection connection;
    private final Collection<String> enabledProtocols;
    private final Collection<String> enabledProtocolsRO;
    private final Map<String, WebSocketExtensionParameterValues> enabledExtensions;
    private final Map<String, WebSocketExtensionParameterValues> enabledExtensionsRO;
    private final Map<String, WebSocketExtensionParameterValues> negotiatedExtensions;
    private final Map<String, WebSocketExtensionParameterValues> negotiatedExtensionsRO;

    private ReadyState readyState;
    private String negotiatedProtocol;

    // TODO
    private WsInputStreamImpl                              inputStream;
    private WsOutputStreamImpl                             outputStream;
    private WsReaderImpl                                   reader;
    private WsWriterImpl                                   writer;
    private WsMessageReaderImpl                            messageReader;
    private WsMessageWriterImpl                            messageWriter;

    private static final WebSocketExtensionParameterValues EMPTY_EXTENSION_PARAMETERS = new EmptyExtensionParameterValues();

    enum ReadyState {
        INITIAL, OPEN, CLOSED;
    }

    public WsURLConnectionImpl(
            URLConnectionHelper helper,
            URL location,
            URI httpLocation,
            Random random,
            WebSocketExtensionFactory extensionFactory) throws IOException {

        super(location);

        this.random = random;
        this.readyState = ReadyState.INITIAL;
        this.extensionFactory = extensionFactory;
        this.enabledProtocols = new LinkedList<String>();
        this.enabledProtocolsRO = unmodifiableCollection(enabledProtocols);
        this.enabledExtensions = new LinkedHashMap<String, WebSocketExtensionParameterValues>();
        this.enabledExtensionsRO = unmodifiableMap(enabledExtensions);
        this.negotiatedExtensions = new LinkedHashMap<String, WebSocketExtensionParameterValues>();
        this.negotiatedExtensionsRO = unmodifiableMap(negotiatedExtensions);
        this.connection = openHttpConnection(helper, httpLocation);
    }

    public WsURLConnectionImpl(
            URLConnectionHelper helper,
            URI location,
            URI httpLocation,
            Random random,
            WebSocketExtensionFactory extensionFactory) throws IOException {

        super(null);

        this.random = random;
        this.readyState = ReadyState.INITIAL;
        this.extensionFactory = extensionFactory;
        this.enabledProtocols = new LinkedList<String>();
        this.enabledProtocolsRO = unmodifiableCollection(enabledProtocols);
        this.enabledExtensions = new LinkedHashMap<String, WebSocketExtensionParameterValues>();
        this.enabledExtensionsRO = unmodifiableMap(enabledExtensions);
        this.negotiatedExtensions = new LinkedHashMap<String, WebSocketExtensionParameterValues>();
        this.negotiatedExtensionsRO = unmodifiableMap(negotiatedExtensions);
        this.connection = openHttpConnection(helper, httpLocation);
    }

    // -------------------------- WsURLConnection Methods -------------------
    @Override
    public void close() throws IOException {
        close(0, null);
    }

    @Override
    public void close(int code) throws IOException {
        close(code, null);
    }

    @Override
    public void close(int code, String reason) throws IOException {
        if (code != 0) {
            //verify code and reason against RFC 6455
            //if code is present, it must equal to 1000 or in range 3000 to 4999
            if (code != 1000 && (code < 3000 || code > 4999)) {
                    throw new IllegalArgumentException("code must equal to 1000 or in range 3000 to 4999");
            }

            //if reason is present, it must not be longer than 123 bytes
            if (reason != null && reason.length() > 0) {
                //convert reason to UTF8 string
                try {
                    byte[] reasonBytes = reason.getBytes("UTF8");
                    if (reasonBytes.length > 123) {
                        throw new IllegalArgumentException("Reason is longer than 123 bytes");
                    }
                    reason = new String(reasonBytes, "UTF8");

                } catch (UnsupportedEncodingException e) {
                    LOG.log(Level.FINEST, e.getMessage(), e);
                    throw new IllegalArgumentException("Reason must be encodable to UTF8");
                }
            }
        }

        // ### TODO: Send the CLOSE frame out.
    }

    @Override
    public void connect() throws IOException {

        switch (readyState) {
        case INITIAL:
            doConnect();
            break;
        default:
            throw new IOException("Already connected");
        }
    }

    @Override
    public ChallengeHandler getChallengeHandler() {
        return connection.getChallengeHandler();
    }

    @Override
    public int getConnectTimeout() {
        return connection.getConnectTimeout();
    }

    /**
     * Gets the names of all the extensions that have been enabled for this
     * connection. The enabled extensions are negotiated between the client
     * and the server during the handshake. The names of the negotiated
     * extensions can be obtained using {@link #getNegotiatedExtensions()} API.
     * An empty Collection is returned if no extensions have been enabled for
     * this connection. The enabled extensions will be a subset of the
     * supported extensions.
     *
     * @return Collection<String>     names of the enabled extensions for this
     *                                connection
     */
    public Collection<String> getEnabledExtensions() {
        return enabledExtensionsRO.keySet();
    }

    /**
     * Gets the value of the specified {@link Parameter} defined in an enabled
     * extension. If the parameter is not defined for this connection but a
     * default value for the parameter is set using the method
     * {@link WebSocketFactory#setDefaultParameter(Parameter, Object)},
     * then the default value is returned.
     * <p>
     * Setting the parameter value when the connection is successfully
     * established will result in an IllegalStateException.
     * </p>
     * @param <T>          Generic type of the value of the Parameter
     * @param parameter    Parameter whose value needs to be set
     * @return the value of the specified parameter
     * @throw IllegalStateException   if this method is invoked after connect()
     */
    public <T> T getEnabledParameter(Parameter<T> parameter) {
        WebSocketExtension extension = parameter.extension();
        WebSocketExtensionParameterValues enabledParameterValues = enabledExtensions.get(extension.name());
        return (enabledParameterValues != null) ? enabledParameterValues.getParameterValue(parameter) : null;
    }

    @Override
    public Collection<String> getEnabledProtocols() {
        return enabledProtocolsRO;
    }

    @Override
    public HttpRedirectPolicy getRedirectPolicy() {
        return connection.getRedirectPolicy();
    }

    @Override
    public InputStream getInputStream() throws IOException {

        if (inputStream == null) {
            ensureConnected();
            inputStream = new WsInputStreamImpl(connection.getInputStream());
        }

        return inputStream;
    }

    @Override
    public MessageReader getMessageReader() throws IOException {
        if (messageReader == null) {
            // TODO: trigger lazy connect, same as HTTP
            throw new UnsupportedOperationException();
        }

        return messageReader;
    }

    @Override
    public MessageWriter getMessageWriter() throws IOException {

        if (messageWriter == null) {
            // TODO: trigger lazy connect, same as HTTP
            throw new UnsupportedOperationException();
        }

        return messageWriter;
    }

    /**
     * Gets names of all the enabled extensions that have been successfully
     * negotiated between the client and the server during the initial
     * handshake.
     * <p>
     * Returns an empty Collection if no extensions were negotiated between the
     * client and the server. The negotiated extensions will be a subset of the
     * enabled extensions.
     * <p>
     * If this method is invoked before a connection is successfully established,
     * an IllegalStateException is thrown.
     *
     * @return Collection<String>      successfully negotiated using this
     *                                 connection
     * @throws IOException
     */
    public Collection<String> getNegotiatedExtensions() throws IOException {
        ensureConnected();
        return negotiatedExtensionsRO.keySet();
    }

    /**
     * Returns the value of the specified {@link Parameter} of a negotiated
     * extension.
     * <p>
     * If this method is invoked before the connection is successfully
     * established, an IllegalStateException is thrown.
     *
     * @param <T>          parameter type
     * @param parameter    parameter of a negotiated extension
     * @return T           value of the specified parameter
     * @throws IOException
     */
    public <T> T getNegotiatedParameter(Parameter<T> parameter) throws IOException {

        ensureConnected();

        WebSocketExtension extension = parameter.extension();
        String extensionName = extension.name();
        WebSocketExtensionParameterValues paramValues = negotiatedExtensions.get(extensionName);
        return (paramValues != null) ? paramValues.getParameterValue(parameter) : null;
    }

    @Override
    public String getNegotiatedProtocol() throws IOException {
        ensureConnected();
        return negotiatedProtocol;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        if (outputStream == null) {
            ensureConnected();
            outputStream = new WsOutputStreamImpl(connection.getOutputStream(), random);
        }

        return outputStream;
    }

    @Override
    public Reader getReader() throws IOException {
        if (reader == null) {
            // TODO: trigger lazy connect, same as HTTP
            ensureConnected();
            reader = new WsReaderImpl(connection.getInputStream());
        }

        return reader;
    }

    /**
     * Returns the names of extensions that have been discovered for this
     * connection. An empty Collection is returned if no extensions were
     * discovered for this connection.
     *
     * @return Collection<String>    extension names discovered for this
     *                               connection
     */
    public Collection<String> getSupportedExtensions() {
        return extensionFactory.getExtensionNames();
    }

    @Override
    public Writer getWriter() throws IOException {

        if (writer == null) {
            // TODO: trigger lazy connect, same as HTTP
            ensureConnected();
            writer = new WsWriterImpl(connection.getOutputStream(), random);
        }

        return writer;
    }

    @Override
    public void setChallengeHandler(ChallengeHandler challengeHandler) {
        connection.setChallengeHandler(challengeHandler);
    }

    @Override
    public void setConnectTimeout(int timeout) {
        connection.setConnectTimeout(timeout);
    }

    @Override
    public void setEnabledProtocols(Collection<String> enabledProtocols) {
        switch (readyState) {
        case INITIAL:
            break;
        default:
            throw new IllegalStateException("Already connected");
        }

        this.enabledProtocols.clear();
        this.enabledProtocols.addAll(enabledProtocols);
    }

    @Override
    public void setRedirectPolicy(HttpRedirectPolicy redirectPolicy) {
        connection.setRedirectPolicy(redirectPolicy);
    }

    // ---------------------- URLConnection Methods ----------------------
    @Override
    public void addRequestProperty(String key, String value) {
        // ignore
    }

    @Override
    public int getReadTimeout() {
        return connection.getReadTimeout();
    }

    @Override
    public void setReadTimeout(int timeout) {
        connection.setReadTimeout(timeout);
    }

    @Override
    public void setDoInput(boolean doInput) {
        if (!doInput) {
            throw new UnsupportedOperationException("WebSocket is bidirectional");
        }
    }

    @Override
    public void setDoOutput(boolean doOutput) {
        if (!doOutput) {
            throw new UnsupportedOperationException("WebSocket is bidirectional");
        }
    }

    @Override
    public boolean getAllowUserInteraction() {
        return connection.getAllowUserInteraction();
    }

    @Override
    public void setAllowUserInteraction(boolean allowuserinteraction) {
        connection.setAllowUserInteraction(allowuserinteraction);
    }

    @Override
    public boolean getUseCaches() {
        return connection.getUseCaches();
    }

    @Override
    public void setUseCaches(boolean usecaches) {
        connection.setUseCaches(usecaches);
    }

    @Override
    public boolean getDefaultUseCaches() {
        return connection.getDefaultUseCaches();
    }

    @Override
    public void setDefaultUseCaches(boolean defaultusecaches) {
        connection.setDefaultUseCaches(defaultusecaches);
    }

    @Override
    public void setIfModifiedSince(long ifmodifiedsince) {
        // ignore
    }

    @Override
    public void setRequestProperty(String key, String value) {
        // ignore
    }

    // ---------------------- Public APIs used internally --------------------

    public void addEnabledExtension(String name) {
        addEnabledExtension(name, EMPTY_EXTENSION_PARAMETERS);
    }

    public void addEnabledExtension(String name, WebSocketExtensionParameterValues parameters) {
        Collection<String> supportedExtensions = getSupportedExtensions();
        if (!supportedExtensions.contains(name)) {
            throw new IllegalStateException("Unsupported extension: " + name);
        }
        enabledExtensions.put(name, parameters);
    }

    public void setEnabledExtensions(
            Map<String, WebSocketExtensionParameterValues> enabledExtensions) {
        this.enabledExtensions.clear();
        this.enabledExtensions.putAll(enabledExtensions);
    }

    private void ensureConnected() throws IOException {

        switch (readyState) {
        case INITIAL:
            doConnect();
            break;
        case OPEN:
        case CLOSED:
            break;
        }
    }

    private void doConnect() throws IOException {

        String websocketKey = base64Encode(randomBytes(16));

        connection.setRequestMethod("GET");
        connection.setRequestProperty(HEADER_UPGRADE, "websocket");
        connection.setRequestProperty(HEADER_CONNECTION, "Upgrade");
        connection.setRequestProperty(HEADER_SEC_WEBSOCKET_KEY, websocketKey);
        connection.setRequestProperty(HEADER_SEC_WEBSOCKET_VERSION, "13");

        if (!enabledExtensions.isEmpty()) {
            String formattedExtensions = formatAsRFC3864(enabledExtensions);
            connection.setRequestProperty(HEADER_SEC_WEBSOCKET_EXTENSIONS, formattedExtensions);
        }

        if (!enabledProtocols.isEmpty()) {
            String formattedProtocols = formatAsRFC3864(enabledProtocols);
            connection.setRequestProperty(HEADER_SEC_WEBSOCKET_PROTOCOL, formattedProtocols);
        }

        if (HTTP_SWITCHING_PROTOCOLS != connection.getResponseCode() ||
            !"websocket".equalsIgnoreCase(connection.getHeaderField(HEADER_UPGRADE)) ||
            !"Upgrade".equalsIgnoreCase(connection.getHeaderField(HEADER_CONNECTION)) ||
            !validateAccept(websocketKey, connection.getHeaderField(HEADER_SEC_WEBSOCKET_ACCEPT))) {

            throw new IOException("Connection failed");
        }

        negotiateProtocol(enabledProtocols, connection.getHeaderField(HEADER_SEC_WEBSOCKET_PROTOCOL));
        negotiateExtensions(enabledExtensions, connection.getHeaderField(HEADER_SEC_WEBSOCKET_EXTENSIONS));

        this.readyState = ReadyState.OPEN;
    }

    private static HttpURLConnection openHttpConnection(URLConnectionHelper helper, URI httpLocation) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) helper.openConnection(httpLocation);
        connection.setDoOutput(true);
        return connection;
    }

    private byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        random.nextBytes(bytes);
        return bytes;
    }

    private static String base64Encode(byte[] bytes) {
        return Base64Util.encode(ByteBuffer.wrap(bytes));
    }

    private static boolean validateAccept(String websocketKey, String websocketHash) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            String input = websocketKey + WEBSOCKET_GUID;
            byte[] hash = sha1.digest(input.getBytes());

            String computedHash = Base64Util.encode(ByteBuffer.wrap(hash));
            return computedHash.equals(websocketHash);
        }
        catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    private void negotiateProtocol(Collection<String> enabledProtocols, String negotiatedProtocol) throws IOException {
        if (negotiatedProtocol != null && !enabledProtocols.contains(negotiatedProtocol)) {
            throw new IOException(format("Connection failed. Negotiated protocol \"%s\" was not enabled", negotiatedProtocol));
        }
        this.negotiatedProtocol = negotiatedProtocol;
    }

    private void negotiateExtensions(
            Map<String, WebSocketExtensionParameterValues> enabledExtensions,
            String formattedExtensions) {

        // TODO
        // 1. parse formatted extensions (names only)
        // 2. verify negotiated extension names are all enabled
        // 3. parse parameters for each negotiated extension

//        if ((rawNegotiatedExtensionsHeader == null) ||
//            (rawNegotiatedExtensionsHeader.trim().length() == 0)) {
//            return;
//        }
//
//        String[] rawExtensions = rawNegotiatedExtensionsHeader.split(",");
//
//        for (String rawExtension : rawExtensions) {
//            String[] tokens = rawExtension.split(";");
//            String   extnName = tokens[0].trim();
//
//            if (!enabledExtensionNames.contains(extnName)) {
//                String s = String.format("Extension '%s' is not an enabled " +
//                        "extension so it should not be in the handshake response", extnName);
//                throw new IOException(s);
//            }
//        }
//
//        if ((formattedExtensions == null) ||
//            (formattedExtensions.trim().length() == 0)) {
//            this.negotiatedExtensions = null;
//            return;
//        }
//
//        String[]     extns = formattedExtensions.split(",");
//        List<String> extnNames = new ArrayList<String>();
//
//        for (String extn : extns) {
//            String[]    properties = extn.split(";");
//            String      extnName = properties[0].trim();
//
//            if (!getEnabledExtensions().contains(extnName)) {
//                String s = String.format("Extension '%s' is not an enabled " +
//                        "extension so it should not have been negotiated", extnName);
////                    setException(new WebSocketException(s));
//                return;
//            }
//
//            WebSocketExtension extension =
//                            WebSocketExtension.getWebSocketExtension(extnName);
//            WsExtensionParameterValuesImpl paramValues =
//                           this.negotiatedParameters.get(extnName);
//            Collection<Parameter<?>> anonymousParams =
//                           extension.getParameters(Metadata.ANONYMOUS);
//
//            // Start from the second(0-based) property to parse the name-value
//            // pairs as the first(or 0th) is the extension name.
//            for (int i = 1; i < properties.length; i++) {
//                String       property = properties[i].trim();
//                String[]     pair = property.split("=");
//                Parameter<?> parameter = null;
//                String       paramValue = null;
//
//                if (pair.length == 1) {
//                    // We are dealing with an anonymous parameter. Since the
//                    // Collection is actually an ArrayList, we are guaranteed to
//                    // iterate the parameters in the definition/creation order.
//                    // As there is no parameter name, we will just get the next
//                    // anonymous Parameter instance and use it for setting the
//                    // value. The onus is on the extension implementor to either
//                    // use only named parameters or ensure that the anonymous
//                    // parameters are defined in the order in which the server
//                    // will send them back during negotiation.
//                    parameter = anonymousParams.iterator().next();
//                    paramValue = pair[0].trim();
//                }
//                else {
//                    parameter = extension.getParameter(pair[0].trim());
//                    paramValue = pair[1].trim();
//                }
//
//                if (parameter.type() != String.class) {
//                    String paramName = parameter.name();
//                    String s = String.format("Negotiated Extension '%s': " +
//                                             "Type of parameter '%s' should be String",
//                                             extnName, paramName);
////                        setException(new WebSocketException(s));
//                    return;
//                }
//
//                if (paramValues == null) {
//                    paramValues = new WsExtensionParameterValuesImpl();
//                    this.negotiatedParameters.put(extnName, paramValues);
//                }
//
//                paramValues.setParameterValue(parameter, paramValue);
//            }
//            extnNames.add(extnName);
//        }
//
//        HashSet<String> extnsSet = new HashSet<String>(extnNames);
//        this.negotiatedExtensions = unmodifiableCollection(extnsSet);

        // ### TODO: Add the extension handlers for the negotiated extensions
        //           to the pipeline.
    }

    private String formatAsRFC3864(Collection<String> protocols) {

        assert protocols != null;

        StringBuilder sb = new StringBuilder();

        for (String protocol : protocols) {
            if (sb.length() > 0) {
                sb.append(", ");
            }

            sb.append(protocol);
        }

        return sb.toString();
    }

    private static String formatAsRFC3864(Map<String, WebSocketExtensionParameterValues> enabledExtensions) {
        // TODO Auto-generated method stub
        return null;
    }

    private static final class EmptyExtensionParameterValues extends WebSocketExtensionParameterValues {
        @Override
        public Collection<Parameter<?>> getParameters() {
            return EMPTY_PARAMETERS;
        }

        @Override
        public <T> T getParameterValue(Parameter<T> parameter) {
            return null;
        }
    }

}
