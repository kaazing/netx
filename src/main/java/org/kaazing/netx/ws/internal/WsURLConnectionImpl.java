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
import static java.util.Collections.unmodifiableMap;
import static org.kaazing.netx.http.HttpURLConnection.HTTP_SWITCHING_PROTOCOLS;
import static org.kaazing.netx.ws.internal.WebSocketState.CLOSED;
import static org.kaazing.netx.ws.internal.util.Utf8Util.initialDecodeUTF8;
import static org.kaazing.netx.ws.internal.util.Utf8Util.remainingBytesUTF8;
import static org.kaazing.netx.ws.internal.util.Utf8Util.remainingDecodeUTF8;
import static org.kaazing.netx.ws.internal.util.Utf8Util.validBytesUTF8;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.kaazing.netx.URLConnectionHelper;
import org.kaazing.netx.http.HttpRedirectPolicy;
import org.kaazing.netx.http.HttpURLConnection;
import org.kaazing.netx.http.auth.ChallengeHandler;
import org.kaazing.netx.ws.WsURLConnection;
import org.kaazing.netx.ws.internal.WebSocketExtension.Parameter;
import org.kaazing.netx.ws.internal.WebSocketExtension.Parameter.Metadata;
import org.kaazing.netx.ws.internal.ext.WebSocketExtensionHooks;
import org.kaazing.netx.ws.internal.ext.WebSocketExtensionSpi;
import org.kaazing.netx.ws.internal.io.WsInputStream;
import org.kaazing.netx.ws.internal.io.WsMessageReader;
import org.kaazing.netx.ws.internal.io.WsMessageWriter;
import org.kaazing.netx.ws.internal.io.WsOutputStream;
import org.kaazing.netx.ws.internal.io.WsReader;
import org.kaazing.netx.ws.internal.io.WsWriter;
import org.kaazing.netx.ws.internal.util.Base64Util;

public final class WsURLConnectionImpl extends WsURLConnection {
    private static final String MSG_END_OF_STREAM = "End of stream";
    private static final String MSG_INDEX_OUT_OF_BOUNDS = "offset = %d; (offset + length) = %d; buffer length = %d";
    private static final String MSG_PING_PAYLOAD_LENGTH_EXCEEDED = "Protocol Violation: PING payload is more than %d bytes";
    private static final String MSG_PONG_PAYLOAD_LENGTH_EXCEEDED = "Protocol Violation: PONG payload is more than %d bytes";
    private static final String MSG_UNRECOGNIZED_OPCODE = "Protocol Violation: Unrecognized opcode %d";
    private static final String MSG_CLOSE_FRAME_VIOLATION = "Protocol Violation: CLOSE Frame - Code = %d; Reason Length = %d";
    private static final String MSG_BUFFER_SIZE_SMALL = "Buffer's remaining capacity %d too small for payload of size %d";

    private static final int MAX_COMMAND_FRAME_PAYLOAD = 125;

    private static final Set<Parameter<?>> EMPTY_PARAMETERS = Collections.emptySet();
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static final String HEADER_CONNECTION = "Connection";
    private static final String HEADER_SEC_WEBSOCKET_ACCEPT = "Sec-WebSocket-Accept";
    private static final String HEADER_SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";
    private static final String HEADER_SEC_WEBSOCKET_EXTENSIONS = "Sec-WebSocket-Extensions";
    private static final String HEADER_SEC_WEBSOCKET_KEY = "Sec-WebSocket-Key";
    private static final String HEADER_SEC_WEBSOCKET_VERSION = "Sec-WebSocket-Version";
    private static final String HEADER_UPGRADE = "Upgrade";

    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final WebSocketExtensionParameterValues EMPTY_EXTENSION_PARAMETERS = new EmptyExtensionParameterValues();

    private final Random random;
    private final WebSocketExtensionFactory extensionFactory;
    private final HttpURLConnection connection;
    private final Collection<String> enabledProtocols;
    private final Collection<String> enabledProtocolsRO;
    private final Map<String, WebSocketExtensionParameterValues> enabledExtensions;
    private final Map<String, WebSocketExtensionParameterValues> enabledExtensionsRO;
    private final Map<String, WebSocketExtensionParameterValues> negotiatedExtensions;
    private final Map<String, WebSocketExtensionParameterValues> negotiatedExtensionsRO;

    private String negotiatedProtocol;
    private WsInputStream inputStream;
    private WsOutputStream outputStream;
    private WsReader reader;
    private WsWriter writer;
    private WsMessageReader messageReader;
    private WsMessageWriter messageWriter;

    private WebSocketState state;
    private int codePoint;
    private int remainingBytes;
    private WebSocketStateMachine stateMachine;

    public WsURLConnectionImpl(
            URLConnectionHelper helper,
            URL location,
            URI httpLocation,
            Random random,
            WebSocketExtensionFactory extensionFactory) throws IOException {

        super(location);

        this.random = random;
        this.state = WebSocketState.START;
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
        this.state = WebSocketState.START;
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
        close(0, null); // ### TODO: Should this be 1000(WS_NORMAL_CLOSE)?
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

        switch (state) {
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
        return enabledExtensionsRO.keySet();
    }

    @Override
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
        return negotiatedExtensionsRO.keySet();
    }

    @Override
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
    public void setEnabledExtensions(Map<String, WebSocketExtensionParameterValues> enabledExtensions) {
        switch (state) {
        case START:
            break;
        default:
            throw new IllegalStateException("Already connected");
        }

        this.enabledExtensions.clear();
        this.enabledExtensions.putAll(enabledExtensions);
    }

    @Override
    public void setEnabledProtocols(Collection<String> enabledProtocols) {
        switch (state) {
        case START:
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

    public void doFail(int code, String exceptionMessage) throws IOException {
        sendClose(code, null);
        throw new IOException(exceptionMessage);
    }

    public Random getRandom() {
        return random;
    }

    public WebSocketStateMachine getStateMachine() throws IOException {
        return stateMachine;
    }

    public InputStream getTcpInputStream() throws IOException {
        return connection.getInputStream();
    }

    public OutputStream getTcpOutputStream() throws IOException {
        return connection.getOutputStream();
    }

    public WebSocketState getWebSocketState() {
        return state;
    }

    public int receiveBinaryFrame(byte[] buf, int offset, int length) throws IOException {
        if (buf == null) {
            throw new NullPointerException("Null buffer passed in");
        }
        else if ((offset < 0) || (length < 0) || (offset + length > buf.length)) {
            throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, offset + length, buf.length));
        }
        else if (length == 0) {
            return 0;
        }

        InputStream in = connection.getInputStream();
        int bytesRead = 0;
        int len = length;
        int mark = offset;

        if (buf.length < (offset + length)) {
            int size = buf.length - offset;
            throw new IOException(format(MSG_BUFFER_SIZE_SMALL, size, length));
        }

        // Read the entire payload from the current frame into the buffer.
        while (len > 0) {
            assert offset + len <= buf.length;

            bytesRead = in.read(buf, offset, len);
            if (bytesRead == -1) {
                doFail(WS_PROTOCOL_ERROR, MSG_END_OF_STREAM);
            }

            offset += bytesRead;
            len -= bytesRead;
        }

        ByteBuffer payload = ByteBuffer.wrap(buf, mark, bytesRead);
        ByteBuffer transformedPayload = stateMachine.receivedBinaryFrame(this, payload);
        int remaining = transformedPayload.remaining();
        if (remaining > buf.length) {
            buf = new byte[remaining];
        }
        transformedPayload.get(buf, mark, remaining);
        return remaining;
    }

    public void receiveControlFrame(int opcode, long payloadLength) throws IOException {
        InputStream in = connection.getInputStream();

        switch (opcode) {
        case 0x08:
            int code = 0;
            int closeCodeRO = 0;
            byte[] reason = null;

            if (payloadLength >= 2) {
                ByteBuffer closePayload = readControlFramePayload(opcode, (int) payloadLength);

                code = closePayload.getShort();
                closeCodeRO = code;

                switch (code) {
                case WS_MISSING_STATUS_CODE:
                case WS_ABNORMAL_CLOSE:
                case WS_UNSUCCESSFUL_TLS_HANDSHAKE:
                    code = WS_PROTOCOL_ERROR;
                    break;
                default:
                    break;
                }

                if (payloadLength > 2) {
                    reason = new byte[(int) (payloadLength - 2)];
                    closePayload.get(reason);

                    if ((reason.length > (MAX_COMMAND_FRAME_PAYLOAD - 2)) || !validBytesUTF8(reason)) {
                        code = WS_PROTOCOL_ERROR;
                    }

                    if (code != WS_NORMAL_CLOSE) {
                        reason = null;
                    }
                }
            }

            sendClose(code, reason);

            if (code == WS_PROTOCOL_ERROR) {
                throw new IOException(format(MSG_CLOSE_FRAME_VIOLATION, closeCodeRO, (reason == null) ? 0 : reason.length));
            }
            break;

        case 0x09:
            if (payloadLength > MAX_COMMAND_FRAME_PAYLOAD) {
                doFail(WS_PROTOCOL_ERROR, format(MSG_PING_PAYLOAD_LENGTH_EXCEEDED, MAX_COMMAND_FRAME_PAYLOAD));
            }

            ByteBuffer pingPayload = readControlFramePayload(opcode, (int) payloadLength);
            byte[] pingBuf = new byte[(int) payloadLength];
            pingPayload.get(pingBuf);

            sendPong(pingBuf);
            break;

        case 0x0A:
            if (payloadLength > MAX_COMMAND_FRAME_PAYLOAD) {
                doFail(WS_PROTOCOL_ERROR, format(MSG_PONG_PAYLOAD_LENGTH_EXCEEDED, MAX_COMMAND_FRAME_PAYLOAD));
            }

            ByteBuffer pongPayload = readControlFramePayload(opcode, (int) payloadLength);
            byte[] pongBuf = new byte[(int) payloadLength];
            pongPayload.get(pongBuf);
            break;

        default:
            doFail(WS_PROTOCOL_ERROR, format(MSG_UNRECOGNIZED_OPCODE, opcode));
            break;
        }
    }

    public int receiveTextFrame(char[] cbuf, int offset, int length, long payloadLength) throws IOException {
        if (cbuf == null) {
            throw new NullPointerException("Null buffer passed in");
        }
        else if ((offset < 0) || (length < 0) || (offset + length > cbuf.length)) {
            throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, offset + length, cbuf.length));
        }
        else if (length == 0) {
            return 0;
        }

        InputStream in = connection.getInputStream();
        int mark = offset;
        int payloadOffset = 0;

        outer:
        for (;;) {
            int b = -1;

            // code point may be split across frames
            while (codePoint != 0 || (payloadOffset < payloadLength && remainingBytes > 0)) {
                // surrogate pair
                if (codePoint != 0 && remainingBytes == 0) {
                    int charCount = charCount(codePoint);
                    if (offset + charCount > length) {
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

        CharBuffer payload = CharBuffer.wrap(cbuf, mark, offset - mark);
        CharBuffer transformedPayload = stateMachine.receivedTextFrame(this, payload);
        int remaining = transformedPayload.remaining();
        if (remaining > cbuf.length) {
            cbuf = new char[remaining];
        }
        transformedPayload.get(cbuf, mark, remaining);
        return remaining;
    }

    public void sendClose(int code, byte[] reason) throws IOException {
        getOutputStream().writeClose(code, reason);
        disconnect();
        state = CLOSED;
    }

    public void sendPong(byte[] buf) throws IOException {
        getOutputStream().writePong(buf);
    }

    public void setWebSocketState(WebSocketState state) {
        this.state = state;
    }

    ///////////////////////////////////////////////////////////////////////////

    private void ensureConnected() throws IOException {
        switch (state) {
        case START:
            doConnect();
            break;
        case CONNECTED:
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

        this.state = WebSocketState.CONNECTED;
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

    private ByteBuffer readControlFramePayload(int opcode, int length) throws IOException {
        InputStream in = connection.getInputStream();
        byte[] buf = new byte[length];
        int offset = 0;

        while (offset < buf.length) {
            int bytesRead = in.read(buf, offset, length);

            if (bytesRead == -1) {
                doFail(WS_PROTOCOL_ERROR, MSG_END_OF_STREAM);
            }

            offset += bytesRead;
            length -= bytesRead;
        }

        ByteBuffer payload = ByteBuffer.wrap(buf);
        ByteBuffer transformedPayload = null;

        switch (opcode) {
        case 0x08:
            transformedPayload = stateMachine.receivedCloseFrame(this, payload);
            break;
        case 0x09:
            transformedPayload = stateMachine.receivedPingFrame(this, payload);
            break;
        case 0x0A:
            transformedPayload = stateMachine.receivedPongFrame(this, payload);
            break;
        }

        return transformedPayload;
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

    private void negotiateExtensions(
            Map<String, WebSocketExtensionParameterValues> enabledExtensions,
            String formattedExtensions) throws IOException {

        if ((formattedExtensions == null) || (formattedExtensions.trim().length() == 0)) {
            stateMachine = new WebSocketStateMachine();
            this.negotiatedExtensions.clear();
            return;
        }

        String[] extensions = formattedExtensions.split(",");
        List<WebSocketExtensionHooks> extensionsHooks = new ArrayList<WebSocketExtensionHooks>();

        for (String extn : extensions) {
            String[] properties = extn.split(";");
            String extnName = properties[0].trim();

            if (!enabledExtensions.containsKey(extnName)) {
                // Only enabled extensions should be negotiated.
                String s = format("Invalid extension '%s' negotiated", extnName);
                throw new IOException(s);
            }

            WebSocketExtension extension = WebSocketExtension.getWebSocketExtension(extnName);
            Collection<Parameter<?>> anonymousParams = extension.getParameters(Metadata.ANONYMOUS);
            WebSocketExtensionParameterValues paramValues = this.negotiatedExtensions.get(extnName);

            if (paramValues == null) {
                paramValues = new WsExtensionParameterValuesImpl();
                this.negotiatedExtensions.put(extnName, paramValues);
            }

            // Start from the second(0-based) property to parse the name-value pairs as the first(or 0th) is the extension name.
            for (int i = 1; i < properties.length; i++) {
                String property = properties[i].trim();
                String[] pair = property.split("=");
                Parameter<?> parameter = null;
                String paramValue = null;

                if (pair.length == 1) {
                    // We are dealing with an anonymous parameter. Since the Collection is actually an ArrayList, we are
                    // guaranteed to iterate the parameters in the definition/creation order. As there is no parameter name, we
                    // will just get the next anonymous Parameter instance and use it for setting the value. The onus is on the
                    // extension implementor to either use only named parameters or ensure that the anonymous parameters are
                    // defined in the order in which the server will send them back during negotiation.
                    parameter = anonymousParams.iterator().next();
                    paramValue = pair[0].trim();
                } else {
                    parameter = extension.getParameter(pair[0].trim());
                    paramValue = pair[1].trim();
                }

                if (parameter.type() != String.class) {
                    String paramName = parameter.name();
                    String s = format("Negotiated Extension '%s': Type of parameter '%s' should be String", extnName, paramName);
                    throw new IOException(s);
                }

                ((WsExtensionParameterValuesImpl) paramValues).setParameterValue(parameter, paramValue);
            }

            WebSocketExtensionSpi extensionSpi = extensionFactory.createExtension(extnName, paramValues);
            WebSocketExtensionHooks hooks = extensionSpi.createWebSocketHooks();
            if (hooks != null) {
                extensionsHooks.add(hooks);
            }
        }

        stateMachine = new WebSocketStateMachine(extensionsHooks);
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

    private static String formatAsRFC3864(Map<String, WebSocketExtensionParameterValues> enabledExtensions) {
        if ((enabledExtensions == null) || enabledExtensions.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, WebSocketExtensionParameterValues> entry : enabledExtensions.entrySet()) {
            String extensionName = entry.getKey();
            WebSocketExtensionParameterValues paramValues = entry.getValue();
            String extension = formattedExtension(extensionName, paramValues);

            if (sb.length() != 0) {
                sb.append(", ");
            }
            sb.append(extension);
        }

        return sb.toString();
    }

    private static String formattedExtension(String extensionName, WebSocketExtensionParameterValues paramValues) {
        assert extensionName != null;

        WebSocketExtension extension = WebSocketExtension.getWebSocketExtension(extensionName);
        Collection<Parameter<?>> parameters = extension.getParameters();
        StringBuffer buffer = new StringBuffer(extension.name());

        // Iterate over ordered list of parameters to create the formatted string.
        for (Parameter<?> param : parameters) {
            if (param.required()) {
                // Required parameter is not enabled/set.
                String s = format("Extension '%s': Required parameter '%s' must be set", extension.name(), param.name());
                if ((paramValues == null) || (paramValues.getParameterValue(param) == null)) {
                    throw new IllegalStateException(s);
                }
            }

            if (paramValues == null) {
                // We should continue so that we can throw an exception if any of the required parameters has not been set.
                continue;
            }

            Object value = paramValues.getParameterValue(param);

            if (value == null) {
                // Non-required parameter has not been set. So, let's continue to the next one.
                continue;
            }

            if (param.temporal()) {
                // Temporal/transient parameters, even if they are required, are not put on the wire.
                continue;
            }

            if (param.anonymous()) {
                // If parameter is anonymous, then only it's value is put on the wire.
                buffer.append(";").append(value);
                continue;
            }

            // Otherwise, append the name=value pair.
            buffer.append(";").append(param.name()).append("=").append(value);
        }

        return buffer.toString();
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
