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

package org.kaazing.netx.ws.internal.ext;

import java.io.IOException;


/**
 * {@link WebSocketExtensionFactorySpi} is part of <i>Service Provider Interface</i> <em>(SPI)</em> for extension developers.
 * <p>
 * Developing an extension involves implementing:
 * <UL>
 *   <LI> a sub-class of {@link WebSocketExtensionFactorySpi}
 *   <LI> a sub-class of {@link WebSocketExtensionSpi}
 * </UL>
 * <p>
 */
public abstract class WebSocketExtensionFactorySpi {

    /**
     * Returns the name of the extension that this factory will create.
     *
     * @return String   name of the extension
     */
    public abstract String getExtensionName();

    /**
     * Creates a {@link WebSocketExtensionSpi} instance. This method is called <b>only</b> when the extension has been
     * successfully negotiated between the client and the server. If this method throws an IOException, then the negotiated
     * extension will not participate when messages are being received or sent. The format for extensionWithParams is as
     * shown below:
     *
     * {@code}
     *      extension-name[;param1=value1;param2;param3=value3]
     * {@code}
     *
     * @param extensionWithParams  String representation of the extension in response header format
     * @return WebSocketExtensionSpi  instance
     * @throw IOException if the specified string contains invalid extension name, parameter name or parameter value
     */
    public abstract WebSocketExtensionSpi createExtension(String extensionWithParams) throws IOException;

    /**
     * Validates the extension name, parameter names and values in the specified string. This method is called for an enabled
     * extensions before the opening handshake to ensure that the string representation of the extension is valid. The extension
     * will not be negotiated if IOException is thrown. The format of the specified string will be as shown below:
     *
     * {@code}
     *      extension-name[;param1=value1;param2;param3=value3]
     * {@code}
     *
     * @param extensionWithParams  String representation of the extension in request header format
     * @throw IOException if the specified string contains invalid extension name, parameter name or parameter value
     */
    public abstract void validateExtension(String extensionWithParams) throws IOException;
}
