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
import static org.kaazing.netx.ws.internal.io.MessageType.BINARY;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
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

/**
 * RFC-6455, section 7 "Closing the Connection"
 */
public class ClosingHandshakeIT {

    private final K3poRule k3po = new K3poRule().setScriptRoot("org/kaazing/specification/ws/closing");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "client.send.empty.close.frame/handshake.response.and.frame" })
    public void shouldCompleteCloseHandshakeWhenClientSendEmptyCloseFrame() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        connection.connect();
        connection.close();
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.close.frame.with.code.1000/handshake.response.and.frame" })
    public void shouldCompleteCloseHandshakeWhenClientSendCloseFrameWithCode1000() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        connection.connect();
        connection.close(1000);
        k3po.finish();
    }

    @Test
    @Specification({
        "client.send.close.frame.with.code.1000.and.reason/handshake.response.and.frame" })
    public void shouldCompleteCloseHandshakeWhenClientSendCloseFrameWithCode1000AndReason() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        String reason = new RandomString(20).nextString();
        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        connection.connect();
        connection.close(1000, reason);
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.empty.close.frame/handshake.response.and.frame" })
    public void shouldCompleteCloseHandshakeWhenServerSendEmptyCloseFrame() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream in = connection.getInputStream();
        in.read();
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.empty.close.frame/handshake.response.and.frame" })
    public void shouldCompleteCloseHandshakeWhenServerSendEmptyCloseFrameUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        reader.read();
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.empty.close.frame/handshake.response.and.frame" })
    public void shouldCompleteCloseHandshakeWhenServerSendEmptyCloseFrameUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = ((WsURLConnectionImpl) connection).getMessageReader();

        byte[] readBytes = new byte[0];
        MessageType type = null;
        int bytesRead = 0;

        if ((type = messageReader.next()) != MessageType.EOS) {
            switch (type) {
            case BINARY:
                bytesRead = messageReader.readFully(readBytes);
                assertEquals(0, bytesRead);
                break;
            default:
                assertSame(BINARY, type);
                break;
            }
        }
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.close.frame.with.code.1000/handshake.response.and.frame" })
    public void shouldCompleteCloseHandshakeWhenServerSendCloseFrameWithCode1000() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream in = connection.getInputStream();
        in.read();
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.close.frame.with.code.1000/handshake.response.and.frame" })
    public void shouldCompleteCloseHandshakeWhenServerSendCloseFrameWithCode1000UsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        reader.read();
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.close.frame.with.code.1000/handshake.response.and.frame" })
    public void shouldCompleteCloseHandshakeWhenServerSendCloseFrameWithCode1000UsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = ((WsURLConnectionImpl) connection).getMessageReader();

        byte[] readBytes = new byte[0];
        MessageType type = null;
        int bytesRead = 0;

        if ((type = messageReader.next()) != MessageType.EOS) {
            switch (type) {
            case BINARY:
                bytesRead = messageReader.readFully(readBytes);
                assertEquals(0, bytesRead);
                break;
            default:
                assertSame(BINARY, type);
                break;
            }
        }
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.close.frame.with.code.1000.and.reason/handshake.response.and.frame" })
    public void shouldCompleteCloseHandshakeWhenServerSendCloseFrameWithCode1000AndReason() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream in = connection.getInputStream();
        in.read();
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.close.frame.with.code.1000.and.reason/handshake.response.and.frame" })
    public void shouldCompleteCloseHandshakeWhenServerSendCloseFrameWithCode1000AndReasonUsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        reader.read();
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.close.frame.with.code.1000.and.reason/handshake.response.and.frame" })
    public void shouldCompleteCloseHandshakeWhenServerSendCloseFrameWithCode1000AndReasonUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = ((WsURLConnectionImpl) connection).getMessageReader();

        byte[] readBytes = new byte[0];
        MessageType type = null;
        int bytesRead = 0;

        if ((type = messageReader.next()) != MessageType.EOS) {
            switch (type) {
            case BINARY:
                bytesRead = messageReader.readFully(readBytes);
                assertEquals(0, bytesRead);
                break;
            default:
                assertSame(BINARY, type);
                break;
            }
        }
        k3po.finish();
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.close.frame.with.code.1000.and.invalid.utf8.reason/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithCode1000AndInvalidUTF8Reason() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream in = connection.getInputStream();

        try {
            in.read();
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.close.frame.with.code.1000.and.invalid.utf8.reason/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithCode1000AndInvalidUTF8ReasonUsingReader()
            throws Exception {
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
        "server.send.close.frame.with.code.1000.and.invalid.utf8.reason/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithCode1000AndInvalidUTF8ReasonUsingMessageReader()
            throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = ((WsURLConnectionImpl) connection).getMessageReader();

        byte[] readBytes = new byte[0];
        MessageType type = null;
        int bytesRead = 0;

        try {
            if ((type = messageReader.next()) != MessageType.EOS) {
                switch (type) {
                case BINARY:
                    bytesRead = messageReader.readFully(readBytes);
                    assertEquals(0, bytesRead);
                    break;
                default:
                    assertSame(BINARY, type);
                    break;
                }
            }
        }
        finally {
            k3po.finish();
        }
    }

    @Test
    @Specification({
        "server.send.close.frame.with.code.1001/handshake.response.and.frame" })
    public void shouldCompleteCloseHandshakeWhenServerSendCloseFrameWithCode1001() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream in = connection.getInputStream();
        in.read();
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.close.frame.with.code.1001/handshake.response.and.frame" })
    public void shouldCompleteCloseHandshakeWhenServerSendCloseFrameWithCode1001UsingReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        Reader reader = connection.getReader();
        reader.read();
        k3po.finish();
    }

    @Test
    @Specification({
        "server.send.close.frame.with.code.1001/handshake.response.and.frame" })
    public void shouldCompleteCloseHandshakeWhenServerSendCloseFrameWithCode1001UsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = ((WsURLConnectionImpl) connection).getMessageReader();

        byte[] readBytes = new byte[0];
        MessageType type = null;
        int bytesRead = 0;

        if ((type = messageReader.next()) != MessageType.EOS) {
            switch (type) {
            case BINARY:
                bytesRead = messageReader.readFully(readBytes);
                assertEquals(0, bytesRead);
                break;
            default:
                assertSame(BINARY, type);
                break;
            }
        }

        k3po.finish();
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.close.frame.with.code.1005/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithCode1005() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream in = connection.getInputStream();

        try {
            in.read();
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.close.frame.with.code.1005/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithCode1005UsingReader() throws Exception {
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
        "server.send.close.frame.with.code.1005/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithCode1005UsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = ((WsURLConnectionImpl) connection).getMessageReader();

        byte[] readBytes = new byte[0];
        MessageType type = null;
        int bytesRead = 0;

        try {
            if ((type = messageReader.next()) != MessageType.EOS) {
                switch (type) {
                case BINARY:
                    bytesRead = messageReader.readFully(readBytes);
                    assertEquals(0, bytesRead);
                    break;
                default:
                    assertSame(BINARY, type);
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
        "server.send.close.frame.with.code.1006/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithCode1006() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream in = connection.getInputStream();

        try {
            in.read();
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.close.frame.with.code.1006/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithCode1006UsingReader() throws Exception {
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
        "server.send.close.frame.with.code.1006/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithCode1006UsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = ((WsURLConnectionImpl) connection).getMessageReader();

        byte[] readBytes = new byte[0];
        MessageType type = null;
        int bytesRead = 0;

        try {
            if ((type = messageReader.next()) != MessageType.EOS) {
                switch (type) {
                case BINARY:
                    bytesRead = messageReader.readFully(readBytes);
                    assertEquals(0, bytesRead);
                    break;
                default:
                    assertSame(BINARY, type);
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
        "server.send.close.frame.with.code.1015/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithCode1015() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        InputStream in = connection.getInputStream();

        try {
            in.read();
        }
        finally {
            k3po.finish();
        }
    }

    @Test(expected = IOException.class)
    @Specification({
        "server.send.close.frame.with.code.1015/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithCode1015UsingReader() throws Exception {
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
        "server.send.close.frame.with.code.1015/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithCode1015UsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = ((WsURLConnectionImpl) connection).getMessageReader();

        byte[] readBytes = new byte[0];
        MessageType type = null;
        int bytesRead = 0;

        try {
            if ((type = messageReader.next()) != MessageType.EOS) {
                switch (type) {
                case BINARY:
                    bytesRead = messageReader.readFully(readBytes);
                    assertEquals(0, bytesRead);
                    break;
                default:
                    assertSame(BINARY, type);
                    break;
                }
            }
        }
        finally {
            k3po.finish();
        }
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
