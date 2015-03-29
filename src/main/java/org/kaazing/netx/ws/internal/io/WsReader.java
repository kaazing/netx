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
import static org.kaazing.netx.ws.internal.ext.flyweight.OpCode.CONTINUATION;
import static org.kaazing.netx.ws.internal.ext.flyweight.OpCode.TEXT;
import static org.kaazing.netx.ws.internal.util.Utf8Util.initialDecodeUTF8;
import static org.kaazing.netx.ws.internal.util.Utf8Util.remainingBytesUTF8;
import static org.kaazing.netx.ws.internal.util.Utf8Util.remainingDecodeUTF8;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;

import org.kaazing.netx.ws.internal.WsURLConnectionImpl;
import org.kaazing.netx.ws.internal.ext.WebSocketContext;
import org.kaazing.netx.ws.internal.ext.flyweight.Flyweight;
import org.kaazing.netx.ws.internal.ext.flyweight.Frame;
import org.kaazing.netx.ws.internal.ext.flyweight.Header;
import org.kaazing.netx.ws.internal.ext.flyweight.HeaderRW;
import org.kaazing.netx.ws.internal.ext.flyweight.OpCode;
import org.kaazing.netx.ws.internal.ext.function.WebSocketFrameConsumer;

public class WsReader extends Reader {
    private static final String MSG_NULL_CONNECTION = "Null HttpURLConnection passed in";
    private static final String MSG_INDEX_OUT_OF_BOUNDS = "offset = %d; (offset + length) = %d; buffer length = %d";
    private static final String MSG_NON_TEXT_FRAME = "Non-text frame - opcode = 0x%02X";
    private static final String MSG_FRAGMENTED_FRAME = "Protocol Violation: Fragmented frame 0x%02X";

    private static final int BUFFER_CHUNK_SIZE = 8192;

    private final WsURLConnectionImpl connection;
    private final InputStream in;

    private byte[] networkBuffer;
    private int networkBufferReadOffset;
    private int networkBufferWriteOffset;
    private char[] applicationBuffer;
    private int applicationBufferReadOffset;
    private int applicationBufferWriteOffset;
    private int codePoint;
    private int remainingBytes;
    private final HeaderRW incomingFrame;

    public WsReader(WsURLConnectionImpl connection) throws IOException {
        if (connection == null) {
            throw new NullPointerException(MSG_NULL_CONNECTION);
        }

        this.connection = connection;
        this.in = connection.getTcpInputStream();
        this.incomingFrame = new HeaderRW();

        this.codePoint = 0;
        this.remainingBytes = 0;

        this.applicationBufferReadOffset = 0;
        this.applicationBufferWriteOffset = 0;
        this.applicationBuffer = new char[BUFFER_CHUNK_SIZE];
        this.networkBufferReadOffset = 0;
        this.networkBufferWriteOffset = 0;
        this.networkBuffer = new byte[BUFFER_CHUNK_SIZE];
    }


    @Override
    public int read(char[] cbuf, int offset, int length) throws IOException {
        if ((offset < 0) || ((offset + length) > cbuf.length) || (length < 0)) {
            int len = offset + length;
            throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, len, cbuf.length));
        }

        if (applicationBufferReadOffset < applicationBufferWriteOffset) {
            return copyCharsFromApplicationBuffer(cbuf, offset, length);
        }

        assert networkBufferReadOffset == networkBufferWriteOffset;
        assert networkBufferWriteOffset == 0;

        int bytesRead = in.read(networkBuffer, 0, networkBuffer.length);
        if (bytesRead == -1) {
            return -1;
        }

        networkBufferReadOffset = 0;
        networkBufferWriteOffset = bytesRead;

        final WebSocketFrameConsumer terminalFrameConsumer = new WebSocketFrameConsumer() {
            private boolean fragmented;

            @Override
            public void accept(WebSocketContext context, Frame frame) throws IOException {
                Header transformedFrame = (Header) frame;
                OpCode opCode = transformedFrame.opCode();
                long xformedPayloadLength = transformedFrame.payloadLength();
                int xformedPayloadOffset = transformedFrame.payloadOffset();

                switch (opCode) {
                case TEXT:
                case CONTINUATION:
                    if ((opCode == TEXT) && fragmented) {
                        byte leadByte = (byte) Flyweight.uint8Get(transformedFrame.buffer(), transformedFrame.offset());
                        connection.doFail(WS_PROTOCOL_ERROR, format(MSG_FRAGMENTED_FRAME, leadByte));
                    }

                    if ((opCode == CONTINUATION) && !fragmented) {
                        byte leadByte = (byte) Flyweight.uint8Get(transformedFrame.buffer(), transformedFrame.offset());
                        connection.doFail(WS_PROTOCOL_ERROR, format(MSG_FRAGMENTED_FRAME, leadByte));
                    }

                    int currentLength = applicationBuffer.length;

                    if (applicationBufferWriteOffset + xformedPayloadLength > currentLength) {
                        char[] appBuffer = new char[(int) (currentLength + Math.max(xformedPayloadLength, BUFFER_CHUNK_SIZE))];

                        System.arraycopy(applicationBuffer, 0, appBuffer, 0, currentLength);
                        applicationBuffer = appBuffer;
                    }

                    int charsConverted = convertBytesToChars(transformedFrame.buffer(),
                                                             xformedPayloadOffset,
                                                             xformedPayloadLength,
                                                             applicationBuffer,
                                                             applicationBufferWriteOffset,
                                                             applicationBuffer.length - applicationBufferWriteOffset);
                    applicationBufferWriteOffset += charsConverted;
                    fragmented = !transformedFrame.fin();
                    break;
                case CLOSE:
                    connection.sendClose(transformedFrame);
                    break;
                case PING:
                    connection.sendPong(transformedFrame);
                    break;
                case BINARY:
                    connection.doFail(WS_PROTOCOL_ERROR, format(MSG_NON_TEXT_FRAME, OpCode.toInt(BINARY)));
                    break;
                case PONG:
                    break;
                }
            }
        };

        while (true) {
            if (networkBufferReadOffset == networkBufferWriteOffset) {
                break;
            }

            // Before wrapping the networkBuffer in a flyweight, ensure that it has the metadata(opcode, payload-length)
            // information.
            int numBytes = ensureFrameMetadata();
            if (numBytes == -1) {
                return -1;
            }

            // At this point, we should have sufficient bytes to figure out whether the frame has been read completely.
            // Ensure that we have at least one complete frame. We may have read only a partial frame the very first time.
            // Figure out the payload length and see how much more we need to read to be frame-aligned.
            incomingFrame.wrap(ByteBuffer.wrap(networkBuffer,
                                               networkBufferReadOffset,
                                               networkBufferWriteOffset), networkBufferReadOffset);

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

                int bufLength = networkBuffer.length - networkBufferWriteOffset;
                bytesRead = in.read(networkBuffer, networkBufferWriteOffset, bufLength);
                if (bytesRead == -1) {
                    return -1;
                }

                networkBufferWriteOffset += bytesRead;
                incomingFrame.wrap(ByteBuffer.wrap(networkBuffer,
                                                   networkBufferReadOffset,
                                                   networkBufferWriteOffset), networkBufferReadOffset);
            }

            connection.processFrame(incomingFrame, terminalFrameConsumer);
            networkBufferReadOffset += incomingFrame.length();
        }

        assert networkBufferReadOffset == networkBufferWriteOffset;

        networkBufferReadOffset = 0;
        networkBufferWriteOffset = 0;

        if (applicationBufferReadOffset < applicationBufferWriteOffset) {
            return copyCharsFromApplicationBuffer(cbuf, offset, length);
        }

        return 0;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    private int convertBytesToChars(ByteBuffer src,
                                    int srcOffset,
                                    long srcLength,
                                    char[] dest,
                                    int destOffset,
                                    int destLength)  throws IOException {
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

    private int copyCharsFromApplicationBuffer(char[] cbuf, int offset, int length) {
        assert applicationBufferReadOffset < applicationBufferWriteOffset;

        int charsRead = Math.min(length, applicationBufferWriteOffset - applicationBufferReadOffset);

        System.arraycopy(applicationBuffer, applicationBufferReadOffset, cbuf, offset, charsRead);
        applicationBufferReadOffset += charsRead;

        if (applicationBufferReadOffset == applicationBufferWriteOffset) {
            applicationBufferReadOffset = 0;
            applicationBufferWriteOffset = 0;
            networkBufferReadOffset = 0;
            networkBufferWriteOffset = 0;
        }

        return charsRead;
    }

    private int ensureFrameMetadata() throws IOException {
        int offsetDiff = networkBufferWriteOffset - networkBufferReadOffset;
        if ((offsetDiff > 10) || (networkBuffer.length - networkBufferWriteOffset > 10)) {
            // The payload length information is definitely available in the network buffer.
            return 0;
        }

        int bytesRead = 0;

        // Ensure that the networkBuffer at the very least contains the payload length related bytes.
        switch (offsetDiff) {
        case 1:
            System.arraycopy(networkBuffer, networkBufferReadOffset, networkBuffer, 0, offsetDiff); // no break
            networkBufferWriteOffset = offsetDiff;
        case 0:
            bytesRead = in.read(networkBuffer, offsetDiff, networkBuffer.length);
            if (bytesRead == -1) {
                return -1;
            }

            networkBufferReadOffset = 0;
            networkBufferWriteOffset += bytesRead;
            break;

        default:
            int frameMetadataLength = 2;
            int b1 = networkBuffer[networkBufferReadOffset];
            int b2 = networkBuffer[networkBufferReadOffset + 1] & 0x7F;

            try {
                OpCode.fromInt(b1 & 0x0F);
            }
            catch (Exception ex) {
                // We are reading garbage.
                return -1;
            }

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

                int remainingCapacity = networkBuffer.length - networkBufferWriteOffset;
                if (remainingCapacity > frameMetadataLength) {
                    // networkBuffer has enough capacity for the payload length bytes.
                    bytesRead = in.read(networkBuffer, networkBufferWriteOffset, remainingCapacity) ;
                    if (bytesRead == -1) {
                        return -1;
                    }

                    networkBufferWriteOffset += bytesRead;
                }
                else {
                    // Shift the frame to the beginning of the buffer and try to read more bytes to be able to figure out
                    // the payload length.
                    System.arraycopy(networkBuffer, networkBufferReadOffset, networkBuffer, 0, offsetDiff);
                    networkBufferReadOffset = 0;
                    networkBufferWriteOffset = offsetDiff;

                    bytesRead = in.read(networkBuffer, networkBufferWriteOffset, networkBuffer.length);
                    if (bytesRead == -1) {
                        return -1;
                    }

                    networkBufferWriteOffset += bytesRead;
                }
            }
        }

        return bytesRead;
    }
}
