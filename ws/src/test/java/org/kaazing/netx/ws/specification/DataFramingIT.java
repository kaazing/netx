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
import static org.junit.Assert.assertSame;
import static org.junit.rules.RuleChain.outerRule;
import static org.kaazing.netx.ws.MessageType.TEXT;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
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
import org.kaazing.netx.ws.WsURLConnection;

/**
 * RFC-6455, section 5.6 "Data Frames"
 */
public class DataFramingIT {

    private final K3poRule k3po = new K3poRule().setScriptRoot("org/kaazing/specification/ws/data");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    // TODO: invalid UTF-8 in text frame (opcode 0x01) RFC-6455, section 8.1

    @Test(expected = IOException.class)
    @Specification({
        "server.send.opcode.0x03/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode3Frame() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();

        try {
            input.read();
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.opcode.0x03/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode3FrameUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();

        try {
            reader.read();
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.opcode.0x03/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode3FrameUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = connection.getMessageReader();

        char[] cbuf = new char[0];
        MessageType type = null;

        try {
            while ((type = messageReader.next()) != MessageType.EOS) {
                switch (type) {
                case TEXT:
                    int charsRead = messageReader.readFully(cbuf);
                    assertEquals(0, charsRead);
                    break;
                default:
                    assertSame(TEXT, type);
                    break;
                }
            }
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.opcode.0x04/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode4Frame() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();

        try {
            input.read();
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.opcode.0x04/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode4FrameUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();

        try {
            reader.read();
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.opcode.0x04/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode4FrameUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = connection.getMessageReader();

        char[] cbuf = new char[0];
        MessageType type = null;

        try {
            while ((type = messageReader.next()) != MessageType.EOS) {
                switch (type) {
                case TEXT:
                    int charsRead = messageReader.readFully(cbuf);
                    assertEquals(0, charsRead);
                    break;
                default:
                    assertSame(TEXT, type);
                    break;
                }
            }
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.opcode.0x05/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode5Frame() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();

        try {
            input.read();
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.opcode.0x05/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode5FrameUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();

        try {
            reader.read();
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.opcode.0x05/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode5FrameUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = connection.getMessageReader();

        char[] cbuf = new char[0];
        MessageType type = null;

        try {
            while ((type = messageReader.next()) != MessageType.EOS) {
                switch (type) {
                case TEXT:
                    int charsRead = messageReader.readFully(cbuf);
                    assertEquals(0, charsRead);
                    break;
                default:
                    assertSame(TEXT, type);
                    break;
                }
            }
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.opcode.0x06/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode6Frame() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();

        try {
            input.read();
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.opcode.0x06/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode6FrameUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();

        try {
            reader.read();
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.opcode.0x06/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode6FrameUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = connection.getMessageReader();

        char[] cbuf = new char[0];
        MessageType type = null;

        try {
            while ((type = messageReader.next()) != MessageType.EOS) {
                switch (type) {
                case TEXT:
                    int charsRead = messageReader.readFully(cbuf);
                    assertEquals(0, charsRead);
                    break;
                default:
                    assertSame(TEXT, type);
                    break;
                }
            }
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.opcode.0x07/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode7Frame() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream input = connection.getInputStream();

        try {
            input.read();
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.opcode.0x07/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode7FrameUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();

        try {
            reader.read();
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.opcode.0x07/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendOpcode7FrameUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = connection.getMessageReader();

        char[] cbuf = new char[0];
        MessageType type = null;

        try {
            while ((type = messageReader.next()) != MessageType.EOS) {
                switch (type) {
                case TEXT:
                    int charsRead = messageReader.readFully(cbuf);
                    assertEquals(0, charsRead);
                    break;
                default:
                    assertSame(TEXT, type);
                    break;
                }
            }
        }
        finally {
            k3po.finish();
        }
    }
}
