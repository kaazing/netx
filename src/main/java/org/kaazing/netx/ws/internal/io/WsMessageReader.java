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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.kaazing.netx.http.HttpURLConnection;
import org.kaazing.netx.ws.MessageReader;
import org.kaazing.netx.ws.MessageType;
import org.kaazing.netx.ws.internal.util.Utf8Util;

public final class WsMessageReader extends MessageReader {
    private final HttpURLConnection connection;
    private final InputStream in;
    private final WsOutputStream out;
    private final byte[] header;

    private MessageType type;
    private int headerOffset;
    private State state;
    private boolean fin;
    private long payloadLength;
    private int payloadOffset;
    private int remaining;
    private int charBytes;
    private int charWidth;
    private final ByteArrayOutputStream tempBuffer;

    private enum State {
        READ_FLAGS_AND_OPCODE, READ_PAYLOAD_LENGTH, READ_PAYLOAD;
    };

    public WsMessageReader(HttpURLConnection connection, WsOutputStream out) throws IOException {
        if (connection == null) {
            throw new NullPointerException("Null HttpURLConnection passed in");
        }

        if (out == null) {
            throw new NullPointerException("Null WsOutputStream passed in");
        }

        this.connection = connection;
        this.in = connection.getInputStream();
        this.out = out;
        this.header = new byte[10];
        this.state = State.READ_FLAGS_AND_OPCODE;
        this.payloadOffset = -1;
        this.tempBuffer = new ByteArrayOutputStream();
    }

    @Override
    public synchronized int read(byte[] buf) throws IOException {
        return this.read(buf, 0, buf.length);
    }

    @Override
    public synchronized int read(byte[] buf, int offset, int length) throws IOException {
        if ((offset < 0) || ((offset + length) > buf.length) || (length < 0)) {
            throw new IndexOutOfBoundsException();
        }

        // Check whether next() has been invoked before this method. If it wasn't invoked, then read the header byte.
        switch (state) {
        case READ_FLAGS_AND_OPCODE:
            readHeaderByte();
            break;
        default:
            break;
        }

        switch (type) {
        case EOS:
            throw new IOException("Connection is closed");
        case TEXT:
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
                throw new IOException("End of stream before the entire payload could be read into the buffer");
                // return offset - mark;
            }

            bytesRead = readBinary(buf, offset, length);

            offset += bytesRead;

            // Once the payload is read, use fin to figure out whether this was
            // the final frame.
            finalFrame = fin;

            if (!finalFrame) {
                // Start reading the CONTINUATION frame for the message.
                assert state == State.READ_FLAGS_AND_OPCODE;
                readHeaderByte();
            }
        } while (!finalFrame);

        state = State.READ_FLAGS_AND_OPCODE;
        return offset - mark;
    }

    @Override
    public synchronized int read(char[] buf) throws IOException {
        return this.read(buf, 0, buf.length);
    }

    @Override
    public synchronized int read(char[] buf, int offset, int length) throws IOException {
        if ((offset < 0) || ((offset + length) > buf.length) || (length < 0)) {
            throw new IndexOutOfBoundsException();
        }

        // Check whether next() has been invoked before this method. If it wasn't invoked, then read the header byte.
        switch (state) {
        case READ_FLAGS_AND_OPCODE:
            readHeaderByte();
            break;
        default:
            break;
        }

        switch (type) {
        case EOS:
            throw new IOException("Connection is closed");
        case BINARY:
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
                throw new IOException("End of stream before the entire payload could be read into the buffer");
                // return offset - mark;
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

        String tempStr = tempBuffer.toString("UTF-8");
        char[] tempCharArray = tempStr.toCharArray();
        byte[] tempByteArray = tempStr.getBytes("UTF-8");

        String str = new String(buf, 0 , offset - mark);
        byte[] arr = str.getBytes("UTF-8");

        state = State.READ_FLAGS_AND_OPCODE;
        return offset - mark;
    }

    @Override
    public synchronized MessageType peek() {
        return type;
    }

    @Override
    public synchronized MessageType next() throws IOException {
        switch (state) {
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
        assert state == State.READ_FLAGS_AND_OPCODE;

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
            out.writeClose(1002, null);
            connection.disconnect();
            throw new IOException(format("Protocol Violation: Reserved bits set %02X", flags));
        }

        int opcode = headerByte & 0x0F;
        switch (opcode) {
        case 0x00:
            break;
        case 0x01:
            type = MessageType.TEXT;
            break;
        case 0x02:
            type = MessageType.BINARY;
            break;
        case 0x08:
        case 0x09:
        case 0x0A:
            filterControlFrames();
            if ((header[0] & 0x0F) == 0x08) {
                type = MessageType.EOS;
                return -1;
            }
            headerByte = readHeaderByte();
            break;
        default:
            out.writeClose(1002, null);
            connection.disconnect();
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
                        out.writeClose(1002, null);
                        connection.disconnect();
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
            int size = buf.length - offset;
            throw new IOException(format("Buffer size (%d) is too small to accommodate payload of size (%d)", size, payloadLength));
        }

        // Read the entire payload from the current frame into the buffer.
        while (payloadOffset < payloadLength) {
            assert len + offset <= buf.length;

            bytesRead = in.read(buf, offset, (int) Math.min(len, payloadLength));
            if (bytesRead == -1) {
                throw new IOException("End of stream before the entire payload could be read into the buffer");
                // return offset - mark;
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

        while (payloadOffset < payloadLength) {
            int b = -1;

            while ((payloadOffset < payloadLength) && (remaining > 0)) {
                // Read the remaining bytes of a multi-byte character. These bytes could be in two successive WebSocket frames.
                b = in.read();
                if (b == -1) {
                    throw new IOException("End of stream before the entire payload could be read into the buffer");
                }

                tempBuffer.write(b);
                payloadOffset++;

                switch (remaining) {
                case 3:
                case 2:
                    System.out.println(format("remaining: %d: b = 0x%08X; b & 0x3F = 0x%08X", remaining, b, (b & 0x3F)));
                    System.out.println(format("charBytes = 0x%08X", charBytes));
                    System.out.println(format("(charBytes << 6) = 0x%08X", (charBytes << 6)));
                    charBytes = (charBytes << 6) | (b & 0x3F);
                    System.out.println(format("charBytes = (charBytes << 6) | (b & 0x3F) = 0x%08X", charBytes));
                    remaining--;
                    break;
                case 1:
                    System.out.println(format("remaining: %d: b = 0x%08X; b & 0x3F = 0x%08X", remaining, b, (b & 0x3F)));
                    System.out.println(format("charBytes = 0x%08X", charBytes));
                    System.out.println(format("(charBytes << 6) = 0x%08X", (charBytes << 6)));
                    cbuf[offset++] = (char) ((charBytes << 6) | (b & 0x3F));
                    System.out.println(format("read: cbuf[%d] = (charBytes << 6) | (b & 0x3F) = 0x%08X", offset - 1, (int) cbuf[offset - 1], charBytes));
                    int cw = expectedBytes((charBytes << 6) | (b & 0x3F));
                    System.out.println(format("---- Expected Width = %d; Actual Width = %d", charWidth, cw));
                    remaining--;
                    charBytes = 0;
                    break;
                case 0:
                    break;
                }
            }

            System.out.println(format("***************************************", payloadOffset));
            if (payloadOffset < payloadLength) {
                b = in.read();
                if (b == -1) {
                    break;
                }

                tempBuffer.write(b);
                payloadOffset++;

                // Check if the byte read is the first of a multi-byte character.
                remaining = Utf8Util.remainingUTF8Bytes(b);
                charWidth = remaining + 1;
                System.out.println(format("read: offset = %d; b = 0x%08X; width = %d bytes; remaining = %d bytes", offset, b, charWidth, remaining));

                switch (remaining) {
                case 0:
                    // ASCII char.
                    cbuf[offset++] = (char) (b & 0x7F);
                    System.out.println(format("read: cbuf[%d] = 0x%08X", offset - 1, (b & 0x7F)));
                    break;
                case 1:
                    charBytes = b & 0x1F;
                    System.out.println(format("1:read: charBytes = (0x%08X & 0x1F) = 0x%08X", b, charBytes));
                    break;
                case 2:
                    charBytes = b & 0x0F;
                    System.out.println(format("2:read: charBytes = (0x%08X & 0x0F) = 0x%08X", b, charBytes));
                    break;
                case 3:
                    charBytes = b & 0x07;
                    System.out.println(format("3:read: charBytes = (0x%08X & 0x07) = 0x%08X", b, charBytes));
                    break;
                default:
                    throw new IOException("Invalid UTF-8 byte sequence. UTF-8 char cannot span for more than 4 bytes.");
                }
            }
        }

        // Unlike WsReader, WsMessageReader has to ensure that the entire payload has been read.
        if (payloadOffset < payloadLength) {
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
                int b2 = in.read();

                code = ((b1 & 0xFF) << 8) | (b2 & 0xFF);
                if ((code == 1005) || (code == 1006) || (code == 1015)) {
                    code = 1002;
                }

                // If reason is also received, then just drain those bytes.
                if (payloadLength > 2) {
                    reason = new byte[(int) (payloadLength - 2)];
                    int bytesRead = in.read(reason);

                    if (bytesRead == -1) {
                        throw new IOException("End of stream");
                    }

                    if ((reason.length > 123) || !Utf8Util.isValidUTF8(reason)) {
                        code = 1002;
                    }

                    if (code != 1000) {
                        reason = null;
                    }
                }
            }

            if (out.wasCloseSent()) {
                // If the client had earlier initiated a CLOSE and this is server's response as part of the CLOSE handshake,
                // then we should close the connection.
                connection.disconnect();
            }
            else {
                // The server has initiated a CLOSE. The client should reflect the CLOSE including the code(if any) to
                // complete the CLOSE handshake and then close the connection.
                out.writeClose(code, reason);
                connection.disconnect();
            }
            break;

        case 0x09:
            byte[] buf = null;
            if (payloadLength > 0) {
                buf = new byte[(int) payloadLength];
                int bytesRead = in.read(buf);

                if (bytesRead == -1) {
                    throw new IOException("End of stream");
                }
            }

            if ((buf != null) && (buf.length > 125)) {
                out.writeClose(1002, null);
                connection.disconnect();

                // ### TODO: Do we need to do this?
                throw new IOException("Protocol Violation: PING payload is more than 125 bytes");
            }
            else {
                if (opcode == 0x09) {
                    // Send the PONG frame out with the same payload that was received with PING.
                    out.writePong(buf);
                }
            }
            break;

        case 0x0A:
            int closeCode = 0;
            if (payloadLength > 125) {
                closeCode = 1002;
            }
            out.writeClose(closeCode, null);
            connection.disconnect();

            // ### TODO: Do we need to do this?
            throw new IOException("Protocol Violation: Received unexpected PONG");

        default:
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

    private static int expectedBytes(int value) {
        if ((value & 0xFFFFFF80) == 0) { // 1 byte
            return 1;
        }
        else if ((value & 0xFFFFF800) == 0) { // 2 bytes.
            return 2;
        }
        else if ((value & 0xFFFF0000) == 0) { // 3 bytes.
            return 3;
        }
        else if ((value & 0xFF200000) == 0) { // 4 bytes.
            return 4;
        }

        throw new IllegalStateException(format("Invalid UTF-8 sequence leader byte: 0x%02X", value));
    }
}
