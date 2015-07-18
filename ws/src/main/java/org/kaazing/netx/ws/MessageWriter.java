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
import java.io.OutputStream;
import java.io.Writer;

/**
 * {@link MessageWriter} is used to send binary and text messages that may span over multiple WebSocket frames. A reference to
 * {@link MessageWriter} is obtained by invoking either {@link WsURLConnection#getMessageWriter()} or
 * {@link WebSocket#getMessageWriter()} methods. Here is sample code that shows how to send a binary message that spans across
 * multiple WebSoket frames:
 *
 * {@code}
 * WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
 * connection.setMaxPayloadLength(125);
 * MessageWriter messageWriter = connection.getMessageWriter();
 *
 * byte[] binaryFrame = new byte[25];
 * int fragmentCount = 5;
 * OutputStream binaryOutputStream = messageWriter.getOutputStream();
 *
 * // Stream out a binary message that spans across multiple WebSocket frames.
 * while (fragmentCount > 0) {
 *     fragmentCount--;
 *     random.nextBytes(binaryFrame);
 *
 *     binaryOutputStream.write(binaryFrame);
 *
 *     if (fragmentCount > 0) {
 *          // Send the CONTINUATION frame.
 *          binaryOutputStream.flush();
 *     }
 *     else {
 *          // Close the stream and send the final frame of the message with FIN bit set.
 *          binaryOutputStream.close();
 *     }
 * }
 *
 * {@link MessageWriter} can also be used to send binary and text messages that fits in a single WebSocket frame using
 * {@link #writeFully(byte[])} and {@link #writeFully(char[])} methods respectively.
 *
 * {@code}
 */
public abstract class MessageWriter {
    /**
     * Return the {@link OutputStream} that can be used to send binary messages that span across multiple WebSocket frames. Once
     * the application gets the reference to the {@link OutputStream}, it can invoke {@link OutputStream#write(byte[])} and/or
     * {@link OutputStream#write(byte[] buf, int offset, int length) methods to assemble a binary message that spans across
     * multiple WebSocket frames. When the length of the internal payload buffer reaches the maximum frame payload length,
     * a CONTINUATION frame is sent.
     *
     * Invoking {@link OutputStream#flush()} forces a CONTINUATION frame to be sent using the current contents of the buffer.
     * Invoking {OutputStream Writer#close()} method forces the final WebSocket frame of the message to be written to the wire
     * using the current contents of the buffer.
     *
     * @return OutputStream to send binary messages that span across multiple WebSocket frames
     * @throws IOException if the connection is closed
     */
    public abstract OutputStream getOutputStream() throws IOException;

    /**
     * Return the {@link Writer} that can be used to send text messages that span across multiple WebSocket frames. Once
     * the application gets the reference to the {@link Writer}, it can invoke {@link Writer#write(char[])} and/or
     * {@link Writer#write(char[] buf, int offset, int length) methods to assemble a text message that spans across
     * multiple WebSocket frames. When the length of the internal payload buffer reaches the maximum frame payload length,
     * a CONTINUATION frame is sent.
     *
     * Invoking {@link Writer#flush()} forces a CONTINUATION frame to be sent using the current contents of the buffer. Invoking
     * {@link Writer#close()} method forces the final WebSocket frame of the message to be written to the wire using the
     * current contents of the buffer.
     *
     * @return Writer to send text messages that span across multiple WebSocket frames
     * @throws IOException if the connection is closed
     */
    public abstract Writer getWriter() throws IOException;

    /**
     * Sends the content of the specified buffer as a binary message in a single WebSocket frame. The length of the buffer
     * must be less than or equal to {@link WsURLConnection#getMaxPayloadLength()} / {@link WebSocket#getMaxPayloadLength()}.
     * Otherwise, an IOException is thrown.
     *
     * @param buffer binary message content
     * @throws IOException if connection is closed or the buffer's length is greater than the max payload length of
     *                     the connection
     */
    public abstract void writeFully(byte[] buffer) throws IOException;

    /**
     * Sends the content of the specified buffer as a text message in a single WebSocket frame. The content of specified
     * char array is transformed to a byte array using UTF-8 encoding. The number of bytes in the transformed byte array must be
     * less than or equal to {@link WsURLConnection#getMaxPayloadLength()} / {@link WebSocket#getMaxPayloadLength()}. Otherwise,
     * an IOException is thrown.
     *
     * @param buffer text message content
     * @throws IOException if connection is closed or the transformed byte buffer's length is greater than the max payload
     *                     length of the connection
     */
    public abstract void writeFully(char[] buffer) throws IOException;
}
