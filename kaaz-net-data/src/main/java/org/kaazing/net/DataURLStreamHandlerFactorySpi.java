/*
 * Copyright 2014 Kaazing Corporation, All rights reserved.
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

package org.kaazing.net;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Collection;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singleton;

public final class DataURLStreamHandlerFactorySpi extends URLStreamHandlerFactorySpi {

    @Override
    public Collection<String> getSupportedProtocols() {
        return singleton("data");
    }

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        if (!"data".equals(protocol)) {
            throw new IllegalArgumentException("Unsupported protocol: " + protocol);
        }
        return new DataURLStreamHandler();
    }

    private static final class DataURLStreamHandler extends URLStreamHandler {

        @Override
        protected URLConnection openConnection(URL url) throws IOException {
            String specificPart = url.getFile();
            if (!specificPart.startsWith(",")) {
                // see http://en.wikipedia.org/wiki/Data_URI_scheme#Format
                throw new MalformedURLException("TODO: support mime-type and base64");
            }

            String data = specificPart.substring(1);
            byte[] dataAsBytes = data.getBytes(UTF_8);
            return new DataURLConnection(url, dataAsBytes);
        }

        private static class DataURLConnection extends URLConnection {

            private final byte[] dataAsBytes;

            private DataURLConnection(URL url, byte[] dataAsBytes) {
                super(url);
                this.dataAsBytes = dataAsBytes;
            }

            @Override
            public void connect() throws IOException {
                // no-op, already "connected"
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(dataAsBytes);
            }

        }
    }
}
