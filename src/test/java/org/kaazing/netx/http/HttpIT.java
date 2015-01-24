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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.rules.RuleChain.outerRule;
import static org.kaazing.netx.http.HttpRedirectPolicy.NEVER;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;
import org.kaazing.netx.URLConnectionHelper;
import org.kaazing.netx.http.auth.ApplicationBasicChallengeHandler;
import org.kaazing.netx.http.auth.LoginHandler;


// TODO: verify specification.http scripts instead
public class HttpIT {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final K3poRule k3po = new K3poRule();

    private final ResetAuthenticatorRule reset = new ResetAuthenticatorRule();

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    private final URLConnectionHelper helper = URLConnectionHelper.newInstance();

    @Rule
    public final TestRule chain = outerRule(k3po).around(reset).around(timeout);

    @Test
    @Specification("response.with.status.code.200")
    public void shouldHandle200() throws Exception {
        URI uri = URI.create("http://localhost:8080/path?query");
        HttpURLConnection connection = (HttpURLConnection) helper.openConnection(uri);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("X-Header", "value");
        assertEquals(200, connection.getResponseCode());
        k3po.join();
    }

    @Test
    @Specification("response.with.status.code.302")
    public void shouldNotFollow302WithPolicyNever() throws Exception {
        URI uri = URI.create("http://localhost:8080/path?query");
        HttpURLConnection connection = (HttpURLConnection) helper.openConnection(uri);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("X-Header", "value");
        connection.setRedirectPolicy(NEVER);
        assertEquals(302, connection.getResponseCode());
        k3po.join();
    }

    @Test
    @Specification("response.with.status.code.401")
    public void shouldHandle401() throws Exception {
        URI uri = URI.create("http://localhost:8080/path?query");
        HttpURLConnection connection = (HttpURLConnection) helper.openConnection(uri);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("X-Header", "value");
        assertEquals(401, connection.getResponseCode());
        k3po.join();
    }

    @Test
    @Specification("response.with.status.code.101")
    public void shouldHandle101() throws Exception {
        URI uri = URI.create("http://localhost:8080/path?query");
        HttpURLConnection connection = (HttpURLConnection) helper.openConnection(uri);
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

        URI uri = URI.create("http://localhost:8080/path?query");
        HttpURLConnection connection = (HttpURLConnection) helper.openConnection(uri);
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

        URI uri = URI.create("http://localhost:8080/path?query");
        HttpURLConnection connection = (HttpURLConnection) helper.openConnection(uri);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Upgrade", "websocket");
        connection.setRequestProperty("Sec-WebSocket-Protocol", "13");

        assertEquals(401, connection.getResponseCode());
        assertEquals(0, authenticationCalls.get());

        k3po.join();
    }

    @Test
    @Specification("response.upgrade.with.status.code.401.then.101.application.basic")
    public void shouldHandleUpgrade401Then101ApplicationBasic() throws Exception {
        final AtomicInteger authenticationCalls = new AtomicInteger();
        ApplicationBasicChallengeHandler challengeHandler = ApplicationBasicChallengeHandler.create();
        LoginHandler loginHandler = new LoginHandler() {
            @Override
            public PasswordAuthentication getCredentials() {
                authenticationCalls.incrementAndGet();
                return new PasswordAuthentication("joe2", "welcome2".toCharArray());
            }
        };
        challengeHandler.setLoginHandler(loginHandler);

        URI uri = URI.create("http://localhost:8080/path?query");
        HttpURLConnection connection = (HttpURLConnection) helper.openConnection(uri);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Upgrade", "websocket");
        connection.setRequestProperty("Sec-WebSocket-Protocol", "13");
        connection.setChallengeHandler(challengeHandler);

        assertEquals(101, connection.getResponseCode());
        assertEquals(1, authenticationCalls.get());

        k3po.join();
    }

    @Test
    @Specification("response.upgrade.failed.with.status.code.302")
    public void shouldNotFollow302Redirect() throws Exception {
        URI uri = URI.create("http://localhost:8080/path?query");
        HttpURLConnection connection = (HttpURLConnection) helper.openConnection(uri);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Upgrade", "websocket");
        connection.setRequestProperty("Sec-WebSocket-Protocol", "13");
        connection.setInstanceFollowRedirects(false);

        assertEquals(302, connection.getResponseCode());
        assertEquals("http://localhost:8080/different/path", connection.getHeaderField("Location"));

        k3po.join();
    }

    @Test
    @Specification("response.with.status.code.302.then.200")
    public void shouldFollow302RedirectThenHandle200OK() throws Exception {
        URI uri = URI.create("http://localhost:8080/path?query");
        HttpURLConnection connection = (HttpURLConnection) helper.openConnection(uri);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("X-Header", "value");

        assertEquals(200, connection.getResponseCode());

        k3po.join();
    }

    @Test
    @Specification("response.upgrade.failed.with.status.code.302.then.200")
    public void shouldFollow302RedirectThenHandleUpgradeFailed200OK() throws Exception {
        URI uri = URI.create("http://localhost:8080/path?query");
        HttpURLConnection connection = (HttpURLConnection) helper.openConnection(uri);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Upgrade", "websocket");
        connection.setRequestProperty("Sec-WebSocket-Protocol", "13");

        assertEquals(200, connection.getResponseCode());

        k3po.join();
    }

    @Test
    @Specification("response.upgrade.with.status.code.302.then.101")
    public void shouldFollow302RedirectThenHandleUpgrade101OK() throws Exception {
        URI uri = URI.create("http://localhost:8080/path?query");
        HttpURLConnection connection = (HttpURLConnection) helper.openConnection(uri);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Upgrade", "websocket");
        connection.setRequestProperty("Sec-WebSocket-Protocol", "13");

        assertEquals(101, connection.getResponseCode());

        k3po.join();
    }

    @Test
    @Specification("response.with.status.code.401.then.200.application.basic")
    public void shouldHandle401ApplicationBasicThen200OK() throws Exception {
        final AtomicInteger authenticationCalls = new AtomicInteger();
        ApplicationBasicChallengeHandler challengeHandler = ApplicationBasicChallengeHandler.create();
        LoginHandler loginHandler = new LoginHandler() {
            @Override
            public PasswordAuthentication getCredentials() {
                authenticationCalls.incrementAndGet();
                return new PasswordAuthentication("joe2", "welcome2".toCharArray());
            }
        };
        challengeHandler.setLoginHandler(loginHandler);

        URI uri = URI.create("http://localhost:8080/path?query");
        HttpURLConnection connection = (HttpURLConnection) helper.openConnection(uri);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("X-Header", "value");
        connection.setChallengeHandler(challengeHandler);
        assertEquals(200, connection.getResponseCode());
        k3po.join();
    }

    @Test
    @Specification("response.with.status.code.401.then.200")
    public void shouldHandle401BasicThen200OK() throws Exception {
        Authenticator authenticator = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("joe2", "welcome2".toCharArray());
            }

        };
        Authenticator.setDefault(authenticator);

        URI uri = URI.create("http://localhost:8080/path?query");
        HttpURLConnection connection = (HttpURLConnection) helper.openConnection(uri);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("X-Header", "value");
        assertEquals(200, connection.getResponseCode());
        k3po.join();
    }
}
