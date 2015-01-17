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

package org.kaazing.netx.http;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.rules.RuleChain.outerRule;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;
import org.kaazing.netx.URLFactory;


// TODO: verify specification.http scripts instead
public class HttpIT {

    private final K3poRule k3po = new K3poRule();

    private final ResetAuthenticatorRule reset = new ResetAuthenticatorRule();

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(reset).around(timeout);

    @Test
    @Specification("response.with.status.code.200")
    public void shouldHandle200() throws Exception {
        URL url = URLFactory.createURL("http://localhost:8080/path?query");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("X-Header", "value");
        assertEquals(200, connection.getResponseCode());
        k3po.join();
    }

    @Test
    @Specification("response.with.status.code.401")
    public void shouldHandle401() throws Exception {
        URL url = URLFactory.createURL("http://localhost:8080/path?query");
//        URL url = new URL("http://localhost:8080/path?query");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("X-Header", "value");
        assertEquals(401, connection.getResponseCode());
        k3po.join();
    }

    @Test
    @Specification("response.with.status.code.101")
    public void shouldHandle101() throws Exception {
        URL url = URLFactory.createURL("http://localhost:8080/path?query");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Upgrade", "websocket");
        connection.setRequestProperty("Sec-WebSocket-Protocol", "13");
        connection.setDoOutput(true);
        Writer output = new OutputStreamWriter(connection.getOutputStream(), UTF_8);
        Reader input = new InputStreamReader(connection.getInputStream(), UTF_8);
        output.write("Hello, world");
        output.flush();
        char[] cbuf = new char[12];
        input.read(cbuf);
        output.close();

        assertEquals(101, connection.getResponseCode());
        assertEquals("Hello, world", new String(cbuf));

        k3po.join();
    }

    @Test
    @Specification("response.upgrade.failed.with.status.code.401.basic")
    public void shouldHandleUpgradeFailed401() throws Exception {
        // TODO: use mock Authenticator
        final AtomicInteger authenticationCalls = new AtomicInteger();
        Authenticator authenticator = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                authenticationCalls.incrementAndGet();
                return null;
            }
            
        };
        Authenticator.setDefault(authenticator);

        URL url = URLFactory.createURL("http://localhost:8080/path?query");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Upgrade", "websocket");
        connection.setRequestProperty("Sec-WebSocket-Protocol", "13");

        assertEquals(401, connection.getResponseCode());
        assertEquals(1, authenticationCalls.get());

        k3po.join();
    }

    @Test
    @Specification("response.upgrade.failed.with.status.code.401.application.basic")
    public void shouldHandleUpgradeFailed401ApplicationBasic() throws Exception {
        // TODO: use mock Authenticator
        final AtomicInteger authenticationCalls = new AtomicInteger();
        Authenticator authenticator = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                authenticationCalls.incrementAndGet();
                return super.getPasswordAuthentication();
            }
            
        };
        Authenticator.setDefault(authenticator);

        URL url = URLFactory.createURL("http://localhost:8080/path?query");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Upgrade", "websocket");
        connection.setRequestProperty("Sec-WebSocket-Protocol", "13");

        assertEquals(401, connection.getResponseCode());
        assertEquals(0, authenticationCalls.get());

        k3po.join();
    }

    @Test
    @Specification("response.upgrade.failed.with.status.code.302")
    public void shouldNotFollow302Redirect() throws Exception {
        URL url = URLFactory.createURL("http://localhost:8080/path?query");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Upgrade", "websocket");
        connection.setRequestProperty("Sec-WebSocket-Protocol", "13");
        connection.setInstanceFollowRedirects(false);

        assertEquals(302, connection.getResponseCode());
        assertEquals("http://localhost:8080/different/path", connection.getHeaderField("Location"));

        k3po.join();
    }
}
