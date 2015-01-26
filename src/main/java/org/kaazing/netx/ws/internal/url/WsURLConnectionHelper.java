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

import static java.util.Collections.unmodifiableMap;
import static org.kaazing.netx.ws.internal.util.Util.changeScheme;

import java.io.IOException;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import javax.annotation.Resource;

import org.kaazing.netx.URLConnectionHelper;
import org.kaazing.netx.URLConnectionHelperSpi;
import org.kaazing.netx.ws.internal.WsURLConnectionImpl;
import org.kaazing.netx.ws.internal.ext.WebSocketExtensionFactory;

public final class WsURLConnectionHelper extends URLConnectionHelperSpi {

    private final Random random;
    private final Map<String, String> supportedProtocols;
    private final WebSocketExtensionFactory extensionFactory;

    private URLConnectionHelper helper;

    public WsURLConnectionHelper() {
        Map<String, String> supportedProtocols = new HashMap<String, String>();
        supportedProtocols.put("ws", "http");
        supportedProtocols.put("wsn", "http"); // TODO: remove?
        supportedProtocols.put("wse", "http"); // TODO
        this.supportedProtocols = unmodifiableMap(supportedProtocols);
        this.extensionFactory = WebSocketExtensionFactory.newInstance();
        this.random = new SecureRandom();
    }

    @Resource
    public void setHelper(URLConnectionHelper helper) {
        this.helper = helper;
    }

    @Override
    public Collection<String> getSupportedProtocols() {
        return supportedProtocols.keySet();
    }

    @Override
    public URLConnection openConnection(URI location) throws IOException {
        String scheme = location.getScheme();
        assert supportedProtocols.containsKey(scheme);
        String httpScheme = supportedProtocols.get(scheme);
        URI httpLocation = changeScheme(URI.create(location.toString()), httpScheme);
        assert helper != null;
        return new WsURLConnectionImpl(helper, location, httpLocation, random, extensionFactory);
    }

    @Override
    public URLStreamHandler newStreamHandler() throws IOException {
        assert helper != null;
        return new WsURLStreamHandlerImpl(helper, supportedProtocols, random, extensionFactory);
    }

}
