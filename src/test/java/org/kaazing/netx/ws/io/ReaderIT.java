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
package org.kaazing.netx.ws.io;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.Charset;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;
import org.kaazing.netx.URLConnectionHelper;
import org.kaazing.netx.ws.WsURLConnection;

public class ReaderIT {
    private final K3poRule k3po = new K3poRule().setScriptRoot("org/kaazing/netx/ws/io");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
    "text.frame.length.bytes.split.across.chunks/handshake.response.and.frame" })
    public void shouldReadTextFrameWithLengthBytesSplitAcrossChunks() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        connection.setMaxMessageLength(8188);

        Reader reader = connection.getReader();
        Writer writer = connection.getWriter();
        Charset UTF_8 = Charset.forName("UTF-8");

        try {
            char[] charBuf = new char[8500];
            int offset = 0;
            int length = charBuf.length;
            int charsRead = 0;
            int expectedBytesLength = 8185 + 150;

            // Unlike binary frames(read using InputStream), we cannot read exact number of bytes with text frames(read using
            // Reader) as the payload can contain multi-byte characters encoded as UTF-8 bytes. In this case, both the text
            // frames with payload sizes of 8185 bytes and 150 bytes get read in the same char array. Also, unlike MessageReader,
            // Reader does not honor WebSocket frame boundaries as it is used for streaming purposes.
            while ((charsRead != -1) && (length > 0)) {
                charsRead = reader.read(charBuf, offset, length);
                if (charsRead != -1) {
                    offset += charsRead;
                    length -= charsRead;
                }

                // This is the only way to break out of this loop as there is no way to know whether the entire payload which is
                // in terms of bytes has been read from the charsRead(in terms of chars).
                int bytesLength = new String(charBuf, 0, offset).getBytes(UTF_8).length;
                if (bytesLength == expectedBytesLength) {
                    break;
                }
            }

            length = offset;
            assert offset <= charBuf.length;

            writer.write(charBuf, 0, length);
        }
        finally {
            k3po.finish();
        }
    }

    @Test
    @Specification({
    "text.frame.length.indicator.in.chunk1.length.bytes.in.chunk2/handshake.response.and.frame" })
    public void shouldReadTextFrameLengthFromNextChunk() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        connection.setMaxMessageLength(8335);
//        connection.setMaxMessageLength(8188);

        Reader reader = connection.getReader();
        Writer writer = connection.getWriter();
        Charset UTF_8 = Charset.forName("UTF-8");

        try {
            char[] charBuf = new char[8500];
            int offset = 0;
            int length = charBuf.length;
            int charsRead = 0;
            int expectedBytesLength = 8186 + 150;

            // Unlike binary frames(read using InputStream), we cannot read exact number of bytes with text frames(read using
            // Reader) as the payload can contain multi-byte characters encoded as UTF-8 bytes. In this case, both the text
            // frames with payload sizes of 8186 bytes and 150 bytes get read in the same char array. Also, unlike MessageReader,
            // Reader does not honor WebSocket frame boundaries as it is used for streaming purposes.
            while ((charsRead != -1) && (length > 0)) {
                charsRead = reader.read(charBuf, offset, length);
                if (charsRead != -1) {
                    offset += charsRead;
                    length -= charsRead;
                }

                // This is the only way to break out of this loop as there is no way to know whether the entire payload which is
                // in terms of bytes has been read from the charsRead(in terms of chars).
                int bytesLength = new String(charBuf, 0, offset).getBytes(UTF_8).length;
                if (bytesLength == expectedBytesLength) {
                    break;
                }
            }

            length = offset;
            assert offset <= charBuf.length;

            writer.write(charBuf, 0, length);
        }
        finally {
            k3po.finish();
        }
    }

    @Test
    @Specification({
    "text.frame.metadata.in.chunk1.payload.in.chunk2/handshake.response.and.frame" })
    public void shouldReadTextFrameWithMetadataAndPayloadInDifferentChunks() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        connection.setMaxMessageLength(8188);

        Reader reader = connection.getReader();
        Writer writer = connection.getWriter();
        Charset UTF_8 = Charset.forName("UTF-8");

        try {
            char[] charBuf = new char[8500];
            int offset = 0;
            int length = charBuf.length;
            int charsRead = 0;
            int expectedBytesLength = 8184 + 150;

            // Unlike binary frames(read using InputStream), we cannot read exact number of bytes with text frames(read using
            // Reader) as the payload can contain multi-byte characters encoded as UTF-8 bytes. In this case, both the text
            // frames with payload sizes of 8184 bytes and 150 bytes get read in the same char array. Also, unlike MessageReader,
            // Reader does not honor WebSocket frame boundaries as it is used for streaming purposes.
            while ((charsRead != -1) && (length > 0)) {
                charsRead = reader.read(charBuf, offset, length);
                if (charsRead != -1) {
                    offset += charsRead;
                    length -= charsRead;
                }

                // This is the only way to break out of this loop as there is no way to know whether the entire payload which is
                // in terms of bytes has been read from the charsRead(in terms of chars).
                int bytesLength = new String(charBuf, 0, offset).getBytes(UTF_8).length;
                if (bytesLength == expectedBytesLength) {
                    break;
                }
            }

            length = offset;
            assert offset <= charBuf.length;

            writer.write(charBuf, 0, length);
        }
        finally {
            k3po.finish();
        }
    }

    @Test
    @Specification({
    "text.frame.opcode.in.chunk1.length.in.chunk2/handshake.response.and.frame" })
    public void shouldReadTextFrameWithOpcodeAndLengthBytesFromDifferentChunks() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        connection.setMaxMessageLength(8188);

        Reader reader = connection.getReader();
        Writer writer = connection.getWriter();
        Charset UTF_8 = Charset.forName("UTF-8");

        try {
            char[] charBuf = new char[8500];
            int offset = 0;
            int length = charBuf.length;
            int charsRead = 0;
            int expectedBytesLength = 8187 + 150;

            // Unlike binary frames(read using InputStream), we cannot read exact number of bytes with text frames(read using
            // Reader) as the payload can contain multi-byte characters encoded as UTF-8 bytes. In this case, both the text
            // frames with payload sizes of 8187 bytes and 150 bytes get read in the same char array. Also, unlike MessageReader,
            // Reader does not honor WebSocket frame boundaries as it is used for streaming purposes.
            while ((charsRead != -1) && (length > 0)) {
                charsRead = reader.read(charBuf, offset, length);
                if (charsRead != -1) {
                    offset += charsRead;
                    length -= charsRead;
                }

                // This is the only way to break out of this loop as there is no way to know whether the entire payload which is
                // in terms of bytes has been read from the charsRead(in terms of chars).
                int bytesLength = new String(charBuf, 0, offset).getBytes(UTF_8).length;
                if (bytesLength == expectedBytesLength) {
                    break;
                }
            }

            length = offset;
            assert offset <= charBuf.length;

            writer.write(charBuf, 0, length);
        }
        finally {
            k3po.finish();
        }
    }
}
