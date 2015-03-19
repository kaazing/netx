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

import static java.lang.Character.charCount;
import static java.lang.Character.toChars;
import static java.lang.String.format;
import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableList;
import static org.kaazing.netx.http.HttpURLConnection.HTTP_SWITCHING_PROTOCOLS;
import static org.kaazing.netx.ws.internal.WebSocketState.CLOSED;
import static org.kaazing.netx.ws.internal.WebSocketState.OPEN;
import static org.kaazing.netx.ws.internal.util.Utf8Util.initialDecodeUTF8;
import static org.kaazing.netx.ws.internal.util.Utf8Util.remainingBytesUTF8;
import static org.kaazing.netx.ws.internal.util.Utf8Util.remainingDecodeUTF8;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.kaazing.netx.URLConnectionHelper;
import org.kaazing.netx.http.HttpRedirectPolicy;
import org.kaazing.netx.http.HttpURLConnection;
import org.kaazing.netx.http.auth.ChallengeHandler;
import org.kaazing.netx.ws.WebSocketExtension;
import org.kaazing.netx.ws.WsURLConnection;
import org.kaazing.netx.ws.internal.ext.WebSocketContext;
import org.kaazing.netx.ws.internal.ext.WebSocketExtensionSpi;
import org.kaazing.netx.ws.internal.ext.frame.Close;
import org.kaazing.netx.ws.internal.ext.frame.Control;
import org.kaazing.netx.ws.internal.ext.frame.Data;
import org.kaazing.netx.ws.internal.ext.frame.Frame.Payload;
import org.kaazing.netx.ws.internal.ext.frame.OpCode;
import org.kaazing.netx.ws.internal.ext.frame.Ping;
import org.kaazing.netx.ws.internal.ext.frame.Pong;
import org.kaazing.netx.ws.internal.io.WsInputStream;
import org.kaazing.netx.ws.internal.io.WsMessageReader;
import org.kaazing.netx.ws.internal.io.WsMessageWriter;
import org.kaazing.netx.ws.internal.io.WsOutputStream;
import org.kaazing.netx.ws.internal.io.WsReader;
import org.kaazing.netx.ws.internal.io.WsWriter;
import org.kaazing.netx.ws.internal.util.Base64Util;
import org.kaazing.netx.ws.internal.util.FrameUtil;

public final class WsURLConnectionImpl extends WsURLConnection {
    private static final String MSG_END_OF_STREAM = "End of stream";
    private static final String MSG_UNRECOGNIZED_OPCODE = "Protocol Violation: Unrecognized opcode %d";

    private static final Charset UTF_8 = Charset.forName("UTF-8");

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
    private final List<String> enabledExtensions;
    private final List<String> enabledExtensionsRO;
    private final List<String> negotiatedExtensions;
    private final List<String> negotiatedExtensionsRO;
    private final List<WebSocketExtensionSpi> negotiatedExtensionSpis;

    private String negotiatedProtocol;
    private WsInputStream inputStream;
    private WsOutputStream outputStream;
    private WsReader reader;
    private WsWriter writer;
    private WsMessageReader messageReader;
    private WsMessageWriter messageWriter;

    private WebSocketState inputState;
    private WebSocketState outputState;
    private int codePoint;
    private int remainingBytes;
    private WebSocketInputStateMachine inputStateMachine;
    private WebSocketOutputStateMachine outputStateMachine;

    public WsURLConnectionImpl(
            URLConnectionHelper helper,
            URL location,
            URI httpLocation,
            Random random,
            WebSocketExtensionFactory extensionFactory) throws IOException {

        super(location);

        this.random = random;
        this.inputState = WebSocketState.START;
        this.outputState = WebSocketState.START;
        this.extensionFactory = extensionFactory;
        this.enabledProtocols = new LinkedList<String>();
        this.enabledProtocolsRO = unmodifiableCollection(enabledProtocols);
        this.enabledExtensions = new ArrayList<String>();
        this.enabledExtensionsRO = unmodifiableList(enabledExtensions);
        this.negotiatedExtensions = new ArrayList<String>();
        this.negotiatedExtensionsRO = unmodifiableList(negotiatedExtensions);
        this.negotiatedExtensionSpis = new ArrayList<WebSocketExtensionSpi>();
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
        this.inputState = WebSocketState.START;
        this.extensionFactory = extensionFactory;
        this.enabledProtocols = new LinkedList<String>();
        this.enabledProtocolsRO = unmodifiableCollection(enabledProtocols);
        this.enabledExtensions = new ArrayList<String>();
        this.enabledExtensionsRO = unmodifiableList(enabledExtensions);
        this.negotiatedExtensions = new ArrayList<String>();
        this.negotiatedExtensionsRO = unmodifiableList(negotiatedExtensions);
        this.negotiatedExtensionSpis = new ArrayList<WebSocketExtensionSpi>();
        this.connection = openHttpConnection(helper, httpLocation);
    }

    // -------------------------- WsURLConnection Methods -------------------
    @Override
    public void addEnabledExtension(WebSocketExtension extension) {
    }

    @Override
    public void addEnabledExtensions(String... extensions) {
        switch (inputState) {
        case START:
            break;
        default:
            throw new IllegalStateException("Already connected");
        }

        this.enabledExtensions.clear();
        this.enabledExtensions.addAll(Arrays.asList(extensions));
    }


    @Override
    public void close() throws IOException {
        close(0, null); // ### TODO: Should this be 1005(WS_MISSING_STATUS) instead of 0?
    }

    @Override
    public void close(int code) throws IOException {
        close(code, null);
    }

    @Override
    public void close(int code, String reason) throws IOException {
        byte[] reasonBytes = null;

        if (code != 0) {
            // Verify code and reason against RFC 6455.
            // If code is present, it must equal to 1000 or in range 3000 to 4999
            if (code != 1000 && (code < 3000 || code > 4999)) {
                    throw new IOException("code must equal to 1000 or in range 3000 to 4999");
            }

            if (reason != null && reason.length() > 0) {
                reasonBytes = reason.getBytes(UTF_8);
            }
        }

        sendClose(code, reasonBytes);

        if ((reasonBytes != null) && (reasonBytes.length > 123)) {
            throw new IOException("Protocol Violation: Reason is longer than 123 bytes");
        }
    }

    @Override
    public void connect() throws IOException {

        switch (inputState) {
        case START:
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

    @Override
    public Collection<String> getEnabledExtensions() {
        return enabledExtensionsRO;
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
    public WsInputStream getInputStream() throws IOException {
        if (inputStream == null) {
            ensureConnected();
            inputStream = new WsInputStream(this);
        }

        return inputStream;
    }

    @Override
    public WsMessageReader getMessageReader() throws IOException {
        if (messageReader == null) {
            // TODO: trigger lazy connect, same as HTTP
            ensureConnected();
            messageReader = new WsMessageReader(this);
        }

        return messageReader;
    }

    @Override
    public WsMessageWriter getMessageWriter() throws IOException {
        if (messageWriter == null) {
            ensureConnected();
            messageWriter = new WsMessageWriter(this);
        }

        return messageWriter;
    }

    @Override
    public Collection<String> getNegotiatedExtensions() throws IOException {
        ensureConnected();
        return negotiatedExtensionsRO;
    }

    @Override
    public String getNegotiatedProtocol() throws IOException {
        ensureConnected();
        return negotiatedProtocol;
    }

    @Override
    public WsOutputStream getOutputStream() throws IOException {
        if (outputStream == null) {
            ensureConnected();
            outputStream = new WsOutputStream(this);
        }

        return outputStream;
    }

    @Override
    public WsReader getReader() throws IOException {
        if (reader == null) {
            // TODO: trigger lazy connect, same as HTTP
            ensureConnected();
            reader = new WsReader(this);
        }

        return reader;
    }

    @Override
    public Collection<String> getSupportedExtensions() {
        return extensionFactory.getExtensionNames();
    }

    @Override
    public Writer getWriter() throws IOException {
        if (writer == null) {
            // TODO: trigger lazy connect, same as HTTP
            ensureConnected();
            writer = new WsWriter(this);
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
    public void setEnabledProtocols(String... enabledProtocols) {
        switch (inputState) {
        case START:
            break;
        default:
            throw new IllegalStateException("Already connected");
        }

        this.enabledProtocols.clear();
        this.enabledProtocols.addAll(Arrays.asList(enabledProtocols));
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

    public void doFail(int code, String exceptionMessage) throws IOException {
        sendClose(code, null, 0, 0);
        throw new IOException(exceptionMessage);
    }

    public WebSocketContext getContext(WebSocketExtensionSpi sentinel, boolean reverse) {
        List<WebSocketExtensionSpi> extensions = new ArrayList<WebSocketExtensionSpi>(this.negotiatedExtensionSpis);
        if (reverse) {
            Collections.reverse(extensions);
        }
        extensions.add(sentinel);
        return new WebSocketContext(this, unmodifiableList(extensions));
    }

    public Random getRandom() {
        return random;
    }

    public WebSocketInputStateMachine getInputStateMachine() throws IOException {
        return inputStateMachine;
    }

    public WebSocketOutputStateMachine getOutputStateMachine() throws IOException {
        return outputStateMachine;
    }

    public InputStream getTcpInputStream() throws IOException {
        return connection.getInputStream();
    }

    public OutputStream getTcpOutputStream() throws IOException {
        return connection.getOutputStream();
    }

    public WebSocketState getInputState() {
        return inputState;
    }

    public WebSocketState getOutputState() {
        return outputState;
    }

    public void processReadBinaryFrame(final Data dataFrame) throws IOException {
        if (dataFrame == null) {
            throw new NullPointerException("Null frame passed in");
        }

        InputStream in = connection.getInputStream();
        int bytesRead = 0;
        int bytesRemaining = dataFrame.getLength();
        Payload payload = dataFrame.getPayload();
        int bytesOffset = payload.offset();

        // Read the entire payload from the current frame into the buffer.
        int size = payload.limit();
        while (bytesRemaining > 0) {
            assert bytesOffset + bytesRemaining <= size;

            bytesRead = in.read(payload.buffer().array(), bytesOffset, bytesRemaining);
            if (bytesRead == -1) {
                doFail(WS_PROTOCOL_ERROR, MSG_END_OF_STREAM);
            }

            bytesOffset += bytesRead;
            bytesRemaining -= bytesRead;
        }

        inputStateMachine.processBinaryFrame(this, dataFrame);
    }

    public void processReadControlFrame(final Control controlFrame) throws IOException {
        if (controlFrame == null) {
            throw new NullPointerException("Null buffer passed in");
        }

        int payloadLength = controlFrame.getLength();

        readControlFramePayload(controlFrame, payloadLength);

        OpCode opCode = controlFrame.getOpCode();
        switch (opCode) {
        case CLOSE:
            inputStateMachine.processCloseFrame(this, (Close) controlFrame);
            break;

        case PING:
            inputStateMachine.processPingFrame(this, (Ping) controlFrame);
            break;

        case PONG:
            inputStateMachine.processPongFrame(this, (Pong) controlFrame);
            break;

        default:
            doFail(WS_PROTOCOL_ERROR, format(MSG_UNRECOGNIZED_OPCODE, opCode));
        }
    }

    public void processReadTextFrame(final Data dataFrame) throws IOException {
        if (dataFrame == null) {
            throw new NullPointerException("Null buffer passed in");
        }

        int payloadLength = dataFrame.getLength();
        if (payloadLength == 0) {
            inputStateMachine.processTextFrame(this, dataFrame);
            return;
        }

        InputStream in = connection.getInputStream();
        int offset = 0;
        int payloadOffset = 0;
        char[] cbuf = new char[payloadLength];

        outer:
        for (;;) {
            int b = -1;

            // code point may be split across frames
            while (codePoint != 0 || (payloadOffset < payloadLength && remainingBytes > 0)) {
                // surrogate pair
                if (codePoint != 0 && remainingBytes == 0) {
                    int charCount = charCount(codePoint);
                    if (offset + charCount > cbuf.length) {
                        break outer;
                    }
                    toChars(codePoint, cbuf, offset);
                    offset += charCount;
                    codePoint = 0;
                    break;
                }

                // detect EOP
                if (payloadOffset == payloadLength) {
                    break;
                }

                // detect EOF
                b = in.read();
                if (b == -1) {
                    break outer;
                }
                payloadOffset++;

                // character encoded in multiple bytes
                codePoint = remainingDecodeUTF8(codePoint, remainingBytes--, b);
            }

            // detect EOP
            if (payloadOffset == payloadLength) {
                break;
            }

            // detect EOF
            b = in.read();
            if (b == -1) {
                break;
            }
            payloadOffset++;

            // detect character encoded in multiple bytes
            remainingBytes = remainingBytesUTF8(b);
            switch (remainingBytes) {
            case 0:
                // no surrogate pair
                int asciiCodePoint = initialDecodeUTF8(remainingBytes, b);
                assert charCount(asciiCodePoint) == 1;
                toChars(asciiCodePoint, cbuf, offset++);
                break;
            default:
                codePoint = initialDecodeUTF8(remainingBytes, b);
                break;
            }
        }

        // Unlike WsReader, WsMessageReader has to ensure that the entire payload has been read.
        if (payloadOffset < payloadLength) {
            doFail(WS_NORMAL_CLOSE, MSG_END_OF_STREAM);
        }

        byte[] bytes = String.valueOf(cbuf, 0, offset).getBytes(UTF_8);
        FrameUtil.encode(dataFrame.buffer(), dataFrame.offset(), dataFrame.getOpCode(), dataFrame.isFin(), false, null, bytes);

        inputStateMachine.processTextFrame(this, dataFrame);
    }

    public void sendClose(int code, byte[] reason) throws IOException {
        getOutputStream().writeClose(code, reason, 0, reason == null ? 0 : reason.length);
        disconnect();
        inputState = CLOSED;
        outputState = CLOSED;
    }

    public void sendClose(int code, byte[] reason, int offset, int length) throws IOException {
        getOutputStream().writeClose(code, reason, offset, length);
        disconnect();
        inputState = CLOSED;
        outputState = CLOSED;
    }

    public void sendPong(byte[] buffer, int offset, int length) throws IOException {
        getOutputStream().writePong(buffer, offset, length);
    }

    public void setInputState(WebSocketState state) {
        this.inputState = state;
    }

    public void setOutputState(WebSocketState state) {
        this.outputState = state;
    }


    ///////////////////////////////////////////////////////////////////////////

    private void ensureConnected() throws IOException {
        switch (inputState) {
        case START:
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

        this.inputState = OPEN;
        this.outputState = OPEN;
    }

    private void disconnect() {
        try {
            if (outputStream != null) {
                outputStream.close();
            }
            if (writer != null) {
                writer.close();
            }
            if (messageReader != null) {
                messageReader.close();
            }
            if (inputStream != null) {
                inputStream.close();
            }
            if (reader != null) {
                reader.close();
            }
            if (messageWriter != null) {
                messageWriter.close();
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
        catch (IOException e) {
            // ignore
        }
    }

    private void readControlFramePayload(Control controlFrame, long payloadLength) throws IOException {
        InputStream in = connection.getInputStream();
        int offset = controlFrame.getDataOffset();
        int length = (int) payloadLength;

        while (length > 0) {
            int bytesRead = in.read(controlFrame.buffer().array(), offset, length);

            if (bytesRead == -1) {
                doFail(WS_PROTOCOL_ERROR, MSG_END_OF_STREAM);
            }

            offset += bytesRead;
            length -= bytesRead;
        }
    }

    private byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        random.nextBytes(bytes);
        return bytes;
    }

    private void negotiateProtocol(Collection<String> enabledProtocols, String negotiatedProtocol) throws IOException {
        if (negotiatedProtocol != null && !enabledProtocols.contains(negotiatedProtocol)) {
            throw new IOException(format("Connection failed. Negotiated protocol \"%s\" was not enabled", negotiatedProtocol));
        }
        this.negotiatedProtocol = negotiatedProtocol;
    }

    private void negotiateExtensions(List<String> enabledExtensions, String formattedExtensions) throws IOException {

        if ((formattedExtensions == null) || (formattedExtensions.trim().length() == 0)) {
            inputStateMachine = new WebSocketInputStateMachine();
            outputStateMachine = new WebSocketOutputStateMachine();
            this.negotiatedExtensions.clear();
            this.negotiatedExtensionSpis.clear();
            return;
        }

        String[] extensions = formattedExtensions.split(",");

        for (String extension : extensions) {
            String[] properties = extension.split(";");
            String extnName = properties[0].trim();

            if (!enabledExtensions.contains(extnName)) {
                // Only enabled extensions should be negotiated.
                String s = format("Invalid extension '%s' negotiated", extnName);
                throw new IOException(s);
            }

            this.negotiatedExtensions.add(extnName);

            Map<String, String> paramValues = new LinkedHashMap<String, String>();
            for (int i = 1; i < properties.length; i++) {
                String property = properties[0];
                String[] propertyTokens = property.split("=");

                if (propertyTokens.length == 1) {
                    paramValues.put(propertyTokens[0], "");
                }
                else {
                    paramValues.put(propertyTokens[0], propertyTokens[1]);
                }
            }

            WebSocketExtensionSpi extensionSpi = extensionFactory.createExtension(extnName, paramValues);
            if (extensionSpi != null) {
                this.negotiatedExtensionSpis.add(extensionSpi);
            }
        }

        inputStateMachine = new WebSocketInputStateMachine();
        outputStateMachine = new WebSocketOutputStateMachine();
    }

    private static String base64Encode(byte[] bytes) {
        return Base64Util.encode(ByteBuffer.wrap(bytes));
    }

    private static String formatAsRFC3864(Collection<String> protocols) {
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

//    private static String formatAsRFC3864(Map<String, WebSocketExtensionParameterValues> enabledExtensions) {
//        if ((enabledExtensions == null) || enabledExtensions.isEmpty()) {
//            return null;
//        }
//
//        StringBuilder sb = new StringBuilder();
//        for (Map.Entry<String, WebSocketExtensionParameterValues> entry : enabledExtensions.entrySet()) {
//            String extensionName = entry.getKey();
//            WebSocketExtensionParameterValues paramValues = entry.getValue();
//            String extension = formattedExtension(extensionName, paramValues);
//
//            if (sb.length() != 0) {
//                sb.append(", ");
//            }
//            sb.append(extension);
//        }
//
//        return sb.toString();
//    }

//    private static String formattedExtension(String extensionName, WebSocketExtensionParameterValues paramValues) {
//        assert extensionName != null;
//
//        WebSocketExtension extension = WebSocketExtension.getWebSocketExtension(extensionName);
//        Collection<Parameter<?>> parameters = extension.getParameters();
//        StringBuffer buffer = new StringBuffer(extension.name());
//
//        // Iterate over ordered list of parameters to create the formatted string.
//        for (Parameter<?> param : parameters) {
//            if (param.required()) {
//                // Required parameter is not enabled/set.
//                String s = format("Extension '%s': Required parameter '%s' must be set", extension.name(), param.name());
//                if ((paramValues == null) || (paramValues.getParameterValue(param) == null)) {
//                    throw new IllegalStateException(s);
//                }
//            }
//
//            if (paramValues == null) {
//                // We should continue so that we can throw an exception if any of the required parameters has not been set.
//                continue;
//            }
//
//            Object value = paramValues.getParameterValue(param);
//
//            if (value == null) {
//                // Non-required parameter has not been set. So, let's continue to the next one.
//                continue;
//            }
//
//            if (param.temporal()) {
//                // Temporal/transient parameters, even if they are required, are not put on the wire.
//                continue;
//            }
//
//            if (param.anonymous()) {
//                // If parameter is anonymous, then only it's value is put on the wire.
//                buffer.append(";").append(value);
//                continue;
//            }
//
//            // Otherwise, append the name=value pair.
//            buffer.append(";").append(param.name()).append("=").append(value);
//        }
//
//        return buffer.toString();
//    }

    private static HttpURLConnection openHttpConnection(URLConnectionHelper helper, URI httpLocation) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) helper.openConnection(httpLocation);
        connection.setDoOutput(true);
        return connection;
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

//    private static final class EmptyExtensionParameterValues extends WebSocketExtensionParameterValues {
//        @Override
//        public Collection<Parameter<?>> getParameters() {
//            return EMPTY_PARAMETERS;
//        }
//
//        @Override
//        public <T> T getParameterValue(Parameter<T> parameter) {
//            return null;
//        }
//    }
}
