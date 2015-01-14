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

package org.kaazing.net.impl.bbosh;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.rules.RuleChain.outerRule;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.net.URLFactory;
import org.kaazing.net.bbosh.BBoshStrategy.Polling;
import org.kaazing.net.bbosh.BBoshStrategy.Streaming;
import org.kaazing.net.bbosh.BBoshURLConnection;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;

public class BBoshURLStreamHandlerFactorySpiIT {

    private TestRule timeout = new DisableOnDebug(new Timeout(1, SECONDS));

    private K3poRule robot = new K3poRule().setScriptRoot("org/kaazing/robotic/bbosh");

    @Rule
    public TestRule chain = outerRule(robot).around(timeout);

    @Test
    @Specification("polling/accept.echo.then.close")
    public void shouldConnectEchoThenClosedViaPolling() throws Exception {
        URL location = URLFactory.createURL("bbosh://localhost:8000/connections");

        BBoshURLConnection connection = (BBoshURLConnection) location.openConnection();
        connection.setSupportedStrategies(new Polling(5, SECONDS));
        connection.connect();
        OutputStream out = connection.getOutputStream();
        InputStream in = connection.getInputStream();

        out.write("Hello, world".getBytes(UTF_8));
        out.close();

        byte[] buf = new byte[32];
        int len = in.read(buf);
        in.close();

        robot.join();

        assertEquals(12, len);
        assertEquals("Hello, world", new String(buf, 0, 12, UTF_8));
    }

    @Test
    @Specification("polling/accept.echo.then.closed")
    public void shouldConnectEchoThenCloseViaPolling() throws Exception {
        URL location = URLFactory.createURL("bbosh://localhost:8000/connections");

        BBoshURLConnection connection = (BBoshURLConnection) location.openConnection();
        connection.setSupportedStrategies(new Polling(5, SECONDS));
        connection.connect();
        OutputStream out = connection.getOutputStream();
        InputStream in = connection.getInputStream();

        out.write("Hello, world".getBytes(UTF_8));
        out.close();

        byte[] buf = new byte[12];
        int len = in.read(buf);
        in.close();

        robot.join();

        assertEquals(12, len);
        assertEquals("Hello, world", new String(buf, 0, 12, UTF_8));
    }

    @Test
    @Specification("streaming/accept.echo.then.close")
    public void shouldConnectEchoThenClosedViaStreaming() throws Exception {
        URL location = URLFactory.createURL("bbosh://localhost:8000/connections");

        BBoshURLConnection connection = (BBoshURLConnection) location.openConnection();
        connection.setSupportedStrategies(new Streaming());
        connection.connect();
        OutputStream out = connection.getOutputStream();
        InputStream in = connection.getInputStream();

        out.write("Hello, world".getBytes(UTF_8));
        out.close();

        byte[] buf = new byte[32];
        int len = in.read(buf);
        in.close();

        robot.join();

        assertEquals(12, len);
        assertEquals("Hello, world", new String(buf, 0, 12, UTF_8));
    }

    @Test
    @Specification("streaming/accept.echo.then.closed")
    public void shouldConnectEchoThenCloseViaStreaming() throws Exception {
        URL location = URLFactory.createURL("bbosh://localhost:8000/connections");

        BBoshURLConnection connection = (BBoshURLConnection) location.openConnection();
        connection.setSupportedStrategies(new Streaming());
        connection.connect();
        OutputStream out = connection.getOutputStream();
        InputStream in = connection.getInputStream();

        out.write("Hello, world".getBytes(UTF_8));
        out.close();

        byte[] buf = new byte[12];
        int len = in.read(buf);
        in.close();

        robot.join();

        assertEquals(12, len);
        assertEquals("Hello, world", new String(buf, 0, 12, UTF_8));
    }
}
