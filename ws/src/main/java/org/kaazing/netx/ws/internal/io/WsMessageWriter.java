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
import static org.kaazing.netx.ws.internal.ext.flyweight.Opcode.BINARY;
import static org.kaazing.netx.ws.internal.ext.flyweight.Opcode.CONTINUATION;
import static org.kaazing.netx.ws.internal.ext.flyweight.Opcode.TEXT;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.concurrent.locks.Lock;

import org.kaazing.netx.ws.MessageWriter;
import org.kaazing.netx.ws.internal.WsURLConnectionImpl;
import org.kaazing.netx.ws.internal.ext.flyweight.Opcode;
import org.kaazing.netx.ws.internal.util.OptimisticReentrantLock;

public class WsMessageWriter extends MessageWriter {
    private final WsURLConnectionImpl connection;
    private final Lock lock;

    private WsBinaryOutputStream messageBinaryStream;
    private WsTextWriter messageTextWriter;

    public WsMessageWriter(WsURLConnectionImpl connection) {
        this.connection = connection;
        this.lock = new OptimisticReentrantLock();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        if (messageBinaryStream != null) {
            return messageBinaryStream;
        }

        try {
            lock.lock();

            if (messageBinaryStream != null) {
                return messageBinaryStream;
            }

            messageBinaryStream = new WsBinaryOutputStream(connection);
        }
        finally {
            lock.unlock();
        }

        return messageBinaryStream;
    }

    @Override
    public Writer getWriter() throws IOException {
        if (messageTextWriter != null) {
            return messageTextWriter;
        }

        try {
            lock.lock();

            if (messageTextWriter != null) {
                return messageTextWriter;
            }

            messageTextWriter = new WsTextWriter(connection);
        }
        finally {
            lock.unlock();
        }

        return messageTextWriter;
    }

    @Override
    public void writeFully(byte[] buffer) throws IOException {
        try {
            lock.lock();

            connection.getOutputStream().write(buffer, 0, buffer.length);
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public void writeFully(char[] buffer) throws IOException {
        try {
            lock.lock();

            connection.getWriter().write(buffer, 0, buffer.length);
        }
        finally {
            lock.unlock();
        }
    }

    public void close() throws IOException {
        if (messageBinaryStream != null) {
            messageBinaryStream.close();
        }

        if (messageTextWriter != null) {
            messageTextWriter.close();
        }
    }

    private static class WsBinaryOutputStream extends OutputStream {
        private final WsURLConnectionImpl connection;
        private final byte[] binaryBuffer;
        private final Lock lock;

        private int binaryBufferOffset;
        private boolean initialFrame;

        public WsBinaryOutputStream(WsURLConnectionImpl connection) {
            this.connection = connection;
            this.binaryBuffer = new byte[connection.getMaxFramePayloadLength()];
            this.lock = new OptimisticReentrantLock();
            this.initialFrame = true;
            this.binaryBufferOffset = 0;
        }

        @Override
        public void write(int b) throws IOException {
            try {
                lock.lock();

                if (binaryBufferOffset < binaryBuffer.length) {
                    binaryBuffer[binaryBufferOffset++] = (byte) b;
                }

                if (binaryBufferOffset == binaryBuffer.length) {
                    Opcode opcode = initialFrame ? BINARY : CONTINUATION;
                    connection.getOutputStream().writeBinary(opcode, binaryBuffer, 0, binaryBuffer.length, false);
                    initialFrame = false;
                    binaryBufferOffset = 0;
                }
            }
            finally {
                lock.unlock();
            }
        }

        @Override
        public void flush() throws IOException {
            try {
                lock.lock();

                if (initialFrame) {
                    // Only one frame in the message.
                    connection.getOutputStream().write(binaryBuffer, 0, binaryBufferOffset);
                }
                else {
                    // Send the final frame.
                    connection.getOutputStream().writeBinary(CONTINUATION, binaryBuffer, 0, binaryBufferOffset, true);
                }
                initialFrame = true;
                binaryBufferOffset = 0;
            }
            finally {
                lock.unlock();
            }
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }

    private static class WsTextWriter extends Writer {
        private static final String MSG_INDEX_OUT_OF_BOUNDS = "offset = %d; (offset + length) = %d; buffer length = %d";

        private final WsURLConnectionImpl connection;
        private final char[] textBuffer;
        private final Lock lock;

        private int textBufferOffset;
        private boolean initialFrame;

        public WsTextWriter(WsURLConnectionImpl connection) {
            this.connection = connection;
            this.textBuffer = new char[connection.getMaxFramePayloadLength()];
            this.lock = new OptimisticReentrantLock();
            this.initialFrame = true;
            this.textBufferOffset = 0;
        }

        @Override
        public void write(char[] cbuf, int offset, int length) throws IOException {
            if (cbuf == null) {
                throw new NullPointerException("Null buffer passed in");
            }
            else if ((offset < 0) || (length < 0) || (offset + length > cbuf.length)) {
                throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, offset + length, cbuf.length));
            }

            try {
                lock.lock();

                while (length > 0) {
                    int len = Math.min(length, textBuffer.length - textBufferOffset);

                    System.arraycopy(cbuf, offset, textBuffer, textBufferOffset, len);
                    textBufferOffset += len;

                    if (textBufferOffset == textBuffer.length) {
                        Opcode opcode = initialFrame ? TEXT : CONTINUATION;
                        connection.getWriter().writeText(opcode, textBuffer, 0, textBuffer.length, false);
                        initialFrame = false;
                        textBufferOffset = 0;
                    }

                    offset += len;
                    length -= len;
                }
            }
            finally {
                lock.unlock();
            }
        }

        @Override
        public void flush() throws IOException {
            try {
                lock.lock();

                if (initialFrame) {
                    // Only one frame in the message.
                    connection.getWriter().write(textBuffer, 0, textBufferOffset);
                }
                else {
                    // Send the final frame.
                    connection.getWriter().writeText(CONTINUATION, textBuffer, 0, textBufferOffset, true);
                }

                initialFrame = true;
                textBufferOffset = 0;
            }
            finally {
                lock.unlock();
            }
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
