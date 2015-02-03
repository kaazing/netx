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

package org.kaazing.netx.ws.specification;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.rules.RuleChain.outerRule;

import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Ignore;
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
import org.kaazing.netx.ws.WsURLConnection;
import org.kaazing.netx.ws.internal.io.WsOutputStream;

/**
 * RFC-6455, section 5.5 "Control Frames"
 */
public class ControlIT {
    private final Random random = new Random();

    private final K3poRule k3po = new K3poRule().setScriptRoot("org/kaazing/specification/ws/control");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "client.send.close.payload.length.0/handshake.response.and.frame" })
    public void shouldEchoClientCloseFrameWithEmptyPayload() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        connection.close();
        k3po.join();
    }

    @Test
    @Specification({
        "client.send.close.payload.length.125/handshake.response.and.frame" })
    public void shouldEchoClientCloseFrameWithPayload() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        String reason = new RandomString(123).nextString();

        connection.close(1000, reason);
        k3po.join();
    }

    @Test
    @Specification({
        "client.send.close.payload.length.126/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendCloseFrameWithPayloadTooLong() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        String reason = new RandomString(124).nextString();
        AtomicInteger exceptionCaught = new AtomicInteger();

        try {
            connection.close(1000, reason);
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        assertEquals(1, exceptionCaught.get());
        k3po.join();
    }

    @Test
    @Specification({
        "client.send.ping.payload.length.0/handshake.response.and.frame" })
    public void shouldPongClientPingFrameWithEmptyPayload() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        WsOutputStream output = (WsOutputStream) connection.getOutputStream();
        output.writePing(null);
        k3po.join();
    }

    @Test
    @Specification({
        "client.send.ping.payload.length.125/handshake.response.and.frame" })
    public void shouldPongClientPingFrameWithPayload() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        WsOutputStream out = (WsOutputStream) connection.getOutputStream();

        byte[] buf = new byte[125];
        random.nextBytes(buf);

        out.writePing(buf);
        k3po.join();
    }

    @Test
    @Specification({
        "client.send.ping.payload.length.126/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendPingFrameWithPayloadTooLong() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        WsOutputStream out = (WsOutputStream) connection.getOutputStream();

        byte[] buf = new byte[126];
        random.nextBytes(buf);

        out.writePing(buf);
        k3po.join();
    }

    @Test
    @Specification({
        "client.send.pong.payload.length.0/handshake.response.and.frame" })
    public void shouldReceiveClientPongFrameWithEmptyPayload() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        WsOutputStream output = (WsOutputStream) connection.getOutputStream();
        output.writePong(null);
        k3po.join();
    }

    @Test
    @Specification({
        "client.send.pong.payload.length.125/handshake.response.and.frame" })
    public void shouldReceiveClientPongFrameWithPayload() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        WsOutputStream out = (WsOutputStream) connection.getOutputStream();

        byte[] buf = new byte[125];
        random.nextBytes(buf);

        out.writePong(buf);
        k3po.join();
    }

    @Test
    @Specification({
        "client.send.pong.payload.length.126/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenClientSendPongFrameWithPayloadTooLong() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        WsOutputStream out = (WsOutputStream) connection.getOutputStream();

        byte[] buf = new byte[126];
        random.nextBytes(buf);

        out.writePong(buf);
        k3po.join();
    }

    @Test
    @Specification({
        "server.send.close.payload.length.0/handshake.response.and.frame" })
    public void shouldEchoServerCloseFrameWithEmptyPayload() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();
        input.read();
        k3po.join();
    }

    @Test
    @Specification({
        "server.send.close.payload.length.0/handshake.response.and.frame" })
    public void shouldEchoServerCloseFrameWithEmptyPayloadUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        reader.read();
        k3po.join();
    }

    @Test
    @Specification({
        "server.send.close.payload.length.0/handshake.response.and.frame" })
    public void shouldEchoServerCloseFrameWithEmptyPayloadUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = connection.getMessageReader();
        byte[] readBytes = new byte[0];
        MessageType type = null;

        while ((type = reader.next()) != MessageType.EOS) {
            switch (type) {
            case BINARY:
                reader.read(readBytes);
                break;
            default:
                assertTrue(type == MessageType.BINARY);
                break;
            }
        }
        k3po.join();
    }

    @Test
    @Ignore
    @Specification({
        "server.send.close.payload.length.125/handshake.response.and.frame" })
    public void shouldEchoServerCloseFrameWithPayload() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();
        input.read();
        k3po.join();
    }

    @Test
    @Ignore
    @Specification({
        "server.send.close.payload.length.125/handshake.response.and.frame" })
    public void shouldEchoServerCloseFrameWithPayloadUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        reader.read();
        k3po.join();
    }

    @Test
    @Ignore
    @Specification({
        "server.send.close.payload.length.125/handshake.response.and.frame" })
    public void shouldEchoServerCloseFrameWithPayloadUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = connection.getMessageReader();
        byte[] readBytes = new byte[0];
        MessageType type = null;

        while ((type = reader.next()) != MessageType.EOS) {
            switch (type) {
            case BINARY:
                reader.read(readBytes);
                break;
            default:
                assertTrue(type == MessageType.BINARY);
                break;
            }
        }

        k3po.join();
    }

    @Test
    @Specification({
        "server.send.close.payload.length.126/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithPayloadTooLong() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();
        input.read();
        k3po.join();
    }

    @Test
    @Specification({
        "server.send.close.payload.length.126/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithPayloadTooLongUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        reader.read();
        k3po.join();
    }

    @Test
    @Specification({
        "server.send.close.payload.length.126/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithPayloadTooLongUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = connection.getMessageReader();
        byte[] readBytes = new byte[0];
        MessageType type = null;

        while ((type = reader.next()) != MessageType.EOS) {
            switch (type) {
            case BINARY:
                reader.read(readBytes);
                break;
            default:
                assertTrue(type == MessageType.BINARY);
                break;
            }
        }
        k3po.join();
    }

    @Test
    @Specification({
        "server.send.ping.payload.length.0/handshake.response.and.frame" })
    public void shouldPongServerPingFrameWithEmptyPayload() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();
        input.read();
        k3po.join();
    }

    @Test
    @Specification({
        "server.send.ping.payload.length.0/handshake.response.and.frame" })
    public void shouldPongServerPingFrameWithEmptyPayloadUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        reader.read();
        k3po.join();
    }

    @Test
    @Specification({
        "server.send.ping.payload.length.0/handshake.response.and.frame" })
    public void shouldPongServerPingFrameWithEmptyPayloadUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = connection.getMessageReader();
        byte[] readBytes = new byte[0];
        MessageType type = null;

        while ((type = reader.next()) != MessageType.EOS) {
            switch (type) {
            case BINARY:
                reader.read(readBytes);
                break;
            default:
                assertTrue(type == MessageType.BINARY);
                break;
            }
        }
        k3po.join();
    }

    @Test
    @Specification({
        "server.send.ping.payload.length.125/handshake.response.and.frame" })
    public void shouldPongServerPingFrameWithPayload() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();
        input.read();
        k3po.join();
    }

    @Test
    @Specification({
        "server.send.ping.payload.length.125/handshake.response.and.frame" })
    public void shouldPongServerPingFrameWithPayloadUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        reader.read();
        k3po.join();
    }

    @Test
    @Specification({
        "server.send.ping.payload.length.125/handshake.response.and.frame" })
    public void shouldPongServerPingFrameWithPayloadUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = connection.getMessageReader();
        byte[] readBytes = new byte[0];
        MessageType type = null;

        while ((type = reader.next()) != MessageType.EOS) {
            switch (type) {
            case BINARY:
                reader.read(readBytes);
                break;
            default:
                assertTrue(type == MessageType.BINARY);
                break;
            }
        }
        k3po.join();
    }

    @Test
    @Specification({
        "server.send.ping.payload.length.126/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendPingFrameWithPayloadTooLong() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();
        AtomicInteger exceptionCaught = new AtomicInteger();

        try {
            input.read();
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        k3po.join();
        assertEquals(1, exceptionCaught.get());
    }

    @Test
    @Specification({
        "server.send.ping.payload.length.126/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendPingFrameWithPayloadTooLongUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        AtomicInteger exceptionCaught = new AtomicInteger();

        try {
            reader.read();
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        k3po.join();
        assertEquals(1, exceptionCaught.get());
    }

    @Test
    @Specification({
        "server.send.ping.payload.length.126/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendPingFrameWithPayloadTooLongUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = connection.getMessageReader();
        byte[] readBytes = new byte[0];
        MessageType type = null;

        AtomicInteger exceptionCaught = new AtomicInteger();

        try {
            while ((type = reader.next()) != MessageType.EOS) {
                switch (type) {
                case BINARY:
                    reader.read(readBytes);
                    break;
                default:
                    assertTrue(type == MessageType.BINARY);
                    break;
                }
            }
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        k3po.join();
        assertEquals(1, exceptionCaught.get());
    }

    @Test
    @Specification({
        "server.send.pong.payload.length.0/handshake.response.and.frame" })
    public void shouldReceiveServerPongFrameWithEmptyPayload() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();
        AtomicInteger exceptionCaught = new AtomicInteger();

        try {
            input.read();
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        k3po.join();
        assertEquals(1, exceptionCaught.get());
    }

    @Test
    @Specification({
        "server.send.pong.payload.length.0/handshake.response.and.frame" })
    public void shouldReceiveServerPongFrameWithEmptyPayloadUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        AtomicInteger exceptionCaught = new AtomicInteger();

        try {
            reader.read();
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        k3po.join();
        assertEquals(1, exceptionCaught.get());
    }

    @Test
    @Specification({
        "server.send.pong.payload.length.0/handshake.response.and.frame" })
    public void shouldReceiveServerPongFrameWithEmptyPayloadUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = connection.getMessageReader();
        byte[] readBytes = new byte[0];
        MessageType type = null;
        AtomicInteger exceptionCaught = new AtomicInteger();

        try {
            while ((type = reader.next()) != MessageType.EOS) {
                switch (type) {
                case BINARY:
                    reader.read(readBytes);
                    break;
                default:
                    assertTrue(type == MessageType.BINARY);
                    break;
                }
            }
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        k3po.join();
        assertEquals(1, exceptionCaught.get());
    }

    @Test
    @Specification({
        "server.send.pong.payload.length.125/handshake.response.and.frame" })
    public void shouldReceiveServerPongFrameWithPayload() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();
        AtomicInteger exceptionCaught = new AtomicInteger();

        try {
            input.read();
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        k3po.join();
        assertEquals(1, exceptionCaught.get());
    }

    @Test
    @Specification({
        "server.send.pong.payload.length.125/handshake.response.and.frame" })
    public void shouldReceiveServerPongFrameWithPayloadUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        AtomicInteger exceptionCaught = new AtomicInteger();

        try {
            reader.read();
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        k3po.join();
        assertEquals(1, exceptionCaught.get());
    }

    @Test
    @Specification({
        "server.send.pong.payload.length.125/handshake.response.and.frame" })
    public void shouldReceiveServerPongFrameWithPayloadUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        AtomicInteger exceptionCaught = new AtomicInteger();
        MessageReader reader = connection.getMessageReader();
        byte[] readBytes = new byte[0];
        MessageType type = null;

        try {
            while ((type = reader.next()) != MessageType.EOS) {
                switch (type) {
                case BINARY:
                    reader.read(readBytes);
                    break;
                default:
                    assertTrue(type == MessageType.BINARY);
                    break;
                }
            }
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        k3po.join();
        assertEquals(1, exceptionCaught.get());
    }

    @Test
    @Specification({
        "server.send.pong.payload.length.126/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendPongFrameWithPayloadTooLong() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();
        AtomicInteger exceptionCaught = new AtomicInteger();

        try {
            input.read();
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        k3po.join();
        assertEquals(1, exceptionCaught.get());
    }

    @Test
    @Specification({
        "server.send.pong.payload.length.126/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendPongFrameWithPayloadTooLongUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        AtomicInteger exceptionCaught = new AtomicInteger();

        try {
            reader.read();
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        k3po.join();
        assertEquals(1, exceptionCaught.get());
    }

    @Test
    @Specification({
        "server.send.pong.payload.length.126/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendPongFrameWithPayloadTooLongUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        AtomicInteger exceptionCaught = new AtomicInteger();
        MessageReader reader = connection.getMessageReader();
        byte[] readBytes = new byte[0];
        MessageType type = null;

        try {
            while ((type = reader.next()) != MessageType.EOS) {
                switch (type) {
                case BINARY:
                    reader.read(readBytes);
                    break;
                default:
                    assertTrue(type == MessageType.BINARY);
                    break;
                }
            }
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        k3po.join();
        assertEquals(1, exceptionCaught.get());
    }

    @Test
    @Specification({
        "server.send.opcode.0x0b/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode11Frame() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();
        AtomicInteger exceptionCaught = new AtomicInteger();

        try {
            input.read();
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        k3po.join();
        assertEquals(1, exceptionCaught.get());
    }

    @Test
    @Specification({
        "server.send.opcode.0x0b/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode11FrameUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        AtomicInteger exceptionCaught = new AtomicInteger();

        try {
            reader.read();
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        k3po.join();
        assertEquals(1, exceptionCaught.get());
    }

    @Test
    @Specification({
        "server.send.opcode.0x0b/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode11FrameUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        AtomicInteger exceptionCaught = new AtomicInteger();
        MessageReader reader = connection.getMessageReader();
        byte[] readBytes = new byte[0];
        MessageType type = null;

        try {
            while ((type = reader.next()) != MessageType.EOS) {
                switch (type) {
                case BINARY:
                    reader.read(readBytes);
                    break;
                default:
                    assertTrue(type == MessageType.BINARY);
                    break;
                }
            }
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        k3po.join();
        assertEquals(1, exceptionCaught.get());
    }

    @Test
    @Specification({
    "server.send.opcode.0x0c/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode12Frame() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();
        AtomicInteger exceptionCaught = new AtomicInteger();

        try {
            input.read();
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        k3po.join();
        assertEquals(1, exceptionCaught.get());
    }

    @Test
    @Specification({
    "server.send.opcode.0x0c/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode12FrameUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        AtomicInteger exceptionCaught = new AtomicInteger();

        try {
            reader.read();
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        k3po.join();
        assertEquals(1, exceptionCaught.get());
    }

    @Test
    @Specification({
    "server.send.opcode.0x0c/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode12FrameUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        AtomicInteger exceptionCaught = new AtomicInteger();
        MessageReader reader = connection.getMessageReader();
        byte[] readBytes = new byte[0];
        MessageType type = null;

        try {
            while ((type = reader.next()) != MessageType.EOS) {
                switch (type) {
                case BINARY:
                    reader.read(readBytes);
                    break;
                default:
                    assertTrue(type == MessageType.BINARY);
                    break;
                }
            }
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        k3po.join();
        assertEquals(1, exceptionCaught.get());
    }

    @Test
    @Specification({
        "server.send.opcode.0x0d/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode13Frame() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();
        AtomicInteger exceptionCaught = new AtomicInteger();

        try {
            input.read();
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        k3po.join();
        assertEquals(1, exceptionCaught.get());
    }

    @Test
    @Specification({
        "server.send.opcode.0x0d/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode13FrameUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        AtomicInteger exceptionCaught = new AtomicInteger();

        try {
            reader.read();
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        k3po.join();
        assertEquals(1, exceptionCaught.get());
    }

    @Test
    @Specification({
        "server.send.opcode.0x0d/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode13FrameUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        AtomicInteger exceptionCaught = new AtomicInteger();
        MessageReader reader = connection.getMessageReader();
        byte[] readBytes = new byte[0];
        MessageType type = null;

        try {
            while ((type = reader.next()) != MessageType.EOS) {
                switch (type) {
                case BINARY:
                    reader.read(readBytes);
                    break;
                default:
                    assertTrue(type == MessageType.BINARY);
                    break;
                }
            }
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        k3po.join();
        assertEquals(1, exceptionCaught.get());
    }

    @Test
    @Specification({
        "server.send.opcode.0x0e/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode14Frame() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();
        AtomicInteger exceptionCaught = new AtomicInteger();

        try {
            input.read();
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        k3po.join();
        assertEquals(1, exceptionCaught.get());
    }

    @Test
    @Specification({
        "server.send.opcode.0x0e/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode14FrameUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        AtomicInteger exceptionCaught = new AtomicInteger();

        try {
            reader.read();
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        k3po.join();
        assertEquals(1, exceptionCaught.get());
    }

    @Test
    @Specification({
        "server.send.opcode.0x0e/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode14FrameUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        AtomicInteger exceptionCaught = new AtomicInteger();
        MessageReader reader = connection.getMessageReader();
        byte[] readBytes = new byte[0];
        MessageType type = null;

        try {
            while ((type = reader.next()) != MessageType.EOS) {
                switch (type) {
                case BINARY:
                    reader.read(readBytes);
                    break;
                default:
                    assertTrue(type == MessageType.BINARY);
                    break;
                }
            }
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        k3po.join();
        assertEquals(1, exceptionCaught.get());
    }

    @Test
    @Specification({
        "server.send.opcode.0x0f/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode15Frame() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();
        AtomicInteger exceptionCaught = new AtomicInteger();

        try {
            input.read();
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        k3po.join();
        assertEquals(1, exceptionCaught.get());
    }

    @Test
    @Specification({
        "server.send.opcode.0x0f/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode15FrameUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        AtomicInteger exceptionCaught = new AtomicInteger();

        try {
            reader.read();
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        k3po.join();
        assertEquals(1, exceptionCaught.get());
    }

    @Test
    @Specification({
        "server.send.opcode.0x0f/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode15FrameUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        AtomicInteger exceptionCaught = new AtomicInteger();
        MessageReader reader = connection.getMessageReader();
        byte[] readBytes = new byte[0];
        MessageType type = null;

        try {
            while ((type = reader.next()) != MessageType.EOS) {
                switch (type) {
                case BINARY:
                    reader.read(readBytes);
                    break;
                default:
                    assertTrue(type == MessageType.BINARY);
                    break;
                }
            }
        }
        catch (Exception ex) {
            exceptionCaught.incrementAndGet();
        }

        k3po.join();
        assertEquals(1, exceptionCaught.get());
    }


    private static class RandomString {

        private static final char[] symbols;

        static {
          StringBuilder tmp = new StringBuilder();
          for (char ch = 32; ch <= 126; ++ch) {
            tmp.append(ch);
          }
          symbols = tmp.toString().toCharArray();
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
            buf[idx] = symbols[random.nextInt(symbols.length)];
          }

          return new String(buf);
        }
    }
}
