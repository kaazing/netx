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

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

import org.kaazing.netx.ws.MessageReader;
import org.kaazing.netx.ws.MessageType;
import org.kaazing.netx.ws.internal.util.Utf8Util;

public final class WsMessageReader extends MessageReader {
    private static final String CLASS_NAME = WsMessageReader.class.getName();
    private static final Logger LOG = Logger.getLogger(CLASS_NAME);

    private final InputStream in;
    private final byte[]      header;

    private MessageType type;
    private int headerOffset;
    private State state;
    private boolean fin;
    private long payloadLength;
    private int payloadOffset;
    private int remaining;
    private int charBytes;


    private enum State {
        READ_FLAGS_AND_OPCODE, READ_PAYLOAD_LENGTH, READ_PAYLOAD;
    };

    public WsMessageReader(InputStream in) {
        if (in == null) {
            throw new NullPointerException("Null InputStream passed in");
        }

        this.in = in;
        this.header = new byte[10];
        this.state = State.READ_FLAGS_AND_OPCODE;
        this.payloadOffset = -1;
    }

    @Override
    public synchronized int read(byte[] buf) throws IOException {
        return this.read(buf, 0, buf.length);
    }

    @Override
    public synchronized int read(byte[] buf, int offset, int length) throws IOException {
        // Check whether next() has been invoked before this method. If it
        // wasn't invoked, then read the header byte.
        switch (state) {
        case READ_FLAGS_AND_OPCODE:
            readHeaderByte();
            break;
        default:
            break;
        }

        if (type != MessageType.BINARY) {
            String s = "Cannot decode the payload as a binary message";
            throw new IOException(s);
        }

        boolean finalFrame = fin;
        int mark = offset;
        int bytesRead = 0;

        do {
            if (length > (buf.length - offset)) {
                int size = buf.length - offset;
                throw new IOException(format("Buffer size (%d) small to accommodate "
                        + "payload of size (%d)", size, length));
            }

            int retval = readPayloadLength();
            if (retval == -1) {
                return offset - mark;
            }

            bytesRead = readBinary(buf, offset, length);
            if (bytesRead == -1) {
                return offset - mark;
            }

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
        // Check whether next() has been invoked before this method. If it
        // wasn't invoked, then read the header byte.
        switch (state) {
        case READ_FLAGS_AND_OPCODE:
            readHeaderByte();
            break;
        default:
            break;
        }

        if (type != MessageType.TEXT) {
            String s = "Cannot decode the payload as a text message";
            throw new IOException(s);
        }

        boolean finalFrame = fin;
        int mark = offset;
        int charsRead = 0;

        do {
            if (length > (buf.length - offset)) {
                int size = buf.length - offset;
                throw new IOException(format("Buffer size (%d) cannot accommodate "
                        + " payload of size (%d)", size, length));
            }

            int retval = readPayloadLength();
            if (retval == -1) {
                return offset - mark;
            }

            charsRead = readText(buf, offset, length);
            if (charsRead == -1) {
                return offset - mark;
            }

            offset += charsRead;

            // Once the payload is read, use fin to figure out whether this was the final frame.
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

        int opcode = headerByte & 0x07;
        switch (opcode) {
        case 0x01:
            type = MessageType.TEXT;
            break;
        case 0x02:
            type = MessageType.BINARY;
            break;
        }

        fin = (headerByte & 0x80) != 0;
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
                        throw new IOException("Masked server-to-client frame");
                    }
                    switch (header[1] & 0x7f) {
                    case 126:
                    case 127:
                        break;
                    default:
                        payloadOffset = 0;
                        payloadLength = payloadLength(header);
                    }
                    break;
                case 4:
                    switch (header[1] & 0x7f) {
                    case 126:
                        payloadOffset = 0;
                        payloadLength = payloadLength(header);
                    default:
                        break;
                    }
                    break;
                case 10:
                    switch (header[1] & 0x7f) {
                    case 127:
                        payloadOffset = 0;
                        payloadLength = payloadLength(header);
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

        if ((buf.length - offset) < payloadLength) {
            int size = buf.length - offset;
            throw new IOException(format("Buffer size (%d) is small to accommodate "
                                    + "payload of size (%d)", size, payloadLength));
        }

        // Read the entire payload from the current frame into the buffer.
        while (payloadOffset < payloadLength) {
            len -= bytesRead;
            offset += bytesRead;

            assert len <= (buf.length - offset);

            bytesRead = in.read(buf, offset, len);
            if (bytesRead == -1) {
                return offset - mark;
            }

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

        int bytesRead = 0;
        int len = length;
        int mark = offset;
        int charsRead = 0;

        while (offset < length) {
            int b = -1;

            while (remaining > 0) {
                // Deal with multi-byte character.
                b = in.read();
                if (b == -1) {
                    return offset - mark;
                }

                payloadOffset++;

                switch (remaining) {
                case 3:
                case 2:
                    charBytes = (charBytes << 6) | (b & 0x3F);
                    remaining--;
                    break;
                case 1:
                    cbuf[offset++] = (char) ((charBytes << 6) | (b & 0x3F));
                    remaining--;
                    charBytes = 0;
                    break;
                case 0:
                    break;
                }
            }

            b = in.read();
            if (b == -1) {
                return offset - mark;
            }

            payloadOffset++;

            remaining = Utf8Util.remainingUTF8Bytes(b);
            switch (remaining) {
            case 0:
                // ASCII char.
                cbuf[offset++] = (char) (b & 0x7F);
                break;
            case 1:
                charBytes = b & 0x1F;
                break;
            case 2:
                charBytes = b & 0x0F;
                break;
            case 3:
                charBytes = b & 0x07;
                break;
            default:
                throw new IOException("Invalid UTF-8 byte sequence. UTF-8 char cannot span for more than 4 bytes.");
            }
        }

        assert payloadOffset == payloadLength ;

        // Entire WebSocket frame has been read. Reset the state.
        headerOffset = 0;
        payloadLength = 0;
        payloadOffset = -1;
        state = State.READ_FLAGS_AND_OPCODE;

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
