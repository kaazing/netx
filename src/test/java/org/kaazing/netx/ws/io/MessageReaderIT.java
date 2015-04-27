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

import java.net.URI;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;
import org.kaazing.netx.URLConnectionHelper;
import org.kaazing.netx.ws.MessageReader;
import org.kaazing.netx.ws.MessageWriter;
import org.kaazing.netx.ws.WsURLConnection;

public class MessageReaderIT {
    private final K3poRule k3po = new K3poRule().setScriptRoot("org/kaazing/netx/ws/io");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
    "binary.frame.length.bytes.split.across.chunks/handshake.response.and.frame" })
    public void shouldReadBinaryFrameWithLengthBytesSplitAcrossChunks() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = connection.getMessageReader();
        MessageWriter writer = connection.getMessageWriter();

        try {
            byte[] readBytes1 = new byte[8185];
            byte[] readBytes2 = new byte[150];

            int bytesRead = reader.read(readBytes1);
            assert bytesRead == readBytes1.length;

            bytesRead = reader.read(readBytes2);
            assert bytesRead == readBytes2.length;

            writer.write(readBytes1);
            writer.write(readBytes2);
        }
        finally {
            k3po.finish();
        }
    }

    @Test
    @Specification({
    "binary.frame.length.indicator.in.chunk1.length.bytes.in.chunk2/handshake.response.and.frame" })
    public void shouldReadBinaryFrameLengthFromNextChunk() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = connection.getMessageReader();
        MessageWriter writer = connection.getMessageWriter();

        try {
            byte[] readBytes1 = new byte[8186];
            byte[] readBytes2 = new byte[150];

            int bytesRead = reader.read(readBytes1);
            assert bytesRead == readBytes1.length;

            bytesRead = reader.read(readBytes2);
            assert bytesRead == readBytes2.length;

            writer.write(readBytes1);
            writer.write(readBytes2);
        }
        finally {
            k3po.finish();
        }
    }

    @Test
    @Specification({
    "binary.frame.metadata.in.chunk1.payload.in.chunk2/handshake.response.and.frame" })
    public void shouldReadBinaryFrameWithMetadataAndPayloadInDifferentChunks() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = connection.getMessageReader();
        MessageWriter writer = connection.getMessageWriter();

        try {
            byte[] readBytes1 = new byte[8184];
            byte[] readBytes2 = new byte[150];

            int bytesRead = reader.read(readBytes1);
            assert bytesRead == readBytes1.length;

            bytesRead = reader.read(readBytes2);
            assert bytesRead == readBytes2.length;

            writer.write(readBytes1);
            writer.write(readBytes2);
        }
        finally {
            k3po.finish();
        }
    }

    @Test
    @Specification({
    "binary.frame.opcode.in.chunk1.length.in.chunk2/handshake.response.and.frame" })
    public void shouldReadBinaryFrameWithOpcodeAndLengthBytesFromDifferentChunks() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = connection.getMessageReader();
        MessageWriter writer = connection.getMessageWriter();

        try {
            byte[] readBytes1 = new byte[8187];
            byte[] readBytes2 = new byte[150];

            int bytesRead = reader.read(readBytes1);
            assert bytesRead == readBytes1.length;

            bytesRead = reader.read(readBytes2);
            assert bytesRead == readBytes2.length;

            writer.write(readBytes1);
            writer.write(readBytes2);
        }
        finally {
            k3po.finish();
        }
    }

    @Test
    @Specification({
    "text.frame.length.bytes.split.across.chunks/handshake.response.and.frame" })
    public void shouldReadTextFrameWithLengthBytesSplitAcrossChunks() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = connection.getMessageReader();
        MessageWriter writer = connection.getMessageWriter();

        try {
            char[] charBuf = new char[8500];
            int offset = 0;
            int length = charBuf.length;
            int charsRead = 0;

            // Read the first text frame that contains 8185 bytes as payload. Note that MessageReader honors WebSocket frame
            // boundary. So, it will read the entire paylaod and then return. Also, note that charsRead is in terms of number of
            // chars. But, the payload of the text frame is in terms of bytes.
            charsRead += reader.read(charBuf, offset, length);

            offset += charsRead;
            length -= charsRead;

            // Read the second text frame that contains 150 bytes as payload. The chars from the second frame are also being
            // copied into charBuf.
            charsRead += reader.read(charBuf, offset, length);

            // Write the text frame with (8185 + 150 = 8335) bytes of payload.
            writer.write(charBuf, 0, charsRead);
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
        MessageReader reader = connection.getMessageReader();
        MessageWriter writer = connection.getMessageWriter();

        try {
            char[] charBuf = new char[8500];
            int offset = 0;
            int length = charBuf.length;
            int charsRead = 0;

            // Read the first text frame that contains 8186 bytes as payload. Note that MessageReader honors WebSocket frame
            // boundary. So, it will read the entire paylaod and then return. Also, note that charsRead is in terms of number of
            // chars. But, the payload of the text frame is in terms of bytes.
            charsRead += reader.read(charBuf, offset, length);

            offset += charsRead;
            length -= charsRead;

            // Read the second text frame that contains 150 bytes as payload. The chars from the second frame are also being
            // copied into charBuf.
            charsRead += reader.read(charBuf, offset, length);

            // Write the text frame with (8186 + 150 = 8336) bytes of payload.
            writer.write(charBuf, 0, charsRead);
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
        MessageReader reader = connection.getMessageReader();
        MessageWriter writer = connection.getMessageWriter();

        try {
            char[] charBuf = new char[8500];
            int offset = 0;
            int length = charBuf.length;
            int charsRead = 0;

            // Read the first text frame that contains 8184 bytes as payload. Note that MessageReader honors WebSocket frame
            // boundary. So, it will read the entire paylaod and then return. Also, note that charsRead is in terms of number of
            // chars. But, the payload of the text frame is in terms of bytes.
            charsRead += reader.read(charBuf, offset, length);

            offset += charsRead;
            length -= charsRead;

            // Read the second text frame that contains 150 bytes as payload. The chars from the second frame are also being
            // copied into charBuf.
            charsRead += reader.read(charBuf, offset, length);

            // Write the text frame with (8184 + 150 = 8334) bytes of payload.
            writer.write(charBuf, 0, charsRead);
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
        MessageReader reader = connection.getMessageReader();
        MessageWriter writer = connection.getMessageWriter();

        try {
            char[] charBuf = new char[8500];
            int offset = 0;
            int length = charBuf.length;
            int charsRead = 0;

            // Unlike binary frames(read using InputStream), we cannot read exact number of bytes with text frames(read using
            // Reader) as the payload can contain multi-byte characters encoded as UTF-8 bytes. In this case, both the text
            // frames with payload sizes of 8187 bytes and 150 bytes get read in the same char array. Also, unlike MessageReader,
            // Reader does not honor WebSocket frame boundaries as it is used for streaming purposes.
            charsRead += reader.read(charBuf, offset, length);

            offset += charsRead;
            length -= charsRead;

            // Read the second text frame that contains 150 bytes as payload. The chars from the second frame are also being
            // copied into charBuf.
            charsRead += reader.read(charBuf, offset, length);

            // Write the text frame with (8176 + 150 = 8337) bytes of payload.
            writer.write(charBuf, 0, charsRead);
        }
        finally {
            k3po.finish();
        }
    }
}
