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
import static org.kaazing.netx.ws.WsURLConnection.WS_ABNORMAL_CLOSE;
import static org.kaazing.netx.ws.WsURLConnection.WS_MISSING_STATUS_CODE;
import static org.kaazing.netx.ws.WsURLConnection.WS_NORMAL_CLOSE;
import static org.kaazing.netx.ws.WsURLConnection.WS_PROTOCOL_ERROR;
import static org.kaazing.netx.ws.WsURLConnection.WS_UNSUCCESSFUL_TLS_HANDSHAKE;
import static org.kaazing.netx.ws.internal.util.Utf8Util.initialDecodeUTF8;
import static org.kaazing.netx.ws.internal.util.Utf8Util.remainingBytesUTF8;
import static org.kaazing.netx.ws.internal.util.Utf8Util.remainingDecodeUTF8;
import static org.kaazing.netx.ws.internal.util.Utf8Util.validBytesUTF8;

import java.io.IOException;
import java.io.InputStream;

import org.kaazing.netx.ws.MessageReader;
import org.kaazing.netx.ws.MessageType;
import org.kaazing.netx.ws.internal.WsURLConnectionImpl;

public final class WsMessageReader extends MessageReader {
    private static final String MSG_NULL_CONNECTION = "Null HttpURLConnection passed in";
    private static final String MSG_INDEX_OUT_OF_BOUNDS = "offset = %d; (offset + length) = %d; buffer length = %d";
    private static final String MSG_NON_BINARY_FRAME = "Non-text frame - opcode = 0x%02X";
    private static final String MSG_NON_TEXT_FRAME = "Non-binary frame - opcode = 0x%02X";
    private static final String MSG_MASKED_FRAME_FROM_SERVER = "Masked server-to-client frame";
    private static final String MSG_END_OF_STREAM = "End of stream";
    private static final String MSG_BUFFER_SIZE_SMALL = "Buffer size %d too small for payload size %d";
    private static final String MSG_PING_PAYLOAD_LENGTH_EXCEEDED = "Protocol Violation: PING payload is more than %d bytes";
    private static final String MSG_RESERVED_BITS_SET = "Protocol Violation: Reserved bits set 0x%02X";
    private static final String MSG_PONG_PAYLOAD_LENGTH_EXCEEDED = "Protocol Violation: PONG payload is more than %d bytes";
    private static final String MSG_UNRECOGNIZED_OPCODE = "Protocol Violation: Unrecognized opcode %d";
    private static final String MSG_CLOSE_FRAME_VIOLATION = "Protocol Violation: CLOSE Frame - Code = %d; Reason Length = %d";
    private static final String MSG_FIRST_FRAME_FRAGMENTED = "Protocol Violation: First frame cannot be a fragmented frame";
    private static final String MSG_UNEXPECTED_OPCODE = "Protocol Violation: Opcode 0x%02X expected only in the initial frame";
    private static final String MSG_FRAGMENTED_CONTROL_FRAME = "Protocol Violation: Fragmented control frame 0x%02X";

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
    private int remainingBytes;
    private int codePoint;

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
        if ((offset < 0) || ((offset + length) > buf.length) || (length < 0)) {
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

        switch (type) {
        case EOS:
            return -1;
        case TEXT:
            throw new IOException(MSG_NON_BINARY_FRAME);
        default:
            break;
        }

        boolean finalFrame = fin;
        int mark = offset;
        int bytesRead = 0;

        do {
            int retval = readPayloadLength();
            if (retval == -1) {
                connection.doFail(WS_NORMAL_CLOSE, MSG_END_OF_STREAM);
            }

            bytesRead = readBinary(buf, offset, length);

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
        return offset - mark;
    }

    @Override
    public synchronized int read(char[] buf) throws IOException {
        return this.read(buf, 0, buf.length);
    }

    @Override
    public synchronized int read(char[] buf, int offset, int length) throws IOException {
        if ((offset < 0) || ((offset + length) > buf.length) || (length < 0)) {
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

        switch (type) {
        case EOS:
            return -1;
        case BINARY:
            throw new IOException(MSG_NON_TEXT_FRAME);
        default:
            break;
        }

        boolean finalFrame = fin;
        int mark = offset;
        int charsRead = 0;

        do {
            int retval = readPayloadLength();
            if (retval == -1) {
                connection.doFail(WS_NORMAL_CLOSE, MSG_END_OF_STREAM);
            }

            charsRead = readText(buf, offset, length);

            offset += charsRead;
            length -= charsRead;

            // Once the payload is read, use fin to figure out whether this was the final frame.
            finalFrame = fin;

            if (!finalFrame) {
                // Start reading the CONTINUATION frame for the message.
                assert state == State.READ_FLAGS_AND_OPCODE;
                readHeaderByte();
            }
        } while (!finalFrame);

        state = State.INITIAL;
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

    private synchronized int readBinary(byte[] buf, int offset, int length) throws IOException {
        if (payloadLength == 0) {
            state = State.READ_FLAGS_AND_OPCODE;
            return 0;
        }

        int bytesRead = 0;
        int len = length;
        int mark = offset;

        if (buf.length < (offset + payloadLength)) {
            int size = buf.length - offset;
            throw new IOException(format(MSG_BUFFER_SIZE_SMALL, size, payloadLength));
        }

        // Read the entire payload from the current frame into the buffer.
        while (payloadOffset < payloadLength) {
            assert offset + len <= buf.length;

            bytesRead = in.read(buf, offset, (int) Math.min(len, payloadLength));
            if (bytesRead == -1) {
                connection.doFail(WS_PROTOCOL_ERROR, MSG_END_OF_STREAM);
            }

            len -= bytesRead;
            offset += bytesRead;
            payloadOffset += bytesRead;
        }

        assert payloadOffset == payloadLength ;

        // Entire WebSocket frame has been read. Reset the state.
        headerOffset = 0;
        payloadLength = 0;
        payloadOffset = -1;
        state = State.READ_FLAGS_AND_OPCODE;

        return offset - mark;
    }

    private synchronized int readText(char[] cbuf, int offset, int length) throws IOException {
        if (payloadLength == 0) {
            state = State.READ_FLAGS_AND_OPCODE;
            return 0;
        }

        int mark = offset;

        outer:
        for (;;) {
            int b = -1;

            // code point may be split across frames
            while (codePoint != 0 || remainingBytes > 0) {
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
                    connection.disconnect();
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
                connection.disconnect();
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
            connection.doFail(WS_NORMAL_CLOSE, MSG_END_OF_STREAM);
        }

        // Entire WebSocket frame has been read. Reset the state.
        headerOffset = 0;
        payloadLength = 0;
        payloadOffset = -1;
        state = State.READ_FLAGS_AND_OPCODE;

        return offset - mark;
    }

    private void filterControlFrames() throws IOException {
        int opcode = header[0] & 0x0F;

        if ((opcode == 0x00) || (opcode == 0x01) || (opcode == 0x02)) {
            return;
        }

        readPayloadLength();

        switch (opcode) {
        case 0x08:
            int code = 0;
            int closeCodeRO = 0;
            byte[] reason = null;

            if (payloadLength >= 2) {
                // Read the first two bytes as the CLOSE code.
                int b1 = in.read();
                if (b1 == -1) {
                    connection.doFail(WS_PROTOCOL_ERROR, MSG_END_OF_STREAM);
                }

                int b2 = in.read();
                if (b2 == -1) {
                    connection.doFail(WS_PROTOCOL_ERROR, MSG_END_OF_STREAM);
                }

                code = ((b1 & 0xFF) << 8) | (b2 & 0xFF);
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

                // If reason is also received, then just drain those bytes.
                if (payloadLength > 2) {
                    reason = new byte[(int) (payloadLength - 2)];
                    int reasonBytesRead = readContrlFramePayload(reason, 0, reason.length);

                    assert reasonBytesRead == (payloadLength - 2);

                    if ((reason.length > (MAX_COMMAND_FRAME_PAYLOAD - 2)) || !validBytesUTF8(reason)) {
                        code = WS_PROTOCOL_ERROR;
                    }

                    if (code != WS_NORMAL_CLOSE) {
                        reason = null;
                    }
                }
            }

            connection.doClose(code, reason);

            if (code == WS_PROTOCOL_ERROR) {
                throw new IOException(format(MSG_CLOSE_FRAME_VIOLATION, closeCodeRO, reason));
            }
            break;

        case 0x09:
            if (payloadLength > MAX_COMMAND_FRAME_PAYLOAD) {
                connection.doFail(WS_PROTOCOL_ERROR, format(MSG_PING_PAYLOAD_LENGTH_EXCEEDED, MAX_COMMAND_FRAME_PAYLOAD));
            }

            byte[] pingBuf = new byte[(int) payloadLength];
            int pingBytesRead = readContrlFramePayload(pingBuf, 0, pingBuf.length);

            assert pingBytesRead == payloadLength;
            connection.doPong(pingBuf);
            break;

        case 0x0A:
            if (payloadLength > MAX_COMMAND_FRAME_PAYLOAD) {
                connection.doFail(WS_PROTOCOL_ERROR, format(MSG_PONG_PAYLOAD_LENGTH_EXCEEDED, MAX_COMMAND_FRAME_PAYLOAD));
            }
            byte[] pongBuf = new byte[(int) payloadLength];
            int pongBytesRead = readContrlFramePayload(pongBuf, 0, pongBuf.length);
            assert pongBytesRead == payloadLength;
            break;

        default:
            connection.doFail(WS_PROTOCOL_ERROR, format(MSG_UNRECOGNIZED_OPCODE, opcode));
            break;
        }

        // Get ready to read the next frame after CLOSE frame is sent out.
        payloadLength = 0;
        payloadOffset = -1;
        headerOffset = 0;
        state = State.READ_FLAGS_AND_OPCODE;
    }

    private int readContrlFramePayload(byte[] buf, int offset, int length) throws IOException {
        int mark = offset;

        while (offset < buf.length) {
            int bytesRead = in.read(buf, offset, length);

            if (bytesRead == -1) {
                connection.doFail(WS_PROTOCOL_ERROR, MSG_END_OF_STREAM);
            }

            offset += bytesRead;
            length -= bytesRead;
        }

        return offset - mark;
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
