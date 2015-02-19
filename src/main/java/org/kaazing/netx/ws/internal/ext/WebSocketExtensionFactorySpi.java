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

import org.kaazing.netx.ws.internal.WebSocketExtension;
import org.kaazing.netx.ws.internal.WebSocketExtensionParameterValues;

/**
 * {@link WebSocketExtensionFactorySpi} is part of <i>Service Provider Interface</i> <em>(SPI)</em> for extension developers.
 * <p>
 * Developing an extension involves the following:
 * <UL>
 *   <LI> a sub-class of {@link WebSocketExtensionFactorySpi}
 *   <LI> a sub-class of {@link WebSocketExtensionSpi}
 *   <LI> a sub-class of {@link WebSocketExtension} with {@link Parameter}s defined as constants
 *   <LI> a sub-class of {@link WebSocketExtensionHooks}
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
     * Creates and returns a {@link WebSocketExtensionSpi} instance corresponding to the extension that this factory is
     * responsible for. Parameters and their corresponding values are passed in as {@link WebSocketExtensionParameterValues}.
     *
     * @param parameters    {@link WebSocketExtensionParameterValues} with parameter name and value
     * @return WebSocketExtensionSpi  instance
     */
    public abstract WebSocketExtensionSpi createExtension(WebSocketExtensionParameterValues parameters);
}
