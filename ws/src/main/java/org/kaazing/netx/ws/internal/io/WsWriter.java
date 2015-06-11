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

import static java.lang.String.format;
import static org.kaazing.netx.ws.internal.WebSocketState.CLOSED;
import static org.kaazing.netx.ws.internal.ext.flyweight.Opcode.CONTINUATION;
import static org.kaazing.netx.ws.internal.ext.flyweight.Opcode.TEXT;

import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.Lock;

import org.kaazing.netx.ws.internal.WsURLConnectionImpl;
import org.kaazing.netx.ws.internal.ext.flyweight.FrameRO;
import org.kaazing.netx.ws.internal.ext.flyweight.FrameRW;
import org.kaazing.netx.ws.internal.ext.flyweight.Opcode;
import org.kaazing.netx.ws.internal.util.OptimisticReentrantLock;
import org.kaazing.netx.ws.internal.util.Utf8Util;

public class WsWriter extends Writer {
    private static final String MSG_INDEX_OUT_OF_BOUNDS = "offset = %d; (offset + length) = %d; buffer length = %d";

    private final WsURLConnectionImpl connection;
    private final FrameRW outgoingFrame;
    private final FrameRO outgoingFrameRO;
    private final ByteBuffer payload;
    private final ByteBuffer heapBuffer;
    private final ByteBuffer heapBufferRO;
    private final Lock stateLock;

    public WsWriter(WsURLConnectionImpl connection) throws IOException {
        this.connection = connection;
        this.outgoingFrame = new FrameRW();
        this.outgoingFrameRO = new FrameRO();
        this.stateLock = new OptimisticReentrantLock();

        this.payload = ByteBuffer.allocate(connection.getMaxFramePayloadLength());
        this.heapBuffer = ByteBuffer.allocate(connection.getMaxFrameLength());
        this.heapBufferRO = heapBuffer.asReadOnlyBuffer();

    }

    @Override
    public void write(char[] cbuf, int offset, int length) throws IOException {
        if (connection.getOutputState() == CLOSED) {
            throw new IOException("Connection closed");
        }

        if (cbuf == null) {
            throw new NullPointerException("Null buffer passed in");
        }
        else if ((offset < 0) || (length < 0) || (offset + length > cbuf.length)) {
            throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, offset + length, cbuf.length));
        }

        try {
            stateLock.lock();

            int payloadLength = Utf8Util.byteCountUTF8(cbuf, offset, length);
            int byteCount = Utf8Util.charstoUTF8Bytes(cbuf, offset, length, payload, 0);

            assert payloadLength == byteCount;

            outgoingFrame.wrap(heapBuffer,  0);
            outgoingFrame.fin(true);
            outgoingFrame.opcode(TEXT);
            outgoingFrame.payloadPut(payload, 0, byteCount);

            outgoingFrameRO.wrap(heapBufferRO, outgoingFrame.offset());
            connection.processOutgoingFrame(outgoingFrameRO);
        }
        finally {
            stateLock.unlock();
        }
    }

    @Override
    public void flush() throws IOException {
    }

    @Override
    public void close() throws IOException {
        connection.getTcpOutputStream().close();
    }

    public void writeText(Opcode opcode, char[] cbuf, int offset, int length, boolean fin) throws IOException {
        if (connection.getOutputState() == CLOSED) {
            throw new IOException("Connection closed");
        }

        // In a text message that spans across multiple frames, the first frame has just the TEXT opcode in the leading byte.
        // The rest of the frames have just the CONTINUATION opcode in the leading byte.
        assert opcode == TEXT || opcode == CONTINUATION;

        // The last frame must have the FIN bit set with CONTINUATION opcode in the leading byte.
        if (fin) {
            assert opcode == CONTINUATION;
        }
        else {
            assert opcode == TEXT;
        }

        if (cbuf == null) {
            throw new NullPointerException("Null buffer passed in");
        }
        else if ((offset < 0) || (length < 0) || (offset + length > cbuf.length)) {
            throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, offset + length, cbuf.length));
        }

        try {
            stateLock.lock();

            int payloadLength = Utf8Util.byteCountUTF8(cbuf, offset, length);
            int byteCount = Utf8Util.charstoUTF8Bytes(cbuf, offset, length, payload, 0);

            assert payloadLength == byteCount;

            outgoingFrame.wrap(heapBuffer,  0);
            outgoingFrame.fin(fin);
            outgoingFrame.opcode(opcode);
            outgoingFrame.payloadPut(payload, 0, byteCount);

            outgoingFrameRO.wrap(heapBufferRO, outgoingFrame.offset());
            connection.processOutgoingFrame(outgoingFrameRO);
        }
        finally {
            stateLock.unlock();
        }
    }
}
