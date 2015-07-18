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

package org.kaazing.netx.ws;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

/**
 * {@link MessageReader} is used to receive complete binary and text messages that may span over multiple WebSocket frames. Here
 * is the sample usage of the APIs to read or stream messages that can either fit in a single WebSocket frame or span across
 * multiple WebSocket frames:
 *
 * {@code}
 * WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
 * connection.setMaxPayloadLength(1024);
 * MessageReader messageReader = connection.getMessageReader();
 *
 * int bytesRead = 0;
 * int charsRead = 0;
 * byte[] binaryFull = new byte[connection.getMaxPayloadLength()];
 * char[] textFull = new char[connection.getMaxPayloadLength()];
 * byte[] binary = new byte[<estimated-message-length>];
 * char[] text = new char[<estimated-message-length>];
 * MessageType type = null;
 *
 * while ((type = messageReader.next()) != EOS) {
 *     switch (type) {
 *     case BINARY:
 *         if (message.streaming()) {
 *             int offset = 0;
 *             InputStream in = messageReader.getInputStream();
 *             while ((count != -1) && (offset < binary.length)) {
 *                 bytesRead = in.read(binary, offset, binary.length - offset);
 *                 if (bytesRead != -1) {
 *                     offset += bytesRead;
 *                 }
 *             }
 *         }
 *         else {
 *             bytesRead = messageReader.readFull(binaryFull);
 *         }
 *         break;
 *
 *     case TEXT:
 *         if (message.streaming() {
 *             int offset = 0;
 *             Reader reader = messageReader.getReader();
 *             while ((count != -1) && (offset < text.length)) {
 *                 charsRead = reader.read(text, offset, text.length - offset);
 *                 if (count != -1) {
 *                     offset += charsRead;
 *                 }
 *             }
 *         }
 *         else {
 *             charsRead = messageReader.readFully(textFull);
 *         }
 *         break;
 *     }
 * }
 * {@code}
 */
public abstract class MessageReader {
    /**
     * Returns an {@link InputStream} to stream a binary message. This method is typically used when the {@link #streaming()}
     * method returns true indicating that the message spans across multiple frames.
     * <p>
     * Once the entire message has been streamed, the {@link InputStream#read(byte[], int, int)} method will return a -1.
     * <p>
     * @return InputStream to stream binary payload
     * @throws IOException is thrown if the connection is closed
     */
    public abstract InputStream getInputStream() throws IOException;

    /**
     * Returns a {@link Reader} to stream a text message. This method is used when the {@link #streaming()} method
     * returns true indicating that the message spans across multiple frames.
     * <p>
     * Once the entire message has been streamed, the {@link Reader#read(char[], int, int)} method will return a -1.
     * <p>
     * @return Reader to stream text payload
     * @throws IOException is thrown if the connection is closed
     */
    public abstract Reader getReader() throws IOException;

    /**
     * Invoking this method will cause the thread to claim ownership of the next message that can be either -- a) read using
     * either the {@link #readFully(byte[])}/{@link #readFully(char[])} methods or b) streamed using the InputStream/Reader.
     * If a thread has already claimed ownership of the message by invoking this method, then subsequent calls to this method
     * by other threads will block until the current message has been read or streamed completely. When the current message has
     * been read completely, then the ownership is given up thereby allowing other threads to acquire ownership of the next
     * message.
     * <p>
     * The thread that acquired ownership can also block till the message arrives. When the message is received, this
     * method returns the type of the newly received message. Based on the returned {@link MessageType},
     * {@link #readFully(byte[])}/{@link #readFully(char[])} methods can be used to read in it's entirety if {@link #streaming}
     * return false indicating that the message fits in a single WebSocket frame. However, if {@link #streaming()} return true,
     * then the message spans across multiple WebSocket frames and should be streamed using either InputStream(obtained using
     * {@link #getInputStream()}) or Reader(obtained using {@link #getReader()}) APIs.
     * <p>
     * When the connection is closed, this method returns {@link MessageType#EOS}.
     * <p>
     * @return WebSocketMessageType     WebSocketMessageType.TEXT for a text message;
     *                                  WebSocketMessageType.BINARY for a binary message;
     *                                  WebSocketMessageType.EOS if the connection is closed
     * @throws IOException  if an I/O error occurs while advancing to the next message type
     */
    public abstract MessageType next() throws IOException;

    /**
     * Returns the {@link MessageType} of the already received message. This method returns a null until the first message is
     * received.  Note that this is not a blocking call. When connected, if this method is invoked immediately after
     * {@link #next()}, then they will return the same value.
     * <p>
     * Based on the returned {@link MessageType}, appropriate read methods can be used to receive the message. This method will
     * continue to return the same {@link MessageType} till the next message arrives. When the next message arrives, this method
     * will return the the {@link MessageType} associated with that message.
     * <p>

     * @return WebSocketMessageType    WebSocketMessageType.TEXT for a text message;
     *                                 WebSocketMessageType.BINARY for a binary message;
     *                                 WebSocketMessageType.EOS if the connection is closed;
     *                                 null before the first message
     */
    public abstract MessageType peek();

    /**
     * Read the binary message in the buffer in it's entirety. This method must be invoked after {@link #next()} method has been
     * successfully invoked by the thread to claim ownership of the message. This method is used when the {@link #streaming()}
     * method returns false indicating that the message fits in a single WebSocket frame. The application can read the message in
     * it's entirety by passing a buffer that is large enough to accommodate the message completely. A non-negative return value
     * indicates the number of bytes copied into the passed in buffer and is typically the same as the number of bytes in the
     * message payload.
     * <p>
     * An IndexOutOfBoundsException is thrown if the buffer is not large enough to hold the the entire message.
     * <p>
     * @param buf  the buffer to receive the binary message
     * @return number of bytes copied into the passed in buffer; -1 if the entire message has already been read
     * @throws IOException if the operation is attempted by a thread that does not own the current message; if the operation is
     *                     performed before invoking {@link #next()} to claim ownership of the current message; if the type of
     *                     the message is not {@link MessageType#BINARY}
     */
    public abstract int readFully(byte[] buf) throws IOException;

    /**
     * Reads the text message in the buffer in it's entirety. This method must be invoked after {@link #next()} method has been
     * successfully invoked by the thread to claim ownership of the message. This method is used when the {@link #streaming()}
     * method returns false indicating that the message fits in a single WebSocket frame. The application can read the message in
     * it's entirety by passing a buffer that is large enough to accommodate the message completely. A non-negative return value
     * indicates the number of chars copied into the passed in buffer and is typically the same as the number of chars in the
     * message payload.
     * <p>
     * An IndexOutOfBoundsException is thrown if the buffer is not large enough to hold the the entire message.
     * <p>
     * @param buf buffer to receive the text message
     * @return number of chars stored in the passed in buffer; -1 if the entire message has already been read
     * @throws IOException if the operation is attempted by a thread that does not own the current message; if the operation is
     *                     performed before invoking {@link #next()} to claim ownership of the current message; if the type of
     *                     the message is not {@link MessageType#TEXT}
     */
    public abstract int readFully(char[] buf) throws IOException;

    /**
     * Skips the current message. If the message fits in a single WebSocket frame, then this method skips the frame. However, if
     * the message spans across multiple WebSocket frames, then all the frames are skipped. Once the message is skipped, then
     * the next thread that invokes {@link #next()} method owns the next message.
     *
     * @throws IOException if the operation is attempted by a thread that does not own the current message; if the operation is
     *                     performed before invoking {@link #next()} to claim ownership of the current message.
     */
    public abstract void skip() throws IOException;

    /**
     * Indicates whether the message should be streamed or can be read in it's entirety. A true is returned if the message spans
     * across multiple WebSocket frames indicating that it can be streamed. A false is returned if the message fits in a single
     * WebSocket frame and can be read in it's entirety using either {@link #readFully(byte[])} or {@link #readFully(char[])}
     * methods.
     * <p>
     * An IllegalStateException is thrown if the operation is attempted by a thread that does not own the current message.
     * <p>
     * @return true if the message can be streamed; otherwise false
     */
    public abstract boolean streaming();
}
