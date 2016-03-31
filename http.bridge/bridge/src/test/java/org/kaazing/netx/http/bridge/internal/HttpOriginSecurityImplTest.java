/**
 * Copyright 2007-2015, Kaazing Corporation. All rights reserved.
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
package org.kaazing.netx.http.bridge.internal;

import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.ServiceLoader;

import org.junit.Test;
import org.kaazing.netx.http.internal.HttpOriginSecuritySpi;

public class HttpOriginSecurityImplTest {

    @Test
    public void shouldLoadService() {
        ServiceLoader<HttpOriginSecuritySpi> securities = ServiceLoader.load(HttpOriginSecuritySpi.class);
        Iterator<HttpOriginSecuritySpi> iterator = securities.iterator();
        assertTrue(iterator.hasNext());
        assertTrue(iterator.next() instanceof HttpOriginSecurityImpl);
    }
}
