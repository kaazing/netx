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
package org.kaazing.netx.ws.specification.ext.primary;

import org.kaazing.netx.ws.WebSocketExtension;

public final class PrimaryExtension extends WebSocketExtension {
    public static final PrimaryExtension PRIMARY_EXTENSION = new PrimaryExtension();

    private static final String EXTENSION_NAME = "primary";

    private PrimaryExtension() {
    }

    @Override
    public String name() {
        return EXTENSION_NAME;
    }
}
