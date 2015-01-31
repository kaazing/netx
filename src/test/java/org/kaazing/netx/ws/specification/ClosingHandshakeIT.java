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
import static org.junit.rules.RuleChain.outerRule;

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
        k3po.join();
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
        k3po.join();
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
        k3po.join();
    }

    @Test
    @Specification({
        "server.send.empty.close.frame/handshake.request.and.frame",
        "server.send.empty.close.frame/handshake.response.and.frame" })
    public void shouldCompleteCloseHandshakeWhenServerSendEmptyCloseFrame() throws Exception {
//        URLConnectionHelper helper = URLConnectionHelper.newInstance();
//        URI location = URI.create("ws://localhost:8080/path");
//
//        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
//        InputStream in = connection.getInputStream();
//        byte[] bytes = new byte[4];
//        in.read(bytes);
        k3po.join();
    }

    @Test
    @Specification({
        "server.send.close.frame.with.code.1000/handshake.request.and.frame",
        "server.send.close.frame.with.code.1000/handshake.response.and.frame" })
    public void shouldCompleteCloseHandshakeWhenServerSendCloseFrameWithCode1000() throws Exception {
//        URLConnectionHelper helper = URLConnectionHelper.newInstance();
//        URI location = URI.create("ws://localhost:8080/path");
//
//        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
//        InputStream in = connection.getInputStream();
//        byte[] bytes = new byte[4];
//        in.read(bytes);
        k3po.join();
    }

    @Test
    @Specification({
        "server.send.close.frame.with.code.1000.and.reason/handshake.request.and.frame",
        "server.send.close.frame.with.code.1000.and.reason/handshake.response.and.frame" })
    public void shouldCompleteCloseHandshakeWhenServerSendCloseFrameWithCode1000AndReason() throws Exception {
        k3po.join();
    }

    @Test
    @Specification({
        "server.send.close.frame.with.code.1000.and.invalid.utf8.reason/handshake.request.and.frame",
        "server.send.close.frame.with.code.1000.and.invalid.utf8.reason/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithCode1000AndInvalidUTF8Reason() throws Exception {
        k3po.join();
    }

    @Test
    @Specification({
        "server.send.close.frame.with.code.1001/handshake.request.and.frame",
        "server.send.close.frame.with.code.1001/handshake.response.and.frame" })
    public void shouldCompleteCloseHandshakeWhenServerSendCloseFrameWithCode1001() throws Exception {
        k3po.join();
    }

    @Test
    @Specification({
        "server.send.close.frame.with.code.1005/handshake.request.and.frame",
        "server.send.close.frame.with.code.1005/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithCode1005() throws Exception {
        k3po.join();
    }

    @Test
    @Specification({
        "server.send.close.frame.with.code.1006/handshake.request.and.frame",
        "server.send.close.frame.with.code.1006/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithCode1006() throws Exception {
        k3po.join();
    }

    @Test
    @Specification({
        "server.send.close.frame.with.code.1015/handshake.request.and.frame",
        "server.send.close.frame.with.code.1015/handshake.response.and.frame" })
    public void shouldFailWebSocketConnectionWhenServerSendCloseFrameWithCode1015() throws Exception {
        k3po.join();
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
