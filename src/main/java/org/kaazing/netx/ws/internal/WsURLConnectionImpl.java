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

import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableMap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kaazing.netx.http.HttpRedirectPolicy;
import org.kaazing.netx.http.auth.ChallengeHandler;
import org.kaazing.netx.ws.WebSocketException;
import org.kaazing.netx.ws.WebSocketExtension;
import org.kaazing.netx.ws.WebSocketMessageReader;
import org.kaazing.netx.ws.WebSocketMessageWriter;
import org.kaazing.netx.ws.WsURLConnection;
import org.kaazing.netx.ws.WebSocketExtension.Parameter;
import org.kaazing.netx.ws.WebSocketExtension.Parameter.Metadata;
import org.kaazing.netx.ws.internal.ext.WebSocketExtensionFactorySpi;
import org.kaazing.netx.ws.internal.io.WsInputStreamImpl;
import org.kaazing.netx.ws.internal.io.WsMessageReaderAdapter;
import org.kaazing.netx.ws.internal.io.WsMessageReaderImpl;
import org.kaazing.netx.ws.internal.io.WsMessageWriterImpl;
import org.kaazing.netx.ws.internal.io.WsOutputStreamImpl;
import org.kaazing.netx.ws.internal.io.WsReaderImpl;
import org.kaazing.netx.ws.internal.io.WsWriterImpl;
import org.kaazing.netx.ws.internal.util.InterruptibleBlockingQueue;
import org.kaazing.netx.ws.internal.wsn.WsURLConnectionHandlerNative;

public class WsURLConnectionImpl extends WsURLConnection {
    private static final String _CLASS_NAME = WsURLConnectionImpl.class.getName();
    private static final Logger _LOG = Logger.getLogger(_CLASS_NAME);

    // These member variables are final as they will not change once they are
    // created/set.
    private final Map<String, WsExtensionParameterValuesSpiImpl> _negotiatedParameters;
    private final Map<String, WebSocketExtensionFactorySpi>      _extensionFactories;
    private final URL                                            _location;

    private WsURLConnectionHandlerNative                   _handler;
    private Map<String, WsExtensionParameterValuesSpiImpl> _enabledParameters;
    private Collection<String>                             _enabledExtensions;
    private Collection<String>                             _negotiatedExtensions;
    private Collection<String>                             _supportedExtensions;
    private Collection<String>                             _enabledProtocols;
    private String                                         _negotiatedProtocol;
    private WsInputStreamImpl                              _inputStream;
    private WsOutputStreamImpl                             _outputStream;
    private WsReaderImpl                                   _reader;
    private WsWriterImpl                                   _writer;
    private WsMessageReaderImpl                            _messageReader;
    private WsMessageWriterImpl                            _messageWriter;
    private InterruptibleBlockingQueue<Object>             _sharedQueue;
    private HttpRedirectPolicy                             _redirectPolicy;
    private ChallengeHandler                               _challengeHandler;

    private ReadyState                                     _readyState;
    private Exception                                      _exception;

    /**
     * Values are CONNECTING = 0, OPEN = 1, CLOSING = 2, and CLOSED = 3;
     */
    enum ReadyState {
        CONNECTING, OPEN, CLOSING, CLOSED;
    }

    public WsURLConnectionImpl(URL                                       location,
                               Map<String, WebSocketExtensionFactorySpi> extensionFactories) {
        super(location);

        assert location != null;

        _location = location;
        _readyState = ReadyState.CLOSED;
        _extensionFactories = extensionFactories;
        _negotiatedParameters = new HashMap<String, WsExtensionParameterValuesSpiImpl>();

        if ((_extensionFactories != null) && (_extensionFactories.size() > 0))
        {
            _supportedExtensions = new HashSet<String>();
            _supportedExtensions.addAll(_extensionFactories.keySet());
        }
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
        String args = String.format("code = '%d',  reason = '%s'", code, reason);
        _LOG.entering(_CLASS_NAME, "close", args);

        if (code != 0) {
            //verify code and reason agaist RFC 6455
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
                    _LOG.log(Level.FINEST, e.getMessage(), e);
                    throw new IllegalArgumentException("Reason must be encodable to UTF8");
                }
            }
        }

        // ### TODO: Send the CLOSE frame out.
    }

    @Override
    public void connect() throws IOException {
        _LOG.entering(_CLASS_NAME, "connect");

        String[] enabledProtocols = null;

        synchronized (this) {
            if (_readyState == ReadyState.OPEN) {
                return;
            }
            else if (_readyState == ReadyState.CONNECTING) {
                String s = "Connection is in progress";
                throw new IllegalStateException(s);
            }
            else if (_readyState == ReadyState.CLOSING) {
                String s = "Connection is being terminated";
                throw new IllegalStateException(s);
            }

            // Prepare for connecting.
            _readyState = ReadyState.CONNECTING;
            // setException(null);

            // Used to transfer messages from the input-stream into a blocking
            // queue ready to be consumed.
            _sharedQueue = new InterruptibleBlockingQueue<Object>();

            _handler = new WsURLConnectionHandlerNative(this);
            _handler.connect();
        }
    }

    @Override
    public ChallengeHandler getChallengeHandler() {
        return _challengeHandler;
    }

    @Override
    public int getConnectTimeout() {
        return super.getConnectTimeout();
    }

    @Override
    public Collection<String> getEnabledExtensions() {
        return (_enabledExtensions == null) ? Collections.<String>emptySet() :
            unmodifiableCollection(_enabledExtensions);
    }

    @Override
    public <T> T getEnabledParameter(Parameter<T> parameter) {
        String                            extName = parameter.extension().name();
        WsExtensionParameterValuesSpiImpl paramValues = _enabledParameters.get(extName);

        if (paramValues == null) {
            return null;
        }

        return paramValues.getParameterValue(parameter);
    }

    @Override
    public Collection<String> getEnabledProtocols() {
        return (_enabledProtocols == null) ? Collections.<String>emptySet() :
            unmodifiableCollection(_enabledProtocols);
    }

    @Override
    public HttpRedirectPolicy getRedirectPolicy() {
        return _redirectPolicy;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (_readyState != ReadyState.OPEN) {
            String s = "Cannot create InputStream as the WebSocket is not connected";
            throw new IOException(s);
        }

        synchronized (this) {
            if ((_inputStream != null) && !_inputStream.isClosed()) {
                return _inputStream;
            }

            WsMessageReaderAdapter adapter = null;
            adapter = new WsMessageReaderAdapter(getMessageReader());
            _inputStream = new WsInputStreamImpl(adapter);
        }

        return _inputStream;
    }

    @Override
    public WebSocketMessageReader getMessageReader() throws IOException {
        if (_readyState != ReadyState.OPEN) {
            String s = "Cannot create MessageReader as the connection has not bee established";
            throw new IOException(s);
        }

        synchronized (this) {
            if ((_messageReader != null) && !_messageReader.isClosed()) {
                return _messageReader;
            }

            if (_sharedQueue == null) {
                // Used by the producer(i.e. the handlerListener) and the
                // consumer(i.e. the WebSocketMessageReader).
                _sharedQueue = new InterruptibleBlockingQueue<Object>();
            }

            _messageReader = new WsMessageReaderImpl(this, _sharedQueue);
        }

        return _messageReader;
    }

    @Override
    public WebSocketMessageWriter getMessageWriter() throws IOException {
        if (_readyState != ReadyState.OPEN) {
            String s = "Cannot create MessageWriter as the connection has not bee established";
            throw new IOException(s);
        }

        synchronized (this) {
            if ((_messageWriter != null) && !_messageWriter.isClosed()) {
                return _messageWriter;
            }

            _messageWriter = new WsMessageWriterImpl(this);
        }
        return _messageWriter;
    }

    @Override
    public Collection<String> getNegotiatedExtensions() {
        if (_readyState != ReadyState.OPEN) {
            String s = "Extensions have not been negotiated as the handshake " +
                       "has not completed";
            throw new IllegalStateException(s);
        }

        return (_negotiatedExtensions == null) ? Collections.<String>emptySet() :
                                                 unmodifiableCollection(_negotiatedExtensions);
    }

    @Override
    public <T> T getNegotiatedParameter(Parameter<T> parameter) {
        if (_readyState != ReadyState.OPEN) {
            String s = "Extensions have not been negotiated as the handshake " +
                       "has not completed";
            throw new IllegalStateException(s);
        }

        String                            extName = parameter.extension().name();
        WsExtensionParameterValuesSpiImpl paramValues = _negotiatedParameters.get(extName);

        if (paramValues == null) {
            return null;
        }

        return paramValues.getParameterValue(parameter);
    }

    @Override
    public String getNegotiatedProtocol() {
        if (_readyState != ReadyState.OPEN) {
            String s = "Protocols have not been negotiated as the handshake " +
                       "has not completed";
            throw new IllegalStateException(s);
        }

        return _negotiatedProtocol;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        if (_readyState != ReadyState.OPEN) {
            String s = "Cannot get the OutputStream as the connection has not been established";
            throw new IOException(s);
        }

        synchronized (this)
        {
            if ((_outputStream != null) && !_outputStream.isClosed()) {
                return _outputStream;
            }

            _outputStream = new WsOutputStreamImpl(getMessageWriter());
        }

        return _outputStream;
    }

    @Override
    public Reader getReader() throws IOException {
        if (_readyState != ReadyState.OPEN) {
            String s = "Cannot create Reader as the connection has not been established";
            throw new IOException(s);
        }

        synchronized (this) {
            if ((_reader != null) && !_reader.isClosed()) {
                return _reader;
            }

            WsMessageReaderAdapter adapter = null;
            adapter = new WsMessageReaderAdapter(getMessageReader());
            _reader = new WsReaderImpl(adapter);
        }

        return _reader;
    }

    @Override
    public Collection<String> getSupportedExtensions() {
        return (_supportedExtensions == null) ? Collections.<String>emptySet() :
            unmodifiableCollection(_supportedExtensions);
    }

    @Override
    public Writer getWriter() throws IOException {
        if (_readyState != ReadyState.OPEN) {
            String s = "Cannot create Writer as the connection has not been established";
            throw new IOException(s);
        }

        synchronized (this)
        {
            if ((_writer != null) && !_writer.isClosed()) {
                return _writer;
            }

            _writer = new WsWriterImpl(getMessageWriter());
        }

        return _writer;
    }

    @Override
    public void setChallengeHandler(ChallengeHandler challengeHandler) {
        if (_readyState != ReadyState.CLOSED) {
            String s = "ChallengeHandler can be set only when the connection is closed";
            throw new IllegalStateException(s);
        }

        _challengeHandler = challengeHandler;
    }

    @Override
    public void setConnectTimeout(int timeout) {
        if (_readyState != ReadyState.CLOSED) {
            String s = "Connection timeout can be set only when the connection is closed";
            throw new IllegalStateException(s);
        }

        super.setConnectTimeout(timeout);
    }

    @Override
    public void setEnabledExtensions(Collection<String> extensions) {
        if (_readyState != ReadyState.CLOSED) {
            String s = "Extensions can be enabled only when the connection is closed";
            throw new IllegalStateException(s);
        }

        if (extensions == null) {
            _enabledExtensions = extensions;
            return;
        }

        Collection<String> supportedExtns = getSupportedExtensions();
        for (String extension : extensions) {
            if (!supportedExtns.contains(extension)) {
                String s = String.format("'%s' is not a supported extension", extension);
                throw new IllegalStateException(s);
            }

            if (_enabledExtensions == null) {
                _enabledExtensions = new ArrayList<String>();
            }

            _enabledExtensions.add(extension);
        }
    }

    @Override
    public <T> void setEnabledParameter(Parameter<T> parameter, T value) {
        if (_readyState != ReadyState.CLOSED) {
            String s = "Parameters can be set only when the connection is closed";
            throw new IllegalStateException(s);
        }

        String extensionName = parameter.extension().name();

        WsExtensionParameterValuesSpiImpl parameterValues = _enabledParameters.get(extensionName);
        if (parameterValues == null) {
            parameterValues = new WsExtensionParameterValuesSpiImpl();
            _enabledParameters.put(extensionName, parameterValues);
        }

        parameterValues.setParameterValue(parameter, value);
    }

    @Override
    public void setEnabledProtocols(Collection<String> protocols) {
        if (_readyState != ReadyState.CLOSED) {
            String s = "Protocols can be enabled only when the connection is closed";
            throw new IllegalStateException(s);
        }

        if ((protocols == null) || protocols.isEmpty()) {
            _enabledProtocols = protocols;
            return;
        }

        _enabledProtocols = new ArrayList<String>();

        for (String protocol : protocols) {
            _enabledProtocols.add(protocol);
        }
    }

    @Override
    public void setRedirectPolicy(HttpRedirectPolicy policy) {
        _redirectPolicy = policy;
    }

    // ---------------------- URLConnection Methods ----------------------
    @Override
    public void addRequestProperty(String key, String value) {
        super.addRequestProperty(key, value);
    }

    @Override
    public int getReadTimeout() {
        return super.getReadTimeout();
    }

    @Override
    public void setReadTimeout(int timeout) {
        super.setReadTimeout(timeout);
    }

    // @Override -- Not available in JDK 6.
    @Override
    public long getContentLengthLong() {
        return super.getContentLengthLong();
    }

    @Override
    public int getContentLength() {
        return super.getContentLength();
    }

    @Override
    public String getContentType() {
        return super.getContentType();
    }

    @Override
    public String getContentEncoding() {
        return super.getContentEncoding();
    }

    @Override
    public long getExpiration() {
        return super.getExpiration();
    }

    @Override
    public long getDate() {
        return super.getDate();
    }

    @Override
    public long getLastModified() {
        return super.getLastModified();
    }

    @Override
    public String getHeaderField(String name) {
        return super.getHeaderField(name);
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
        return super.getHeaderFields();
    }

    @Override
    public int getHeaderFieldInt(String name, int Default) {
        return super.getHeaderFieldInt(name, Default);
    }

    // @Override -- Not available in JDK 6.
    @Override
    public long getHeaderFieldLong(String name, long Default) {
        return super.getHeaderFieldLong(name, Default);
    }

    @Override
    public long getHeaderFieldDate(String name, long Default) {
        return super.getHeaderFieldDate(name, Default);
    }

    @Override
    public String getHeaderFieldKey(int n) {
        return super.getHeaderFieldKey(n);
    }

    @Override
    public String getHeaderField(int n) {
        return super.getHeaderField(n);
    }

    @Override
    public Object getContent() throws IOException {
        return super.getContent();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Object getContent(Class[] classes) throws IOException {
        return super.getContent(classes);
    }

    @Override
    public Permission getPermission() throws IOException {
        return super.getPermission();
    }

    @Override
    public boolean getDoInput() {
        return super.getDoInput();
    }

    @Override
    public void setDoInput(boolean doinput) {
        super.setDoInput(doinput);
    }

    @Override
    public boolean getDoOutput() {
        return super.getDoOutput();
    }

    @Override
    public void setDoOutput(boolean dooutput) {
        super.setDoOutput(dooutput);
    }

    @Override
    public boolean getAllowUserInteraction() {
        return super.getAllowUserInteraction();
    }

    @Override
    public void setAllowUserInteraction(boolean allowuserinteraction) {
        super.setAllowUserInteraction(allowuserinteraction);
    }

    @Override
    public boolean getUseCaches() {
        return super.getUseCaches();
    }

    @Override
    public void setUseCaches(boolean usecaches) {
        super.setUseCaches(usecaches);
    }

    @Override
    public long getIfModifiedSince() {
        return super.getIfModifiedSince();
    }

    @Override
    public void setIfModifiedSince(long ifmodifiedsince) {
        super.setIfModifiedSince(ifmodifiedsince);
    }

    @Override
    public boolean getDefaultUseCaches() {
        return super.getDefaultUseCaches();
    }

    @Override
    public void setDefaultUseCaches(boolean defaultusecaches) {
        super.setDefaultUseCaches(defaultusecaches);
    }

    @Override
    public String getRequestProperty(String key) {
        return super.getRequestProperty(key);
    }

    @Override
    public Map<String, List<String>> getRequestProperties() {
        return super.getRequestProperties();
    }

    @Override
    public void setRequestProperty(String key, String value) {
        super.setRequestProperty(key, value);
    }

    // ---------------------- Public APIs used internally --------------------
    public URI getLocation() {
        try {
            return _location.toURI();
        }
        catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean isConnected() {
        return (_readyState == ReadyState.OPEN);
    }

    public boolean isDisconnected() {
        return (_readyState == ReadyState.CLOSED);
    }

    public synchronized void send(ByteBuffer buf) throws IOException {
        _LOG.entering(_CLASS_NAME, "send", buf);

        if (_readyState != ReadyState.OPEN) {
            String s = "Messages can be sent only when the WebSocket is connected";
            throw new WebSocketException(s);
        }

//        _handler.processBinaryMessage(_channel, new WrappedByteBuffer(buf));
    }

    public synchronized void send(String message) throws IOException {
        _LOG.entering(_CLASS_NAME, "send", message);

        if (_readyState != ReadyState.OPEN) {
            String s = "Messages can be sent only when the WebSocket is connected";
            throw new WebSocketException(s);
        }

//        _handler.processTextMessage(_channel, message);
    }

    public String getEnabledExtensionsWithParamsAsRFC3864FormattedString() {
        return null;
    }

    public Map<String, WebSocketExtensionFactorySpi> getExtensionFactories() {
        return unmodifiableMap(_extensionFactories);
    }

    public Map<String, WsExtensionParameterValuesSpiImpl> getEnabledParameters() {
        return unmodifiableMap(_enabledParameters);
    }

    public void setEnabledParameters(Map<String, WsExtensionParameterValuesSpiImpl> enabledParameters) {
        this._enabledParameters = enabledParameters;
    }

    public void setNegotiatedProtocol(String protocol) {
        _negotiatedProtocol = protocol;
    }

    // Comma separated list of negotiated extensions and parameters based on
    // RFC 3864 format.
    public void setNegotiatedExtensions(String extensionsHeader) {
        if ((extensionsHeader == null) ||
            (extensionsHeader.trim().length() == 0)) {
            _negotiatedExtensions = null;
            return;
        }

        String[]     extns = extensionsHeader.split(",");
        List<String> extnNames = new ArrayList<String>();

        for (String extn : extns) {
            String[]    properties = extn.split(";");
            String      extnName = properties[0].trim();

            if (!getEnabledExtensions().contains(extnName)) {
                String s = String.format("Extension '%s' is not an enabled " +
                        "extension so it should not have been negotiated", extnName);
//                setException(new WebSocketException(s));
                return;
            }

            WebSocketExtension extension =
                            WebSocketExtension.getWebSocketExtension(extnName);
            WsExtensionParameterValuesSpiImpl paramValues =
                           _negotiatedParameters.get(extnName);
            Collection<Parameter<?>> anonymousParams =
                           extension.getParameters(Metadata.ANONYMOUS);

            // Start from the second(0-based) property to parse the name-value
            // pairs as the first(or 0th) is the extension name.
            for (int i = 1; i < properties.length; i++) {
                String       property = properties[i].trim();
                String[]     pair = property.split("=");
                Parameter<?> parameter = null;
                String       paramValue = null;

                if (pair.length == 1) {
                    // We are dealing with an anonymous parameter. Since the
                    // Collection is actually an ArrayList, we are guaranteed to
                    // iterate the parameters in the definition/creation order.
                    // As there is no parameter name, we will just get the next
                    // anonymous Parameter instance and use it for setting the
                    // value. The onus is on the extension implementor to either
                    // use only named parameters or ensure that the anonymous
                    // parameters are defined in the order in which the server
                    // will send them back during negotiation.
                    parameter = anonymousParams.iterator().next();
                    paramValue = pair[0].trim();
                }
                else {
                    parameter = extension.getParameter(pair[0].trim());
                    paramValue = pair[1].trim();
                }

                if (parameter.type() != String.class) {
                    String paramName = parameter.name();
                    String s = String.format("Negotiated Extension '%s': " +
                                             "Type of parameter '%s' should be String",
                                             extnName, paramName);
//                    setException(new WebSocketException(s));
                    return;
                }

                if (paramValues == null) {
                    paramValues = new WsExtensionParameterValuesSpiImpl();
                    _negotiatedParameters.put(extnName, paramValues);
                }

                paramValues.setParameterValue(parameter, paramValue);
            }
            extnNames.add(extnName);
        }

        HashSet<String> extnsSet = new HashSet<String>(extnNames);
        _negotiatedExtensions = unmodifiableCollection(extnsSet);

        // ### TODO: Add the extension handlers for the negotiated extensions
        //           to the pipeline.
    }

    // --------------------- Private Implementation --------------------------

    private synchronized void connectionOpened(String protocol,
                                               String extensionsHeader) {
        // ### TODO: Currently, the Gateway is not sending the negotiated
        //           protocol.
        setNegotiatedProtocol(protocol);

        // Parse the negotiated extensions and parameters. This can result in
        // _exception to be setup indicating that there is something wrong
        // while parsing the negotiated extensions and parameters.
        setNegotiatedExtensions(extensionsHeader);

        if (_readyState == ReadyState.CONNECTING) {  // (getException() == null) && (
            _readyState = ReadyState.OPEN;
        }
        else {
            // The exception can be caused either while parsing the negotiated
            // extensions and parameters or the expiry of the connection timeout.
            // The parsing of negotiated extension can cause an exception if --
            // 1) a negotiated extension is not an enabled extension or
            // 2) the type of a negotiated parameter is not String.
            _readyState = ReadyState.CLOSED;

            // Inform the Gateway to close the WebSocket.
//            _handler.processClose(_channel, 0, null);
        }

        // Unblock the connect() call so that it can proceed.
        notifyAll();
    }

    private synchronized void connectionClosed(boolean wasClean,
                                               int     code,
                                               String  reason) {
        if (_readyState == ReadyState.CLOSED) {
            return;
        }

        _readyState = ReadyState.CLOSED;

        if (!wasClean) {
            if (reason == null) {
                reason = "Connection Failed";
            }

//            setException(new WebSocketException(code, reason));
        }

        cleanupAfterClose();

        // Unblock the close() call so that it can proceed.
        notifyAll();
    }

    private synchronized void connectionClosed(Exception ex) {
        if (_readyState == ReadyState.CLOSED) {
            return;
        }

//        setException(ex);

        _readyState = ReadyState.CLOSED;

        cleanupAfterClose();

        // Unblock the close() call so that it can proceed.
        notifyAll();
    }

    private synchronized void connectionFailed(Exception ex) {
        if (_readyState == ReadyState.CLOSED) {
            return;
        }

        if (ex == null) {
            ex = new WebSocketException("Connection Failed");
        }

//        setException(ex);

        _readyState = ReadyState.CLOSED;

        cleanupAfterClose();

        // Unblock threads so that they can proceed.
        notifyAll();
    }

    private synchronized void cleanupAfterClose() {
        setNegotiatedExtensions(null);
        setNegotiatedProtocol(null);
        _negotiatedParameters.clear();

        // ### TODO:
        // 1. WsExtensionHandlerSpis that were been added to the pipeline based
        //    on negotiated extensions for this connection should be removed.

        if (_messageReader != null) {
            // Notify the waiting consumers that the connection is closing.
            try {
                _messageReader.close();
            }
            catch (IOException ex) {
                _LOG.log(Level.FINE, ex.getMessage(), ex);
            }
        }

        if (_sharedQueue != null) {
            _sharedQueue.done();
        }

        if (_inputStream != null) {
            try {
                _inputStream.close();
            }
            catch (Exception ex) {
                _LOG.log(Level.FINE, ex.getMessage(), ex);
            }
        }

        if (_outputStream != null) {
            try {
                _outputStream.close();
            }
            catch (Exception ex) {
                _LOG.log(Level.FINE, ex.getMessage(), ex);
            }
        }

        if (_reader != null) {
            try {
                _reader.close();
            }
            catch (Exception ex) {
                _LOG.log(Level.FINE, ex.getMessage(), ex);
            }
        }

        if (_writer != null) {
            try {
                _writer.close();
            }
            catch (Exception ex) {
                _LOG.log(Level.FINE, ex.getMessage(), ex);
            }
        }

        _messageReader = null;
        _sharedQueue = null;
        _messageWriter = null;
        _inputStream = null;
        _outputStream = null;
        _reader = null;
        _writer = null;
    }

    private InterruptibleBlockingQueue<Object> getSharedQueue() {
        return _sharedQueue;
    }

//    private static final WebSocketHandlerListener handlerListener = new WebSocketHandlerListener() {
//
//        @Override
//        public void connectionOpened(WebSocketChannel channel, String protocol) {
//            _LOG.entering(_CLASS_NAME, "connectionOpened");
//
//            WebSocketCompositeChannel cc = (WebSocketCompositeChannel)channel;
//            WebSocketImpl webSocket = (WebSocketImpl) cc.getWebSocket();
//            WebSocketSelectedChannel selChan = ((WebSocketCompositeChannel)channel).selectedChannel;
//
//            synchronized (webSocket) {
//                // ### TODO: Currently, Gateway is not returning the negotiated
//                //           protocol.
//                // Try parsing the negotiated extensions in the
//                // connectionOpened() method. Only when everything looks good,
//                // mark the connection as opened. If a negotiated extension is
//                // not in the list of enabled extensions, then we will setup an
//                // exception and close down.
//                webSocket.connectionOpened(protocol,
//                                           selChan.getNegotiatedExtensions());
//            }
//        }
//
//        @Override
//        public void binaryMessageReceived(WebSocketChannel channel, WrappedByteBuffer buf) {
//            _LOG.entering(_CLASS_NAME, "binaryMessageReceived");
//
//            WebSocketCompositeChannel cc = (WebSocketCompositeChannel)channel;
//            WebSocketImpl webSocket = (WebSocketImpl) cc.getWebSocket();
//
//            synchronized (webSocket) {
//                BlockingQueueImpl<Object> sharedQueue = webSocket.getSharedQueue();
//                if (sharedQueue != null) {
//                    synchronized (sharedQueue) {
//                        try {
//                            ByteBuffer  payload = buf.getNioByteBuffer();
//                            sharedQueue.put(payload);
//                        }
//                        catch (InterruptedException ex) {
//                            _LOG.log(Level.INFO, ex.getMessage(), ex);
//                        }
//                    }
//                }
//            }
//        }
//
//        @Override
//        public void textMessageReceived(WebSocketChannel channel, String text) {
//            _LOG.entering(_CLASS_NAME, "textMessageReceived", text);
//
//            WebSocketCompositeChannel cc = (WebSocketCompositeChannel)channel;
//            WebSocketImpl      webSocket = (WebSocketImpl) cc.getWebSocket();
//
//            synchronized (webSocket) {
//                BlockingQueueImpl<Object> sharedQueue = webSocket.getSharedQueue();
//                if (sharedQueue != null) {
//                    synchronized (sharedQueue) {
//                        try {
//                            sharedQueue.put(text);
//                        }
//                        catch (InterruptedException ex) {
//                            _LOG.log(Level.INFO, ex.getMessage(), ex);
//                        }
//                    }
//                }
//            }
//        }
//
//        @Override
//        public void connectionClosed(WebSocketChannel channel,
//                                     boolean          wasClean,
//                                     int              code,
//                                     String           reason) {
//            _LOG.entering(_CLASS_NAME, "connectionClosed");
//
//            WebSocketCompositeChannel cc = (WebSocketCompositeChannel)channel;
//            WebSocketImpl webSocket = (WebSocketImpl) cc.getWebSocket();
//
//            // Since close() is a blocking call, if there is any thread
//            // waiting then we should call webSocket.connectionClosed() to
//            // unblock it.
//            synchronized (webSocket) {
//                webSocket.connectionClosed(wasClean, code, reason);
//            }
//        }
//
//        @Override
//        public void connectionClosed(WebSocketChannel channel, Exception ex) {
//            _LOG.entering(_CLASS_NAME, "onError");
//
//            WebSocketCompositeChannel cc = (WebSocketCompositeChannel)channel;
//            WebSocketImpl webSocket = (WebSocketImpl) cc.getWebSocket();
//
//            synchronized (webSocket) {
//                webSocket.connectionClosed(ex);
//            }
//        }
//
//        @Override
//        public void connectionFailed(WebSocketChannel channel, Exception ex) {
//            _LOG.entering(_CLASS_NAME, "onError");
//
//            WebSocketCompositeChannel cc = (WebSocketCompositeChannel)channel;
//            WebSocketImpl webSocket = (WebSocketImpl) cc.getWebSocket();
//
//            synchronized (webSocket) {
//                webSocket.connectionFailed(ex);
//            }
//        }
//
//        @Override
//        public void authenticationRequested(WebSocketChannel channel,
//                                            String           location,
//                                            String           challenge) {
//            // Should never be fired from WebSocketCompositeHandler
//        }
//
//        @Override
//        public void redirected(WebSocketChannel channel, String location) {
//            // Should never be fired from WebSocketCompositeHandler
//        }
//
//        @Override
//        public void commandMessageReceived(WebSocketChannel channel,
//                                           CommandMessage   message) {
//            // ignore
//        }
//    };
}
