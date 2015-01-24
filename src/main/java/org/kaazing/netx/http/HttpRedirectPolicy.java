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

package org.kaazing.netx.http;

/**
 * Policy for following HTTP redirect requests with response code 3xx.
 */
public enum HttpRedirectPolicy {
    /**
     * Do not follow HTTP redirects.
     */
    NEVER,

    /**
     * Follow HTTP redirect requests always regardless of the origin, domain, etc.
     */
    ALWAYS,

    /**
     * Follow HTTP redirect only if the redirected request is for the same
     * origin. This implies that both the scheme/protocol and the
     * <b>authority</b> should match between the current and the redirect URIs.
     * Note that authority includes the hostname and the port.
     */
    ORIGIN,

    /**
     * Follow HTTP redirect only if the redirected request is for the
     * same-domain, peer-domain, or sub-domain.
     * <p>
     * URIs that satisfy HttpRedirectPolicy.ORIGIN policy will implicitly
     * satisfy HttpRedirectPolicy.DOMAIN policy.
     * <p>
     * URIs with identical domains would be http://example.com:8080 and
     * https://example.com:9090.
     * <p>
     * Domains in URIs ws://marketing.example.com:8001 and
     * ws://sales.example.com:8002 are peers of each other.
     * <p>
     * Domain of the redirected URI https://east.example.com:8080 is a
     * sub-domain of the domain of the original URI
     * http://example.com:8080.
     */
    DOMAIN
}
