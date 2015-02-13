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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import org.kaazing.netx.ws.internal.WsURLConnectionImpl;

public class WsReader extends Reader {
    private static final String MSG_NULL_CONNECTION = "Null HttpURLConnection passed in";
    private static final String MSG_INDEX_OUT_OF_BOUNDS = "offset = %d; (offset + length) = %d; buffer length = %d";
    private static final String MSG_NON_TEXT_FRAME = "Non-text frame - opcode = 0x%02X";
    private static final String MSG_MASKED_FRAME_FROM_SERVER = "Masked server-to-client frame";
    private static final String MSG_RESERVED_BITS_SET = "Protocol Violation: Reserved bits set 0x%02X";
    private static final String MSG_FRAGMENTED_CONTROL_FRAME = "Protocol Violation: Fragmented control frame 0x%02X";
    private static final String MSG_FRAGMENTED_FRAME = "Protocol Violation: Fragmented frame 0x%02X";

    private static final int MAX_TEXT_PAYLOAD_LENGTH = 8192;


    private final WsURLConnectionImpl connection;
    private final InputStream in;
    private final byte[] header;

    private int headerOffset;
    private int payloadOffset;
    private long payloadLength;
    private char[] receiveBuffer;
    private int charPayloadOffset;
    private int charPayloadLength;

    public WsReader(WsURLConnectionImpl connection) throws IOException {
        if (connection == null) {
            throw new NullPointerException(MSG_NULL_CONNECTION);
        }

        this.connection = connection;
        this.in = connection.getTcpInputStream();
        this.header = new byte[10];
        this.payloadOffset = -1;
        this.receiveBuffer = new char[MAX_TEXT_PAYLOAD_LENGTH];
    }

    @Override
    public int read(char[] cbuf, int offset, int length) throws IOException {
        if ((offset < 0) || ((offset + length) > cbuf.length) || (length < 0)) {
            int len = offset + length;
            throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, len, cbuf.length));
        }

        // This loop will be entered only if the entire WebSocket frame has been drained. If there was fragmentation at the TCP
        // level, this method will be invoked again with different offset and length. At that time, we will just use the
        // payloadLength and payloadOffset values that were from the previous attempt(s) to read.
        while (payloadLength == 0) {
            while (payloadOffset == -1) {
                int headerByte = in.read();
                if (headerByte == -1) {
                    return -1;
                }
                header[headerOffset++] = (byte) headerByte;
                switch (headerOffset) {
                case 1:
                    int flags = (header[0] & 0xF0) >> 4;
                    switch (flags) {
                    case 0:
                    case 8:
                        break;
                    default:
                        connection.doFail(WS_PROTOCOL_ERROR, format(MSG_RESERVED_BITS_SET, flags));
                    }

                    int opcode = header[0] & 0x0F;
                    switch (opcode) {
                    case 0x08:
                    case 0x09:
                    case 0x0A:
                        if ((headerByte & 0x80) == 0) {
                            connection.doFail(WS_PROTOCOL_ERROR, format(MSG_FRAGMENTED_CONTROL_FRAME, headerByte));
                        }
                        break;
                    case 0x00:
                        connection.doFail(WS_PROTOCOL_ERROR, format(MSG_FRAGMENTED_FRAME, headerByte));
                        break;
                    case 0x01:
                        break;
                    default:
                        connection.doFail(WS_PROTOCOL_ERROR, MSG_NON_TEXT_FRAME);
                    }
                    break;
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

            // If the current frame is either CLOSE, PING, or PONG, then we just filter out it's bytes.
            filterControlFrames();
            if ((header[0] & 0x0F) == 0x08) {
                return -1;
            }

            // If the payload length is zero, then we should start reading the new frame.
            if (payloadLength == 0) {
                payloadOffset = -1;
                headerOffset = 0;
            }
            else {
                if (payloadLength > receiveBuffer.length) {
                    // Note that the payloadLength is in terms of bytes. And, we are allocating a char[]. If the payload
                    // contains only ASCII chars, then the number of chars will be equal to the number of bytes in the
                    // payload. However, if the payload has multi-byte UTF-8 codepoints, then the number of chars decoded
                    // will be less than the number of bytes in the payload. So, if we allocate a char[] of using
                    // payloadLength, then we should be fine regardless of what's in the payload.
                    receiveBuffer = new char[(int) payloadLength];
                }

                charPayloadLength = connection.doTextFrame(receiveBuffer, 0, receiveBuffer.length, payloadLength);
            }
        }

        int charsRead = Math.min(length, charPayloadLength - charPayloadOffset);
        System.arraycopy(receiveBuffer, charPayloadOffset, cbuf, offset, charsRead);
        charPayloadOffset += charsRead;

        if (charPayloadOffset == charPayloadLength) {
            headerOffset = 0;
            payloadOffset = -1;
            payloadLength = 0;
            charPayloadOffset = 0;
            charPayloadLength = 0;
        }

        // number of chars (not code points) read
        return charsRead;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    private void filterControlFrames() throws IOException {
        int opcode = header[0] & 0x0F;

        if ((opcode == 0x00) || (opcode == 0x01)) {
            return;
        }

        connection.doControlFrame(opcode, payloadLength);

        // Get ready to read the next frame after CLOSE frame is sent out.
        payloadLength = 0;
        payloadOffset = -1;
        headerOffset = 0;
    }

    private static long payloadLength(byte[] header) {
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
