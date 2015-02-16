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

/**
 * WebSocketExtensionSpi is part of <i>Service Provider Interface</i> <em>(SPI)</em> for extension developers.
 * <p>
 * Developing an extension involves the following:
 * <UL>
 *   <LI> a sub-class of {@link WebSocketExtensionFactorySpi}
 *   <LI> a sub-class of {@link WebSocketExtensionSpi}
 *   <LI> a sub-class of {@link WebSocketExtension} with {@link Parameter}s defined as constants
 *   <LI> a sub-class of {@link WebSocketHooks}
 * </UL>
 * <p>
 * When an enabled extension is successfully negotiated, an instance of this class is created using the corresponding
 * {@link WebSocketExtensionFactorySpi} that is registered through META-INF/services. This class is used to instantiate the
 * {@link WebSocketHooks} that can be exercised as the state machine transitions from one state to another while handling the
 * WebSocket traffic. Based on the functionality of the extension, the developer can decide which hooks to code.
 */
public abstract class WebSocketExtensionSpi {

    public abstract WebSocketHooks createWebSocketHooks();
}
