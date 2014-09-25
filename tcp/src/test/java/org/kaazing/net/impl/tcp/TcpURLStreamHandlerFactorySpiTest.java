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

package org.kaazing.net.impl.tcp;

import static java.net.InetAddress.getByName;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.kaazing.net.URLFactory;

public class TcpURLStreamHandlerFactorySpiTest {

    private Thread runner;

    @Before
    public void start() throws Exception {
        EchoServer echoServer = new EchoServer(getByName("localhost"), 61234);
        runner = new Thread(echoServer);
        runner.start();
        while (!echoServer.isBound()) {
            Thread.sleep(50);
        }
    }

    @After
    public void stop() {
        runner.interrupt();
    }

    @Test(timeout = 1000L)
    public void shouldEcho() throws Exception {
        URL location = URLFactory.createURL("tcp://localhost:61234");

        URLConnection connection = location.openConnection();
        connection.connect();
        OutputStream out = connection.getOutputStream();
        InputStream in = connection.getInputStream();

        out.write("Hello, world".getBytes(UTF_8));
        byte[] buf = new byte[32];
        int len = in.read(buf);

        out.close();
        in.close();

        assertEquals(12, len);
        assertEquals("Hello, world", new String(buf, 0, 12, UTF_8));
    }

    private static final class EchoServer implements Runnable {

        private final ServerSocket server;

        private EchoServer(InetAddress address, int port) throws IOException {
            this.server = new ServerSocket(port, 50, address);
        }

        public boolean isBound() {
            return server.isBound();
        }

        @Override
        public void run() {
            InputStream in = null;
            OutputStream out = null;
            try {
                Socket socket = server.accept();
                in = socket.getInputStream();
                out = socket.getOutputStream();

                byte[] buf = new byte[32];

                for (int len = in.read(buf); len > 0; len = in.read(buf)) {
                    out.write(buf, 0, len);
                }

            }
            catch (IOException e) {
            }
            finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
                if (server != null) {
                    try {
                        server.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        }
    }
}
