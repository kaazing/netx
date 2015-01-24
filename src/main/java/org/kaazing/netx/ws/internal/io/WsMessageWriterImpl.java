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

package org.kaazing.netx.ws.internal.io;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import org.kaazing.netx.ws.WebSocketMessageWriter;
import org.kaazing.netx.ws.internal.WebSocketException;
import org.kaazing.netx.ws.internal.WsURLConnectionImpl;

public class WsMessageWriterImpl extends WebSocketMessageWriter {
    private WsURLConnectionImpl    _urlConnection;
    private boolean  _closed;

    public WsMessageWriterImpl(WsURLConnectionImpl    urlConnection) {
        _urlConnection = urlConnection;
    }

    @Override
    public void writeText(CharSequence src) throws IOException {
        if (isClosed()) {
            String s = "Cannot write as the MessageWriter is closed";
            throw new WebSocketException(s);
        }
        try {
            src.toString().getBytes("UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            String s = "The platform must support UTF-8 encoded text per RFC 6455";
            throw new IOException(s);
        }

        _urlConnection.send(src.toString());
    }

    @Override
    public void writeBinary(ByteBuffer src) throws IOException {
        if (isClosed()) {
            String s = "Cannot write as the MessageWriter is closed";
            throw new WebSocketException(s);
        }

        _urlConnection.send(src);
    }

    // ----------------- Internal Implementation ----------------------------
    public void close() {
        _closed = true;
        _urlConnection = null;
    }

    public boolean isClosed() {
        return _closed;
    }
}