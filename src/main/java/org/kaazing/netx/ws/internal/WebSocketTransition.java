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
package org.kaazing.netx.ws.internal;

public enum WebSocketTransition {
    RECEIVED_UPGRADE_RESPONSE,
    RECEIVED_CLOSE_FRAME,
    SEND_CLOSE_FRAME,
    RECEIVED_PING_FRAME,
    RECEIVED_PONG_FRAME,
    SEND_PONG_FRAME,
    RECEIVED_BINARY_FRAME,
    SEND_BINARY_FRAME,
    RECEIVED_TEXT_FRAME,
    SEND_TEXT_FRAME,
    ERROR
}
