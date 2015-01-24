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

package org.kaazing.netx.tcp.impl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;
import org.kaazing.netx.URLConnectionHelper;

public class TcpURLConnectionHelperIT {

    private final K3poRule robot = new K3poRule();

    private final TestRule timeout = new DisableOnDebug(new Timeout(1, SECONDS));

    @Rule
    public final TestRule chain = RuleChain.outerRule(robot).around(timeout);

    @Test
    @Specification("echo.then.closed")
    public void shouldEchoURI() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("tcp://localhost:61234");

        URLConnection connection = helper.openConnection(location);
        connection.connect();

        OutputStream out = connection.getOutputStream();
        out.write("Hello, world".getBytes(UTF_8));
        out.close();

        InputStream in = connection.getInputStream();
        byte[] buf = new byte[32];
        int len = in.read(buf);
        in.close();

        robot.join();

        assertEquals(12, len);
        assertEquals("Hello, world", new String(buf, 0, 12, UTF_8));
    }

    @Test
    @Specification("echo.then.closed")
    public void shouldEchoURL() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("tcp://localhost:61234");

        URL locationURL = helper.toURL(location);
        URLConnection connection = locationURL.openConnection();
        connection.connect();

        OutputStream out = connection.getOutputStream();
        out.write("Hello, world".getBytes(UTF_8));
        out.close();

        InputStream in = connection.getInputStream();
        byte[] buf = new byte[32];
        int len = in.read(buf);
        in.close();

        robot.join();

        assertEquals(12, len);
        assertEquals("Hello, world", new String(buf, 0, 12, UTF_8));
    }

}
