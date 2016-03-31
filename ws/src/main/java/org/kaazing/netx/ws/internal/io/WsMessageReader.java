/**
 * Copyright 2007-2015, Kaazing Corporation. All rights reserved.
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

import static java.lang.Character.charCount;
import static java.lang.Character.toChars;
import static java.lang.String.format;
import static org.kaazing.netx.ws.MessageType.EOS;
import static org.kaazing.netx.ws.WsURLConnection.WS_PROTOCOL_ERROR;
import static org.kaazing.netx.ws.internal.ext.flyweight.Flyweight.uint8Get;
import static org.kaazing.netx.ws.internal.ext.flyweight.Opcode.BINARY;
import static org.kaazing.netx.ws.internal.ext.flyweight.Opcode.CLOSE;
import static org.kaazing.netx.ws.internal.ext.flyweight.Opcode.CONTINUATION;
import static org.kaazing.netx.ws.internal.ext.flyweight.Opcode.TEXT;
import static org.kaazing.netx.ws.internal.util.Utf8Util.initialDecodeUTF8;
import static org.kaazing.netx.ws.internal.util.Utf8Util.remainingBytesUTF8;
import static org.kaazing.netx.ws.internal.util.Utf8Util.remainingDecodeUTF8;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

import org.kaazing.netx.ws.MessageReader;
import org.kaazing.netx.ws.MessageType;
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

public class WsMessageReader extends MessageReader {
    private static final String MSG_NULL_CONNECTION = "Null connection passed in";
    private static final String MSG_INDEX_OUT_OF_BOUNDS = "offset = %d; (offset + length) = %d; buffer length = %d";
    private static final String MSG_NON_BINARY_FRAME = "Non-text frame - opcode = 0x%02X";
    private static final String MSG_NON_TEXT_FRAME = "Non-binary frame - opcode = 0x%02X";
    private static final String MSG_BUFFER_SIZE_SMALL = "Buffer's remaining capacity %d too small for payload of size %d";
    private static final String MSG_MASKED_FRAME_FROM_SERVER = "Protocol Violation: Masked server-to-client frame";
    private static final String MSG_RESERVED_BITS_SET = "Protocol Violation: Reserved bits set 0x%02X";
    private static final String MSG_UNRECOGNIZED_OPCODE = "Protocol Violation: Unrecognized opcode %d";
    private static final String MSG_FIRST_FRAME_FRAGMENTED = "Protocol Violation: First frame cannot be a fragmented frame";
    private static final String MSG_UNEXPECTED_OPCODE = "Protocol Violation: Opcode 0x%02X expected only in the initial frame";
    private static final String MSG_FRAGMENTED_CONTROL_FRAME = "Protocol Violation: Fragmented control frame 0x%02X";
    private static final String MSG_FRAGMENTED_FRAME = "Protocol Violation: Fragmented frame 0x%02X";
    private static final String MSG_NEXT_NOT_INVOKED = "MessageReader.next() method must be called before reading a message";
    private static final String MSG_MAX_MESSAGE_LENGTH = "Message length %d is greater than the maximum allowed %d";
    private static final String MSG_NOT_CURRENT_OWNER = "Thread reading the currrent message must perform this operation";
    private static final String MSG_CANNOT_BE_READ_FULLY = "Message should be streamed as it spans across multiple frames";
    private static final String MSG_CAN_BE_READ_FULLY = "Message can be read in it's entirety instead of streaming";
    private static final String MSG_INVALID_MESSAGE_TYPE = "Invalid message type: '%s'";
    private static final String MSG_BUFFER_OVERFLOW = "Buffer size '%d' small to accommodate a message of length '%d'";
    private static final String MSG_END_OF_MESSAGE_STREAM = "End of message stream";

    private enum State {
        INITIAL, PROCESS_FRAME;
    };

    private final WsURLConnectionImpl connection;
    private final InputStream in;
    private final FrameRW incomingFrame;
    private final FrameRO incomingFrameRO;
    private final ByteBuffer heapBuffer;
    private final ByteBuffer heapBufferRO;
    private final byte[] networkBuffer;
    private final AtomicReference<Thread> currentMessageOwner;
    private final Lock lock;

    private int networkBufferReadOffset;
    private int networkBufferWriteOffset;
    private byte[] applicationByteBuffer;
    private char[] applicationCharBuffer;
    private int applicationBufferWriteOffset;
    private int applicationBufferLength;
    private int codePoint;
    private int remainingBytes;
    private MessageType type;
    private State state;
    private boolean fragmented;
    private int messageLength;   // -1 for messages that span across multiple frames.
    private boolean finalFrame;
    private WsBinaryStream messageBinaryStream;
    private WsTextReader messageTextReader;

    final WebSocketFrameConsumer terminalBinaryFrameConsumer = new WebSocketFrameConsumer() {
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

                if (applicationBufferWriteOffset + xformedPayloadLength > applicationByteBuffer.length) {
                    // MessageReader requires reading the entire message/frame. So, if there isn't enough space to read the
                    // frame, we should throw an exception.
                    int available = applicationByteBuffer.length - applicationBufferWriteOffset;
                    throw new IOException(format(MSG_BUFFER_SIZE_SMALL, available, xformedPayloadLength));
                }

                // Using System.arraycopy() to copy the contents of transformed.buffer().array() to the applicationBuffer
                // results in java.nio.ReadOnlyBufferException as we will be getting a RO flyweight in the terminal consumer.
                for (int i = 0; i < xformedPayloadLength; i++) {
                    applicationByteBuffer[applicationBufferWriteOffset++] = frame.buffer().get(xformedPayloadOffset + i);
                }
                fragmented = !frame.fin();
                break;
            default:
                connection.doFail(WS_PROTOCOL_ERROR, format(MSG_NON_BINARY_FRAME, Opcode.toInt(opcode)));
                break;
            }
        }
    };

    private final WebSocketFrameConsumer terminalTextFrameConsumer = new WebSocketFrameConsumer() {
        @Override
        public void accept(WebSocketContext context, Frame frame) throws IOException {
            Opcode opcode = frame.opcode();
            long xformedPayloadLength = frame.payloadLength();
            int xformedPayloadOffset = frame.payloadOffset();

            switch (opcode) {
            case TEXT:
            case CONTINUATION:
                if ((opcode == TEXT) && fragmented) {
                    byte leadByte = (byte) Flyweight.uint8Get(frame.buffer(), frame.offset());
                    connection.doFail(WS_PROTOCOL_ERROR, format(MSG_FRAGMENTED_FRAME, leadByte));
                }

                if ((opcode == CONTINUATION) && !fragmented) {
                    byte leadByte = (byte) Flyweight.uint8Get(frame.buffer(), frame.offset());
                    connection.doFail(WS_PROTOCOL_ERROR, format(MSG_FRAGMENTED_FRAME, leadByte));
                }

                int charsConverted = utf8BytesToChars(frame.buffer(),
                                                      xformedPayloadOffset,
                                                      xformedPayloadLength,
                                                      applicationCharBuffer,
                                                      applicationBufferWriteOffset,
                                                      applicationBufferLength);
                applicationBufferWriteOffset += charsConverted;
                applicationBufferLength -= charsConverted;
                fragmented = !frame.fin();
                break;
            default:
                connection.doFail(WS_PROTOCOL_ERROR, format(MSG_NON_BINARY_FRAME, Opcode.toInt(opcode)));
                break;
            }
        }
    };

    private final WebSocketFrameConsumer terminalControlFrameConsumer = new WebSocketFrameConsumer() {
        @Override
        public void accept(WebSocketContext context, Frame frame) throws IOException {
            Opcode opcode = frame.opcode();

            switch (opcode) {
            case CLOSE:
                connection.sendCloseIfNecessary(frame);
                break;
            case PING:
                connection.sendPong(frame);
                break;
            case PONG:
                break;
            default:
                connection.doFail(WS_PROTOCOL_ERROR, format(MSG_UNRECOGNIZED_OPCODE, Opcode.toInt(opcode)));
                break;
            }
        }
    };


    public WsMessageReader(WsURLConnectionImpl connection) throws IOException {
        if (connection == null) {
            throw new NullPointerException(MSG_NULL_CONNECTION);
        }

        this.connection = connection;
        this.currentMessageOwner = new AtomicReference<Thread>(null);

        int maxFrameLength = connection.getMaxFrameLength();

        this.in = connection.getTcpInputStream();
        this.incomingFrame = new FrameRW();
        this.incomingFrameRO = new FrameRO();
        this.lock = new OptimisticReentrantLock();
        this.state = State.INITIAL;

        this.fragmented = false;
        this.finalFrame = false;
        this.applicationBufferWriteOffset = 0;
        this.applicationBufferLength = 0;
        this.networkBufferReadOffset = 0;
        this.networkBufferWriteOffset = 0;
        this.networkBuffer = new byte[maxFrameLength];
        this.heapBuffer = ByteBuffer.wrap(networkBuffer);
        this.heapBufferRO = heapBuffer.asReadOnlyBuffer();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (messageBinaryStream != null) {
            return messageBinaryStream;
        }

        try {
            lock.lock();

            if (messageBinaryStream != null) {
                return messageBinaryStream;
            }

            messageBinaryStream = new WsBinaryStream(connection, this);
        }
        finally {
            lock.unlock();
        }

        return messageBinaryStream;
    }

    @Override
    public Reader getReader() throws IOException {
        if (messageTextReader != null) {
            return messageTextReader;
        }

        try {
            lock.lock();

            if (messageTextReader != null) {
                return messageTextReader;
            }

            messageTextReader = new WsTextReader(connection, this);
        }
        finally {
            lock.unlock();
        }

        return messageTextReader;
    }

    @Override
    public MessageType next() throws IOException {
        while (!currentMessageOwner.compareAndSet(null, Thread.currentThread())) {
            // Spin till the current message has been completely read by the current owner.
        }

        switch (state) {
        case INITIAL:
            if (readDataFrameFully() == -1) {
                return EOS;
            }

            if (messageBinaryStream != null) {
                messageBinaryStream.resetState();
            }

            if (messageTextReader != null) {
                messageTextReader.resetState();
            }
            break;
        default:
            throw new IOException(MSG_NOT_CURRENT_OWNER);
        }

        return type;
    }

    @Override
    public MessageType peek() {
        return type;
    }

    @Override
    public int readFully(byte[] buffer) throws IOException {
        if (buffer == null) {
            throw new NullPointerException("Null buffer passed in");
        }

        if (currentMessageOwner.get() == null) {
            throw new IOException(MSG_NEXT_NOT_INVOKED);
        }

        if (currentMessageOwner.get() != Thread.currentThread()) {
            throw new IOException(MSG_NOT_CURRENT_OWNER);
        }

        switch (type) {
        case EOS:
            return -1;
        case TEXT:
            throw new IOException(MSG_NON_BINARY_FRAME);
        default:
            break;
        }

        if (streaming()) {
            throw new IOException(MSG_CANNOT_BE_READ_FULLY);
        }

        if (messageLength > buffer.length) {
            throw new IOException(format(MSG_BUFFER_OVERFLOW, buffer.length, messageLength));
        }

        assert finalFrame;

        int bytesRead = readAndProcessBinaryFrame(buffer, 0, buffer.length);

        messageLength = -1;
        resetCurrentOwner();

        return bytesRead;
    }

    @Override
    public int readFully(char[] buffer) throws IOException {
        if (buffer == null) {
            throw new NullPointerException("Null buffer passed in");
        }

        if (currentMessageOwner.get() == null) {
            throw new IOException(MSG_NEXT_NOT_INVOKED);
        }

        if (currentMessageOwner.get() != Thread.currentThread()) {
            throw new IOException(MSG_NOT_CURRENT_OWNER);
        }

        switch (type) {
        case EOS:
            return -1;
        case BINARY:
            throw new IOException(MSG_NON_TEXT_FRAME);
        default:
            break;
        }

        if (streaming()) {
            throw new IOException(MSG_CANNOT_BE_READ_FULLY);
        }

        assert finalFrame;

        int charsRead = readAndProcessTextFrame(buffer, 0, buffer.length);

        messageLength = -1;
        resetCurrentOwner();

        return charsRead;
    }

    @Override
    public void skip() throws IOException {
        if (currentMessageOwner.get() == null) {
            throw new IOException(MSG_NEXT_NOT_INVOKED);
        }

        if (currentMessageOwner.get() != Thread.currentThread()) {
            throw new IOException(MSG_NOT_CURRENT_OWNER);
        }

        if (finalFrame) {
            // Skip a message that fits in a single frame.
            incomingFrame.wrap(heapBuffer, networkBufferReadOffset);
            networkBufferReadOffset += incomingFrame.length();

            if (networkBufferReadOffset == networkBufferWriteOffset) {
                networkBufferReadOffset = 0;
                networkBufferWriteOffset = 0;
            }
        }
        else {
            // Skip a message spanning across multiple frames.
            while (!finalFrame) {
                readDataFrameFully();
            }
        }

        state = State.INITIAL;
        currentMessageOwner.set(null);
    }

    @Override
    public boolean streaming() {
        if (currentMessageOwner.get() == null) {
            throw new IllegalStateException(MSG_NEXT_NOT_INVOKED);
        }

        if (currentMessageOwner.get() != Thread.currentThread()) {
            throw new IllegalStateException(MSG_NOT_CURRENT_OWNER);
        }

        return messageLength == -1;
    }

    public void close() throws IOException {
        try {
            lock.lock();
            in.close();
            type = null;
            state = null;
        }
        finally {
            lock.unlock();
        }
    }

    // Package-Private Methods

    boolean isFinalFrame() {
        return finalFrame;
    }

    Thread getCurrentOwner() {
        return currentMessageOwner.get();
    }

    void resetCurrentOwner() {
        currentMessageOwner.set(null);
    }

    // Private Methods

    private int readAndProcessBinaryFrame(byte[] buffer, int offset, int length) throws IOException {
        if (type != MessageType.BINARY) {
            throw new IOException(format(MSG_INVALID_MESSAGE_TYPE, type));
        }

        applicationByteBuffer = buffer;
        applicationBufferWriteOffset = offset;

        if (readDataFrameFully() == -1) {
            return -1;
        }

        incomingFrame.wrap(heapBuffer, networkBufferReadOffset);
        finalFrame = incomingFrame.fin();

        validateOpcode();
        DefaultWebSocketContext context = connection.getIncomingContext();
        IncomingSentinelExtension sentinel = (IncomingSentinelExtension) context.getSentinelExtension();
        sentinel.setTerminalConsumer(terminalBinaryFrameConsumer, incomingFrame.opcode());
        connection.processIncomingFrame(incomingFrameRO.wrap(heapBufferRO, networkBufferReadOffset));
        networkBufferReadOffset += incomingFrame.length();

        if (networkBufferReadOffset == networkBufferWriteOffset) {
            networkBufferReadOffset = 0;
            networkBufferWriteOffset = 0;
        }

        state = finalFrame ? State.INITIAL : State.PROCESS_FRAME;
        return applicationBufferWriteOffset - offset;
    }

    private int readAndProcessTextFrame(char[] buffer, int offset, int length) throws IOException {
        if (type != MessageType.TEXT) {
            throw new IOException(format(MSG_INVALID_MESSAGE_TYPE, type));
        }

        applicationCharBuffer = buffer;
        applicationBufferWriteOffset = offset;
        applicationBufferLength = length;

        if (readDataFrameFully() == -1) {
            return -1;
        }

        incomingFrame.wrap(heapBuffer, networkBufferReadOffset);
        finalFrame = incomingFrame.fin();

        validateOpcode();
        DefaultWebSocketContext context = connection.getIncomingContext();
        IncomingSentinelExtension sentinel = (IncomingSentinelExtension) context.getSentinelExtension();
        sentinel.setTerminalConsumer(terminalTextFrameConsumer, incomingFrame.opcode());
        connection.processIncomingFrame(incomingFrameRO.wrap(heapBufferRO, networkBufferReadOffset));
        networkBufferReadOffset += incomingFrame.length();

        if (networkBufferReadOffset == networkBufferWriteOffset) {
            networkBufferReadOffset = 0;
            networkBufferWriteOffset = 0;
        }

        state = finalFrame ? State.INITIAL : State.PROCESS_FRAME;
        return applicationBufferWriteOffset - offset;
    }

    // Returns the leadByte of the next data frame. Otherwise -1.
    private int readDataFrameFully() throws IOException {
        if (networkBufferWriteOffset == 0) {
            int bytesRead = in.read(networkBuffer, 0, networkBuffer.length);
            if (bytesRead == -1) {
                resetCurrentOwner();
                type = EOS;
                return -1;
            }

            networkBufferReadOffset = 0;
            networkBufferWriteOffset = bytesRead;
        }

        int numBytes = ensureFrameMetadata();
        if (numBytes == -1) {
            resetCurrentOwner();
            type = EOS;
            return -1;
        }

        incomingFrame.wrap(heapBuffer, networkBufferReadOffset);
        int payloadLength = incomingFrame.payloadLength();

        if (incomingFrame.offset() + payloadLength > networkBufferWriteOffset) {
            if (payloadLength > networkBuffer.length) {
                int maxPayloadLength = connection.getMaxFramePayloadLength();
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
                    resetCurrentOwner();
                    return -1;
                }

                remainingBytes -= bytesRead;
                networkBufferWriteOffset += bytesRead;
            }

            incomingFrame.wrap(heapBuffer, networkBufferReadOffset);
        }

        int leadByte = Flyweight.uint8Get(incomingFrame.buffer(), incomingFrame.offset());
        int flags = incomingFrame.flags();

        switch (flags) {
        case 0:
            break;
        default:
            connection.doFail(WS_PROTOCOL_ERROR, format(MSG_RESERVED_BITS_SET, flags));
            break;
        }

        Opcode opcode = null;
        finalFrame = incomingFrame.fin();

        try {
            opcode = incomingFrame.opcode();
        }
        catch (Exception ex) {
            connection.doFail(WS_PROTOCOL_ERROR, format(MSG_UNRECOGNIZED_OPCODE, leadByte & 0x0F));
        }

        byte maskByte = (byte) uint8Get(incomingFrame.buffer(), incomingFrame.offset() + 1);
        if ((maskByte & 0x80) != 0) {
            connection.doFail(WS_PROTOCOL_ERROR, MSG_MASKED_FRAME_FROM_SERVER);
        }

        switch (opcode) {
        case CONTINUATION:
            if (state == State.INITIAL) {
                // The first frame cannot be a fragmented frame..
                type = MessageType.EOS;
                connection.doFail(WS_PROTOCOL_ERROR, MSG_FIRST_FRAME_FRAGMENTED);
            }
            break;
        case TEXT:
            if (state == State.PROCESS_FRAME) {
                // In a subsequent fragmented frame, the opcode should NOT be set.
                connection.doFail(WS_PROTOCOL_ERROR, format(MSG_UNEXPECTED_OPCODE, Opcode.toInt(TEXT)));
            }
            type = MessageType.TEXT;
            messageLength = (state == State.INITIAL) && finalFrame ? payloadLength : -1;
            break;
        case BINARY:
            if (state == State.PROCESS_FRAME) {
                // In a subsequent fragmented frame, the opcode should NOT be set.
                connection.doFail(WS_PROTOCOL_ERROR, format(MSG_UNEXPECTED_OPCODE, Opcode.toInt(BINARY)));
            }
            type = MessageType.BINARY;
            messageLength = (state == State.INITIAL) && finalFrame ? payloadLength : -1;
            break;
        case CLOSE:
        case PING:
        case PONG:
            if (!incomingFrame.fin()) {
                // Control frames cannot be fragmented.
                connection.doFail(WS_PROTOCOL_ERROR, MSG_FRAGMENTED_CONTROL_FRAME);
            }

            DefaultWebSocketContext context = connection.getIncomingContext();
            IncomingSentinelExtension sentinel = (IncomingSentinelExtension) context.getSentinelExtension();
            sentinel.setTerminalConsumer(terminalControlFrameConsumer, incomingFrame.opcode());
            connection.processIncomingFrame(incomingFrameRO.wrap(heapBufferRO, networkBufferReadOffset));
            networkBufferReadOffset += incomingFrame.length();

            if (networkBufferReadOffset == networkBufferWriteOffset) {
                networkBufferReadOffset = 0;
                networkBufferWriteOffset = 0;
            }

            if (opcode == CLOSE) {
                type = MessageType.EOS;
                resetCurrentOwner();
                return -1;
            }
            leadByte = readDataFrameFully();
            break;
        default:
            connection.doFail(WS_PROTOCOL_ERROR, format(MSG_UNRECOGNIZED_OPCODE, opcode.ordinal()));
            break;
        }

        return leadByte;
    }

    private int utf8BytesToChars(
            ByteBuffer src,
            int srcOffset,
            long srcLength,
            char[] dest,
            int destOffset,
            int destLength) throws IOException {
        int destMark = destOffset;
        int index = 0;

        while (index < srcLength) {
            int b = -1;

            while (codePoint != 0 || ((index < srcLength) && (remainingBytes > 0))) {
                // Surrogate pair.
                if (codePoint != 0 && remainingBytes == 0) {
                    int charCount = charCount(codePoint);
                    if (charCount > destLength) {
                        int len = destOffset + charCount;
                        throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, destOffset, len, destLength));
                    }
                    toChars(codePoint, dest, destOffset);
                    destOffset += charCount;
                    destLength -= charCount;
                    codePoint = 0;
                    break;
                }

                // EOP
                if (index == srcLength) {
                    // We have multi-byte chars split across WebSocket frames.
                    break;
                }

                b = src.get(srcOffset++);
                index++;

                // character encoded in multiple bytes
                codePoint = remainingDecodeUTF8(codePoint, remainingBytes--, b);
            }

            if (index < srcLength) {
                b = src.get(srcOffset++);
                index++;

                // Detect whether character is encoded using multiple bytes.
                remainingBytes = remainingBytesUTF8(b);
                switch (remainingBytes) {
                case 0:
                    // No surrogate pair.
                    int asciiCodePoint = initialDecodeUTF8(remainingBytes, b);
                    assert charCount(asciiCodePoint) == 1;
                    toChars(asciiCodePoint, dest, destOffset++);
                    destLength--;
                    break;
                default:
                    codePoint = initialDecodeUTF8(remainingBytes, b);
                    break;
                }
            }
        }

        return destOffset - destMark;
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
            connection.doFail(WS_PROTOCOL_ERROR, format(MSG_UNRECOGNIZED_OPCODE, leadByte & 0x0F));
        }
    }

    private static class WsBinaryStream extends InputStream {
        private final byte[] binaryBuffer;
        private final WsMessageReader messageReader;

        private boolean fin;
        private int binaryBufferReadOffset;
        private int binaryBufferWriteOffset;

        public WsBinaryStream(WsURLConnectionImpl connection,
                              WsMessageReader messageReader) {
            this.binaryBuffer = new byte[connection.getMaxFramePayloadLength()];
            this.messageReader = messageReader;
        }

        @Override
        public int read() throws IOException {
            if (messageReader.getCurrentOwner() == null) {
                throw new IOException(MSG_NEXT_NOT_INVOKED);
            }

            if (fin && (binaryBufferReadOffset == binaryBufferWriteOffset)) {
                return -1;
            }

            if (messageReader.getCurrentOwner() != Thread.currentThread()) {
                throw new IOException(MSG_NOT_CURRENT_OWNER);
            }

            if (!messageReader.streaming()) {
                // This can be relaxed if we like. Meaning -- we can allow streaming of messages that fit in a single WebSocket
                // frame.
                throw new IOException(MSG_CAN_BE_READ_FULLY);
            }

            try {
                return readInternal();
            }
            catch (RuntimeException ex) {
                return -1;
            }
            catch (IOException ex) {
                throw ex;
            }
        }

        @Override
        public int read(byte[] buf, int offset, int length) throws IOException {
            if (buf == null) {
                throw new NullPointerException("Null buffer passed in");
            }
            else if ((offset < 0) || (length < 0) || (offset + length > buf.length)) {
                throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, offset + length, buf.length));
            }

            if (fin && (binaryBufferReadOffset == binaryBufferWriteOffset)) {
                return -1;
            }

            if (messageReader.getCurrentOwner() == null) {
                throw new IOException(MSG_NEXT_NOT_INVOKED);
            }

            if (messageReader.getCurrentOwner() != Thread.currentThread()) {
                throw new IOException(MSG_NOT_CURRENT_OWNER);
            }

            if (!messageReader.streaming()) {
                // This can be relaxed if we like. Meaning -- we can allow streaming of messages that fit in a single WebSocket
                // frame.
                throw new IOException(MSG_CAN_BE_READ_FULLY);
            }

            try {
                int mark = offset;
                try {
                    populateBuffer();

                    int len = Math.min(length, binaryBufferWriteOffset - binaryBufferReadOffset);
                    while (len > 0) {
                        buf[offset] = (byte) readInternal();
                        len--;
                        offset++;
                    }
                }
                catch (RuntimeException ex) {
                    int count = offset - mark;
                    return count == 0 ? -1 : count;
                }


                return offset - mark;
            }
            catch (RuntimeException ex) {
                return -1;
            }
            catch (IOException ex) {
                throw ex;
            }
        }

        @Override
        public void close() throws IOException {
            if (messageReader.getCurrentOwner() == null) {
                throw new IOException(MSG_NEXT_NOT_INVOKED);
            }

            if (messageReader.getCurrentOwner() != Thread.currentThread()) {
                throw new IOException(MSG_NOT_CURRENT_OWNER);
            }

            if (!messageReader.streaming()) {
                throw new IOException(MSG_CAN_BE_READ_FULLY);
            }

            if (!fin) {
                messageReader.skip();
            }
            resetState();
        }

        void resetState() throws IOException {
            fin = false;
            binaryBufferReadOffset = 0;
            binaryBufferReadOffset = 0;
        }

        private void populateBuffer() throws IOException {
            while (binaryBufferReadOffset == binaryBufferWriteOffset) {
                binaryBufferWriteOffset = 0;
                binaryBufferReadOffset = 0;

                if (fin) {
                    // The final frame of this message has been read. Make sure that read() returns -1.
                    messageReader.resetCurrentOwner();
                    throw new RuntimeException(MSG_END_OF_MESSAGE_STREAM);
                }

                binaryBufferWriteOffset = messageReader.readAndProcessBinaryFrame(binaryBuffer, 0, binaryBuffer.length);
                fin = messageReader.isFinalFrame();

                if (binaryBufferWriteOffset == -1) {
                    binaryBufferWriteOffset = 0;
                    throw new RuntimeException(MSG_END_OF_MESSAGE_STREAM);
                }
                else if (binaryBufferWriteOffset > 0) {
                    break;
                }
            }
        }

        private int readInternal() throws IOException {
            populateBuffer();

            assert binaryBufferReadOffset < binaryBufferWriteOffset;

            int b = binaryBuffer[binaryBufferReadOffset++];
            if (binaryBufferReadOffset == binaryBufferWriteOffset) {
                binaryBufferReadOffset = 0;
                binaryBufferWriteOffset = 0;

                if (fin) {
                    // The final frame of this message has been read.
                    messageReader.resetCurrentOwner();
                }
            }

            return b;
        }
    }

    private static class WsTextReader extends Reader {
        private final char[] textBuffer;
        private final WsMessageReader messageReader;

        private boolean fin;
        private int textBufferReadOffset;
        private int textBufferWriteOffset;

        public WsTextReader(WsURLConnectionImpl connection, WsMessageReader messageReader) {
            this.textBuffer = new char[connection.getMaxFramePayloadLength()];
            this.messageReader = messageReader;
        }

        @Override
        public int read(char[] cbuf, int offset, int length) throws IOException {
            if ((offset < 0) || ((offset + length) > cbuf.length) || (length < 0)) {
                int len = offset + length;
                throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, len, cbuf.length));
            }

            if (fin && (textBufferReadOffset == textBufferWriteOffset)) {
                return -1;
            }

            if (messageReader.getCurrentOwner() == null) {
                throw new IOException(MSG_NEXT_NOT_INVOKED);
            }

            if (messageReader.getCurrentOwner() != Thread.currentThread()) {
                throw new IOException(MSG_NOT_CURRENT_OWNER);
            }

            if (!messageReader.streaming()) {
                throw new IOException(MSG_CAN_BE_READ_FULLY);
            }

            while (textBufferReadOffset == textBufferWriteOffset) {
                textBufferReadOffset = 0;
                textBufferWriteOffset = 0;

                if (fin) {
                    // The final frame of this message has been read.
                    messageReader.resetCurrentOwner();
                    return -1;
                }

                textBufferWriteOffset = messageReader.readAndProcessTextFrame(textBuffer, 0, textBuffer.length);
                if (textBufferWriteOffset == -1) {
                    textBufferWriteOffset = 0;
                    messageReader.resetCurrentOwner();
                    return -1;
                }
                fin = messageReader.isFinalFrame();
            }

            assert textBufferReadOffset < textBufferWriteOffset;

            int charsRead = Math.min(length, textBufferWriteOffset - textBufferReadOffset);
            System.arraycopy(textBuffer, textBufferReadOffset, cbuf, offset, charsRead);
            textBufferReadOffset += charsRead;

            if (textBufferReadOffset == textBufferWriteOffset) {
                textBufferReadOffset = 0;
                textBufferWriteOffset = 0;

                if (fin) {
                    // The final frame of this message has been read.
                    messageReader.resetCurrentOwner();
                }
            }

            return charsRead;
        }

        @Override
        public void close() throws IOException {
            if (messageReader.getCurrentOwner() == null) {
                throw new IOException(MSG_NEXT_NOT_INVOKED);
            }

            if (messageReader.getCurrentOwner() != Thread.currentThread()) {
                throw new IOException(MSG_NOT_CURRENT_OWNER);
            }

            if (!messageReader.streaming()) {
                throw new IOException(MSG_CAN_BE_READ_FULLY);
            }

            if (!fin) {
                messageReader.skip();
            }
            resetState();
        }

        void resetState() throws IOException {
            fin = false;
            textBufferReadOffset = 0;
            textBufferReadOffset = 0;
        }
    }
}
