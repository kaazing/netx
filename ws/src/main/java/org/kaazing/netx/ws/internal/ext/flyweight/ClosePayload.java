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
package org.kaazing.netx.ws.internal.ext.flyweight;

/**
 * ClosePayload provides convenience APIs to access the payload of WebSocket CLOSE frame.
 *
 */
public abstract class ClosePayload extends Flyweight {
    /**
     * Returns the status code in the WebSocket CLOSE payload.
     *
     * @return code
     */
    public abstract int statusCode();

    /**
     * Returns the reason offset in the WebSocket CLOSE payload.
     * @return
     */
    public abstract int reasonOffset();

    /**
     * Returns the length of the reason in the WebSocket CLOSE payload.
     * @return
     */
    public abstract int reasonLength();
}
