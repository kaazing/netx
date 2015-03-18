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
import static org.kaazing.netx.ws.WsURLConnection.WS_NORMAL_CLOSE;
import static org.kaazing.netx.ws.WsURLConnection.WS_PROTOCOL_ERROR;

import java.io.IOException;
import java.io.InputStream;

import org.kaazing.netx.ws.MessageReader;
import org.kaazing.netx.ws.MessageType;
import org.kaazing.netx.ws.internal.WsURLConnectionImpl;
import org.kaazing.netx.ws.internal.ext.frame.Control;
import org.kaazing.netx.ws.internal.ext.frame.Data;
import org.kaazing.netx.ws.internal.ext.frame.Frame;
import org.kaazing.netx.ws.internal.ext.frame.Frame.Payload;
import org.kaazing.netx.ws.internal.ext.frame.FrameFactory;
import org.kaazing.netx.ws.internal.ext.frame.OpCode;

public final class WsMessageReader extends MessageReader {
    private static final String MSG_NULL_CONNECTION = "Null HttpURLConnection passed in";
    private static final String MSG_INDEX_OUT_OF_BOUNDS = "offset = %d; (offset + length) = %d; buffer length = %d";
    private static final String MSG_NON_BINARY_FRAME = "Non-text frame - opcode = 0x%02X";
    private static final String MSG_NON_TEXT_FRAME = "Non-binary frame - opcode = 0x%02X";
    private static final String MSG_MASKED_FRAME_FROM_SERVER = "Masked server-to-client frame";
    private static final String MSG_END_OF_STREAM = "End of stream";
    private static final String MSG_BUFFER_SIZE_SMALL = "Buffer's remaining capacity %d too small for payload of size %d";
    private static final String MSG_RESERVED_BITS_SET = "Protocol Violation: Reserved bits set 0x%02X";
    private static final String MSG_UNRECOGNIZED_OPCODE = "Protocol Violation: Unrecognized opcode %d";
    private static final String MSG_FIRST_FRAME_FRAGMENTED = "Protocol Violation: First frame cannot be a fragmented frame";
    private static final String MSG_UNEXPECTED_OPCODE = "Protocol Violation: Opcode 0x%02X expected only in the initial frame";
    private static final String MSG_FRAGMENTED_CONTROL_FRAME = "Protocol Violation: Fragmented control frame 0x%02X";
    private static final String MSG_PAYLOAD_LENGTH_EXCEEDED = "Protocol Violation: %s payload is more than 125 bytes";

    private static final int MAX_COMMAND_FRAME_PAYLOAD = 125;

    private final WsURLConnectionImpl connection;
    private final InputStream in;
    private final byte[] header;

    private MessageType type;
    private int headerOffset;
    private State state;
    private boolean fin;
    private long payloadLength;
    private int payloadOffset;
    private Data dataFrame;
    private char[] textReceiveBuffer;

    private enum State {
        INITIAL, READ_FLAGS_AND_OPCODE, READ_PAYLOAD_LENGTH, READ_PAYLOAD;
    };

    public WsMessageReader(WsURLConnectionImpl connection) throws IOException {
        if (connection == null) {
            throw new NullPointerException(MSG_NULL_CONNECTION);
        }

        this.connection = connection;
        this.in = connection.getTcpInputStream();
        this.header = new byte[10];
        this.state = State.INITIAL;
        this.payloadOffset = -1;
    }

    @Override
    public synchronized int read(byte[] buf) throws IOException {
        return this.read(buf, 0, buf.length);
    }

    @Override
    public synchronized int read(byte[] buf, int offset, int length) throws IOException {
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
        case READ_FLAGS_AND_OPCODE:
            readHeaderByte();
            break;
        default:
            break;
        }

        boolean finalFrame = fin;
        int mark = offset;
        int bytesRead = 0;

        do {
            switch (type) {
            case EOS:
                return -1;
            case TEXT:
                throw new IOException(MSG_NON_BINARY_FRAME);
            default:
                break;
            }

            int retval = readPayloadLength();
            if (retval == -1) {
                connection.doFail(WS_NORMAL_CLOSE, MSG_END_OF_STREAM);
            }

            dataFrame = (Data) getFrame(header[0] & 0x0F, payloadLength);
            readBinary(dataFrame);
            bytesRead = dataFrame.getLength();

            if (length < dataFrame.getLength()) {
                // MessageReader requires reading the entire message/frame. So, if there isn't enough space to read the frame,
                // we should throw an exception.
                throw new IOException(format(MSG_BUFFER_SIZE_SMALL, length, payloadLength));
            }

            Payload payload = dataFrame.getPayload();
            System.arraycopy(payload.buffer().array(), payload.offset(), buf, offset, dataFrame.getLength());

            offset += bytesRead;
            length -= bytesRead;

            // Once the payload is read, use fin to figure out whether this was the final frame.
            finalFrame = fin;

            if (!finalFrame) {
                // Start reading the CONTINUATION frame for the message.
                assert state == State.READ_FLAGS_AND_OPCODE;
                readHeaderByte();
            }
        } while (!finalFrame);

        state = State.INITIAL;

        if ((offset - mark == 0) && (length > 0)) {
            // An extension can consume the entire message and not let it surface to the app. In which case, we just try to
            // read the next message.
            return read(buf, offset, length);
        }

        return offset - mark;
    }

    @Override
    public synchronized int read(char[] buf) throws IOException {
        return this.read(buf, 0, buf.length);
    }

    @Override
    public synchronized int read(char[] buf, int offset, int length) throws IOException {
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
        case READ_FLAGS_AND_OPCODE:
            readHeaderByte();
            break;
        default:
            break;
        }

        boolean finalFrame = fin;
        int mark = offset;

        do {
            switch (type) {
            case EOS:
                return -1;
            case BINARY:
                throw new IOException(MSG_NON_TEXT_FRAME);
            default:
                break;
            }

            int retval = readPayloadLength();
            if (retval == -1) {
                connection.doFail(WS_NORMAL_CLOSE, MSG_END_OF_STREAM);
            }

            dataFrame = (Data) getFrame(header[0] & 0x0F, payloadLength);
            readText(dataFrame);

            Payload payload = dataFrame.getPayload();
            textReceiveBuffer = new String(payload.buffer().array(), payload.offset(), dataFrame.getLength()).toCharArray();

            if (length < textReceiveBuffer.length) {
                // MessageReader requires reading the entire message/frame. So, if there isn't enough space to read the frame,
                // we should throw an exception.
                throw new IOException(format(MSG_BUFFER_SIZE_SMALL, length, payloadLength));
            }

            System.arraycopy(textReceiveBuffer, 0, buf, offset, textReceiveBuffer.length);

            offset += textReceiveBuffer.length;
            length -= textReceiveBuffer.length;

            // Once the payload is read, use fin to figure out whether this was the final frame.
            finalFrame = fin;

            if (!finalFrame) {
                // Start reading the CONTINUATION frame for the message.
                assert state == State.READ_FLAGS_AND_OPCODE;
                readHeaderByte();
            }
        } while (!finalFrame);

        state = State.INITIAL;

        if ((offset - mark == 0) && (length > 0)) {
            // An extension can consume the entire message and not let it surface to the app. In which case, we just try to
            // read the next message.
            return read(buf, offset, length);
        }

        return offset - mark;
    }

    @Override
    public synchronized MessageType peek() {
        return type;
    }

    @Override
    public synchronized MessageType next() throws IOException {
        switch (state) {
        case INITIAL:
        case READ_FLAGS_AND_OPCODE:
            readHeaderByte();
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

    private synchronized int readHeaderByte() throws IOException {
        assert state == State.READ_FLAGS_AND_OPCODE || state == State.INITIAL;

        int headerByte = in.read();
        if (headerByte == -1) {
            type = MessageType.EOS;
            return -1;
        }

        header[headerOffset++] = (byte) headerByte;

        fin = (headerByte & 0x80) != 0;

        int flags = (header[0] & 0xF0) >> 4;
        switch (flags) {
        case 0:
        case 8:
            break;
        default:
            connection.doFail(WS_PROTOCOL_ERROR, format(MSG_RESERVED_BITS_SET, flags));
            break;
        }

        int opcode = headerByte & 0x0F;
        switch (opcode) {
        case 0x00:
            if (state == State.INITIAL) {
                // The first frame cannot be a fragmented frame..
                type = MessageType.EOS;
                connection.doFail(WS_PROTOCOL_ERROR, MSG_FIRST_FRAME_FRAGMENTED);
            }
            break;
        case 0x01:
            if (state == State.READ_FLAGS_AND_OPCODE) {
                // In a subsequent fragmented frame, the opcode should NOT be set.
                connection.doFail(WS_PROTOCOL_ERROR, format(MSG_UNEXPECTED_OPCODE, opcode));
            }
            type = MessageType.TEXT;
            break;
        case 0x02:
            if (state == State.READ_FLAGS_AND_OPCODE) {
                // In a subsequent fragmented frame, the opcode should NOT be set.
                connection.doFail(WS_PROTOCOL_ERROR, format(MSG_UNEXPECTED_OPCODE, opcode));
            }
            type = MessageType.BINARY;
            break;
        case 0x08:
        case 0x09:
        case 0x0A:
            if (!fin) {
                // Control frames cannot be fragmented.
                connection.doFail(WS_PROTOCOL_ERROR, MSG_FRAGMENTED_CONTROL_FRAME);
            }

            filterControlFrames();
            if ((header[0] & 0x0F) == 0x08) {
                type = MessageType.EOS;
                return -1;
            }
            headerByte = readHeaderByte();
            break;
        default:
            connection.doFail(WS_PROTOCOL_ERROR, format(MSG_UNRECOGNIZED_OPCODE, opcode));
            break;
        }

        state = State.READ_PAYLOAD_LENGTH;
        return headerByte;
    }

    private synchronized int readPayloadLength() throws IOException {
        while (payloadLength == 0) {
            while (payloadOffset == -1) {
                int headerByte = in.read();
                if (headerByte == -1) {
                    return -1;
                }
                header[headerOffset++] = (byte) headerByte;
                switch (headerOffset) {
                case 2:
                    boolean masked = (header[1] & 0x80) != 0x00;
                    if (masked) {
                        connection.doFail(WS_PROTOCOL_ERROR, MSG_MASKED_FRAME_FROM_SERVER);
                    }
                    switch (header[1] & 0x7f) {
                    case 126:
                    case 127:
                        break;
                    default:
                        payloadOffset = 0;
                        payloadLength = payloadLength(header);
                        break;
                    }
                    break;
                case 4:
                    switch (header[1] & 0x7f) {
                    case 126:
                        payloadOffset = 0;
                        payloadLength = payloadLength(header);
                        break;
                    default:
                        break;
                    }
                    break;
                case 10:
                    switch (header[1] & 0x7f) {
                    case 127:
                        payloadOffset = 0;
                        payloadLength = payloadLength(header);
                        break;
                    default:
                        break;
                    }
                    break;
                }
            }

            // This can happen if the payload length is zero.
            if (payloadOffset == payloadLength) {
                payloadOffset = -1;
                headerOffset = 0;
                break;
            }
        }

        state = State.READ_PAYLOAD;
        return 0;
    }

    private synchronized void readBinary(Data frame) throws IOException {
        if (frame.getLength() == 0) {
            state = State.READ_FLAGS_AND_OPCODE;
            return;
        }

        connection.receiveBinaryFrame(frame);

        // Entire WebSocket frame has been read. Reset the state.
        headerOffset = 0;
        payloadLength = 0;
        payloadOffset = -1;
        state = State.READ_FLAGS_AND_OPCODE;
    }

    private synchronized void readText(Data frame) throws IOException {
        if (frame.getLength() == 0) {
            state = State.READ_FLAGS_AND_OPCODE;
            return;
        }

        connection.receiveTextFrame(frame);

        // Entire WebSocket frame has been read. Reset the state.
        headerOffset = 0;
        payloadLength = 0;
        payloadOffset = -1;
        state = State.READ_FLAGS_AND_OPCODE;
    }

    private void filterControlFrames() throws IOException {
        int opcode = header[0] & 0x0F;

        if ((opcode == 0x00) || (opcode == 0x01) || (opcode == 0x02)) {
            return;
        }

        readPayloadLength();

        if (payloadLength > MAX_COMMAND_FRAME_PAYLOAD) {
            connection.doFail(WS_PROTOCOL_ERROR, format(MSG_PAYLOAD_LENGTH_EXCEEDED, opcode));
        }

        Control frame = (Control) getFrame(opcode, payloadLength);
        connection.receiveControlFrame(frame);

        // Get ready to read the next frame after CLOSE frame is sent out.
        payloadLength = 0;
        payloadOffset = -1;
        headerOffset = 0;
        state = State.READ_FLAGS_AND_OPCODE;
    }

    private Frame getFrame(int opcode, long payloadLen) throws IOException {
        FrameFactory factory = connection.getInputStateMachine().getFrameFactory();
        OpCode opCode = OpCode.fromInt(opcode);
        return factory.getFrame(opCode, fin, false, payloadLen);
    }

    private static int payloadLength(byte[] header) {
        int length = header[1] & 0x7f;

        switch (length) {
        case 126:
            return (header[2] & 0xff) << 8 | (header[3] & 0xff);
        case 127:
            return (header[2] & 0xff) << 56 |
                   (header[3] & 0xff) << 48 |
                   (header[4] & 0xff) << 40 |
                   (header[5] & 0xff) << 32 |
                   (header[6] & 0xff) << 24 |
                   (header[7] & 0xff) << 16 |
                   (header[8] & 0xff) << 8  |
                   (header[9] & 0xff);
        default:
            return length;
        }
    }
}
