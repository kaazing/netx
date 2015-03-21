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
package org.kaazing.netx.ws.ext;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.rules.RuleChain.outerRule;

import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
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
import org.kaazing.netx.ws.specification.ext.primary.PrimaryExtension;
import org.kaazing.netx.ws.specification.ext.secondary.SecondaryExtension;

public class ExtensionsIT {
    private final K3poRule k3po = new K3poRule().setScriptRoot("org/kaazing/netx/ws/ext");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
        "extensions.primary.hello/handshake.response.and.frame" })
    public void shouldEchoTextFrameWithPayloadLength125WithPrimaryExtension() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");
        URL locationURL = helper.toURL(location);
        WsURLConnection conn = (WsURLConnection) locationURL.openConnection();
        conn.addEnabledExtension(new PrimaryExtension());
        conn.addEnabledExtension(new SecondaryExtension());

        conn.connect();

        Writer writer = conn.getWriter();
        Reader reader = conn.getReader();

        String randomString = new RandomString(125).nextString();
        String writeString = "Hello, " + randomString;
        writer.write(writeString.toCharArray());

        char[] cbuf = new char[140];
        int offset = 0;
        int length = cbuf.length;
        int charsRead = 0;

        while ((charsRead != -1) && (length > 0)) {
            charsRead = reader.read(cbuf, offset, length);
            if (charsRead != -1) {
                offset += charsRead;
                length -= charsRead;
            }
        }

        String expectedString = "nuqneH, " + randomString;   // Klingon translated.
        String readString = String.valueOf(cbuf, 0, offset);
        k3po.join();
        assertEquals(expectedString, readString);
    }

    private static class RandomString {

        private static final char[] SYMBOLS;

        static {
            StringBuilder tmp = new StringBuilder();
            for (char ch = 32; ch <= 126; ++ch) {
                tmp.append(ch);
            }
            SYMBOLS = tmp.toString().toCharArray();
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
                buf[idx] = SYMBOLS[random.nextInt(SYMBOLS.length)];
            }

            return new String(buf);
        }
    }
}
