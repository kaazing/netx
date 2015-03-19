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
 * {@link MessageWriter} is used to send binary and text messages. A reference to {@link MessageWriter} is obtained by invoking
 * either {@link WsURLConnection#getMessageWriter()} or {@link WebSocket#getMessageWriter()} methods.
 */
public abstract class MessageWriter {
    /**
     * Sends a binary message using the entire specified buffer.
     *
     * @param  buf            binary payload of the message
     * @throws IOException    if an IO error occurs
     */
    public abstract void write(byte[] buf) throws IOException;

    /**
     * Sends a binary message using a portion of the specified buffer.
     *
     * @param  buf            binary payload of the message
     * @param  offset         offset from which to start sending
     * @param  length         number of bytes to send
     * @throws IOException    if an IO error occurs
     */
    public abstract void write(byte[] buf, int offset, int length) throws IOException;

    /**
     * Sends a text message using the entire specified buffer.
     *
     * @param  buf            text payload of the message
     * @throws IOException    if an IO error occurs
     */
    public abstract void write(char[] buf) throws IOException;

    /**
     * Sends a text message using a portion of the specified buffer.
     *
     * @param  buf            text payload of the message
     * @param  offset         offset from which to start sending
     * @param  length         number of characters to send
     * @throws IOException    if an IO error occurs
     */
    public abstract void write(char[] buf, int offset, int length) throws IOException;

}
