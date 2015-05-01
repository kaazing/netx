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
import static org.junit.Assert.assertSame;
import static org.junit.rules.RuleChain.outerRule;
import static org.kaazing.netx.ws.internal.io.MessageType.BINARY;

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
import org.kaazing.netx.ws.WsURLConnection;
import org.kaazing.netx.ws.internal.WsURLConnectionImpl;
import org.kaazing.netx.ws.internal.io.MessageReader;
import org.kaazing.netx.ws.internal.io.MessageType;
import org.kaazing.netx.ws.internal.io.MessageWriter;

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

        k3po.finish();

        assertArrayEquals(writeBytes, readBytes);
    }

    @Test
    @Specification({
        "echo.binary.payload.length.0/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength0UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = ((WsURLConnectionImpl) connection).getMessageReader();
        MessageWriter writer = ((WsURLConnectionImpl) connection).getMessageWriter();

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
                assertSame(BINARY, type);
                break;
            }
        }

        k3po.finish();

        assertArrayEquals(writeBytes, readBytes);
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
        k3po.finish();

        assertArrayEquals(writeBytes, readBytes);
    }

    @Test
    @Specification({
        "echo.binary.payload.length.125/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength125UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = ((WsURLConnectionImpl) connection).getMessageReader();
        MessageWriter writer = ((WsURLConnectionImpl) connection).getMessageWriter();

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
                assertSame(BINARY, type);
                break;
            }
        }

        k3po.finish();

        assertArrayEquals(writeBytes, readBytes);
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
        k3po.finish();

        assertArrayEquals(writeBytes, readBytes);
    }

    @Test
    @Specification({
        "echo.binary.payload.length.126/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength126UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = ((WsURLConnectionImpl) connection).getMessageReader();
        MessageWriter writer = ((WsURLConnectionImpl) connection).getMessageWriter();

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
                assertSame(BINARY, type);
                break;
            }
        }

        k3po.finish();

        assertArrayEquals(writeBytes, readBytes);
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
        k3po.finish();

        assertArrayEquals(writeBytes, readBytes);
    }

    @Test
    @Specification({
        "echo.binary.payload.length.127/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength127UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = ((WsURLConnectionImpl) connection).getMessageReader();
        MessageWriter writer = ((WsURLConnectionImpl) connection).getMessageWriter();

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
                assertSame(BINARY, type);
                break;
            }
        }

        k3po.finish();

        assertArrayEquals(writeBytes, readBytes);
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
        k3po.finish();

        assertArrayEquals(writeBytes, readBytes);
    }

    @Test
    @Specification({
        "echo.binary.payload.length.128/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength128UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = ((WsURLConnectionImpl) connection).getMessageReader();
        MessageWriter writer = ((WsURLConnectionImpl) connection).getMessageWriter();

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
                assertSame(BINARY, type);
                break;
            }
        }

        k3po.finish();

        assertArrayEquals(writeBytes, readBytes);
    }

    @Test
    @Specification({
        "echo.binary.payload.length.65535/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength65535() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        connection.setMaxPayloadLength(65536);

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
        k3po.finish();

        assertArrayEquals(writeBytes, readBytes);
    }

    @Test
    @Specification({
        "echo.binary.payload.length.65535/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength65535UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        connection.setMaxPayloadLength(65536);

        MessageReader reader = ((WsURLConnectionImpl) connection).getMessageReader();
        MessageWriter writer = ((WsURLConnectionImpl) connection).getMessageWriter();

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
                assertSame(BINARY, type);
                break;
            }
        }

        k3po.finish();

        assertArrayEquals(writeBytes, readBytes);
    }

    @Test
    @Specification({
        "echo.binary.payload.length.65536/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength65536() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        connection.setMaxPayloadLength(65536);

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
        k3po.finish();

        assertArrayEquals(writeBytes, readBytes);
    }

    @Test
    @Specification({
        "echo.binary.payload.length.65536/handshake.response.and.frame" })
    public void shouldEchoBinaryFrameWithPayloadLength65536UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        connection.setMaxPayloadLength(65536);

        MessageReader reader = ((WsURLConnectionImpl) connection).getMessageReader();
        MessageWriter writer = ((WsURLConnectionImpl) connection).getMessageWriter();

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
                assertSame(BINARY, type);
                break;
            }
        }

        k3po.finish();

        assertArrayEquals(writeBytes, readBytes);
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

        k3po.finish();

        assertEquals(writeString, readString);
    }

    @Test
    @Specification({
        "echo.text.payload.length.0/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength0UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = ((WsURLConnectionImpl) connection).getMessageReader();
        MessageWriter writer = ((WsURLConnectionImpl) connection).getMessageWriter();

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
                assertSame(BINARY, type);
                break;
            }
        }

        String readString = String.valueOf(cbuf);

        k3po.finish();

        assertEquals(writeString, readString);
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

        k3po.finish();

        assertEquals(writeString, readString);
    }

    @Test
    @Specification({
        "echo.text.payload.length.125/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength125ReadInPartsOfEqualLength() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Writer writer = connection.getWriter();
        Reader reader = connection.getReader();

        String writeString = new RandomString(125).nextString();
        writer.write(writeString.toCharArray());

        char[] cbuf1 = new char[25];
        char[] cbuf2 = new char[25];
        char[] cbuf3 = new char[25];
        char[] cbuf4 = new char[25];
        char[] cbuf5 = new char[25];

        int offset1 = 0;
        int offset2 = 0;
        int offset3 = 0;
        int offset4 = 0;
        int offset5 = 0;

        int length1 = cbuf1.length;
        int length2 = cbuf2.length;
        int length3 = cbuf3.length;
        int length4 = cbuf4.length;
        int length5 = cbuf5.length;

        int charsRead1 = 0;
        int charsRead2 = 0;
        int charsRead3 = 0;
        int charsRead4 = 0;
        int charsRead5 = 0;

        while ((charsRead1 != -1) && (length1 > 0)) {
            charsRead1 = reader.read(cbuf1, offset1, length1);
            if (charsRead1 != -1) {
                offset1 += charsRead1;
                length1 -= charsRead1;
            }
        }
        while ((charsRead2 != -1) && (length2 > 0)) {
            charsRead2 = reader.read(cbuf2, offset2, length2);
            if (charsRead2 != -1) {
                offset2 += charsRead2;
                length2 -= charsRead2;
            }
        }
        while ((charsRead3 != -1) && (length3 > 0)) {
            charsRead3 = reader.read(cbuf3, offset3, length3);
            if (charsRead3 != -1) {
                offset3 += charsRead3;
                length3 -= charsRead3;
            }
        }
        while ((charsRead4 != -1) && (length4 > 0)) {
            charsRead4 = reader.read(cbuf4, offset4, length4);
            if (charsRead4 != -1) {
                offset4 += charsRead4;
                length4 -= charsRead4;
            }
        }
        while ((charsRead5 != -1) && (length5 > 0)) {
            charsRead5 = reader.read(cbuf5, offset5, length5);
            if (charsRead5 != -1) {
                offset5 += charsRead5;
                length5 -= charsRead5;
            }
        }

        StringBuilder sb = new StringBuilder().append(cbuf1).append(cbuf2).append(cbuf3).append(cbuf4).append(cbuf5);

        String readString = sb.toString();
        k3po.finish();
        assertEquals(writeString, readString);
    }

    @Test
    @Specification({
        "echo.text.payload.length.125/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength125ReadInPartsOfDifferentLength() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Writer writer = connection.getWriter();
        Reader reader = connection.getReader();

        String writeString = new RandomString(125).nextString();
        writer.write(writeString.toCharArray());

        char[] cbuf1 = new char[50];
        char[] cbuf2 = new char[50];
        char[] cbuf3 = new char[50];

        int offset1 = 0;
        int offset2 = 0;
        int offset3 = 0;

        int length1 = cbuf1.length;
        int length2 = cbuf2.length;
        int length3 = cbuf3.length;

        int charsRead1 = 0;
        int charsRead2 = 0;
        int charsRead3 = 0;

        while ((charsRead1 != -1) && (length1 > 0)) {
            charsRead1 = reader.read(cbuf1, offset1, length1);
            if (charsRead1 != -1) {
                offset1 += charsRead1;
                length1 -= charsRead1;
            }
        }
        while ((charsRead2 != -1) && (length2 > 0)) {
            charsRead2 = reader.read(cbuf2, offset2, length2);
            if (charsRead2 != -1) {
                offset2 += charsRead2;
                length2 -= charsRead2;
            }
        }

        while ((charsRead3 != -1) && (length3 > 0)) {
            charsRead3 = reader.read(cbuf3, offset3, length3);
            if (charsRead3 != -1) {
                offset3 += charsRead3;
                length3 -= charsRead3;
            }
        }

        StringBuilder sb = new StringBuilder().append(cbuf1).append(cbuf2).append(cbuf3, 0, offset3);

        String readString = sb.toString();
        k3po.finish();
        assertEquals(writeString, readString);
    }

    @Test
    @Specification({
        "echo.text.payload.length.125/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength125UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = ((WsURLConnectionImpl) connection).getMessageReader();
        MessageWriter writer = ((WsURLConnectionImpl) connection).getMessageWriter();

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
                assertSame(BINARY, type);
                break;
            }
        }

        String readString = String.valueOf(cbuf);
        k3po.finish();
        assertEquals(writeString, readString);
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

        k3po.finish();

        assertEquals(writeString, readString);
    }

    @Test
    @Specification({
        "echo.text.payload.length.126/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength126UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = ((WsURLConnectionImpl) connection).getMessageReader();
        MessageWriter writer = ((WsURLConnectionImpl) connection).getMessageWriter();

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
                assertSame(BINARY, type);
                break;
            }
        }

        String readString = String.valueOf(cbuf);

        k3po.finish();

        assertEquals(writeString, readString);
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

        k3po.finish();

        assertEquals(writeString, readString);
    }

    @Test
    @Specification({
        "echo.text.payload.length.127/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength127UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = ((WsURLConnectionImpl) connection).getMessageReader();
        MessageWriter writer = ((WsURLConnectionImpl) connection).getMessageWriter();

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
                assertSame(BINARY, type);
                break;
            }
        }

        String readString = String.valueOf(cbuf);

        k3po.finish();

        assertEquals(writeString, readString);
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

        k3po.finish();

        assertEquals(writeString, readString);
    }

    @Test
    @Specification({
        "echo.text.payload.length.128/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength128UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = ((WsURLConnectionImpl) connection).getMessageReader();
        MessageWriter writer = ((WsURLConnectionImpl) connection).getMessageWriter();

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
                assertSame(BINARY, type);
                break;
            }
        }

        String readString = String.valueOf(cbuf);

        k3po.finish();

        assertEquals(writeString, readString);
    }

    @Test
    @Specification({
        "echo.text.payload.length.65535/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength65535() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        connection.setMaxPayloadLength(65536);

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

        k3po.finish();

        assertEquals(writeString, readString);
    }

    @Test
    @Specification({
        "echo.text.payload.length.65535/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength65535UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        connection.setMaxPayloadLength(65536);

        MessageReader reader = ((WsURLConnectionImpl) connection).getMessageReader();
        MessageWriter writer = ((WsURLConnectionImpl) connection).getMessageWriter();

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
                assertSame(BINARY, type);
                break;
            }
        }

        String readString = String.valueOf(cbuf);

        k3po.finish();

        assertEquals(writeString, readString);
    }

    @Test
    @Specification({
        "echo.text.payload.length.65536/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength65536() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        connection.setMaxPayloadLength(65536);

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

        k3po.finish();

        assertEquals(writeString, readString);
    }

    @Test
    @Specification({
        "echo.text.payload.length.65536/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength65536UsingMessageWriterAndMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        connection.setMaxPayloadLength(65536);

        MessageReader reader = ((WsURLConnectionImpl) connection).getMessageReader();
        MessageWriter writer = ((WsURLConnectionImpl) connection).getMessageWriter();

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
                assertSame(BINARY, type);
                break;
            }
        }

        String readString = String.valueOf(cbuf);

        k3po.finish();

        assertEquals(writeString, readString);
    }

    private static class RandomString {

        private static final char[] SYMBOLS;

        static {
            StringBuilder tmp = new StringBuilder();
            for (char ch = 32; ch <= 126; ++ch) {
                tmp.append(ch);
            }
            SYMBOLS = tmp.toString().toCharArray();
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
