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

/**
 * {@link MessageReader} is used to receive complete binary and text messages
 * that may span over multiple frames. It is the application developer's
 * responsibility to pass in appropriately sized byte array for binary messages
 * and char array for text messages.
 * <p>
 * A {@link MessageReader} allows looking at the {@link MessageType} so
 * that the application developer can invoke the appropriate read() method to
 * either retrieve a text message or a binary message.
 * <p>
 * Once the connection is closed, a new {@link MessageReader} should
 * be obtained using the aforementioned methods after the connection has been
 * established. Using the old reader will result in IOException.
 */
public abstract class MessageReader {
    /**
     * Invoking this method will cause the thread to block until a message is
     * received. When the message is received, this method returns the type of
     * the newly received message. Based on the returned
     * {@link MessageType}, appropriate getter methods can be used to
     * retrieve the binary or text message. When the connection is closed, this
     * method returns {@link MessageType#EOS}.
     * <p>
     * If this method is invoked while a message is being read into a buffer,
     * it will just return the type associated with the current message.
     * <p>
     * @return WebSocketMessageType     WebSocketMessageType.TEXT for a text
     *                         message; WebSocketMessageType.BINARY
     *                         for a binary message; WebSocketMessageType.EOS
     *                         if the connection is closed
     * @throws IOException  if an I/O error occurs while advancing to the next message type
     */
    public abstract MessageType next() throws IOException;

    /**
     * Returns the {@link MessageType} of the already received message.
     * This method returns a null until the first message is received.  Note
     * that this is not a blocking call. When connected, if this method is
     * invoked immediately after {@link #next()}, then they will return the same
     * value.
     * <p>
     * Based on the returned {@link MessageType}, appropriate read
     * methods can be used to receive the message. This method will continue to
     * return the same {@link MessageType} till the next message
     * arrives. When the next message arrives, this method will return the
     * the {@link MessageType} associated with that message.
     * <p>

     * @return WebSocketMessageType    WebSocketMessageType.TEXT for a text
     *                                 message; WebSocketMessageType.BINARY
     *                                 for a binary message; WebSocketMessageType.EOS
     *                                 if the connection is closed; null before
     *                                 the first message
     */
    public abstract MessageType peek();

    /**
     * Returns the payload of the entire binary message. If the message is being
     * received in multiple CONTINUATION frames, then this method will read all
     * the frames into the specified buffer. For each frame, the method will try
     * to read the same number of bytes as specified by the len parameter. It is
     * the responsibility of the application developer to not only pass in a
     * buffer that can hold the entire message but also ensure that the payload
     * of all the CONTINUATION frames is within the bounds of the len parameter.
     * <p>
     * Ideally, this method should be invoked after {@link #next()} only if the
     * return value is {@link MessageType#BINARY}. An IOException is thrown if
     * this method is used to read a text message.
     * <p>
     * @param buf      buffer into which data is read
     * @param offset   the start offset in array b at which the data is written
     * @param length      the maximum number of bytes to read.
     * @return the number of bytes read
     * @throws IOException  if the type of the is not {@link MessageType#BINARY};
     *                      if the buffer cannot accommodate the entire message
     */
    public abstract int read(byte[] buf, int offset, int length) throws IOException;

    /**
     * Returns the payload of the bianry message. This method should be used if
     * the entire text message fits in a single WebSocket frame. It is
     * the responsibility of the application developer to pass in a buffer that
     * can hold the entire message. If the buffer cannot hold the entire message,
     * an IOException is thrown.
     * <p>
     * Ideally, this method should be invoked after {@link #next()} only if the
     * return value is {@link MessageType#BINARY}. An IOException is thrown if
     * this method is used to read a text message.
     * <p>
     * @param buf      buffer into which data is read
     * @return the number of bytes read
     * @throws IOException  if the type of the is not {@link MessageType#BINARY};
     *                      if the buffer cannot accommodate the entire message
     */
    public abstract int read(byte[] buf) throws IOException;

    /**
     * Returns the payload of the entire text message. If the message is being
     * received in multiple CONTINUATION frames, then this method will read all
     * the frames into the specified buffer. For each frame, the method will try
     * to read the same number of bytes as specified by the len parameter. It is
     * the responsibility of the application developer to not only pass in a
     * buffer that can hold the entire message but also ensure that the payload
     * of all the CONTINUATION frames is within the bounds of the len parameter.
     * <p>
     * Ideally, this method should be invoked after {@link #next()} only if the
     * return value is {@link MessageType#TEXT}. An IOException is thrown if
     * this method is used to read a binary message.
     * <p>
     * @param buf      buffer into which data is read
     * @param offset   the start offset in array b at which the data is written
     * @param length      the maximum number of bytes to read for each frame
     * @return the number of chars read
     * @throws IOException  if the type of the is not {@link MessageType#TEXT};
     *                      if the buffer cannot accommodate the entire message
     */
    public abstract int read(char[] buf, int offset, int length) throws IOException;

    /**
     * Returns the payload of the text message. This method should be used if
     * the entire text message fits in a single WebSocket frame. It is
     * the responsibility of the application developer to pass in a buffer that
     * can hold the entire message. If the buffer cannot hold the entire message,
     * an IOException is thrown.
     * <p>
     * @param buf      buffer into which data is read
     * @return the number of chars read
     * @throws IOException  if the type of the is not {@link MessageType#TEXT};
     *                      if the buffer cannot accommodate the entire message
     */
    public abstract int read(char[] buf) throws IOException;
}
