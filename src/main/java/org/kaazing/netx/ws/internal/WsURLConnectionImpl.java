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
import static java.util.Collections.unmodifiableList;
import static org.kaazing.netx.http.HttpURLConnection.HTTP_SWITCHING_PROTOCOLS;
import static org.kaazing.netx.ws.internal.WebSocketState.CLOSED;
import static org.kaazing.netx.ws.internal.WebSocketState.OPEN;
import static org.kaazing.netx.ws.internal.ext.flyweight.Flyweight.uint16Get;
import static org.kaazing.netx.ws.internal.ext.flyweight.Flyweight.uint8Get;

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
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kaazing.netx.URLConnectionHelper;
import org.kaazing.netx.http.HttpRedirectPolicy;
import org.kaazing.netx.http.HttpURLConnection;
import org.kaazing.netx.http.auth.ChallengeHandler;
import org.kaazing.netx.ws.WsURLConnection;
import org.kaazing.netx.ws.internal.ext.WebSocketExtensionSpi;
import org.kaazing.netx.ws.internal.ext.flyweight.Flyweight;
import org.kaazing.netx.ws.internal.ext.flyweight.Frame;
import org.kaazing.netx.ws.internal.ext.flyweight.OpCode;
import org.kaazing.netx.ws.internal.io.IncomingSentinelExtension;
import org.kaazing.netx.ws.internal.io.OutgoingSentinelExtension;
import org.kaazing.netx.ws.internal.io.WsInputStream;
import org.kaazing.netx.ws.internal.io.WsMessageReader;
import org.kaazing.netx.ws.internal.io.WsMessageWriter;
import org.kaazing.netx.ws.internal.io.WsOutputStream;
import org.kaazing.netx.ws.internal.io.WsReader;
import org.kaazing.netx.ws.internal.io.WsWriter;
import org.kaazing.netx.ws.internal.util.Base64Util;

public final class WsURLConnectionImpl extends WsURLConnection {
    private static final Pattern PATTERN_EXTENSION_FORMAT = Pattern.compile("([a-zA-Z0-9]*)(;?(.*))");

    private static final String MSG_INVALID_OPCODE = "Protocol Violation: Invalid opcode = 0x%02X";
    private static final String MSG_MASKED_FRAME_FROM_SERVER = "Protocol Violation: Masked server-to-client frame";
    private static final String MSG_RESERVED_BITS_SET = "Protocol Violation: Reserved bits set 0x%02X";
    private static final String MSG_FRAGMENTED_CONTROL_FRAME = "Protocol Violation: Fragmented control frame 0x%02X";
    private static final String MSG_PAYLOAD_LENGTH_EXCEEDED = "Protocol Violation: %s payload is more than 125 bytes";

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static final String HEADER_CONNECTION = "Connection";
    private static final String HEADER_SEC_WEBSOCKET_ACCEPT = "Sec-WebSocket-Accept";
    private static final String HEADER_SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";
    private static final String HEADER_SEC_WEBSOCKET_EXTENSIONS = "Sec-WebSocket-Extensions";
    private static final String HEADER_SEC_WEBSOCKET_KEY = "Sec-WebSocket-Key";
    private static final String HEADER_SEC_WEBSOCKET_VERSION = "Sec-WebSocket-Version";
    private static final String HEADER_UPGRADE = "Upgrade";

    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int MAX_COMMAND_FRAME_PAYLOAD = 125;

    private final Random random;
    private final HttpURLConnection connection;
    private final Collection<String> enabledProtocols;
    private final Collection<String> enabledProtocolsRO;
    private final List<String> enabledExtensions;
    private final List<String> enabledExtensionsRO;
    private final List<String> negotiatedExtensions;
    private final List<String> negotiatedExtensionsRO;
    private final List<WebSocketExtensionSpi> negotiatedExtensionSpis;
    private final byte[] commandFramePayload;

    private String negotiatedProtocol;
    private WsInputStream inputStream;
    private WsOutputStream outputStream;
    private WsReader reader;
    private WsWriter writer;
    private WsMessageReader messageReader;
    private WsMessageWriter messageWriter;

    private WebSocketState inputState;
    private WebSocketState outputState;
    private final WebSocketInputStateMachine inputStateMachine;
    private final WebSocketOutputStateMachine outputStateMachine;
    private WebSocketExtensionFactory extensionFactory;
    private DefaultWebSocketContext incomingContext;
    private DefaultWebSocketContext outgoingContext;

    public WsURLConnectionImpl(
            URLConnectionHelper helper,
            URL location,
            URI httpLocation,
            Random random,
            WebSocketExtensionFactory extensionFactory,
            WebSocketInputStateMachine inputStateMachine,
            WebSocketOutputStateMachine outputStateMachine) throws IOException {

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
        this.commandFramePayload = new byte[MAX_COMMAND_FRAME_PAYLOAD];
        this.inputStateMachine = inputStateMachine;
        this.outputStateMachine = outputStateMachine;
        this.connection = openHttpConnection(helper, httpLocation);
    }

    public WsURLConnectionImpl(
            URLConnectionHelper helper,
            URI location,
            URI httpLocation,
            Random random,
            WebSocketExtensionFactory extensionFactory,
            WebSocketInputStateMachine inputStateMachine,
            WebSocketOutputStateMachine outputStateMachine) throws IOException {

        this(helper,
             (URL) null,
             httpLocation,
             random,
             extensionFactory,
             inputStateMachine,
             outputStateMachine);
    }

    // -------------------------- WsURLConnection Methods -------------------
    @Override
    public void addEnabledExtensions(String... extensions) {
        if (extensions == null) {
            throw new NullPointerException("Null extension passed in");
        }

        if (extensions.length == 0) {
            throw new IllegalArgumentException("No extensions specified to be enabled");
        }

        enabledExtensions.clear();

        for (String extension : extensions) {
            try {
                // Validate the string representation of the extension.
                extensionFactory.validateExtension(extension);
                enabledExtensions.add(extension);
            }
            catch (IOException ex) {
                // The string representation of the extension was deemed invalid by the extension.
                // So, it will not be negotiated during the opening handshake.

                // #### TODO: Log to indicate why the enabled extension was not negotiated.
            }
        }
    }

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
        byte[] reasonBytes = null;

        if (code != 0) {
            // Verify code and reason against RFC 6455.
            // If code is present, it must equal to 1000 or in range 3000 to 4999
            if (code != 1000 && (code < 3000 || code > 4999)) {
                    throw new IOException("code must equal to 1000 or within range 3000-4999");
            }

            if (reason != null && reason.length() > 0) {
                reasonBytes = reason.getBytes(UTF_8);
            }
        }

        sendClose(code, reasonBytes, 0, reasonBytes == null ? 0 : reasonBytes.length);
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

    public DefaultWebSocketContext getIncomingContext() {
        if (incomingContext != null) {
            return incomingContext;
        }

        List<WebSocketExtensionSpi> extensions = new ArrayList<WebSocketExtensionSpi>(this.negotiatedExtensionSpis);
        extensions.add(new IncomingSentinelExtension());
        incomingContext = new DefaultWebSocketContext(this, unmodifiableList(extensions));
        return incomingContext;
    }

    public DefaultWebSocketContext getOutgoingContext() {
        if (outgoingContext != null) {
            return outgoingContext;
        }

        List<WebSocketExtensionSpi> extensions = new ArrayList<WebSocketExtensionSpi>(this.negotiatedExtensionSpis);
        Collections.reverse(extensions);
        extensions.add(new OutgoingSentinelExtension(this));
        outgoingContext = new DefaultWebSocketContext(this, unmodifiableList(extensions));
        return outgoingContext;
    }

    public WebSocketExtensionFactory getExtensionFactory() {
        return extensionFactory;
    }

    public Random getRandom() {
        return random;
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

    public void processIncomingFrame(final Frame frameRO) throws IOException {
        if (frameRO == null) {
            throw new NullPointerException("Null frame passed in");
        }

        int leadByte = Flyweight.uint8Get(frameRO.buffer(), frameRO.offset());
        int flags = frameRO.flags();

        switch (flags) {
        case 0:
            break;
        default:
          doFail(WS_PROTOCOL_ERROR, format(MSG_RESERVED_BITS_SET, flags));
        }

        try {
            OpCode opcode = frameRO.opCode();
            switch (opcode) {
            case CLOSE:
            case PING:
            case PONG:
                if (!frameRO.fin()) {
                    doFail(WS_PROTOCOL_ERROR, format(MSG_FRAGMENTED_CONTROL_FRAME, leadByte));
                }

                if (frameRO.payloadLength() > MAX_COMMAND_FRAME_PAYLOAD) {
                    doFail(WS_PROTOCOL_ERROR, format(MSG_PAYLOAD_LENGTH_EXCEEDED, opcode));
                }
                break;
            case BINARY:
            case CONTINUATION:
            case TEXT:
                break;
            }
        }
        catch (Exception ex) {
            doFail(WS_PROTOCOL_ERROR, format(MSG_INVALID_OPCODE, leadByte));
        }

        byte maskByte = (byte) uint8Get(frameRO.buffer(), frameRO.offset() + 1);
        if ((maskByte & 0x80) != 0) {
            doFail(WS_PROTOCOL_ERROR, MSG_MASKED_FRAME_FROM_SERVER);
        }

        inputStateMachine.processFrame(this, frameRO);
    }

    public void processOutgoingFrame(final Frame frameRO) throws IOException {
        outputStateMachine.processFrame(this, frameRO);
    }

    public void sendClose(Frame closeFrame) throws IOException {
        int closePayloadLength = closeFrame.payloadLength();
        int code = 0;
        int reasonOffset = 0;
        int reasonLength = 0;

        if (closePayloadLength >= 2) {
            code = uint16Get(closeFrame.buffer(), closeFrame.payloadOffset());

            if (closePayloadLength > 2) {
                reasonOffset = closeFrame.payloadOffset() + 2;
                reasonLength = closePayloadLength - 2;
            }
        }

        // Using System.arraycopy() to copy the contents from transformed.buffer().array() causes
        // java.nio.ReadOnlyBufferException as we will be getting RO flyweight.
        for (int i = 0; i < reasonLength; i++) {
            commandFramePayload[i] = closeFrame.buffer().get(reasonOffset + i);
        }

        sendClose(code, commandFramePayload, 0, reasonLength);
    }

    public void sendPong(Frame frame) throws IOException {
        long payloadLength = frame.payloadLength();
        int  payloadOffset = frame.payloadOffset();

        for (int i = 0; i < payloadLength; i++) {
            commandFramePayload[i] = frame.buffer().get(payloadOffset + i);
        }

        getOutputStream().writePong(commandFramePayload, 0, (int) payloadLength);
    }

    public void setExtensionFactory(WebSocketExtensionFactory extensionFactory) {
        this.extensionFactory = extensionFactory;
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
            String formattedExtensions = formatExtensionsAsRequestHeader(enabledExtensions);
            connection.setRequestProperty(HEADER_SEC_WEBSOCKET_EXTENSIONS, formattedExtensions);
        }

        if (!enabledProtocols.isEmpty()) {
            String formattedProtocols = formatProtocolsAsRequestHeader(enabledProtocols);
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

    private byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        random.nextBytes(bytes);
        return bytes;
    }

    private void sendClose(int code, byte[] reason, int offset, int length) throws IOException {
        getOutputStream().writeClose(code, reason, offset, length);
        disconnect();
        inputState = CLOSED;
        outputState = CLOSED;
    }

    private void negotiateProtocol(Collection<String> enabledProtocols, String negotiatedProtocol) throws IOException {
        if (negotiatedProtocol != null && !enabledProtocols.contains(negotiatedProtocol)) {
            throw new IOException(format("Connection failed. Negotiated protocol \"%s\" was not enabled", negotiatedProtocol));
        }
        this.negotiatedProtocol = negotiatedProtocol;
    }

    private void negotiateExtensions(List<String> enabledExtensions, String formattedExtensions) throws IOException {

        if ((formattedExtensions == null) || (formattedExtensions.trim().length() == 0)) {
            this.negotiatedExtensions.clear();
            this.negotiatedExtensionSpis.clear();
            return;
        }

        String[] extensions = formattedExtensions.split(",");
        Collection<String> enabledExtensionNames = getEnabledExtensionNames(enabledExtensions);
        for (String extension : extensions) {
            Matcher extensionMatcher = PATTERN_EXTENSION_FORMAT.matcher(extension);
            if (!extensionMatcher.matches()) {
                throw new IllegalStateException(format("Bad extension syntax: %s", extension));
            }

            String extnName = extensionMatcher.group(1);
            if (!enabledExtensionNames.contains(extnName)) {
                // Only enabled extensions should be negotiated.
                String s = format("Invalid extension '%s' negotiated", extnName);
                throw new IOException(s);
            }

            this.negotiatedExtensions.add(extnName);

            try {
                WebSocketExtensionSpi extensionSpi = extensionFactory.createExtension(extension);
                if (extensionSpi != null) {
                    this.negotiatedExtensionSpis.add(extensionSpi);
                }
            }
            catch (IOException ex) {
                // The string representation of the negotiated extension was deemed invalid by the extension.
                // So, it will not be activated. This means, it's hooks will not be exercised when the messages
                //  are being sent or received.

                // ### TODO: Log to indicate why a negotiated extension was not activated.
            }
        }
    }

    private static String base64Encode(byte[] bytes) {
        return Base64Util.encode(ByteBuffer.wrap(bytes));
    }

    private static String formatProtocolsAsRequestHeader(Collection<String> protocols) {
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

    private static String formatExtensionsAsRequestHeader(Collection<String> extensions) {
        assert extensions != null;

        StringBuilder sb = new StringBuilder();

        for (String extension : extensions) {
            if (sb.length() > 0) {
                sb.append(", ");
            }

            sb.append(extension.toString());
        }

        return sb.toString();
    }

    private static List<String> getEnabledExtensionNames(Collection<String> extensions) {
        if ((extensions == null) || extensions.isEmpty()) {
            return Collections.<String>emptyList();
        }

        List<String> names = new ArrayList<String>();
        for (String extension : extensions) {
            String[] tokens = extension.split(";");
            String extnName = tokens[0].trim();

            names.add(extnName);
        }

        return names;
    }

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
}
