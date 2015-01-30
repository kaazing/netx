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

package org.kaazing.netx.ws.internal.url;

import static org.kaazing.netx.ws.internal.util.Util.changeScheme;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLStreamHandler;
import java.util.Map;
import java.util.Random;

import org.kaazing.netx.URLConnectionHelper;
import org.kaazing.netx.ws.WsURLConnection;
import org.kaazing.netx.ws.internal.WsURLConnectionImpl;
import org.kaazing.netx.ws.internal.ext.WebSocketExtensionFactory;

final class WsURLStreamHandlerImpl extends URLStreamHandler {

    private final URLConnectionHelper helper;
    private final Map<String, String> supportedProtocols;
    private final WebSocketExtensionFactory extensionFactory;
    private final Random random;

    public WsURLStreamHandlerImpl(
            URLConnectionHelper helper,
            Map<String, String> supportedProtocols,
            Random random,
            WebSocketExtensionFactory extensionFactory) {
        this.helper = helper;
        this.supportedProtocols = supportedProtocols;
        this.extensionFactory = extensionFactory;
        this.random = random;
    }

    @Override
    protected int getDefaultPort() {
        return 80;
    }

    @Override
    protected WsURLConnection openConnection(URL location) throws IOException {
        URI locationURI = URI.create(location.toString());
        String scheme = locationURI.getScheme();
        String httpScheme = supportedProtocols.get(scheme);
        assert httpScheme != null;
        URI httpLocation = changeScheme(locationURI, httpScheme);
        return new WsURLConnectionImpl(helper, location, httpLocation, random, extensionFactory);
    }

}
