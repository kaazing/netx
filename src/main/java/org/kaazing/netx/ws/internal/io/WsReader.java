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
import java.io.Reader;

import org.kaazing.netx.ws.internal.WsURLConnectionImpl;

public class WsReader extends Reader {
    private static final String MSG_NULL_CONNECTION = "Null HttpURLConnection passed in";
    private static final String MSG_INDEX_OUT_OF_BOUNDS = "offset = %d; (offset + length) = %d; buffer length = %d";
    private static final String MSG_NON_TEXT_FRAME = "Non-text frame - opcode = 0x%02X";
    private static final String MSG_MASKED_FRAME_FROM_SERVER = "Masked server-to-client frame";
    private static final String MSG_END_OF_STREAM = "End of stream";
    private static final String MSG_PING_PAYLOAD_LENGTH_EXCEEDED = "Protocol Violation: PING payload is more than %d bytes";
    private static final String MSG_RESERVED_BITS_SET = "Protocol Violation: Reserved bits set 0x%02X";
    private static final String MSG_PONG_PAYLOAD_LENGTH_EXCEEDED = "Protocol Violation: PONG payload is more than %d bytes";
    private static final String MSG_UNRECOGNIZED_OPCODE = "Protocol Violation: Unrecognized opcode %d";
    private static final String MSG_CLOSE_FRAME_VIOLATION = "Protocol Violation: CLOSE Frame - Code = %d; Reason Length = %d";
    private static final String MSG_FRAGMENTED_CONTROL_FRAME = "Protocol Violation: Fragmented control frame 0x%02X";
    private static final String MSG_FRAGMENTED_FRAME = "Protocol Violation: Fragmented frame 0x%02X";

    private static final int MAX_COMMAND_FRAME_PAYLOAD = 125;

    private final WsURLConnectionImpl connection;
    private final InputStream in;
    private final byte[] header;

    private int headerOffset;
    private int payloadOffset;
    private long payloadLength;
    private int codePoint;
    private int remainingBytes;

    public WsReader(WsURLConnectionImpl connection) throws IOException {
        if (connection == null) {
            throw new NullPointerException(MSG_NULL_CONNECTION);
        }

        this.connection = connection;
        this.in = connection.getTcpInputStream();
        this.header = new byte[10];
        this.payloadOffset = -1;
    }

    @Override
    public int read(char[] cbuf, int offset, int length) throws IOException {
        if ((offset < 0) || ((offset + length) > cbuf.length) || (length < 0)) {
            int len = offset + length;
            throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, len, cbuf.length));
        }

        int mark = offset;

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
        }

        // payloadOffset and payloadLength are in terms of bytes. However, off and len are in terms of chars. The payload can
        // include multi-byte UTF-8 characters. The multi-byte characters can span across WebSocket frames or TCP fragments. If
        // there was fragmentation at the TCP layer before the entire frame was drained, this loop should be executed to drain
        // the payload.
        while (payloadOffset < payloadLength) {
            int b = -1;

            while (codePoint != 0 || (payloadOffset < payloadLength && remainingBytes > 0)) {

                // surrogate pair
                if (codePoint != 0 && remainingBytes == 0) {
                    int charCount = charCount(codePoint);
                    if (offset + charCount > length) {
                        return offset - mark;
                    }
                    toChars(codePoint, cbuf, offset);
                    offset += charCount;
                    codePoint = 0;
                    break;
                }

                // detect EOF
                b = in.read();
                if (b == -1) {
                    return offset - mark;
                }
                payloadOffset++;

                // character encoded in multiple bytes
                codePoint = remainingDecodeUTF8(codePoint, remainingBytes--, b);
            }

            // detect EOF
            b = in.read();
            if (b == -1) {
                return -1;
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

        if (payloadOffset == payloadLength) {
            headerOffset = 0;
            payloadOffset = -1;
            payloadLength = 0;
        }

        // number of chars (not code points) read
        return offset - mark;
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
                    int reasonBytesRead = readControlFramePayload(reason, 0, reason.length);

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
            int pingBytesRead = readControlFramePayload(pingBuf, 0, pingBuf.length);

            assert pingBytesRead == payloadLength;
            connection.doPong(pingBuf);
            break;

        case 0x0A:
            if (payloadLength > MAX_COMMAND_FRAME_PAYLOAD) {
                connection.doFail(WS_PROTOCOL_ERROR, format(MSG_PONG_PAYLOAD_LENGTH_EXCEEDED, MAX_COMMAND_FRAME_PAYLOAD));
            }
            byte[] pongBuf = new byte[(int) payloadLength];
            int pongBytesRead = readControlFramePayload(pongBuf, 0, pongBuf.length);
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
    }

    private int readControlFramePayload(byte[] buf, int offset, int length) throws IOException {
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
