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

import static java.lang.Character.charCount;
import static java.lang.Character.toChars;
import static java.lang.String.format;
import static org.kaazing.netx.ws.WsURLConnection.WS_PROTOCOL_ERROR;
import static org.kaazing.netx.ws.internal.ext.flyweight.OpCode.BINARY;
import static org.kaazing.netx.ws.internal.ext.flyweight.OpCode.CLOSE;
import static org.kaazing.netx.ws.internal.ext.flyweight.OpCode.CONTINUATION;
import static org.kaazing.netx.ws.internal.ext.flyweight.OpCode.TEXT;
import static org.kaazing.netx.ws.internal.util.FrameUtil.calculateCapacity;
import static org.kaazing.netx.ws.internal.util.Utf8Util.initialDecodeUTF8;
import static org.kaazing.netx.ws.internal.util.Utf8Util.remainingBytesUTF8;
import static org.kaazing.netx.ws.internal.util.Utf8Util.remainingDecodeUTF8;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.kaazing.netx.ws.MessageReader;
import org.kaazing.netx.ws.MessageType;
import org.kaazing.netx.ws.internal.WsURLConnectionImpl;
import org.kaazing.netx.ws.internal.ext.WebSocketContext;
import org.kaazing.netx.ws.internal.ext.flyweight.Flyweight;
import org.kaazing.netx.ws.internal.ext.flyweight.Frame;
import org.kaazing.netx.ws.internal.ext.flyweight.FrameRW;
import org.kaazing.netx.ws.internal.ext.flyweight.OpCode;
import org.kaazing.netx.ws.internal.ext.function.WebSocketFrameConsumer;

public final class WsMessageReader extends MessageReader {
    private static final String MSG_NULL_CONNECTION = "Null HttpURLConnection passed in";
    private static final String MSG_INDEX_OUT_OF_BOUNDS = "offset = %d; (offset + length) = %d; buffer length = %d";
    private static final String MSG_NON_BINARY_FRAME = "Non-text frame - opcode = 0x%02X";
    private static final String MSG_NON_TEXT_FRAME = "Non-binary frame - opcode = 0x%02X";
    private static final String MSG_BUFFER_SIZE_SMALL = "Buffer's remaining capacity %d too small for payload of size %d";
    private static final String MSG_RESERVED_BITS_SET = "Protocol Violation: Reserved bits set 0x%02X";
    private static final String MSG_UNRECOGNIZED_OPCODE = "Protocol Violation: Unrecognized opcode %d";
    private static final String MSG_FIRST_FRAME_FRAGMENTED = "Protocol Violation: First frame cannot be a fragmented frame";
    private static final String MSG_UNEXPECTED_OPCODE = "Protocol Violation: Opcode 0x%02X expected only in the initial frame";
    private static final String MSG_FRAGMENTED_CONTROL_FRAME = "Protocol Violation: Fragmented control frame 0x%02X";
    private static final String MSG_FRAGMENTED_FRAME = "Protocol Violation: Fragmented frame 0x%02X";

    private static final int BUFFER_CHUNK_SIZE = 8192;

    private final WsURLConnectionImpl connection;
    private final InputStream in;
    private final FrameRW incomingFrame;

    private byte[] networkBuffer;
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

    private enum State {
        INITIAL, PROCESS_MESSAGE_TYPE, PROCESS_FRAME;
    };

    final WebSocketFrameConsumer terminalBinaryFrameConsumer = new WebSocketFrameConsumer() {
        @Override
        public void accept(WebSocketContext context, Frame frame) throws IOException {
            OpCode opCode = frame.opCode();
            long xformedPayloadLength = frame.payloadLength();
            int xformedPayloadOffset = frame.payloadOffset();

            switch (opCode) {
            case BINARY:
            case CONTINUATION:
                if ((opCode == BINARY) && fragmented) {
                    byte leadByte = (byte) Flyweight.uint8Get(frame.buffer(), frame.offset());
                    connection.doFail(WS_PROTOCOL_ERROR, format(MSG_FRAGMENTED_FRAME, leadByte));
                }

                if ((opCode == CONTINUATION) && !fragmented) {
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
                connection.doFail(WS_PROTOCOL_ERROR, format(MSG_NON_BINARY_FRAME, OpCode.toInt(opCode)));
                break;
            }
        }
    };

    private final WebSocketFrameConsumer terminalTextFrameConsumer = new WebSocketFrameConsumer() {
        @Override
        public void accept(WebSocketContext context, Frame frame) throws IOException {
            OpCode opCode = frame.opCode();
            long xformedPayloadLength = frame.payloadLength();
            int xformedPayloadOffset = frame.payloadOffset();

            switch (opCode) {
            case TEXT:
            case CONTINUATION:
                if ((opCode == TEXT) && fragmented) {
                    byte leadByte = (byte) Flyweight.uint8Get(frame.buffer(), frame.offset());
                    connection.doFail(WS_PROTOCOL_ERROR, format(MSG_FRAGMENTED_FRAME, leadByte));
                }

                if ((opCode == CONTINUATION) && !fragmented) {
                    byte leadByte = (byte) Flyweight.uint8Get(frame.buffer(), frame.offset());
                    connection.doFail(WS_PROTOCOL_ERROR, format(MSG_FRAGMENTED_FRAME, leadByte));
                }

                int charsConverted = convertBytesToChars(frame.buffer(),
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
                connection.doFail(WS_PROTOCOL_ERROR, format(MSG_NON_BINARY_FRAME, OpCode.toInt(opCode)));
                break;
            }
        }
    };

    private final WebSocketFrameConsumer terminalControlFrameConsumer = new WebSocketFrameConsumer() {
        @Override
        public void accept(WebSocketContext context, Frame frame) throws IOException {
            OpCode opCode = frame.opCode();

            switch (opCode) {
            case CLOSE:
                connection.sendClose(frame);
                break;
            case PING:
                connection.sendPong(frame);
                break;
            case PONG:
                break;
            default:
                connection.doFail(WS_PROTOCOL_ERROR, format("Unexpected frame opcode 0x%02X", OpCode.toInt(opCode)));
                break;
            }
        }
    };

    public WsMessageReader(WsURLConnectionImpl connection) throws IOException {
        if (connection == null) {
            throw new NullPointerException(MSG_NULL_CONNECTION);
        }

        this.connection = connection;
        this.in = connection.getTcpInputStream();
        this.state = State.INITIAL;
        this.incomingFrame = new FrameRW();

        this.fragmented = false;
        this.networkBufferReadOffset = 0;
        this.networkBufferWriteOffset = 0;
        this.networkBuffer = new byte[BUFFER_CHUNK_SIZE];

        this.applicationBufferWriteOffset = 0;
        this.applicationBufferLength = 0;
    }

    @Override
    public int read(byte[] buf) throws IOException {
        return this.read(buf, 0, buf.length);
    }

    @Override
    public int read(final byte[] buf, final int offset, final int length) throws IOException {
        if (buf == null) {
            throw new NullPointerException("Null buf passed in");
        }
        else if ((offset < 0) || ((offset + length) > buf.length) || (length < 0)) {
            int len = offset + length;
            throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, len, buf.length));
        }

        // Check whether next() has been invoked before this method. If it wasn't invoked, then read the header byte.
        switch (state) {
        case INITIAL:
        case PROCESS_MESSAGE_TYPE:
            readMessageType();
            break;
        default:
            break;
        }

        applicationByteBuffer = buf;
        applicationBufferWriteOffset = offset;

        boolean finalFrame = false;

        do {
            switch (type) {
            case EOS:
                return -1;
            case TEXT:
                throw new IOException(MSG_NON_BINARY_FRAME);
            default:
                break;
            }

            incomingFrame.wrap(ByteBuffer.wrap(networkBuffer,
                                               networkBufferReadOffset,
                                               networkBufferWriteOffset - networkBufferReadOffset), networkBufferReadOffset);
            finalFrame = incomingFrame.fin();

            validateOpCode();
            connection.getIncomingSentinel().setTerminalConsumer(terminalBinaryFrameConsumer, incomingFrame.opCode());
            connection.processFrame(incomingFrame, connection.getIncomingSentinel());
            networkBufferReadOffset += incomingFrame.length();
            state = State.PROCESS_MESSAGE_TYPE;

            if (networkBufferReadOffset == networkBufferWriteOffset) {
                networkBufferReadOffset = 0;
                networkBufferWriteOffset = 0;
            }

            if (!finalFrame) {
                // Start reading the CONTINUATION frame for the message.
                assert state == State.PROCESS_MESSAGE_TYPE;
                readMessageType();
            }
        } while (!finalFrame);

        state = State.INITIAL;

        if ((applicationBufferWriteOffset - offset == 0) && (length > 0)) {
            // An extension can consume the entire message and not let it surface to the app. In which case, we just try to
            // read the next message.
            return read(buf, offset, length);
        }

        return applicationBufferWriteOffset - offset;
    }

    @Override
    public int read(char[] buf) throws IOException {
        return this.read(buf, 0, buf.length);
    }

    @Override
    public int read(final char[] buf, int offset, int length) throws IOException {
        if (buf == null) {
            throw new NullPointerException("Null buf passed in");
        }
        else if ((offset < 0) || ((offset + length) > buf.length) || (length < 0)) {
            int len = offset + length;
            throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, len, buf.length));
        }

        // Check whether next() has been invoked before this method. If it wasn't invoked, then read the header byte.
        switch (state) {
        case INITIAL:
        case PROCESS_MESSAGE_TYPE:
            readMessageType();
            break;
        default:
            break;
        }

        applicationCharBuffer = buf;
        applicationBufferWriteOffset = offset;
        applicationBufferLength = length;

        boolean finalFrame = false;

        do {
            switch (type) {
            case EOS:
                return -1;
            case BINARY:
                throw new IOException(MSG_NON_TEXT_FRAME);
            default:
                break;
            }

            incomingFrame.wrap(ByteBuffer.wrap(networkBuffer,
                                               networkBufferReadOffset,
                                               networkBufferWriteOffset - networkBufferReadOffset), networkBufferReadOffset);

            finalFrame = incomingFrame.fin();

            validateOpCode();
            connection.getIncomingSentinel().setTerminalConsumer(terminalTextFrameConsumer, incomingFrame.opCode());
            connection.processFrame(incomingFrame, connection.getIncomingSentinel());
            networkBufferReadOffset += incomingFrame.length();
            state = State.PROCESS_MESSAGE_TYPE;

            if (networkBufferReadOffset == networkBufferWriteOffset) {
                networkBufferReadOffset = 0;
                networkBufferWriteOffset = 0;
            }

            if (!finalFrame) {
                // Start reading the CONTINUATION frame for the message.
                assert state == State.PROCESS_MESSAGE_TYPE;
                readMessageType();
            }
        } while (!finalFrame);

        state = State.INITIAL;

        if ((applicationBufferWriteOffset - offset == 0) && (length > 0)) {
            // An extension can consume the entire message and not let it surface to the app. In which case, we just try to
            // read the next message.
            return read(buf, offset, length);
        }

        return applicationBufferWriteOffset - offset;
    }

    @Override
    public MessageType peek() {
        return type;
    }

    @Override
    public MessageType next() throws IOException {
        switch (state) {
        case INITIAL:
        case PROCESS_MESSAGE_TYPE:
            readMessageType();
            break;
        default:
            break;
        }

        return type;
    }

    public void close() throws IOException {
        in.close();
        type = null;
        state = null;
    }

    private int readMessageType() throws IOException {
        assert state == State.PROCESS_MESSAGE_TYPE || state == State.INITIAL;

        if (networkBufferWriteOffset == 0) {
            int bytesRead = in.read(networkBuffer, 0, networkBuffer.length);
            if (bytesRead == -1) {
                type = MessageType.EOS;
                return -1;
            }

            networkBufferReadOffset = 0;
            networkBufferWriteOffset = bytesRead;
        }

        int numBytes = ensureFrameMetadata();
        if (numBytes == -1) {
            type = MessageType.EOS;
            return -1;
        }

        incomingFrame.wrap(ByteBuffer.wrap(networkBuffer,
                                           networkBufferReadOffset,
                                           networkBufferWriteOffset - networkBufferReadOffset), networkBufferReadOffset);
        int payloadLength = incomingFrame.payloadLength();

        if (incomingFrame.offset() + payloadLength > networkBufferWriteOffset) {
            // We have an incomplete frame. Let's read it fully. Ensure that the buffer has adequate space.
            if (payloadLength > networkBuffer.length) {
                // networkBuffer needs to be resized.
                int additionalBytes = Math.max(BUFFER_CHUNK_SIZE, payloadLength);
                byte[] netBuffer = new byte[networkBuffer.length + additionalBytes];
                int len = networkBufferWriteOffset - networkBufferReadOffset;

                System.arraycopy(networkBuffer, networkBufferReadOffset, netBuffer, 0, len);
                networkBuffer = netBuffer;
                networkBufferReadOffset = 0;
                networkBufferWriteOffset = len;
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

            int frameLength = calculateCapacity(false, payloadLength);
            int remainingBytes = networkBufferReadOffset + frameLength - networkBufferWriteOffset;
            while (remainingBytes > 0) {
                int bytesRead = in.read(networkBuffer, networkBufferWriteOffset, remainingBytes);
                if (bytesRead == -1) {
                    type = MessageType.EOS;
                    return -1;
                }

                remainingBytes -= bytesRead;
                networkBufferWriteOffset += bytesRead;
            }

            incomingFrame.wrap(ByteBuffer.wrap(networkBuffer,
                                               networkBufferReadOffset,
                                               networkBufferWriteOffset - networkBufferReadOffset), networkBufferReadOffset);
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

        OpCode opCode = null;

        try {
            opCode = incomingFrame.opCode();
        }
        catch (Exception ex) {
            connection.doFail(WS_PROTOCOL_ERROR, format(MSG_UNRECOGNIZED_OPCODE, leadByte & 0x0F));
        }

        switch (opCode) {
        case CONTINUATION:
            if (state == State.INITIAL) {
                // The first frame cannot be a fragmented frame..
                type = MessageType.EOS;
                connection.doFail(WS_PROTOCOL_ERROR, MSG_FIRST_FRAME_FRAGMENTED);
            }
            break;
        case TEXT:
            if (state == State.PROCESS_MESSAGE_TYPE) {
                // In a subsequent fragmented frame, the opcode should NOT be set.
                connection.doFail(WS_PROTOCOL_ERROR, format(MSG_UNEXPECTED_OPCODE, OpCode.toInt(TEXT)));
            }
            type = MessageType.TEXT;
            break;
        case BINARY:
            if (state == State.PROCESS_MESSAGE_TYPE) {
                // In a subsequent fragmented frame, the opcode should NOT be set.
                connection.doFail(WS_PROTOCOL_ERROR, format(MSG_UNEXPECTED_OPCODE, OpCode.toInt(BINARY)));
            }
            type = MessageType.BINARY;
            break;
        case CLOSE:
        case PING:
        case PONG:
            if (!incomingFrame.fin()) {
                // Control frames cannot be fragmented.
                connection.doFail(WS_PROTOCOL_ERROR, MSG_FRAGMENTED_CONTROL_FRAME);
            }

            connection.getIncomingSentinel().setTerminalConsumer(terminalControlFrameConsumer, incomingFrame.opCode());
            connection.processFrame(incomingFrame, connection.getIncomingSentinel());
            networkBufferReadOffset += incomingFrame.length();

            if (networkBufferReadOffset == networkBufferWriteOffset) {
                networkBufferReadOffset = 0;
                networkBufferWriteOffset = 0;
            }

            if (opCode == CLOSE) {
                type = MessageType.EOS;
                return -1;
            }
            leadByte = readMessageType();
            break;
        default:
            connection.doFail(WS_PROTOCOL_ERROR, format(MSG_UNRECOGNIZED_OPCODE, opCode.ordinal()));
            break;
        }

        state = State.PROCESS_FRAME;
        return leadByte;
    }

    private int convertBytesToChars(ByteBuffer src,
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

    private void validateOpCode() throws IOException {
        int leadByte = Flyweight.uint8Get(incomingFrame.buffer(), incomingFrame.offset());
        try {
            incomingFrame.opCode();
        }
        catch (Exception ex) {
            connection.doFail(WS_PROTOCOL_ERROR, format(MSG_UNRECOGNIZED_OPCODE, leadByte & 0x0F));
        }
    }
}
