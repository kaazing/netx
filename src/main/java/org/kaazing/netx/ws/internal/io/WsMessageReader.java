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
            throw new NullPointerException("Null HttpURLConnection passed in");
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
            connection.doClose(WS_NORMAL_CLOSE, null);
            int len = offset + length;
            throw new IndexOutOfBoundsException(format("offset = %d; (offset + length) = %d; buffer length = %d",
                                                      offset, len, buf.length));
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
            connection.disconnect();
            throw new IOException("Connection is closed");
        case TEXT:
            connection.doClose(WS_NORMAL_CLOSE, null);
            throw new IOException("Cannot decode the payload as a binary message");
        default:
            break;
        }

        boolean finalFrame = fin;
        int mark = offset;
        int bytesRead = 0;

        do {
            int retval = readPayloadLength();
            if (retval == -1) {
                connection.disconnect();
                throw new IOException("End of stream before the entire payload could be read into the buffer");
            }

            bytesRead = readBinary(buf, offset, length);

            offset += bytesRead;

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
            connection.doClose(WS_NORMAL_CLOSE, null);
            int len = offset + length;
            throw new IndexOutOfBoundsException(format("offset = %d; (offset + length) = %d; buffer length = %d",
                                                      offset, len, buf.length));
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
            connection.disconnect();
            throw new IOException("Connection is closed");
        case BINARY:
            connection.doClose(WS_NORMAL_CLOSE, null);
            throw new IOException("Cannot decode the payload as a text message");
        default:
            break;
        }

        boolean finalFrame = fin;
        int mark = offset;
        int charsRead = 0;

        do {
            int retval = readPayloadLength();
            if (retval == -1) {
                connection.disconnect();
                throw new IOException("End of stream before the entire payload could be read into the buffer");
            }

            charsRead = readText(buf, offset, length);

            offset += charsRead;

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


    // ### TODO: May not need this.
    public void close() throws IOException {
        in.close();
        type = null;
        state = null;
    }

    private synchronized int readHeaderByte() throws IOException {
        assert state == State.READ_FLAGS_AND_OPCODE || state == State.INITIAL;

        int headerByte = in.read();
        if (headerByte == -1) {
            connection.disconnect();
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
            connection.doClose(WS_PROTOCOL_ERROR, null);
            throw new IOException(format("Protocol Violation: Reserved bits set %02X", flags));
        }

        int opcode = headerByte & 0x0F;
        switch (opcode) {
        case 0x00:
            if (state == State.INITIAL) {
                // The first frame cannot be a fragmented frame..
                connection.doClose(WS_PROTOCOL_ERROR, null);
                type = MessageType.EOS;
                throw new IOException(format("Protocol Violation: First frame cannot be a fragmented frame", opcode));
            }
            break;
        case 0x01:
            if (state == State.READ_FLAGS_AND_OPCODE) {
                // In a subsequent fragmented frame, the opcode should NOT be set.
                connection.doClose(WS_PROTOCOL_ERROR, null);
                throw new IOException(format("Protocol Violation: Opcode 0x%02X expected only in the initial frame", opcode));
            }
            type = MessageType.TEXT;
            break;
        case 0x02:
            if (state == State.READ_FLAGS_AND_OPCODE) {
                // In a subsequent fragmented frame, the opcode should NOT be set.
                connection.doClose(WS_PROTOCOL_ERROR, null);
                throw new IOException(format("Protocol Violation: Opcode 0x%02X expected only in the initial frame", opcode));
            }
            type = MessageType.BINARY;
            break;
        case 0x08:
        case 0x09:
        case 0x0A:
            if (!fin) {
                // Control frames cannot be fragmented.
                connection.doClose(WS_PROTOCOL_ERROR, null);
                throw new IOException(format("Protocol Violation: Fragmented control frame 0x%02X", headerByte));
            }

            filterControlFrames();
            if ((header[0] & 0x0F) == 0x08) {
                type = MessageType.EOS;
                return -1;
            }
            headerByte = readHeaderByte();
            break;
        default:
            connection.doClose(WS_PROTOCOL_ERROR, null);
            throw new IOException(format("Protocol Violation: Invalid opcode %d", opcode));
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
                        connection.doClose(WS_PROTOCOL_ERROR, null);
                        throw new IOException("Masked server-to-client frame");
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
            connection.doClose(WS_NORMAL_CLOSE, null);
            int size = buf.length - offset;
            throw new IOException(format("Buffer size (%d) too small for payload size (%d)", size, payloadLength));
        }

        // Read the entire payload from the current frame into the buffer.
        while (payloadOffset < payloadLength) {
            assert offset + payloadLength <= buf.length;

            bytesRead = in.read(buf, offset, (int) Math.min(len, payloadLength));
            if (bytesRead == -1) {
                connection.disconnect();
                throw new IOException("End of stream before the entire payload could be read into the buffer");
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
            connection.doClose(WS_NORMAL_CLOSE, null);
            throw new IOException("End of stream before the entire payload could be read into the buffer");
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
            byte[] reason = null;

            if (payloadLength >= 2) {
                // Read the first two bytes as the CLOSE code.
                int b1 = in.read();
                if (b1 == -1) {
                    connection.disconnect();
                    throw new IOException("End of stream");
                }

                int b2 = in.read();
                if (b1 == -1) {
                    connection.disconnect();
                    throw new IOException("End of stream");
                }

                code = ((b1 & 0xFF) << 8) | (b2 & 0xFF);
                if ((code == WS_MISSING_STATUS_CODE) || (code == WS_ABNORMAL_CLOSE) || (code == WS_UNSUCCESSFUL_TLS_HANDSHAKE)) {
                    code = WS_PROTOCOL_ERROR;
                }

                // If reason is also received, then just drain those bytes.
                if (payloadLength > 2) {
                    reason = new byte[(int) (payloadLength - 2)];
                    int bytesRead = in.read(reason);

                    if (bytesRead == -1) {
                        connection.disconnect();
                        throw new IOException("End of stream");
                    }

                    if ((reason.length > 123) || !validBytesUTF8(reason)) {
                        code = WS_PROTOCOL_ERROR;
                    }

                    if (code != WS_NORMAL_CLOSE) {
                        reason = null;
                    }
                }
            }

            connection.doClose(code, reason);

            if (code == WS_PROTOCOL_ERROR) {
                throw new IOException("Protocol Violation");
            }

            break;

        case 0x09:
            byte[] buf = null;
            if (payloadLength > 0) {
                buf = new byte[(int) payloadLength];

                int bytesRead = in.read(buf);
                if (bytesRead == -1) {
                    connection.disconnect();
                    throw new IOException("End of stream");
                }
            }

            if ((buf != null) && (buf.length > 125)) {
                connection.doClose(WS_PROTOCOL_ERROR, null);
                throw new IOException("Protocol Violation: PING payload is more than 125 bytes");
            }
            else {
                if (opcode == 0x09) {
                    // Send the PONG frame out with the same payload that was received with PING.
                    connection.doPong(buf);
                }
            }
            break;

        case 0x0A:
            int closeCode = 0;
            if (payloadLength > 125) {
                closeCode = WS_PROTOCOL_ERROR;
            }
            connection.doClose(closeCode, null);
            throw new IOException("Protocol Violation: Received unexpected PONG");

        default:
            connection.doClose(WS_PROTOCOL_ERROR, null);
            throw new IOException(format("Protocol Violation: Unrecognized opcode %d", opcode));
        }

        // Get ready to read the next frame after CLOSE frame is sent out.
        payloadLength = 0;
        payloadOffset = -1;
        headerOffset = 0;
        state = State.READ_FLAGS_AND_OPCODE;
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
