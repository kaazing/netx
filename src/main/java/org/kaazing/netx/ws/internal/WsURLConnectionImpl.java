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
import java.util.concurrent.locks.Lock;
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
import org.kaazing.netx.ws.internal.ext.flyweight.Opcode;
import org.kaazing.netx.ws.internal.io.IncomingSentinelExtension;
import org.kaazing.netx.ws.internal.io.OutgoingSentinelExtension;
import org.kaazing.netx.ws.internal.io.WsInputStream;
import org.kaazing.netx.ws.internal.io.WsMessageReader;
import org.kaazing.netx.ws.internal.io.WsMessageWriter;
import org.kaazing.netx.ws.internal.io.WsOutputStream;
import org.kaazing.netx.ws.internal.io.WsReader;
import org.kaazing.netx.ws.internal.io.WsWriter;
import org.kaazing.netx.ws.internal.util.Base64Util;
import org.kaazing.netx.ws.internal.util.OptimisticReentrantLock;

public final class WsURLConnectionImpl extends WsURLConnection {
    private static final Pattern PATTERN_EXTENSION_FORMAT = Pattern.compile("([a-zA-Z0-9]*)(;?(.*))");
    private static final Pattern PATTERN_COMMA_SEPARATED_FORMAT = Pattern.compile(",");
    private static final Pattern PATTERN_SEMI_COLON_SEPARATED_FORMAT = Pattern.compile(";");

    private static final String MSG_INVALID_OPCODE = "Protocol Violation: Invalid opcode = 0x%02X";
    private static final String MSG_MASKED_FRAME_FROM_SERVER = "Protocol Violation: Masked server-to-client frame";
    private static final String MSG_RESERVED_BITS_SET = "Protocol Violation: Reserved bits set 0x%02X";
    private static final String MSG_FRAGMENTED_CONTROL_FRAME = "Protocol Violation: Fragmented control frame 0x%02X";
    private static final String MSG_PAYLOAD_LENGTH_EXCEEDED = "Protocol Violation: %s payload is more than 125 bytes";
    private static final String MSG_INVALID_PROTOCOL_NEGOTIATED = "Negotiated protocol \"%s\" was not enabled";
    private static final String MSG_INVALID_EXTENSION_NEGOTIATED = "Negotiated extension \"%s\" was not enabled";
    private static final String MSG_INVALID_EXTENSION_SYNTAX = "Bad extension syntax: %s";
    private static final String MSG_NO_EXTENSIONS_SPEICIFIED = "Empty string passed in to enable extensions";
    private static final String MSG_INVALID_CLOSE_CODE = "CLOSE code must be equal to 1000 or within the range 3000-4999";
    private static final String MSG_ALREADY_CONNECTED = "Already connected";
    private static final String MSG_WEBSOCKET_BIDIRECTIONAL = "WebSocket is bidirectional";

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
    private static final int MAX_PAYLOAD_LENGTH = 8192;

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
    private final WebSocketInputStateMachine inputStateMachine;
    private final WebSocketOutputStateMachine outputStateMachine;
    private final WebSocketExtensionFactory extensionFactory;
    private final Lock readLock;
    private final Lock stateLock;
    private final Lock writeLock;

    private volatile String negotiatedProtocol;
    private volatile WsInputStream inputStream;
    private volatile WsOutputStream outputStream;
    private volatile WsReader reader;
    private volatile WsWriter writer;
    private volatile WsMessageReader messageReader;
    private volatile WsMessageWriter messageWriter;

    private volatile WebSocketState inputState;
    private volatile WebSocketState outputState;
    private volatile DefaultWebSocketContext incomingContext;
    private volatile DefaultWebSocketContext outgoingContext;

    private int maxMessageLength;
    private int maxFrameLength;

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
        this.readLock = new OptimisticReentrantLock();
        this.stateLock = new OptimisticReentrantLock();
        this.writeLock = new OptimisticReentrantLock();
        this.maxMessageLength = MAX_PAYLOAD_LENGTH;
        this.maxFrameLength = getFrameLength(false, maxMessageLength);
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
            throw new IllegalArgumentException(MSG_NO_EXTENSIONS_SPEICIFIED);
        }

        ensureReconfigurable();

        try {
            stateLock.lock();

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
        finally {
            stateLock.unlock();
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
        if (outputState == CLOSED) {
            return;
        }

        try {
            stateLock.lock();

            if (outputState == CLOSED) {
                return;
            }

            byte[] reasonBytes = null;

            if (code != 0) {
                // Verify code and reason against RFC 6455.
                // If code is present, it must equal to 1000 or in range 3000 to 4999
                if (code != 1000 && (code < 3000 || code > 4999)) {
                    throw new IOException(MSG_INVALID_CLOSE_CODE);
                }

                if (reason != null && reason.length() > 0) {
                    reasonBytes = reason.getBytes(UTF_8);
                }
            }

            sendClose(code, reasonBytes, 0, reasonBytes == null ? 0 : reasonBytes.length);
        }
        finally {
            stateLock.unlock();
        }
    }

    @Override
    public void connect() throws IOException {
        try {
            stateLock.lock();
            switch (inputState) {
            case START:
                doConnect();
                break;
            default:
                throw new IOException(MSG_ALREADY_CONNECTED);
            }
        }
        finally {
            stateLock.unlock();
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
    public int getMaxMessageLength() {
        return maxMessageLength;
    }

    @Override
    public HttpRedirectPolicy getRedirectPolicy() {
        return connection.getRedirectPolicy();
    }

    @Override
    public WsInputStream getInputStream() throws IOException {
        if (inputStream != null) {
            return inputStream;
        }

        try {
            stateLock.lock();
            ensureConnected();

            if (inputStream != null) {
                return inputStream;
            }

            inputStream = new WsInputStream(this);
            return inputStream;
        }
        finally {
            stateLock.unlock();
        }
    }

    public WsMessageReader getMessageReader() throws IOException {
        if (messageReader != null) {
            return messageReader;
        }

        try {
            stateLock.lock();
            ensureConnected();

            if (messageReader != null) {
                return messageReader;
            }

            messageReader = new WsMessageReader(this);
            return messageReader;
        }
        finally {
            stateLock.unlock();
        }
    }

    public WsMessageWriter getMessageWriter() throws IOException {
        if (messageWriter != null) {
            return messageWriter;
        }

        try {
            stateLock.lock();
            ensureConnected();

            if (messageWriter != null) {
                return messageWriter;
            }

            messageWriter = new WsMessageWriter(this);
            return messageWriter;
        }
        finally {
            stateLock.unlock();
        }
    }

    @Override
    public Collection<String> getNegotiatedExtensions() throws IOException {
        try {
            stateLock.lock();
            ensureConnected();
            return negotiatedExtensionsRO;
        }
        finally {
            stateLock.unlock();
        }
    }

    @Override
    public  String getNegotiatedProtocol() throws IOException {
        try {
            stateLock.lock();
            ensureConnected();
            return negotiatedProtocol;
        }
        finally {
            stateLock.unlock();
        }
    }

    @Override
    public WsOutputStream getOutputStream() throws IOException {
        if (outputStream != null) {
            return outputStream;
        }

        try {
            stateLock.lock();
            ensureConnected();

            if (outputStream != null) {
                return outputStream;
            }

            outputStream = new WsOutputStream(this);
            return outputStream;
        }
        finally {
            stateLock.unlock();
        }
    }

    @Override
    public WsReader getReader() throws IOException {
        if (reader != null) {
            return reader;
        }

        try {
            stateLock.lock();
            ensureConnected();

            if (reader != null) {
                return reader;
            }

            reader = new WsReader(this);
            return reader;
        }
        finally {
            stateLock.unlock();
        }
    }

    @Override
    public Collection<String> getSupportedExtensions() {
        return extensionFactory.getExtensionNames();
    }

    @Override
    public Writer getWriter() throws IOException {
        if (writer != null) {
            return writer;
        }

        try {
            stateLock.lock();
            ensureConnected();

            if (writer != null) {
                return writer;
            }

            writer = new WsWriter(this);
            return writer;
        }
        finally {
            stateLock.unlock();
        }
    }

    @Override
    public void setChallengeHandler(ChallengeHandler challengeHandler) {
        ensureReconfigurable();
        connection.setChallengeHandler(challengeHandler);
    }

    @Override
    public void setConnectTimeout(int timeout) {
        ensureReconfigurable();
        connection.setConnectTimeout(timeout);
    }

    @Override
    public void setEnabledProtocols(String... enabledProtocols) {
        ensureReconfigurable();

        try {
            stateLock.lock();
            this.enabledProtocols.clear();
            this.enabledProtocols.addAll(Arrays.asList(enabledProtocols));
        }
        finally {
            stateLock.unlock();
        }
    }

    @Override
    public void setMaxMessageLength(int maxPayloadLength) {
        ensureReconfigurable();

        if (maxPayloadLength > Integer.MAX_VALUE - 14) {
            throw new IllegalArgumentException(format("Maximim payload length must not exceed %d", Integer.MAX_VALUE - 14));
        }

        if (maxPayloadLength <= 0) {
            throw new IllegalArgumentException("Maximum payload length must be positive integer value");
        }

        this.maxMessageLength = maxPayloadLength;
        this.maxFrameLength = getFrameLength(false, maxMessageLength);
    }

    @Override
    public void setRedirectPolicy(HttpRedirectPolicy redirectPolicy) {
        ensureReconfigurable();
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
            throw new UnsupportedOperationException(MSG_WEBSOCKET_BIDIRECTIONAL);
        }
    }

    @Override
    public void setDoOutput(boolean doOutput) {
        if (!doOutput) {
            throw new UnsupportedOperationException(MSG_WEBSOCKET_BIDIRECTIONAL);
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

        try {
            stateLock.lock();

            List<WebSocketExtensionSpi> extensions = new ArrayList<WebSocketExtensionSpi>(this.negotiatedExtensionSpis);
            extensions.add(new IncomingSentinelExtension());
            incomingContext = new DefaultWebSocketContext(this, unmodifiableList(extensions));
            return incomingContext;
        }
        finally {
            stateLock.unlock();
        }
    }

    public DefaultWebSocketContext getOutgoingContext() {
        if (outgoingContext != null) {
            return outgoingContext;
        }

        try {
            stateLock.lock();

            List<WebSocketExtensionSpi> extensions = new ArrayList<WebSocketExtensionSpi>(this.negotiatedExtensionSpis);
            Collections.reverse(extensions);
            extensions.add(new OutgoingSentinelExtension(this));
            outgoingContext = new DefaultWebSocketContext(this, unmodifiableList(extensions));
            return outgoingContext;
        }
        finally {
            stateLock.unlock();
        }
    }

    public int getFrameLength(boolean masked, int messageLength) {
        int frameLength = 1; // opcode

        if (messageLength < 126) {
            frameLength++;
        } else if (messageLength <= 0xFFFF) {
            frameLength += 3;
        } else {
            frameLength += 9;
        }

        if (masked) {
            frameLength += 4;
        }

        frameLength += messageLength;
        return frameLength;
    }

    public int getMaxFrameLength() {
        return maxFrameLength;
    }

    public Random getRandom() {
        return random;
    }

    public Lock getReadLock() {
        return readLock;
    }

    public Lock getWriteLock() {
        return writeLock;
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
          break;
        }

        try {
            Opcode opcode = frameRO.opcode();
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

    public void sendCloseIfNecessary(Frame closeFrame) throws IOException {
        if (outputState == CLOSED) {
            return;
        }

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

    public void setInputState(WebSocketState state) {
        this.inputState = state;
    }

    public void setOutputState(WebSocketState state) {
        this.outputState = state;
    }


    ///////////////////////////////////////////////////////////////////////////
    private void ensureReconfigurable() {
        switch (inputState) {
        case START:
            break;
        default:
            throw new IllegalStateException(MSG_ALREADY_CONNECTED);
        }
    }

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
            String formattedExtensions = formatAsRequestHeader(enabledExtensions);
            connection.setRequestProperty(HEADER_SEC_WEBSOCKET_EXTENSIONS, formattedExtensions);
        }

        if (!enabledProtocols.isEmpty()) {
            String formattedProtocols = formatAsRequestHeader(enabledProtocols);
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

        inputState = OPEN;
        outputState = OPEN;
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
    }

    private void negotiateProtocol(
            Collection<String> enabledProtocols,
            String negotiatedProtocol) throws IOException {
        if (negotiatedProtocol != null && !enabledProtocols.contains(negotiatedProtocol)) {
            throw new IOException(format(MSG_INVALID_PROTOCOL_NEGOTIATED, negotiatedProtocol));
        }
        this.negotiatedProtocol = negotiatedProtocol;
    }

    private void negotiateExtensions(
            List<String> enabledExtensions,
            String formattedExtensions) throws IOException {
        negotiatedExtensions.clear();
        negotiatedExtensionSpis.clear();

        if ((formattedExtensions == null) || (formattedExtensions.trim().length() == 0)) {
            return;
        }

        String[] extensions = PATTERN_COMMA_SEPARATED_FORMAT.split(formattedExtensions);
        Collection<String> enabledExtensionNames = getEnabledExtensionNames(enabledExtensions);
        for (String extension : extensions) {
            Matcher extensionMatcher = PATTERN_EXTENSION_FORMAT.matcher(extension);
            if (!extensionMatcher.matches()) {
                throw new IllegalStateException(format(MSG_INVALID_EXTENSION_SYNTAX, extension));
            }

            String extnName = extensionMatcher.group(1);
            if (!enabledExtensionNames.contains(extnName)) {
                // Only enabled extensions should be negotiated.
                String s = format(MSG_INVALID_EXTENSION_NEGOTIATED, extnName);
                throw new IOException(s);
            }

            negotiatedExtensions.add(extnName);

            try {
                WebSocketExtensionSpi extensionSpi = extensionFactory.createExtension(extension);
                if (extensionSpi != null) {
                    negotiatedExtensionSpis.add(extensionSpi);
                }
            }
            catch (IOException ex) {
                // The string representation of the negotiated extension was deemed invalid by the extension-factory.
                // So, it will not be activated. This means, the extension-hooks will not be exercised when the messages
                // are being sent or received.

                // ### TODO: Log to indicate why a negotiated extension was not activated.
            }
        }
    }

    private static String base64Encode(byte[] bytes) {
        return Base64Util.encode(ByteBuffer.wrap(bytes));
    }

    private static String formatAsRequestHeader(Collection<String> values) {
        assert values != null;

        StringBuilder sb = new StringBuilder();

        for (String value : values) {
            if (sb.length() > 0) {
                sb.append(", ");
            }

            sb.append(value);
        }

        return sb.toString();
    }

    private static List<String> getEnabledExtensionNames(Collection<String> extensions) {
        if ((extensions == null) || extensions.isEmpty()) {
            return Collections.<String>emptyList();
        }

        List<String> names = new ArrayList<String>();
        for (String extension : extensions) {
            String[] tokens = PATTERN_SEMI_COLON_SEPARATED_FORMAT.split(extension);
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
