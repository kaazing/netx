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
import static org.kaazing.netx.ws.WsURLConnection.WS_PROTOCOL_ERROR;
import static org.kaazing.netx.ws.internal.ext.flyweight.Opcode.BINARY;
import static org.kaazing.netx.ws.internal.ext.flyweight.Opcode.CONTINUATION;
import static org.kaazing.netx.ws.internal.ext.flyweight.Opcode.TEXT;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.Lock;

import org.kaazing.netx.ws.internal.DefaultWebSocketContext;
import org.kaazing.netx.ws.internal.WsURLConnectionImpl;
import org.kaazing.netx.ws.internal.ext.WebSocketContext;
import org.kaazing.netx.ws.internal.ext.flyweight.Flyweight;
import org.kaazing.netx.ws.internal.ext.flyweight.Frame;
import org.kaazing.netx.ws.internal.ext.flyweight.FrameRO;
import org.kaazing.netx.ws.internal.ext.flyweight.FrameRW;
import org.kaazing.netx.ws.internal.ext.flyweight.Opcode;
import org.kaazing.netx.ws.internal.ext.function.WebSocketFrameConsumer;
import org.kaazing.netx.ws.internal.util.OptimisticReentrantLock;

public final class WsInputStream extends InputStream {
    private static final String MSG_NULL_CONNECTION = "Null HttpURLConnection passed in";
    private static final String MSG_INDEX_OUT_OF_BOUNDS = "offset = %d; (offset + length) = %d; buffer length = %d";
    private static final String MSG_NON_BINARY_FRAME = "Non-binary frame - opcode = 0x%02X";
    private static final String MSG_FRAGMENTED_FRAME = "Protocol Violation: Fragmented frame 0x%02X";
    private static final String MSG_INVALID_OPCODE = "Protocol Violation: Invalid opcode = 0x%02X";
    private static final String MSG_UNSUPPORTED_OPERATION = "Unsupported Operation";
    private static final String MSG_MAX_MESSAGE_LENGTH = "Message length %d is greater than the maximum allowed %d";

    private final WsURLConnectionImpl connection;
    private final InputStream in;
    private final FrameRW incomingFrame;
    private final FrameRO incomingFrameRO;
    private final byte[] applicationBuffer;
    private final byte[] networkBuffer;
    private final ByteBuffer heapBuffer;
    private final ByteBuffer heapBufferRO;
    private final Lock stateLock;

    private int networkBufferReadOffset;
    private int networkBufferWriteOffset;
    private int applicationBufferReadOffset;
    private int applicationBufferWriteOffset;
    private boolean fragmented;

    private final WebSocketFrameConsumer terminalFrameConsumer = new WebSocketFrameConsumer() {
        @Override
        public void accept(WebSocketContext context, Frame frame) throws IOException {
            Opcode opcode = frame.opcode();
            long xformedPayloadLength = frame.payloadLength();
            int xformedPayloadOffset = frame.payloadOffset();

            switch (opcode) {
            case BINARY:
            case CONTINUATION:
                if ((opcode == BINARY) && fragmented) {
                    byte leadByte = (byte) Flyweight.uint8Get(frame.buffer(), frame.offset());
                    connection.doFail(WS_PROTOCOL_ERROR, format(MSG_FRAGMENTED_FRAME, leadByte));
                }

                if ((opcode == CONTINUATION) && !fragmented) {
                    byte leadByte = (byte) Flyweight.uint8Get(frame.buffer(), frame.offset());
                    connection.doFail(WS_PROTOCOL_ERROR, format(MSG_FRAGMENTED_FRAME, leadByte));
                }

                int currentLength = applicationBuffer.length;

                if (applicationBufferWriteOffset + xformedPayloadLength > currentLength) {
                    int maxPayloadLength = connection.getMaxMessageLength();
                    throw new IOException(format(MSG_MAX_MESSAGE_LENGTH, xformedPayloadLength, maxPayloadLength));
                }

                // Using System.arraycopy() to copy the contents of transformed.buffer().array() to the applicationBuffer
                // results in java.nio.ReadOnlyBufferException as we will be getting a RO flyweight in the terminal consumer.
                for (int i = 0; i < xformedPayloadLength; i++) {
                    applicationBuffer[applicationBufferWriteOffset++] = frame.buffer().get(xformedPayloadOffset + i);
                }
                fragmented = !frame.fin();
                break;
            case CLOSE:
                connection.sendCloseIfNecessary(frame);
                break;
            case PING:
                connection.sendPong(frame);
                break;
            case PONG:
                break;
            case TEXT:
                connection.doFail(WS_PROTOCOL_ERROR, format(MSG_NON_BINARY_FRAME, Opcode.toInt(TEXT)));
                break;
            }
        }
    };

    public WsInputStream(WsURLConnectionImpl connection) throws IOException {
        if (connection == null) {
            throw new NullPointerException(MSG_NULL_CONNECTION);
        }

        int maxFrameLength = connection.getMaxFrameLength();

        this.connection = connection;
        this.in = connection.getTcpInputStream();
        this.incomingFrame = new FrameRW();
        this.incomingFrameRO = new FrameRO();
        this.stateLock = new OptimisticReentrantLock();

        this.applicationBufferReadOffset = 0;
        this.applicationBufferWriteOffset = 0;
        this.applicationBuffer = new byte[maxFrameLength];
        this.networkBufferReadOffset = 0;
        this.networkBufferWriteOffset = 0;
        this.fragmented = false;
        this.networkBuffer = new byte[maxFrameLength];
        this.heapBuffer = ByteBuffer.wrap(networkBuffer);
        this.heapBufferRO = heapBuffer.asReadOnlyBuffer();
    }

    @Override
    public int available() throws IOException {
        return in.available();
    }

    @Override
    public int read() throws IOException {
        try {
            // Use lock() method(instead of tryLock()) as read() is supposed to block and cannot return a zero like the other
            // read(byte[] buf, int offset, int length) method can.
            stateLock.lock();
            return readInternal();
        }
        finally {
            stateLock.unlock();
        }
    }

    @Override
    public int read(byte[] buf) throws IOException {
        return this.read(buf, 0, buf.length);
    }

    @Override
    public int read(byte[] buf, int offset, int length) throws IOException {
        if (buf == null) {
            throw new NullPointerException("Null buffer passed in");
        }
        else if ((offset < 0) || (length < 0) || (offset + length > buf.length)) {
            throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, offset + length, buf.length));
        }
        else if (length == 0) {
            return 0;
        }

        if (stateLock.tryLock()) {
            try {
                int mark = offset;
                while (length > 0) {
                    buf[offset++] = (byte) readInternal();
                    length--;
                }

                return offset - mark;
            }
            finally {
                stateLock.unlock();
            }
        }

        return 0;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    @Override
    public long skip(long n) throws IOException {
        // ### TODO: Perhaps this can be implemented in future by incrementing applicationBufferReadOffset by n. We should
        //           ensure that applicationBufferReadOffset >= n before doing incrementing. Otherwise, we can thrown an
        //           an exception.
        throw new UnsupportedOperationException(MSG_UNSUPPORTED_OPERATION);
    }

    @Override
    public void mark(int readAheadLimit) {
        throw new UnsupportedOperationException(MSG_UNSUPPORTED_OPERATION);
    }

    @Override
    public void reset() throws IOException {
        throw new IOException(MSG_UNSUPPORTED_OPERATION);
    }

    private int readInternal() throws IOException {
        try {
            if (applicationBufferReadOffset < applicationBufferWriteOffset) {
                return applicationBuffer[applicationBufferReadOffset++];
            }

            if (applicationBufferReadOffset == applicationBufferWriteOffset) {
                applicationBufferReadOffset = 0;
                applicationBufferWriteOffset = 0;
            }

            if (networkBufferWriteOffset > networkBufferReadOffset) {
                int leftOverBytes = networkBufferWriteOffset - networkBufferReadOffset;
                System.arraycopy(networkBuffer, networkBufferReadOffset, networkBuffer, 0, leftOverBytes);
                networkBufferReadOffset = 0;
                networkBufferWriteOffset = leftOverBytes;
            }

            while (true) {
                if (networkBufferReadOffset == networkBufferWriteOffset) {
                    networkBufferReadOffset = 0;
                    networkBufferWriteOffset = 0;

                    int remainingLength = networkBuffer.length - networkBufferWriteOffset;
                    int bytesRead = 0;
                    try {
                        bytesRead = in.read(networkBuffer, networkBufferWriteOffset, remainingLength);
                        if (bytesRead == -1) {
                            return -1;
                        }
                    }
                    catch (SocketException ex) {
                        return -1;
                    }

                    networkBufferReadOffset = 0;
                    networkBufferWriteOffset = bytesRead;
                }

                // Before wrapping the networkBuffer in a flyweight, ensure that it has the metadata(opcode, payload-length)
                // information.
                int numBytes = ensureFrameMetadata();
                if (numBytes == -1) {
                    return -1;
                }

                // At this point, we should have sufficient bytes to figure out whether the frame has been read completely.
                // Ensure that we have at least one complete frame. Figure out the payload length and see how much more
                // we need to read to be frame-aligned.
                incomingFrame.wrap(heapBuffer, networkBufferReadOffset);
                int payloadLength = incomingFrame.payloadLength();

                if (incomingFrame.offset() + payloadLength > networkBufferWriteOffset) {
                    if (payloadLength > networkBuffer.length) {
                        int maxPayloadLength = connection.getMaxMessageLength();
                        throw new IOException(format(MSG_MAX_MESSAGE_LENGTH, payloadLength, maxPayloadLength));
                    }
                    else {
                        // Enough space. But may need shifting the frame to the beginning to be able to fit the payload.
                        if (incomingFrame.offset() + payloadLength > networkBuffer.length) {
                            int len = networkBufferWriteOffset - networkBufferReadOffset;
                            System.arraycopy(networkBuffer, networkBufferReadOffset, networkBuffer, 0, len);
                            networkBufferReadOffset = 0;
                            networkBufferWriteOffset = len;
                        }
                    }

                    int frameLength = connection.getFrameLength(false, payloadLength);
                    int remainingBytes = networkBufferReadOffset + frameLength - networkBufferWriteOffset;
                    while (remainingBytes > 0) {
                        int bytesRead = in.read(networkBuffer, networkBufferWriteOffset, remainingBytes);
                        if (bytesRead == -1) {
                            return -1;
                        }

                        remainingBytes -= bytesRead;
                        networkBufferWriteOffset += bytesRead;
                    }

                    incomingFrame.wrap(heapBuffer, networkBufferReadOffset);
                }

                validateOpcode();
                DefaultWebSocketContext context = connection.getIncomingContext();
                IncomingSentinelExtension sentinel = (IncomingSentinelExtension) context.getSentinelExtension();
                sentinel.setTerminalConsumer(terminalFrameConsumer, incomingFrame.opcode());
                connection.processIncomingFrame(incomingFrameRO.wrap(heapBufferRO, networkBufferReadOffset));
                networkBufferReadOffset += incomingFrame.length();

                if (!isControlFrame()) {
                    break;
                }
            }

            assert applicationBufferReadOffset < applicationBufferWriteOffset;
            return applicationBuffer[applicationBufferReadOffset++];
        }
        finally {
            stateLock.unlock();
        }
    }

    private boolean isControlFrame() {
        switch (incomingFrame.opcode()) {
        case CLOSE:
        case PING:
        case PONG:
            return true;

        default:
            return false;
        }
    }

    private int ensureFrameMetadata() throws IOException {
        int offsetDiff = networkBufferWriteOffset - networkBufferReadOffset;
        if (offsetDiff > 10) {
            // The payload length information is definitely available in the network buffer.
            return 0;
        }

        int bytesRead = 0;
        int maxMetadata = 10;
        int length = maxMetadata - offsetDiff;
        int frameMetadataLength = 2;

        // Ensure that the networkBuffer at the very least contains the payload length related bytes.
        switch (offsetDiff) {
        case 1:
            System.arraycopy(networkBuffer, networkBufferReadOffset, networkBuffer, 0, offsetDiff);
            networkBufferWriteOffset = offsetDiff;  // no break
        case 0:
            length = frameMetadataLength - offsetDiff;
            while (length > 0) {
                bytesRead = in.read(networkBuffer, offsetDiff, length);
                if (bytesRead == -1) {
                    return -1;
                }

                length -= bytesRead;
                networkBufferWriteOffset += bytesRead;
            }
            networkBufferReadOffset = 0;           // no break;
        default:
            // int b1 = networkBuffer[networkBufferReadOffset]; // fin, flags and opcode
            int b2 = networkBuffer[networkBufferReadOffset + 1] & 0x7F;

            if (b2 > 0) {
                switch (b2) {
                case 126:
                    frameMetadataLength += 2;
                    break;
                case 127:
                    frameMetadataLength += 8;
                    break;
                default:
                    break;
                }

                if (offsetDiff >= frameMetadataLength) {
                    return 0;
                }

                int remainingMetadata = networkBufferReadOffset + frameMetadataLength - networkBufferWriteOffset;
                if (networkBuffer.length <= networkBufferWriteOffset + remainingMetadata) {
                    // Shift the frame to the beginning of the buffer and try to read more bytes to be able to figure out
                    // the payload length.
                    System.arraycopy(networkBuffer, networkBufferReadOffset, networkBuffer, 0, offsetDiff);
                    networkBufferReadOffset = 0;
                    networkBufferWriteOffset = offsetDiff;
                }

                while (remainingMetadata > 0) {
                    bytesRead = in.read(networkBuffer, networkBufferWriteOffset, remainingMetadata);
                    if (bytesRead == -1) {
                        return -1;
                    }

                    remainingMetadata -= bytesRead;
                    networkBufferWriteOffset += bytesRead;
                }
            }
        }

        return bytesRead;
    }

    private void validateOpcode() throws IOException {
        int leadByte = Flyweight.uint8Get(incomingFrame.buffer(), incomingFrame.offset());
        try {
            incomingFrame.opcode();
        }
        catch (Exception ex) {
            connection.doFail(WS_PROTOCOL_ERROR, format(MSG_INVALID_OPCODE, leadByte));
        }
    }
}
