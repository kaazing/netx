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

package org.kaazing.netx.http.bridge.internal;

import static java.security.AccessController.doPrivileged;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.rules.RuleChain.outerRule;

import java.net.HttpURLConnection;
import java.net.SocketPermission;
import java.net.URI;
import java.net.URL;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.DomainCombiner;
import java.security.Permissions;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.security.Policy;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;
import org.kaazing.netx.URLConnectionHelper;

import sun.security.provider.PolicyFile;

public class HttpCrossOriginSecurityIT {

    private final K3poRule k3po = new K3poRule();

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
//        "netx.http.bridge.response",
        "response.with.status.code.200"})
    public void shouldRequestResponseCrossOrigin() throws Exception {

        // TODO: PolicyRule
        URL policyFile = getClass().getResource(".java.policy");
        Policy newPolicy = new PolicyFile(policyFile);
        Policy oldPolicy = Policy.getPolicy();
        try {
            Policy.setPolicy(newPolicy);
            URI location = URI.create("http://localhost:8081/path");
            URLConnectionHelper helper = URLConnectionHelper.newInstance();

            System.setSecurityManager(new SecurityManager());
            HttpURLConnection connection = (HttpURLConnection) helper.openConnection(location);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("X-Header", "value");
            int responseCode = connection.getResponseCode();
            assertEquals(200, responseCode);
            k3po.join();
        }
        finally {
            Policy.setPolicy(oldPolicy);
        }
    }
}
