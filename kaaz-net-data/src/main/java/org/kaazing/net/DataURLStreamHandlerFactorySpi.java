/**
 * Copyright (c) 2007-2013, Kaazing Corporation. All rights reserved.
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
