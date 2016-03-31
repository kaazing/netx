/**
 * Copyright 2007-2015, Kaazing Corporation. All rights reserved.
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.rules.RuleChain.outerRule;
import static org.kaazing.netx.ws.MessageType.BINARY;

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
import org.kaazing.netx.ws.MessageType;
import org.kaazing.netx.ws.MessageWriter;
import org.kaazing.netx.ws.WsURLConnection;
import org.kaazing.netx.ws.internal.WsURLConnectionImpl;

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
        connection.setMaxFramePayloadLength(8188);

        MessageReader messageReader = ((WsURLConnectionImpl) connection).getMessageReader();
        MessageWriter messageWriter = ((WsURLConnectionImpl) connection).getMessageWriter();

        byte[] readBytes1 = new byte[8185];
        byte[] readBytes2 = new byte[150];
        MessageType type = null;
        int iter = 1;
        int bytesRead = 0;

        try {
            while ((iter <= 2) && (type = messageReader.next()) != MessageType.EOS) {
                switch (type) {
                case BINARY:
                    if (iter == 1) {
                        bytesRead = messageReader.readFully(readBytes1);
                        assertEquals(8185, bytesRead);
                    }
                    else {
                        bytesRead = messageReader.readFully(readBytes2);
                        assertEquals(150, bytesRead);
                    }
                    iter++;
                    break;
                default:
                    assertSame(BINARY, type);
                    break;
                }
            }

            messageWriter.writeFully(readBytes1);
            messageWriter.writeFully(readBytes2);
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
        connection.setMaxFramePayloadLength(8188);

        MessageReader messageReader = ((WsURLConnectionImpl) connection).getMessageReader();
        MessageWriter messageWriter = ((WsURLConnectionImpl) connection).getMessageWriter();

        byte[] readBytes1 = new byte[8186];
        byte[] readBytes2 = new byte[150];
        MessageType type = null;
        int iter = 1;
        int bytesRead = 0;

        try {
            while ((iter <= 2) && (type = messageReader.next()) != MessageType.EOS) {
                switch (type) {
                case BINARY:
                    if (iter == 1) {
                        bytesRead = messageReader.readFully(readBytes1);
                        assertEquals(8186, bytesRead);
                    }
                    else {
                        bytesRead = messageReader.readFully(readBytes2);
                        assertEquals(150, bytesRead);
                    }
                    iter++;
                    break;
                default:
                    assertSame(BINARY, type);
                    break;
                }
            }

            messageWriter.writeFully(readBytes1);
            messageWriter.writeFully(readBytes2);
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
        connection.setMaxFramePayloadLength(8188);

        MessageReader messageReader = ((WsURLConnectionImpl) connection).getMessageReader();
        MessageWriter messageWriter = ((WsURLConnectionImpl) connection).getMessageWriter();

        byte[] readBytes1 = new byte[8184];
        byte[] readBytes2 = new byte[150];
        MessageType type = null;
        int iter = 1;
        int bytesRead = 0;

        try {
            while ((iter <= 2) && (type = messageReader.next()) != MessageType.EOS) {
                switch (type) {
                case BINARY:
                    if (iter == 1) {
                        bytesRead = messageReader.readFully(readBytes1);
                        assertEquals(8184, bytesRead);
                    }
                    else {
                        bytesRead = messageReader.readFully(readBytes2);
                        assertEquals(150, bytesRead);
                    }
                    iter++;
                    break;
                default:
                    assertSame(BINARY, type);
                    break;
                }
            }

            messageWriter.writeFully(readBytes1);
            messageWriter.writeFully(readBytes2);
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
        connection.setMaxFramePayloadLength(8188);

        MessageReader messageReader = ((WsURLConnectionImpl) connection).getMessageReader();
        MessageWriter messageWriter = ((WsURLConnectionImpl) connection).getMessageWriter();

        byte[] readBytes1 = new byte[8187];
        byte[] readBytes2 = new byte[150];
        MessageType type = null;
        int iter = 1;
        int bytesRead = 0;

        try {
            while ((iter <= 2) && (type = messageReader.next()) != MessageType.EOS) {
                switch (type) {
                case BINARY:
                    if (iter == 1) {
                        bytesRead = messageReader.readFully(readBytes1);
                        assertEquals(8187, bytesRead);
                    }
                    else {
                        bytesRead = messageReader.readFully(readBytes2);
                        assertEquals(150, bytesRead);
                    }
                    iter++;
                    break;
                default:
                    assertSame(BINARY, type);
                    break;
                }
            }

            messageWriter.writeFully(readBytes1);
            messageWriter.writeFully(readBytes2);
        }
        finally {
            k3po.finish();
        }
    }
}
