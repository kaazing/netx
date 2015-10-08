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
package org.kaazing.netx.bbosh.internal;

import static java.util.Collections.unmodifiableMap;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.kaazing.netx.URLConnectionHelperSpi;

public final class BBoshURLConnectionHelper extends URLConnectionHelperSpi {

    private static final Map<String, String> HTTP_PROTOCOLS;

    static {
        Map<String, String> protocolMapping = new HashMap<String, String>();
        protocolMapping.put("bbosh", "http");
        protocolMapping.put("bbosh+ssl", "https");
        HTTP_PROTOCOLS = unmodifiableMap(protocolMapping);
    }

    @Override
    public Collection<String> getSupportedProtocols() {
        return HTTP_PROTOCOLS.keySet();
    }

    @Override
    public URLConnection openConnection(URI location) throws IOException {
        String protocol = location.getScheme();
        String httpOrHttps = HTTP_PROTOCOLS.get(protocol);
        if (httpOrHttps == null) {
            throw new IllegalArgumentException("Unsupported protocol: " + protocol);
        }
        return new BBoshURLConnectionImpl(location, httpOrHttps);
    }

    @Override
    public URLStreamHandler newStreamHandler() throws IOException {
        return new BBoshURLStreamHandler();
    }

    private static final class BBoshURLStreamHandler extends URLStreamHandler {

        @Override
        protected URLConnection openConnection(URL url) throws IOException {
            String httpOrHttps = HTTP_PROTOCOLS.get(url.getProtocol());
            assert httpOrHttps != null;
            return new BBoshURLConnectionImpl(url, httpOrHttps);
        }
    }
}
