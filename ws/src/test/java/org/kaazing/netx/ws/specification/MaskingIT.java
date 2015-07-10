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
import static org.kaazing.netx.ws.MessageType.BINARY;
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
 * RFC-6455
 * section 5.1 "Overview"
 * section 5.3 "Client-to-Server Masking"
 */
public class MaskingIT {

    private final K3poRule k3po = new K3poRule().setScriptRoot("org/kaazing/specification/ws/masking");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test(expected = IOException.class)
    @Specification({
        "server.send.masked.text/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendsMaskWithTextFrame() throws Exception {
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
        "server.send.masked.text/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendsMaskWithTextFrameUsingMessageReader() throws Exception {
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
        "server.send.masked.binary/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendsMaskWithBinaryFrame() throws Exception {
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
        "server.send.masked.binary/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendsMaskWithBinaryFrameUsingMessageReader() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        MessageReader messageReader = connection.getMessageReader();

        byte[] readBytes = new byte[0];
        MessageType type = null;

        try {
            while ((type = messageReader.next()) != MessageType.EOS) {
                switch (type) {
                case BINARY:
                    int bytesRead = messageReader.readFully(readBytes);
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
}
