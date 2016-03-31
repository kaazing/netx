/**
 * Copyright 2007-2015, Kaazing Corporation. All rights reserved.
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
package org.kaazing.netx;

import static java.util.Collections.singleton;
import static org.junit.Assert.assertSame;

import java.io.IOException;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Collection;

import javax.annotation.Resource;

import org.junit.Test;


public class URLConnectionHelperTest {

    @Test
    public void shouldLoadAndInject() throws IOException {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        TestURLConnection connection = (TestURLConnection) helper.openConnection(URI.create("test://case"));
        assertSame(helper, connection.getHelper());
    }

    public static class TestURLConnectionHelper extends URLConnectionHelperSpi {

        private URLConnectionHelper helper;

        @Resource
        public void setHelper(URLConnectionHelper helper) {
            this.helper = helper;
        }

        @Override
        public Collection<String> getSupportedProtocols() {
            return singleton("test");
        }

        @Override
        public URLConnection openConnection(URI location) throws IOException {
            return new TestURLConnection(helper, location);
        }

        @Override
        public URLStreamHandler newStreamHandler() throws IOException {
            throw new UnsupportedOperationException();
        }
    }

    private static final class TestURLConnection extends URLConnection {

        private final URLConnectionHelper helper;

        public TestURLConnection(URLConnectionHelper helper, URI location) {
            super(null);
            this.helper = helper;
        }

        @Override
        public void connect() throws IOException {
        }

        public URLConnectionHelper getHelper() {
            return helper;
        }
    }
}
