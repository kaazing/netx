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

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;
import org.kaazing.netx.URLConnectionHelper;
import org.kaazing.netx.http.HttpURLConnection;
import org.kaazing.netx.ws.WsURLConnection;

/**
 * RFC-6455, section 4.1 "Client-Side Requirements"
 * RFC-6455, section 4.2 "Server-Side Requirements"
 */
public class OpeningHandshakeIT {

    private final K3poRule k3po = new K3poRule().setScriptRoot("org/kaazing/specification/ws/opening");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    // TODO:
    // proxy => HTTP CONNECT w/ optional authorization, auto-configuration via ws://, wss://
    // TLS (not SSL) w/ SNI for wss://

    @Test
    @Specification("connection.established/handshake.response")
    public void shouldEstablishConnection() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path?query");
        URL locationURL = helper.toURL(location);
        WsURLConnection conn = (WsURLConnection) locationURL.openConnection();

        conn.connect();
        k3po.join();
    }

    @Test
    @Specification("request.header.cookie/handshake.response")
    public void shouldEstablishConnectionWithCookieRequestHeader() throws Exception {
        CookieManager handler = new CookieManager();
        CookieHandler.setDefault(handler);

        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI preflight = URI.create("http://localhost:8080/preflight");
        HttpURLConnection connection = (HttpURLConnection) helper.toURL(preflight).openConnection();
        connection.setRequestMethod("GET");
        connection.connect();
        connection.getInputStream().close();

        URI location = URI.create("ws://localhost:8080/path?query");
        URL locationURL = helper.toURL(location);
        WsURLConnection conn = (WsURLConnection) locationURL.openConnection();

        conn.connect();
        k3po.join();
    }

    @Test
    @Specification("request.headers.random.case/handshake.response")
    public void shouldEstablishConnectionWithRandomCaseRequestHeaders() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path?query");
        URL locationURL = helper.toURL(location);
        WsURLConnection conn = (WsURLConnection) locationURL.openConnection();

        conn.connect();
        k3po.join();
    }

    @Test
    @Specification("response.headers.random.case/handshake.response")
    public void shouldEstablishConnectionWithRandomCaseResponseHeaders() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path?query");
        URL locationURL = helper.toURL(location);
        WsURLConnection conn = (WsURLConnection) locationURL.openConnection();

        conn.connect();
        k3po.join();
    }

    @Test
    @Specification("request.header.sec.websocket.protocol/handshake.response")
    public void shouldEstablishConnectionWithRequestHeaderSecWebSocketProtocol() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path?query");
        URL locationURL = helper.toURL(location);
        WsURLConnection conn = (WsURLConnection) locationURL.openConnection();

        conn.setEnabledProtocols(Arrays.asList("primary", "secondary"));
        conn.connect();
        k3po.join();
    }

    @Test
    @Ignore
    @Specification("request.header.sec.websocket.extensions/handshake.response")
    public void shouldEstablishConnectionWithRequestHeaderSecWebSocketExtensions() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path?query");
        URL locationURL = helper.toURL(location);
        WsURLConnection conn = (WsURLConnection) locationURL.openConnection();

//      conn.setEnabledExtensions(unmodifiableList(asList("primary", "secondary")));

        conn.connect();
        k3po.join();
    }

    @Test
    @Specification("multiple.connections.established/handshake.responses")
    public void shouldEstablishMultipleConnections() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path?query");
        URL locationURL = helper.toURL(location);

        WsURLConnection conn1 = (WsURLConnection) locationURL.openConnection();
        conn1.connect();

        WsURLConnection conn2 = (WsURLConnection) locationURL.openConnection();
        conn2.connect();

        k3po.join();
    }
}
