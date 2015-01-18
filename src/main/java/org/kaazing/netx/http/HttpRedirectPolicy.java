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
    SAME_ORIGIN,

    /**
     * Follow HTTP redirect only if the redirected request is for the same
     * domain. This implies that both the scheme/protocol and the
     * <b>hostname</b> should match between the current and the redirect URIs.
     * <p>
     * URIs that satisfy HttpRedirectPolicy.SAME_ORIGIN policy will implicitly
     * satisfy HttpRedirectPolicy.SAME_DOMAIN policy.
     * <p>
     * URIs with identical domains would be http://example.com:8080 and
     * https://example.com:9090.
     */
    SAME_DOMAIN,

    /**
     * Follow HTTP redirect only if the redirected request is for a peer-domain.
     * This implies that both the scheme/protocol and the <b>domain</b> should
     * match between the current and the redirect URIs.
     * <p>
     * URIs that satisfy HttpRedirectPolicy.SAME_DOMAIN policy will implicitly
     * satisfy HttpRedirectPolicy.PEER_DOMAIN policy.
     * <p>
     * To determine if the two URIs have peer-domains, we do the following:
     * <ul>
     *   <li>compute base-domain by removing the token before the first '.' in
     *       the hostname of the original URI and check if the hostname of the
     *       redirected URI ends with the computed base-domain
     *  <li>compute base-domain by removing the token before the first '.' in
     *      the hostname of the redirected URI and check if the hostname of the
     *      original URI ends with the computed base-domain
     * </ul>
     * <p>
     * If both the conditions are satisfied, then we conclude that the URIs are
     * for peer-domains. However, if the host in the URI has no '.'(for eg.,
     * ws://localhost:8000), then we just use the entire hostname as the
     * computed base-domain.
     * <p>
     * If you are using this policy, it is recommended that the number of tokens
     * in the hostname be atleast 2 + number_of_tokens(top-level-domain). For
     * example, if the top-level-domain(TLD) is "com", then the URIs should have
     * atleast 3 tokens in the hostname. So, ws://marketing.example.com:8001 and
     * ws://sales.example.com:8002 are examples of URIs with peer-domains. Similarly,
     * if the TLD is "co.uk", then the URIs should have atleast 4 tokens in the
     * hostname. So, ws://marketing.example.co.uk:8001 and
     * ws://sales.example.co.uk:8002 are examples of URIs with peer-domains.
     */
    PEER_DOMAIN,

    /**
     * Follow HTTP redirect only if the redirected request is for child-domain
     * or sub-domain of the original request.
     * <p>
     * URIs that satisfy HttpRedirectPolicy.SAME_DOMAIN policy will implicitly
     * satisfy HttpRedirectPolicy.SUB_DOMAIN policy.
     * <p>
     * To determine if the domain of the redirected URI is sub-domain/child-domain
     * of the domain of the original URI, we check if the hostname of the
     * redirected URI ends with the hostname of the original URI.
     * <p>
     * Domain of the redirected URI https://east.example.com:8080 is a
     * sub-domain/child-domain of the domain of the original URL
     * http://example.com:8080.
     */
    SUB_DOMAIN

}
