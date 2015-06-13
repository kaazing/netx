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
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.rules.RuleChain.outerRule;
import static org.kaazing.netx.ws.MessageType.BINARY;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.util.Random;

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

public class StreamingIT {
    private final Random random = new Random();

    private final K3poRule k3po = new K3poRule().setScriptRoot("org/kaazing/netx/ws/streaming");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "client.send.binary.payload.length.125.fragmented/handshake.response.and.frames" })
    public void shouldEchoClientSentBinaryFrameWithPayloadFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        connection.setMaxFramePayloadLength(25);
        MessageReader messageReader = ((WsURLConnectionImpl) connection).getMessageReader();
        MessageWriter messageWriter = ((WsURLConnectionImpl) connection).getMessageWriter();

        byte[] binaryMessage = new byte[125];
        byte[] binaryFrame = new byte[25];
        int fragmentCount = 5;
        int messageOffset = 0;
        OutputStream binaryOutputStream = messageWriter.getOutputStream();

        // Stream out a binary message that spans across multiple WebSocket frames.
        while (fragmentCount > 0) {
            random.nextBytes(binaryFrame);
            System.arraycopy(binaryFrame, 0, binaryMessage, messageOffset, binaryFrame.length);
            messageOffset += binaryFrame.length;

            binaryOutputStream.write(binaryFrame);
            fragmentCount--;
        }

        // Flush the stream to indicate the end of the message.
        binaryOutputStream.flush();

        byte[] recvdBinaryMessage = new byte[125];
        MessageType type = null;
        int bytesRead = 0;
        int count = 0;

        if ((type = messageReader.next()) != MessageType.EOS) {
            assert messageReader.streaming();

            switch (type) {
            case BINARY:
                InputStream in = messageReader.getInputStream();
                int offset = 0;

                // Stream in a binary message that spans across multiple WebSocket frames.
                while ((count != -1) && (offset < recvdBinaryMessage.length)) {
                    count = in.read(recvdBinaryMessage, offset, recvdBinaryMessage.length - offset);
                    if (count != -1) {
                        bytesRead += count;
                        offset += count;
                    }
                }
                assertEquals(125, bytesRead);
                break;
            default:
                assertSame(BINARY, type);
                break;
            }
        }

        assertArrayEquals(binaryMessage, recvdBinaryMessage);
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.text.payload.length.125.fragmented/handshake.response.and.frames" })
    public void shouldEchoClientSentTextFrameWithPayloadFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        connection.setMaxFramePayloadLength(25);
        MessageReader messageReader = ((WsURLConnectionImpl) connection).getMessageReader();
        MessageWriter messageWriter = ((WsURLConnectionImpl) connection).getMessageWriter();


        char[] textMessage = new char[125];
        char[] textFrame;
        int fragmentCount = 5;
        int messageOffset = 0;
        Writer textWriter = messageWriter.getWriter();

        // Stream out a text message that spans across multiple WebSocket frames.
        while (fragmentCount > 0) {
            String frame = new RandomString(25).nextString();
            textFrame = frame.toCharArray();
            System.arraycopy(textFrame, 0, textMessage, messageOffset, textFrame.length);
            messageOffset += textFrame.length;

            textWriter.write(textFrame);
            fragmentCount--;
        }

        // Flush the stream to indicate the end of the message.
        textWriter.flush();

        char[] recvdTextMessage = new char[125];
        MessageType type = null;
        int charsRead = 0;
        int count = 0;

        if ((type = messageReader.next()) != MessageType.EOS) {
            assert messageReader.streaming();

            switch (type) {
            case TEXT:
                Reader reader = messageReader.getReader();
                int offset = 0;

                // Stream in a text message that spans across multiple WebSocket frames.
                while ((count != -1) && (offset < recvdTextMessage.length)) {
                    count = reader.read(recvdTextMessage, offset, recvdTextMessage.length - offset);
                    if (count != -1) {
                        charsRead += count;
                        offset += count;
                    }
                }
                assertEquals(125, charsRead);
                break;
            default:
                assertSame(BINARY, type);
                break;
            }
        }

        assertArrayEquals(textMessage, recvdTextMessage);
        k3po.finish();
    }


    private static class RandomString {
        private static final char[] SYMBOLS;

        static {
            StringBuilder symbols = new StringBuilder();
            for (char ch = 32; ch <= 126; ++ch) {
                symbols.append(ch);
            }
            SYMBOLS = symbols.toString().toCharArray();
        }

        private final Random random = new Random();

        private final char[] buf;

        public RandomString(int length) {
          if (length < 1) {
            throw new IllegalArgumentException("length < 1: " + length);
          }
          buf = new char[length];
        }

        public String nextString() {
          for (int idx = 0; idx < buf.length; ++idx) {
            buf[idx] = SYMBOLS[random.nextInt(SYMBOLS.length)];
          }

          return new String(buf);
        }
    }
}
