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
import static org.kaazing.netx.ws.internal.ext.flyweight.OpCode.TEXT;

import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;

import org.kaazing.netx.ws.internal.WebSocketOutputStateMachine;
import org.kaazing.netx.ws.internal.WsURLConnectionImpl;
import org.kaazing.netx.ws.internal.ext.flyweight.FrameRO;
import org.kaazing.netx.ws.internal.ext.flyweight.FrameRW;
import org.kaazing.netx.ws.internal.util.FrameUtil;
import org.kaazing.netx.ws.internal.util.Utf8Util;

public class WsWriter extends Writer {
    private static final String MSG_INDEX_OUT_OF_BOUNDS = "offset = %d; (offset + length) = %d; buffer length = %d";

    private final WsURLConnectionImpl connection;
    private final FrameRW outgoingFrame;
    private final FrameRO outgoingFrameRO;
    private ByteBuffer payload;

    public WsWriter(WsURLConnectionImpl connection) throws IOException {
        this.connection = connection;
        this.outgoingFrame = new FrameRW();
        this.outgoingFrameRO = new FrameRO();
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

        int payloadLength = Utf8Util.byteCountUTF8(cbuf, offset, length);
        int capacity = FrameUtil.calculateCapacity(false, payloadLength);

        if ((payload == null) || (payload.capacity() < payloadLength)) {
            payload = ByteBuffer.allocate(payloadLength);
        }

        int byteCount = Utf8Util.charstoUTF8Bytes(cbuf, offset, length, payload, 0);
        assert payloadLength == byteCount;

        if ((outgoingFrame.buffer() == null) || (outgoingFrame.buffer().capacity() < capacity)) {
            outgoingFrame.wrap(ByteBuffer.allocate(capacity),  0);
        }

        outgoingFrame.fin(true);
        outgoingFrame.opCode(TEXT);
        outgoingFrame.payloadPut(payload, 0, byteCount);

        outgoingFrameRO.wrap(outgoingFrame.buffer().asReadOnlyBuffer(), outgoingFrame.offset());
        WebSocketOutputStateMachine.instance().processFrame(connection, outgoingFrameRO);
    }

    @Override
    public void flush() throws IOException {
        // No-op
    }

    @Override
    public void close() throws IOException {
        connection.getTcpOutputStream().close();
    }
}
