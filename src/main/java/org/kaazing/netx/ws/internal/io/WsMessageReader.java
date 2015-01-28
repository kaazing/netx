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

public final class WsMessageReader extends MessageReader {
    private static final String CLASS_NAME = WsMessageReader.class.getName();
    private static final Logger LOG = Logger.getLogger(CLASS_NAME);

    private final InputStream in;
    private final byte[]      header;

    private MessageType       type;
    private int               headerOffset;
    private State             state;
    private boolean           fin;
    private long              payloadLength;

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
    }

    @Override
    public synchronized int read(byte[] buf) throws IOException {
        return this.read(buf, 0, buf.length);
    }

    @Override
    public synchronized int read(byte[] buf, int offset, int len) throws IOException {
        int count = 0;

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
        int index = offset;
        do {
            index += count;

            if (len > (buf.length - index)) {
                int size = buf.length - index;
                throw new IOException(format("Buffer size (%d) small to accommodate "
                        + "payload of size (%d)", size, len));
            }

            assert state == State.READ_PAYLOAD_LENGTH;
            payloadLength = readPayloadLength();

            assert state == State.READ_PAYLOAD;
            count += readBinary(buf, index, len);

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
        return count;
    }

    @Override
    public synchronized int read(char[] buf) throws IOException {
        return this.read(buf, 0, buf.length);
    }

    @Override
    public synchronized int read(char[] buf, int offset, int len) throws IOException {
        int count = 0;

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
        int index = offset;
        do {
            index += count;

            if (len > (buf.length - index)) {
                int size = buf.length - index;
                throw new IOException(format("Buffer size (%d) cannot accommodate "
                        + " payload of size (%d)", size, len));
            }

            assert state == State.READ_PAYLOAD_LENGTH;
            payloadLength = readPayloadLength();

            assert state == State.READ_PAYLOAD;
            count += readText(buf, index, len);

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
        return count;
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

    private synchronized long readPayloadLength() throws IOException {
        assert state == State.READ_PAYLOAD_LENGTH;

        long length = -1;
        int payloadOffset = -1;

        while (length == -1) {
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
                        length = payloadLength(header);
                    }
                    break;
                case 4:
                    switch (header[1] & 0x7f) {
                    case 126:
                        payloadOffset = 0;
                        length = payloadLength(header);
                    default:
                        break;
                    }
                    break;
                case 10:
                    switch (header[1] & 0x7f) {
                    case 127:
                        payloadOffset = 0;
                        length = payloadLength(header);
                    default:
                        break;
                    }
                    break;
                }
            }
        }

        state = State.READ_PAYLOAD;
        return length == -1 ? 0 : length;
    }

    private synchronized int readBinary(byte[] buf, int offset, int len) throws IOException {
        assert state == State.READ_PAYLOAD;

        int numBytes = 0;
        int length = len;
        int index = offset;
        int payloadOffset = 0;

        if ((buf.length - offset) < payloadLength) {
            int size = buf.length - offset;
            throw new IOException(format("Buffer size (%d) is small to accommodate "
                                    + "payload of size (%d)", size, payloadLength));
        }

        // Read the entire payload from the current frame into the buffer.
        while (payloadOffset < payloadLength) {
            length -= payloadOffset;
            index += numBytes;

            assert length <= (buf.length - index);

            numBytes = in.read(buf, index, length);
            payloadOffset += numBytes;
        }

        assert payloadOffset == payloadLength ;

        // Entire WebSocket frame has been read. Reset the state.
        headerOffset = 0;
        payloadLength = 0;
        state = State.READ_FLAGS_AND_OPCODE;

        return payloadOffset;
    }

    private synchronized int readText(char[] buf, int offset, int len) throws IOException {
        assert state == State.READ_PAYLOAD;

        int bytesRead = 0;
        int length = len;
        int index = offset;
        int payloadOffset = 0;
        int charsRead = 0;

        if ((buf.length - offset) < payloadLength) {
            int size = buf.length - offset;
            throw new IOException(format("Buffer size (%d) is small to accommodate "
                                    + "payload of size (%d)", size, payloadLength));
        }

        // Read the entire payload from the current frame into the buffer.
        while (payloadOffset < payloadLength) {
            length -= payloadOffset;
            index += charsRead;

            assert length <= (buf.length - index);

            byte[] bytes = new byte[(int) (payloadLength - payloadOffset)];
            bytesRead = in.read(bytes, 0, bytes.length);
            if (bytesRead == -1) {
                throw new IOException("End of stream");
            }

            // int[] utf8buf = Utf8Util.decode(bytes);
            char[] utf8buf = new String(bytes, 0, bytesRead, "UTF-8").toCharArray();
            charsRead += utf8buf.length;
            for (int i = 0; i < utf8buf.length; i++) {
                buf[index + i] = utf8buf[i];
            }

            payloadOffset += bytesRead;
        }

        assert payloadOffset == payloadLength ;

        // Entire WebSocket frame has been read. Reset the state.
        headerOffset = 0;
        payloadLength = 0;
        state = State.READ_FLAGS_AND_OPCODE;

        return payloadOffset;
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
