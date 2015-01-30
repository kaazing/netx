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
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.rules.RuleChain.outerRule;

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

/**
 * RFC-6455, section 5.2 "Base Framing Protocol"
 */
public class BaseFramingIT {

    private final Random random = new Random();

    private final K3poRule k3po = new K3poRule().setScriptRoot("org/kaazing/specification/ws/framing");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "echo.binary.payload.length.0/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength0() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        OutputStream out = connection.getOutputStream();
        InputStream in = connection.getInputStream();

        byte[] writeBytes = new byte[0];
        random.nextBytes(writeBytes);
        out.write(writeBytes);

        byte[] readBytes = new byte[0];
        in.read(readBytes);

        k3po.join();

        assertArrayEquals(writeBytes, readBytes);

        connection.close();
    }

    @Test
    @Specification({
        "echo.binary.payload.length.0/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength0UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageWriter writer = connection.getMessageWriter();
        MessageReader reader = connection.getMessageReader();

        byte[] writeBytes = new byte[0];
        random.nextBytes(writeBytes);
        writer.write(writeBytes);

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

        assertArrayEquals(writeBytes, readBytes);

        connection.close();
    }

    @Test
    @Specification({
        "echo.binary.payload.length.125/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength125() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        OutputStream out = connection.getOutputStream();
        InputStream in = connection.getInputStream();

        byte[] writeBytes = new byte[125];
        random.nextBytes(writeBytes);
        out.write(writeBytes);

        byte[] readBytes = new byte[125];
        int offset = 0;
        int length = readBytes.length;
        int bytesRead = 0;

        while ((bytesRead != -1) && (length > 0)) {
            bytesRead = in.read(readBytes, offset, length);
            if (bytesRead != -1) {
                offset += bytesRead;
                length -= bytesRead;
            }
        }
        k3po.join();

        assertArrayEquals(writeBytes, readBytes);

        connection.close();
    }

    @Test
    @Specification({
        "echo.binary.payload.length.125/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength125UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageWriter writer = connection.getMessageWriter();
        MessageReader reader = connection.getMessageReader();

        byte[] writeBytes = new byte[125];
        random.nextBytes(writeBytes);
        writer.write(writeBytes);

        byte[] readBytes = new byte[125];
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

        assertArrayEquals(writeBytes, readBytes);

        connection.close();
    }

    @Test
    @Specification({
        "echo.binary.payload.length.126/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength126() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        OutputStream out = connection.getOutputStream();
        InputStream in = connection.getInputStream();

        byte[] writeBytes = new byte[126];
        random.nextBytes(writeBytes);
        out.write(writeBytes);

        byte[] readBytes = new byte[126];
        int offset = 0;
        int length = readBytes.length;
        int bytesRead = 0;

        while ((bytesRead != -1) && (length > 0)) {
            bytesRead = in.read(readBytes, offset, length);
            if (bytesRead != -1) {
                offset += bytesRead;
                length -= bytesRead;
            }
        }
        k3po.join();

        assertArrayEquals(writeBytes, readBytes);

        connection.close();
    }

    @Test
    @Specification({
        "echo.binary.payload.length.126/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength126UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageWriter writer = connection.getMessageWriter();
        MessageReader reader = connection.getMessageReader();

        byte[] writeBytes = new byte[126];
        random.nextBytes(writeBytes);
        writer.write(writeBytes);

        byte[] readBytes = new byte[126];
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

        assertArrayEquals(writeBytes, readBytes);

        connection.close();
    }

    @Test
    @Specification({
        "echo.binary.payload.length.127/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength127() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        OutputStream out = connection.getOutputStream();
        InputStream in = connection.getInputStream();

        byte[] writeBytes = new byte[127];
        random.nextBytes(writeBytes);
        out.write(writeBytes);

        byte[] readBytes = new byte[127];
        int offset = 0;
        int length = readBytes.length;
        int bytesRead = 0;

        while ((bytesRead != -1) && (length > 0)) {
            bytesRead = in.read(readBytes, offset, length);
            if (bytesRead != -1) {
                offset += bytesRead;
                length -= bytesRead;
            }
        }
        k3po.join();

        assertArrayEquals(writeBytes, readBytes);

        connection.close();
    }

    @Test
    @Specification({
        "echo.binary.payload.length.127/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength127UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageWriter writer = connection.getMessageWriter();
        MessageReader reader = connection.getMessageReader();

        byte[] writeBytes = new byte[127];
        random.nextBytes(writeBytes);
        writer.write(writeBytes);

        byte[] readBytes = new byte[127];
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

        assertArrayEquals(writeBytes, readBytes);

        connection.close();
    }

    @Test
    @Specification({
        "echo.binary.payload.length.128/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength128() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        OutputStream out = connection.getOutputStream();
        InputStream in = connection.getInputStream();

        byte[] writeBytes = new byte[128];
        random.nextBytes(writeBytes);
        out.write(writeBytes);

        byte[] readBytes = new byte[128];
        int offset = 0;
        int length = readBytes.length;
        int bytesRead = 0;

        while ((bytesRead != -1) && (length > 0)) {
            bytesRead = in.read(readBytes, offset, length);
            if (bytesRead != -1) {
                offset += bytesRead;
                length -= bytesRead;
            }
        }
        k3po.join();

        assertArrayEquals(writeBytes, readBytes);

        connection.close();
    }

    @Test
    @Specification({
        "echo.binary.payload.length.128/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength128UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageWriter writer = connection.getMessageWriter();
        MessageReader reader = connection.getMessageReader();

        byte[] writeBytes = new byte[128];
        random.nextBytes(writeBytes);
        writer.write(writeBytes);

        byte[] readBytes = new byte[128];
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

        assertArrayEquals(writeBytes, readBytes);

        connection.close();
    }

    @Test
    @Specification({
        "echo.binary.payload.length.65535/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength65535() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        OutputStream out = connection.getOutputStream();
        InputStream in = connection.getInputStream();

        byte[] writeBytes = new byte[65535];
        random.nextBytes(writeBytes);
        out.write(writeBytes);

        byte[] readBytes = new byte[65535];
        int offset = 0;
        int length = readBytes.length;
        int bytesRead = 0;

        while ((bytesRead != -1) && (length > 0)) {
            bytesRead = in.read(readBytes, offset, length);
            if (bytesRead != -1) {
                offset += bytesRead;
                length -= bytesRead;
            }
        }
        k3po.join();

        assertArrayEquals(writeBytes, readBytes);

        connection.close();
    }

    @Test
    @Specification({
        "echo.binary.payload.length.65535/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength65535UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageWriter writer = connection.getMessageWriter();
        MessageReader reader = connection.getMessageReader();

        byte[] writeBytes = new byte[65535];
        random.nextBytes(writeBytes);
        writer.write(writeBytes);

        byte[] readBytes = new byte[65535];
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

        assertArrayEquals(writeBytes, readBytes);

        connection.close();
    }

    @Test
    @Specification({
        "echo.binary.payload.length.65536/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength65536() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        OutputStream out = connection.getOutputStream();
        InputStream in = connection.getInputStream();

        byte[] writeBytes = new byte[65536];
        random.nextBytes(writeBytes);
        out.write(writeBytes);

        byte[] readBytes = new byte[65536];
        int offset = 0;
        int length = readBytes.length;
        int bytesRead = 0;

        while ((bytesRead != -1) && (length > 0)) {
            bytesRead = in.read(readBytes, offset, length);
            if (bytesRead != -1) {
                offset += bytesRead;
                length -= bytesRead;
            }
        }
        k3po.join();

        assertArrayEquals(writeBytes, readBytes);

        connection.close();
    }

    @Test
    @Specification({
        "echo.binary.payload.length.65536/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength65536UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageWriter writer = connection.getMessageWriter();
        MessageReader reader = connection.getMessageReader();

        byte[] writeBytes = new byte[65536];
        random.nextBytes(writeBytes);
        writer.write(writeBytes);

        byte[] readBytes = new byte[65536];
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

        assertArrayEquals(writeBytes, readBytes);

        connection.close();
    }

    @Test
    @Specification({
        "echo.text.payload.length.0/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength0() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Writer writer = connection.getWriter();
        Reader reader = connection.getReader();

        String writeString = "";
        writer.write(writeString.toCharArray());

        char[] cbuf = new char[writeString.toCharArray().length];
        int offset = 0;
        int length = cbuf.length;
        int charsRead = 0;

        while ((charsRead != -1) && (length > 0)) {
            charsRead = reader.read(cbuf, offset, length);
            if (charsRead != -1) {
                offset += charsRead;
                length -= charsRead;
            }
        }
        String readString = String.valueOf(cbuf);

        k3po.join();

        assertEquals(writeString, readString);

        connection.close();
    }

    @Test
    @Specification({
        "echo.text.payload.length.0/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength0UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageWriter writer = connection.getMessageWriter();
        MessageReader reader = connection.getMessageReader();

        String writeString = "";
        writer.write(writeString.toCharArray());

        char[] cbuf = new char[writeString.toCharArray().length];
        MessageType type = null;
        while ((type = reader.next()) != MessageType.EOS) {
            switch (type) {
            case TEXT:
                reader.read(cbuf);
                break;
            default:
                assertTrue(type == MessageType.BINARY);
                break;
            }
        }

        String readString = String.valueOf(cbuf);

        k3po.join();

        assertEquals(writeString, readString);

        connection.close();
    }

    @Test
    @Specification({
        "echo.text.payload.length.125/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength125() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Writer writer = connection.getWriter();
        Reader reader = connection.getReader();

        String writeString = new RandomString(125).nextString();
        writer.write(writeString.toCharArray());

        char[] cbuf = new char[writeString.toCharArray().length];
        int offset = 0;
        int length = cbuf.length;
        int charsRead = 0;

        while ((charsRead != -1) && (length > 0)) {
            charsRead = reader.read(cbuf, offset, length);
            if (charsRead != -1) {
                offset += charsRead;
                length -= charsRead;
            }
        }
        String readString = String.valueOf(cbuf);

        k3po.join();

        assertEquals(writeString, readString);

        connection.close();
    }

    @Test
    @Specification({
        "echo.text.payload.length.125/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength125UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageWriter writer = connection.getMessageWriter();
        MessageReader reader = connection.getMessageReader();

        String writeString = new RandomString(125).nextString();
        writer.write(writeString.toCharArray());

        char[] cbuf = new char[writeString.toCharArray().length];
        MessageType type = null;
        while ((type = reader.next()) != MessageType.EOS) {
            switch (type) {
            case TEXT:
                reader.read(cbuf);
                break;
            default:
                assertTrue(type == MessageType.BINARY);
                break;
            }
        }

        String readString = String.valueOf(cbuf);
        k3po.join();
        assertEquals(writeString, readString);
        connection.close();
    }

    @Test
    @Specification({
        "echo.text.payload.length.126/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength126() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Writer writer = connection.getWriter();
        Reader reader = connection.getReader();

        String writeString = new RandomString(126).nextString();
        writer.write(writeString.toCharArray());

        char[] cbuf = new char[writeString.toCharArray().length];
        int offset = 0;
        int length = cbuf.length;
        int charsRead = 0;

        while ((charsRead != -1) && (length > 0)) {
            charsRead = reader.read(cbuf, offset, length);
            if (charsRead != -1) {
                offset += charsRead;
                length -= charsRead;
            }
        }
        String readString = String.valueOf(cbuf);

        k3po.join();

        assertEquals(writeString, readString);

        connection.close();
    }

    @Test
    @Specification({
        "echo.text.payload.length.126/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength126UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageWriter writer = connection.getMessageWriter();
        MessageReader reader = connection.getMessageReader();

        String writeString = new RandomString(126).nextString();
        writer.write(writeString.toCharArray());

        char[] cbuf = new char[writeString.toCharArray().length];
        MessageType type = null;
        while ((type = reader.next()) != MessageType.EOS) {
            switch (type) {
            case TEXT:
                reader.read(cbuf);
                break;
            default:
                assertTrue(type == MessageType.BINARY);
                break;
            }
        }

        String readString = String.valueOf(cbuf);

        k3po.join();

        assertEquals(writeString, readString);

        connection.close();
    }

    @Test
    @Specification({
        "echo.text.payload.length.127/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength127() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Writer writer = connection.getWriter();
        Reader reader = connection.getReader();

        String writeString = new RandomString(127).nextString();
        writer.write(writeString.toCharArray());

        char[] cbuf = new char[writeString.toCharArray().length];
        int offset = 0;
        int length = cbuf.length;
        int charsRead = 0;

        while ((charsRead != -1) && (length > 0)) {
            charsRead = reader.read(cbuf, offset, length);
            if (charsRead != -1) {
                offset += charsRead;
                length -= charsRead;
            }
        }
        String readString = String.valueOf(cbuf);

        k3po.join();

        assertEquals(writeString, readString);

        connection.close();
    }

    @Test
    @Specification({
        "echo.text.payload.length.127/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength127UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageWriter writer = connection.getMessageWriter();
        MessageReader reader = connection.getMessageReader();

        String writeString = new RandomString(127).nextString();
        writer.write(writeString.toCharArray());

        char[] cbuf = new char[writeString.toCharArray().length];
        MessageType type = null;
        while ((type = reader.next()) != MessageType.EOS) {
            switch (type) {
            case TEXT:
                reader.read(cbuf);
                break;
            default:
                assertTrue(type == MessageType.BINARY);
                break;
            }
        }

        String readString = String.valueOf(cbuf);

        k3po.join();

        assertEquals(writeString, readString);

        connection.close();
    }

    @Test
    @Specification({
        "echo.text.payload.length.128/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength128() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Writer writer = connection.getWriter();
        Reader reader = connection.getReader();

        String writeString = new RandomString(128).nextString();
        writer.write(writeString.toCharArray());

        char[] cbuf = new char[writeString.toCharArray().length];
        int offset = 0;
        int length = cbuf.length;
        int charsRead = 0;

        while ((charsRead != -1) && (length > 0)) {
            charsRead = reader.read(cbuf, offset, length);
            if (charsRead != -1) {
                offset += charsRead;
                length -= charsRead;
            }
        }
        String readString = String.valueOf(cbuf);

        k3po.join();

        assertEquals(writeString, readString);

        connection.close();
    }

    @Test
    @Specification({
        "echo.text.payload.length.128/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength128UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageWriter writer = connection.getMessageWriter();
        MessageReader reader = connection.getMessageReader();

        String writeString = new RandomString(128).nextString();
        writer.write(writeString.toCharArray());

        char[] cbuf = new char[writeString.toCharArray().length];
        MessageType type = null;
        while ((type = reader.next()) != MessageType.EOS) {
            switch (type) {
            case TEXT:
                reader.read(cbuf);
                break;
            default:
                assertTrue(type == MessageType.BINARY);
                break;
            }
        }

        String readString = String.valueOf(cbuf);

        k3po.join();

        assertEquals(writeString, readString);

        connection.close();
    }

    @Test
    @Specification({
        "echo.text.payload.length.65535/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength65535() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Writer writer = connection.getWriter();
        Reader reader = connection.getReader();

        String writeString = new RandomString(65535).nextString();
        writer.write(writeString.toCharArray());

        char[] cbuf = new char[writeString.toCharArray().length];
        int offset = 0;
        int length = cbuf.length;
        int charsRead = 0;

        while ((charsRead != -1) && (length > 0)) {
            charsRead = reader.read(cbuf, offset, length);
            if (charsRead != -1) {
                offset += charsRead;
                length -= charsRead;
            }
        }

        String readString = String.valueOf(cbuf);

        k3po.join();

        assertEquals(writeString, readString);

        connection.close();
    }

    @Test
    @Specification({
        "echo.text.payload.length.65535/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength65535UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageWriter writer = connection.getMessageWriter();
        MessageReader reader = connection.getMessageReader();

        String writeString = new RandomString(65535).nextString();
        writer.write(writeString.toCharArray());

        char[] cbuf = new char[writeString.toCharArray().length];
        MessageType type = null;
        while ((type = reader.next()) != MessageType.EOS) {
            switch (type) {
            case TEXT:
                reader.read(cbuf);
                break;
            default:
                assertTrue(type == MessageType.BINARY);
                break;
            }
        }

        String readString = String.valueOf(cbuf);

        k3po.join();

        assertEquals(writeString, readString);

        connection.close();
    }

    @Test
    @Specification({
        "echo.text.payload.length.65536/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength65536() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Writer writer = connection.getWriter();
        Reader reader = connection.getReader();

        String writeString = new RandomString(65533).nextString() + "\u1FFF";
        writer.write(writeString.toCharArray());

        char[] cbuf = new char[writeString.toCharArray().length];
        int offset = 0;
        int length = cbuf.length;
        int charsRead = 0;

        while ((charsRead != -1) && (length > 0)) {
            charsRead = reader.read(cbuf, offset, length);
            if (charsRead != -1) {
                offset += charsRead;
                length -= charsRead;
            }
        }

        String readString = String.valueOf(cbuf);

        k3po.join();

        assertEquals(writeString, readString);

        connection.close();
    }

    @Test
    @Specification({
        "echo.text.payload.length.65536/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength65536UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageWriter writer = connection.getMessageWriter();
        MessageReader reader = connection.getMessageReader();

        String writeString = new RandomString(65533).nextString() + "\u1FFF";
        writer.write(writeString.toCharArray());

        char[] cbuf = new char[writeString.toCharArray().length];
        MessageType type = null;
        while ((type = reader.next()) != MessageType.EOS) {
            switch (type) {
            case TEXT:
                reader.read(cbuf);
                break;
            default:
                assertTrue(type == MessageType.BINARY);
                break;
            }
        }

        String readString = String.valueOf(cbuf);

        k3po.join();

        assertEquals(writeString, readString);

        connection.close();
    }

    private int getByteCount(String str) {
        char[] buf = str.toCharArray();
        int count = 0;

        for (int i = 0; i < buf.length; i++) {
            count += expectedBytes(buf[i]);
        }

        return count;

    }
    private int expectedBytes(int value) {
        if (value < 0x80) {
            return 1;
        }
        if (value < 0x800) {
            return 2;
        }
        if (value <= '\uFFFF') {
            return 3;
        }
        return 4;
    }

    private static void hexDump(byte[] bytes) {
        StringBuilder hexDump = new StringBuilder("");

        for (int i = 0; i < bytes.length; i++) {
            if (hexDump.length() > 0) {
                hexDump.append(", ");
            }

            hexDump.append(String.format("%02x", 0xFF & bytes[i]).toUpperCase());
        }

        String s = hexDump.toString();
        System.out.println("Number of bytes: " + bytes.length);
        System.out.println("Hex Dump: " + s);
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
