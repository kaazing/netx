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

import java.io.IOException;

/**
 * {@link MessageReader} is used to receive complete binary and text messages that may span over multiple frames. It is the
 * application developer's responsibility to pass in appropriately sized byte array for binary messages and char array for text
 * messages that can hold the entire message.
 * <p>
 * A {@link MessageReader} allows looking at the {@link MessageType} so that the application developer can invoke the
 * appropriate read() method to either retrieve a text message or a binary message.
 */
public abstract class MessageReader {
    /**
     * Invoking this method will cause the thread to block until a message is received. When the message is received, this
     * method returns the type of the newly received message. Based on the returned {@link MessageType}, appropriate getter
     * methods can be used to retrieve the binary or text message. When the connection is closed, this method returns
     * {@link MessageType#EOS}.
     * <p>
     * If this method is invoked while a message is being read into a buffer, it will just return the type associated with the
     * current message.
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
     * Returns the payload of the entire binary message. If the message is being received in multiple CONTINUATION frames, then
     * this method will read all the frames into the specified buffer. It is the responsibility of the application developer to
     * not only pass in a buffer that can hold the entire message but also ensure that the payload of all the CONTINUATION frames
     * is within the bounds of the length parameter.
     * <p>
     * An IOException is thrown if this method is used to read a text message. An IndexOutOfBoundsException is thrown if the
     * buffer is not large enough to hold the the entire message.
     * <p>
     * @param buf      buffer into which data is read
     * @param offset   the start offset in array buf at which the data is written
     * @param length   total number of bytes to be read; if the payload is fragmented, then this value should be greater than
     *                 or equal to the sum of the payloads of the individual frames that make up a message.
     * @return total number of bytes read
     * @throws IOException  if the type of the message is not {@link MessageType#BINARY}
     */
    public abstract int read(byte[] buf, int offset, int length) throws IOException;

    /**
     * Returns the payload of the entire binary message. It is the responsibility of the application developer to pass in a
     * buffer that can hold the entire message that may span across multiple CONTINUATION frames.
     * <p>
     * An IOException is thrown if this method is used to read a text message. An IndexOutOfBoundsException is thrown if the
     * buffer is not large enough to hold the the entire message.
     * <p>
     * @param buf      buffer into which data is read
     * @return total number of bytes read
     * @throws IOException  if the type of the message is not {@link MessageType#BINARY}
     */
    public abstract int read(byte[] buf) throws IOException;

    /**
     * Returns the payload of the entire text message. If the message is being received in multiple CONTINUATION frames, then
     * this method will read all the frames into the specified buffer. It is the responsibility of the application developer to
     * not only pass in a buffer that can hold the entire message but also ensure that the payload of all the CONTINUATION frames
     * is within the bounds of the length parameter.
     * <p>
     * An IOException is thrown if this method is used to read a binary message. An IndexOutOfBoundsException is thrown if the
     * buffer is not large enough to hold the the entire message.
     * <p>
     * @param buf      buffer into which data is read
     * @param offset   the start offset in array buf at which the data is written
     * @param length   total number of chars to be read; if the payload is fragmented, then this value should be greater than
     *                 or equal to the sum of the number of chars in each of the individual frames that make up a message.
     * @return total number of chars read
     * @throws IOException  if the type of the message is not {@link MessageType#TEXT}
     */
    public abstract int read(char[] buf, int offset, int length) throws IOException;

    /**
     * Returns the payload of the entire text message. It is the responsibility of the application developer to pass in a buffer
     * that can hold the entire message that may span across multiple CONTINUATION frames.
     * <p>
     * An IOException is thrown if this method is used to read a text message. An IndexOutOfBoundsException is thrown if the
     * buffer is not large enough to hold the entire message..
     * <p>
     * @param buf      buffer into which data is read
     * @return total number of chars read
     * @throws IOException  if the type of the message is not {@link MessageType#BINARY}
     */
    public abstract int read(char[] buf) throws IOException;
}
