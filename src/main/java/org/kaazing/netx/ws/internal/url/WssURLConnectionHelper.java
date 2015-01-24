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

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import java.io.IOException;
import java.net.URLStreamHandler;
import java.util.Collection;

public class WssURLConnectionHelper extends WsURLConnectionHelper {
    private static final Collection<String> SUPPORTED_PROTOCOLS = unmodifiableList(asList("wss", "wse+ssl", "wssn"));

    @Override
    public Collection<String> getSupportedProtocols() {
        return SUPPORTED_PROTOCOLS;
    }


    @Override
    public URLStreamHandler newStreamHandler() throws IOException {
        return new WssURLStreamHandlerImpl(super.getExtensionFactories());
    }
}
