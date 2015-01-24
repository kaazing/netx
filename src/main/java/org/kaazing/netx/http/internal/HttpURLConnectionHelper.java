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

package org.kaazing.netx.http.internal;

import static java.util.Arrays.asList;

import java.io.IOException;
import java.net.URI;
import java.net.URLConnection;
import java.util.Collection;
import java.util.List;

import org.kaazing.netx.URLConnectionHelperSpi;

public final class HttpURLConnectionHelper extends URLConnectionHelperSpi {

    private static final List<String> SUPPORTED_PROTOCOLS = asList("http");

    @Override
    public URLConnection openConnection(URI location) throws IOException {
        assert SUPPORTED_PROTOCOLS.contains(location.getScheme());
        return new HttpURLConnectionImpl(location.toURL());
    }

    @Override
    public int getDefaultPort() {
        return 80;
    }

    @Override
    public Collection<String> getSupportedProtocols() {
        return SUPPORTED_PROTOCOLS;
    }

}
