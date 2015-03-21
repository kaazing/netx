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

package org.kaazing.netx.ws;

/**
 * {@link WebSocketExtension} is the application facing class that an extension developer must provide along with other SPIs
 * such as WebSocketExtensionSpi and WebSocketExtensionFactorySpi so that it can be enabled. An extension is enabled
 * using @{link WebSocketFactory#addDefaultEnabledExtension}, {@link WebSocket#addEnabledExtension(WebSocketExtension)}
 * or {@link WsURLConnection#addEnabledExtension(WebSocketExtension)} APIs. The extension developer <em>MUST</em> override
 * the <code>toString()</code> method in their sub-class to return a RFC-3864 formatted string that looks as shown below:
 *
 * {@code}
 *      extension-name[;param1=value1;param2=value2;param3;param4=value4;...]
 * {@code}
 *
 * The return value of the <code>toString()</code> method is sent as part of <i>Sec-WebSocket-Extensions</i> header during the
 * opening handshake to negotiate the extension with the server.
 */
public abstract class WebSocketExtension {
    /**
     * Protected constructor to be invoked by the sub-class constructor.
     *
     * @param name    name of the WebSocketExtension
     */
    protected WebSocketExtension() {
    }

    /**
     * Returns the name of this {@link WebSocketExtension}.
     *
     * @return the name of the extension
     */
    public abstract String name() ;

    /**
     * Returns RFC-3864 formatted string representation of the extension. The formatted string looks as shown below:
     *
     * {@code}
     *      extension-name[;param1=value1;param2=value2;param3;param4=value4;...]
     * {@code}
     *
     * @return RFC-3864 formatted string representation of this extension
     */
    @Override
    public abstract String toString();
}
