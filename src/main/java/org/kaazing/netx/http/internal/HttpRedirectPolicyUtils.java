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

package org.kaazing.netx.http.internal;

import static java.util.Collections.unmodifiableMap;

import java.net.URL;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.kaazing.netx.http.HttpRedirectPolicy;

final class HttpRedirectPolicyUtils {

    private HttpRedirectPolicyUtils() {
        // utilities only
    }

    public static boolean shouldFollowRedirect(HttpRedirectPolicy redirectPolicy, URL currentURL, URL redirectURL) {
        return POLICY_CHECKERS.get(redirectPolicy).compare(currentURL, redirectURL) == 0;
    }

    private static final Map<HttpRedirectPolicy, Comparator<URL>> POLICY_CHECKERS;

    static
    {
        Map<HttpRedirectPolicy, Comparator<URL>> policyCheckers = new HashMap<HttpRedirectPolicy, Comparator<URL>>();

        policyCheckers.put(HttpRedirectPolicy.NEVER, new Comparator<URL>() {
            @Override
            public int compare(URL current, URL redirect) {
                return -1;
            }
        });

        policyCheckers.put(HttpRedirectPolicy.ALWAYS, new Comparator<URL>() {
            @Override
            public int compare(URL current, URL redirect) {
                return 0;
            }
        });

        policyCheckers.put(HttpRedirectPolicy.ORIGIN, new Comparator<URL>() {
            @Override
            public int compare(URL current, URL redirect) {
                // origin consists of protocol://host:port
                if (current.getProtocol().equalsIgnoreCase(redirect.getProtocol()) &&
                    current.getHost().equalsIgnoreCase(redirect.getHost()) &&
                    current.getPort() == redirect.getPort()) {
                    return 0;
                }

                return -1;
            }
        });

        policyCheckers.put(HttpRedirectPolicy.DOMAIN, new Comparator<URL>() {
            @Override
            public int compare(URL current, URL redirect) {
                // domain consists of host, but not protocol or port
                if (current.getHost().equalsIgnoreCase(redirect.getHost())) {
                    return 0;
                }

                // peer domains are both subdomains of the same larger domain
                // eg. east.example.com and west.example.com are peer domains,
                // where each peer domain is a subdomain of the example.com domain
                // http -> https OK
                // https -> http NOT OK

                String currentHost = current.getHost();
                String redirectHost = redirect.getHost();
                int currentDotAt = currentHost.indexOf('.');
                int redirectDotAt = redirectHost.indexOf('.');

                if (redirect.getProtocol().startsWith(current.getProtocol()) &&
                    currentDotAt != -1 && redirectDotAt != -1 &&
                    currentHost.substring(currentDotAt + 1).equalsIgnoreCase(redirectHost.substring(redirectDotAt + 1))) {
                    return 0;
                }

                if (redirect.getProtocol().startsWith(current.getProtocol()) &&
                        redirectHost.toLowerCase().endsWith("." + currentHost.toLowerCase())) {
                        return 0;
                }

                return -1;
            }
        });

        POLICY_CHECKERS = unmodifiableMap(policyCheckers);
    }
}
