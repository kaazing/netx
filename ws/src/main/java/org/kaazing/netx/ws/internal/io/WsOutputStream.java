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
import static org.kaazing.netx.ws.internal.ext.flyweight.Opcode.BINARY;
import static org.kaazing.netx.ws.internal.ext.flyweight.Opcode.CLOSE;
import static org.kaazing.netx.ws.internal.ext.flyweight.Opcode.CONTINUATION;
import static org.kaazing.netx.ws.internal.ext.flyweight.Opcode.PONG;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.Lock;

import org.kaazing.netx.ws.internal.WsURLConnectionImpl;
import org.kaazing.netx.ws.internal.ext.flyweight.FrameRO;
import org.kaazing.netx.ws.internal.ext.flyweight.FrameRW;
import org.kaazing.netx.ws.internal.ext.flyweight.Opcode;
import org.kaazing.netx.ws.internal.util.OptimisticReentrantLock;

public final class WsOutputStream extends FilterOutputStream {
    private static final String MSG_INDEX_OUT_OF_BOUNDS = "offset = %d; (offset + length) = %d; buffer length = %d";
    private static final String MSG_MAX_MESSAGE_LENGTH = "Message length %d is greater than the maximum allowed %d";

    private final WsURLConnectionImpl connection;
    private final byte[] controlFramePayload;
    private final FrameRW outgoingDataFrame;
    private final FrameRW outgoingControlFrame;
    private final FrameRO outgoingFrameRO;
    private final ByteBuffer heapBufferControlFrameRO;
    private final ByteBuffer heapBuffer;
    private final ByteBuffer heapBufferRO;
    private final Lock stateLock;

    public WsOutputStream(WsURLConnectionImpl connection) throws IOException {
        super(connection.getTcpOutputStream());
        this.connection = connection;
        this.heapBuffer = ByteBuffer.allocate(connection.getMaxFrameLength());
        this.heapBufferRO = heapBuffer.asReadOnlyBuffer();
        this.outgoingDataFrame = new FrameRW();
        this.outgoingControlFrame = new FrameRW();
        this.controlFramePayload = new byte[150]; // To handle negative tests. Have some extra bytes.
        this.outgoingControlFrame.wrap(ByteBuffer.allocate(150), 0);
        this.heapBufferControlFrameRO = outgoingControlFrame.buffer().asReadOnlyBuffer();
        this.outgoingFrameRO = new FrameRO();
        this.stateLock = new OptimisticReentrantLock();
    }

    @Override
    public void close() throws IOException {
        try {
            stateLock.lock();
            out.close();
        }
        finally {
            stateLock.unlock();
        }
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[] { (byte) b });
    }

    @Override
    public  void write(byte[] buf, int offset, int length) throws IOException {
        if (connection.getOutputState() == CLOSED) {
            throw new IOException("Connection closed");
        }

        if (buf == null) {
            throw new NullPointerException("Null buffer passed in");
        }
        else if ((offset < 0) || (length < 0) || (offset + length > buf.length)) {
            throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, offset + length, buf.length));
        }

        try {
            stateLock.lock();

            int maxPayloadLength = connection.getMaxFramePayloadLength();
            if (length > maxPayloadLength) {
                throw new IOException(format(MSG_MAX_MESSAGE_LENGTH, length, maxPayloadLength));
            }

            outgoingDataFrame.wrap(heapBuffer,  0);
            outgoingDataFrame.fin(true);
            outgoingDataFrame.opcode(BINARY);
            outgoingDataFrame.payloadPut(buf, offset, length);

            outgoingFrameRO.wrap(heapBufferRO, outgoingDataFrame.offset());
            connection.processOutgoingFrame(outgoingFrameRO);
        }
        finally {
            stateLock.unlock();
        }
    }

    public void writeBinary(Opcode opcode, byte[] buf, int offset, int length, boolean fin) throws IOException {
        if (connection.getOutputState() == CLOSED) {
            throw new IOException("Connection closed");
        }

        // In a binary message that spans across multiple frames, the first frame has just the BINARY opcode in the leading byte.
        // The rest of the frames have just the CONTINUATION opcode in the leading byte.
        assert opcode == BINARY || opcode == CONTINUATION;

        // The last frame must have the FIN bit set with CONTINUATION opcode in the leading byte.
        if (fin) {
            assert opcode == CONTINUATION;
        }

        if (buf == null) {
            throw new NullPointerException("Null buffer passed in");
        }
        else if ((offset < 0) || (length < 0) || (offset + length > buf.length)) {
            throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, offset + length, buf.length));
        }

        try {
            stateLock.lock();

            int maxPayloadLength = connection.getMaxFramePayloadLength();
            if (length > maxPayloadLength) {
                throw new IOException(format(MSG_MAX_MESSAGE_LENGTH, length, maxPayloadLength));
            }

            outgoingDataFrame.wrap(heapBuffer,  0);
            outgoingDataFrame.fin(fin);
            outgoingDataFrame.opcode(opcode);
            outgoingDataFrame.payloadPut(buf, offset, length);

            outgoingFrameRO.wrap(heapBufferRO, outgoingDataFrame.offset());
            connection.processOutgoingFrame(outgoingFrameRO);
        }
        finally {
            stateLock.unlock();
        }
    }

    public void writeClose(int code, byte[] reason, int offset, int length) throws IOException {
        if (connection.getOutputState() == CLOSED) {
            throw new IOException("Connection closed");
        }

        if (reason != null) {
            if ((offset < 0) || (length < 0) || (offset + length > reason.length)) {
                throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, offset + length, reason.length));
            }
        }

        try {
            stateLock.lock();

            int payloadLen = 0;

            if (code > 0) {
                payloadLen += 2;
                payloadLen += length;

                controlFramePayload[0] = (byte) ((code >> 8) & 0xFF);
                controlFramePayload[1] = (byte) (code & 0xFF);
                if (reason != null) {
                    System.arraycopy(reason, offset, controlFramePayload, 2, length);
                }
            }

            outgoingControlFrame.fin(true);
            outgoingControlFrame.opcode(CLOSE);
            outgoingControlFrame.payloadPut(controlFramePayload, 0, payloadLen);

            outgoingFrameRO.wrap(heapBufferControlFrameRO, outgoingControlFrame.offset());
            connection.processOutgoingFrame(outgoingFrameRO);
        }
        finally {
            stateLock.unlock();
        }
    }

    public void writePong(byte[] buf, int offset, int length) throws IOException {
        if (connection.getOutputState() == CLOSED) {
            throw new IOException("Connection closed");
        }

        try {
            stateLock.lock();
            outgoingControlFrame.fin(true);
            outgoingControlFrame.opcode(PONG);
            outgoingControlFrame.payloadPut(buf, offset, length);

            outgoingFrameRO.wrap(heapBufferControlFrameRO, outgoingControlFrame.offset());
            connection.processOutgoingFrame(outgoingFrameRO);
        }
        finally {
            stateLock.unlock();
        }
    }
}
