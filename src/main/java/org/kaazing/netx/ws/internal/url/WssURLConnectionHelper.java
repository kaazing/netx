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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Resource;

import org.kaazing.netx.URLConnectionHelper;
import org.kaazing.netx.URLConnectionHelperSpi;
import org.kaazing.netx.ws.internal.WsURLConnectionImpl;
import org.kaazing.netx.ws.internal.ext.WebSocketExtensionFactory;

public final class WssURLConnectionHelper extends URLConnectionHelperSpi {

    private final Map<String, String> supportedProtocols;
    private final WebSocketExtensionFactory extensionFactory;

    private URLConnectionHelper helper = URLConnectionHelper.newInstance(); // TODO: inject

    public WssURLConnectionHelper() {
        Map<String, String> supportedProtocols = new HashMap<String, String>();
        supportedProtocols.put("wss", "https");
        supportedProtocols.put("wsn+ssl", "https"); // TODO: remove?
        supportedProtocols.put("wse+ssl", "https"); // TODO
        this.supportedProtocols = unmodifiableMap(supportedProtocols);
        this.extensionFactory = WebSocketExtensionFactory.newInstance();
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
        return new WsURLConnectionImpl(helper, location, httpLocation, extensionFactory);
    }

    @Override
    public URLStreamHandler newStreamHandler() throws IOException {
        return new WssURLStreamHandlerImpl(helper, supportedProtocols, extensionFactory);
    }
}
