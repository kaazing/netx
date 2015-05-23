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

package org.kaazing.netx.http.bridge.itest.internal;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.rules.RuleChain.outerRule;

import java.net.HttpURLConnection;
import java.net.URI;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;
import org.kaazing.netx.URLConnectionHelper;

public class HttpCrossOriginSecurityIT {

    private final K3poRule k3po = new K3poRule();

    private final AppletRule applet = new AppletRule();

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(applet).around(timeout);

    @Test
    @Ignore
    @Specification({
//        "netx.http.bridge.response",
        "response.with.status.code.200" })
    public void shouldRequestResponseCrossOrigin() throws Exception {
        // TODO: need a better way to simulate Applet security environment
        URI location = URI.create("http://localhost:8081/path");
        URLConnectionHelper helper = URLConnectionHelper.newInstance();

        HttpURLConnection connection = (HttpURLConnection) helper.openConnection(location);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("X-Header", "value");
        int responseCode = connection.getResponseCode();

        assertEquals(200, responseCode);
        k3po.finish();
    }

    @Test
    @Ignore
    @Specification({
        "test.applet.response",
        "netx.http.bridge.response",
        "response.with.status.code.200" })
    public void shouldTestApplet() throws Exception {
        k3po.finish();
    }

}
