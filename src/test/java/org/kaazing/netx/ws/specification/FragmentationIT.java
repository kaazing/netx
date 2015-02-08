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
import static org.junit.rules.RuleChain.outerRule;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
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
import org.kaazing.netx.ws.MessageWriter;
import org.kaazing.netx.ws.WsURLConnection;

/**
 * RFC-6455, section 5.4 "Fragmentation"
 */
public class FragmentationIT {
    private final Random random = new Random();

    private final K3poRule k3po = new K3poRule().setScriptRoot("org/kaazing/specification/ws/fragmentation");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "client.echo.text.payload.length.125.not.fragmented/handshake.response.and.frame" })
    public void shouldEchoClientSendTextFrameWithPayloadNotFragmented() throws Exception {
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
    }

    @Test
    @Specification({
        "client.echo.binary.payload.length.125.not.fragmented/handshake.response.and.frame" })
    public void shouldEchoClientSendBinaryFrameWithPayloadNotFragmented() throws Exception {
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
    }


    @Test
    @Specification({
        "server.echo.binary.payload.length.0.fragmented/handshake.response.and.frames" })
    public void shouldEchoServerSendBinaryFrameWithEmptyPayloadFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageWriter writer = connection.getMessageWriter();
        MessageReader reader = connection.getMessageReader();

        byte[] readBytesA = new byte[0];
        byte[] readBytesB = new byte[0];

        reader.read(readBytesA);
        writer.write(readBytesA);
        reader.read(readBytesB);

        k3po.join();
        assertArrayEquals(readBytesA, readBytesB);
    }

    @Test
    @Specification({
        "server.echo.binary.payload.length.0.fragmented.with.injected.ping.pong/handshake.response.and.frames" })
    public void shouldEchoServerSendBinaryFrameWithEmptyPayloadFragmentedAndInjectedPingPong() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageWriter writer = connection.getMessageWriter();
        MessageReader reader = connection.getMessageReader();

        byte[] readBytesA = new byte[0];
        byte[] readBytesB = new byte[0];

        reader.read(readBytesA);
        writer.write(readBytesA);
        reader.read(readBytesB);

        k3po.join();
        assertArrayEquals(readBytesA, readBytesB);
    }

    @Test
    @Specification({
        "server.echo.binary.payload.length.125.fragmented/handshake.response.and.frames" })
    public void shouldEchoServerSendBinaryFrameWithPayloadFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageWriter writer = connection.getMessageWriter();
        MessageReader reader = connection.getMessageReader();

        byte[] readBytesA = new byte[125];
        byte[] readBytesB = new byte[125];

        reader.read(readBytesA);
        writer.write(readBytesA);
        reader.read(readBytesB);

        k3po.join();
        assertArrayEquals(readBytesA, readBytesB);
    }

    @Test
    @Specification({
        "server.echo.binary.payload.length.125.fragmented.with.injected.ping.pong/handshake.response.and.frames" })
    public void shouldEchoServerSendBinaryFrameWithPayloadFragmentedAndInjectedPingPong() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageWriter writer = connection.getMessageWriter();
        MessageReader reader = connection.getMessageReader();

        byte[] readBytesA = new byte[125];
        byte[] readBytesB = new byte[125];

        reader.read(readBytesA);
        writer.write(readBytesA);
        reader.read(readBytesB);

        k3po.join();
        assertArrayEquals(readBytesA, readBytesB);
    }

    @Test
    @Specification({
        "server.echo.binary.payload.length.125.fragmented.with.some.empty.fragments/handshake.response.and.frames" })
    public void shouldEchoServerSendBinaryFrameWithPayloadFragmentedWithSomeEmptyFragments() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageWriter writer = connection.getMessageWriter();
        MessageReader reader = connection.getMessageReader();

        byte[] readBytesA = new byte[125];
        byte[] readBytesB = new byte[125];

        reader.read(readBytesA);
        writer.write(readBytesA);
        reader.read(readBytesB);

        k3po.join();
        assertArrayEquals(readBytesA, readBytesB);
    }

    @Test
    @Specification({
        "server.echo.binary.payload.length.125.not.fragmented/handshake.response.and.frame" })
    public void shouldEchoServerSendBinaryFrameWithPayloadNotFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageWriter writer = connection.getMessageWriter();
        MessageReader reader = connection.getMessageReader();

        byte[] readBytesA = new byte[125];
        byte[] readBytesB = new byte[125];

        reader.read(readBytesA);
        writer.write(readBytesA);
        reader.read(readBytesB);

        k3po.join();
        assertArrayEquals(readBytesA, readBytesB);
    }

    @Test
    @Specification({
        "server.echo.text.payload.length.0.fragmented/handshake.response.and.frames" })
    public void shouldEchoServerSendTextFrameWithEmptyPayloadFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageWriter writer = connection.getMessageWriter();
        MessageReader reader = connection.getMessageReader();

        char[] readCharsA = new char[0];
        char[] readCharsB = new char[0];

        reader.read(readCharsA);
        writer.write(readCharsA);
        reader.read(readCharsB);

        k3po.join();
        assertArrayEquals(readCharsA, readCharsB);
    }

    @Test
    @Specification({
        "server.echo.text.payload.length.0.fragmented.with.injected.ping.pong/handshake.response.and.frames" })
    public void shouldEchoServerSendTextFrameWithEmptyPayloadFragmentedAndInjectedPingPong() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageWriter writer = connection.getMessageWriter();
        MessageReader reader = connection.getMessageReader();

        char[] readCharsA = new char[0];
        char[] readCharsB = new char[0];

        reader.read(readCharsA);
        writer.write(readCharsA);
        reader.read(readCharsB);

        k3po.join();
        assertArrayEquals(readCharsA, readCharsB);
    }

    @Test
    @Ignore
    @Specification({
        "server.echo.text.payload.length.125.fragmented/handshake.response.and.frames" })
    public void shouldEchoServerSendTextFrameWithPayloadFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageWriter writer = connection.getMessageWriter();
        MessageReader reader = connection.getMessageReader();

        char[] readCharsA = new char[125];
        char[] readCharsB = new char[125];

        int n = reader.read(readCharsA);
        writer.write(readCharsA, 0 , n);
        reader.read(readCharsB, 0, n);

        k3po.join();
        assertArrayEquals(readCharsA, readCharsB);
    }

    @Test
    @Ignore
    @Specification({
        "server.echo.text.payload.length.125.fragmented.but.not.utf8.aligned/handshake.response.and.frames" })
    public void shouldEchoServerSendTextFrameWithPayloadFragmentedEvenWhenNotUTF8Aligned() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageWriter writer = connection.getMessageWriter();
        MessageReader reader = connection.getMessageReader();

        char[] readCharsA = new char[125];
        char[] readCharsB = new char[125];

        int n = reader.read(readCharsA);
        writer.write(readCharsA, 0 , n);
        reader.read(readCharsB, 0, n);

        k3po.join();
        assertArrayEquals(readCharsA, readCharsB);
    }

    @Test
    @Ignore
    @Specification({
        "server.echo.text.payload.length.125.fragmented.with.injected.ping.pong/handshake.response.and.frames" })
    public void shouldEchoServerSendTextFrameWithPayloadFragmentedAndInjectedPingPong() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageWriter writer = connection.getMessageWriter();
        MessageReader reader = connection.getMessageReader();

        char[] readCharsA = new char[125];
        char[] readCharsB = new char[125];

        reader.read(readCharsA);
        writer.write(readCharsA);
        reader.read(readCharsB);

        k3po.join();
        assertArrayEquals(readCharsA, readCharsB);
    }

    @Test
    @Ignore
    @Specification({
        "server.echo.text.payload.length.125.fragmented.with.some.empty.fragments/handshake.response.and.frames" })
    public void shouldEchoServerSendTextFrameWithPayloadFragmentedWithSomeEmptyFragments() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageWriter writer = connection.getMessageWriter();
        MessageReader reader = connection.getMessageReader();

        char[] readCharsA = new char[125];
        char[] readCharsB = new char[125];

        reader.read(readCharsA);
        writer.write(readCharsA);
        reader.read(readCharsB);

        k3po.join();
        assertArrayEquals(readCharsA, readCharsB);
    }

    @Test
    @Ignore
    @Specification({
        "server.echo.text.payload.length.125.not.fragmented/handshake.response.and.frame" })
    public void shouldEchoServerSendTextFrameWithPayloadNotFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageWriter writer = connection.getMessageWriter();
        MessageReader reader = connection.getMessageReader();

        char[] readCharsA = new char[125];
        char[] readCharsB = new char[125];

        reader.read(readCharsA);
        writer.write(readCharsA);
        reader.read(readCharsB);

        k3po.join();
        assertArrayEquals(readCharsA, readCharsB);
    }

    @Test
    @Specification({
        "server.send.binary.payload.length.125.fragmented.but.not.continued/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendBinaryFrameWithPayloadFragmentedButNotContinued() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();
        byte[] readBytes = new byte[125];
        AtomicInteger expectedException = new AtomicInteger();

        try {
            input.read(readBytes);
        }
        catch (IOException ex) {
            expectedException.incrementAndGet();
        }
        k3po.join();
        assertEquals(1, expectedException.get());
    }

    @Test
    @Specification({
        "server.send.binary.payload.length.125.fragmented.but.not.continued/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendBinaryFrameWithPayloadFragmentedButNotContinuedUsingReader()
            throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        char[] readChars = new char[125];
        AtomicInteger expectedException = new AtomicInteger();

        try {
            reader.read(readChars);
        }
        catch (IOException ex) {
            expectedException.incrementAndGet();
        }
        k3po.join();
        assertEquals(1, expectedException.get());
    }

    @Test
    @Specification({
        "server.send.binary.payload.length.125.fragmented.but.not.continued/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendBinaryFrameWithPayloadFragmentedButNotContinuedUsingMessageReader()
            throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = connection.getMessageReader();
        byte[] readBytes = new byte[125];
        AtomicInteger expectedException = new AtomicInteger();

        try {
            reader.read(readBytes);
        }
        catch (IOException ex) {
            expectedException.incrementAndGet();
        }
        k3po.join();
        assertEquals(1, expectedException.get());
    }

    @Test
    @Specification({
        "server.send.close.payload.length.2.fragmented/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithPayloadFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();
        byte[] readBytes = new byte[125];
        AtomicInteger expectedException = new AtomicInteger();

        try {
            input.read(readBytes);
        }
        catch (IOException ex) {
            expectedException.incrementAndGet();
        }
        k3po.join();
        assertEquals(1, expectedException.get());
    }

    @Test
    @Specification({
        "server.send.close.payload.length.2.fragmented/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithPayloadFragmentedUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        char[] readChars = new char[125];
        AtomicInteger expectedException = new AtomicInteger();

        try {
            reader.read(readChars);
        }
        catch (IOException ex) {
            expectedException.incrementAndGet();
        }
        k3po.join();
        assertEquals(1, expectedException.get());
    }

    @Test
    @Specification({
    "server.send.close.payload.length.2.fragmented/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithPayloadFragmentedUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = connection.getMessageReader();
        byte[] readBytes = new byte[125];
        AtomicInteger expectedException = new AtomicInteger();

        try {
            reader.read(readBytes);
        }
        catch (IOException ex) {
            expectedException.incrementAndGet();
        }
        k3po.join();
        assertEquals(1, expectedException.get());
    }

    @Test
    @Specification({
        "server.send.continuation.payload.length.125.fragmented/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendContinuationFrameWithPayloadFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();
        byte[] readBytes = new byte[125];
        AtomicInteger expectedException = new AtomicInteger();

        try {
            input.read(readBytes);
        }
        catch (IOException ex) {
            expectedException.incrementAndGet();
        }
        k3po.join();
        assertEquals(1, expectedException.get());
    }

    @Test
    @Ignore
    @Specification({
        "server.send.continuation.payload.length.125.fragmented/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendContinuationFrameWithPayloadFragmentedUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        char[] readChars = new char[125];
        AtomicInteger expectedException = new AtomicInteger();

        try {
            reader.read(readChars);
        }
        catch (IOException ex) {
            expectedException.incrementAndGet();
        }
        k3po.join();
        assertEquals(1, expectedException.get());
    }

    @Test
    @Specification({
        "server.send.continuation.payload.length.125.fragmented/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendContinuationFrameWithPayloadFragmentedUsingMessageReader()
            throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = connection.getMessageReader();
        byte[] readBytes = new byte[125];
        AtomicInteger expectedException = new AtomicInteger();

        try {
            reader.read(readBytes);
        }
        catch (IOException ex) {
            expectedException.incrementAndGet();
        }
        k3po.join();
        assertEquals(1, expectedException.get());
    }

    @Test
    @Specification({
        "server.send.continuation.payload.length.125.not.fragmented/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendContinuationFrameWithPayloadNotFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();
        byte[] readBytes = new byte[125];
        AtomicInteger expectedException = new AtomicInteger();

        try {
            input.read(readBytes);
        }
        catch (IOException ex) {
            expectedException.incrementAndGet();
        }
        k3po.join();
        assertEquals(1, expectedException.get());
    }

    @Test
    @Ignore
    @Specification({
        "server.send.continuation.payload.length.125.not.fragmented/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendContinuationFrameWithPayloadNotFragmentedUsingReader()
            throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        char[] readChars = new char[125];
        AtomicInteger expectedException = new AtomicInteger();

        try {
            reader.read(readChars);
        }
        catch (IOException ex) {
            expectedException.incrementAndGet();
        }
        k3po.join();
        assertEquals(1, expectedException.get());
    }

    @Test
    @Specification({
        "server.send.continuation.payload.length.125.not.fragmented/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendContinuationFrameWithPayloadNotFragmentedUsingMessageReader()
            throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = connection.getMessageReader();
        byte[] readBytes = new byte[125];
        AtomicInteger expectedException = new AtomicInteger();

        try {
            reader.read(readBytes);
        }
        catch (IOException ex) {
            expectedException.incrementAndGet();
        }
        k3po.join();
        assertEquals(1, expectedException.get());
    }

    @Test
    @Specification({
        "server.send.ping.payload.length.0.fragmented/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendPingFrameWithPayloadFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();
        byte[] readBytes = new byte[125];
        AtomicInteger expectedException = new AtomicInteger();

        try {
            input.read(readBytes);
        }
        catch (IOException ex) {
            expectedException.incrementAndGet();
        }
        k3po.join();
        assertEquals(1, expectedException.get());
    }

    @Test
    @Specification({
        "server.send.ping.payload.length.0.fragmented/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendPingFrameWithPayloadFragmentedUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        char[] readChars = new char[125];
        AtomicInteger expectedException = new AtomicInteger();

        try {
            reader.read(readChars);
        }
        catch (IOException ex) {
            expectedException.incrementAndGet();
        }
        k3po.join();
        assertEquals(1, expectedException.get());
    }

    @Test
    @Specification({
        "server.send.ping.payload.length.0.fragmented/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendPingFrameWithPayloadFragmentedUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = connection.getMessageReader();
        byte[] readBytes = new byte[125];
        AtomicInteger expectedException = new AtomicInteger();

        try {
            reader.read(readBytes);
        }
        catch (IOException ex) {
            expectedException.incrementAndGet();
        }
        k3po.join();
        assertEquals(1, expectedException.get());
    }

    @Test
    @Specification({
        "server.send.pong.payload.length.0.fragmented/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendPongFrameWithPayloadFragmented() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();
        byte[] readBytes = new byte[125];
        AtomicInteger expectedException = new AtomicInteger();

        try {
            input.read(readBytes);
        }
        catch (IOException ex) {
            expectedException.incrementAndGet();
        }
        k3po.join();
        assertEquals(1, expectedException.get());
    }

    @Test
    @Specification({
        "server.send.pong.payload.length.0.fragmented/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendPongFrameWithPayloadFragmentedUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        char[] readChars = new char[125];
        AtomicInteger expectedException = new AtomicInteger();

        try {
            reader.read(readChars);
        }
        catch (IOException ex) {
            expectedException.incrementAndGet();
        }
        k3po.join();
        assertEquals(1, expectedException.get());
    }

    @Test
    @Specification({
        "server.send.pong.payload.length.0.fragmented/handshake.response.and.frames" })
    public void shouldFailWebSocketConnectionWhenServerSendPongFrameWithPayloadFragmentedUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader reader = connection.getMessageReader();
        byte[] readBytes = new byte[125];
        AtomicInteger expectedException = new AtomicInteger();

        try {
            reader.read(readBytes);
        }
        catch (IOException ex) {
            expectedException.incrementAndGet();
        }
        k3po.join();
        assertEquals(1, expectedException.get());
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
