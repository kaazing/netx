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

import static org.kaazing.netx.http.HttpRedirectPolicy.ORIGIN;

import java.net.URL;

import org.kaazing.netx.http.auth.ChallengeHandler;

/**
 * {@code HttpURLConnection} enhances the built-in HTTP-based {@code URLConnection}.
 *
 * Support is added for HTTP upgrade, an origin-aware HTTP redirect policy, and an application-level security challenge handler.
 */
public abstract class HttpURLConnection extends java.net.HttpURLConnection {

    /**
     * HTTP Status-Code 101: Switching Protocols.
     */
    public static final int HTTP_SWITCHING_PROTOCOLS = 101;

    private ChallengeHandler challengeHandler;
    private HttpRedirectPolicy redirectPolicy;

    /**
     * Creates a new {@code HttpURLConnection}.
     *
     * @param url the location for this connection
     */
    protected HttpURLConnection(URL url) {
        super(url);

        this.redirectPolicy = ORIGIN;
    }

    /**
     * Sets a new origin-aware HTTP redirect policy.
     *
     * @param redirectPolicy  the new HTTP redirect policy
     */
    public void setRedirectPolicy(HttpRedirectPolicy redirectPolicy) {
        this.redirectPolicy = redirectPolicy;
    }

    /**
     * Returns the current origin-aware HTTP redirect policy.
     *
     * @return the current HTTP redirect policy
     */
    public HttpRedirectPolicy getRedirectPolicy() {
        return redirectPolicy;
    }

    /**
     * Sets a new application-level HTTP security challenge handler.
     *
     * @param challengeHandler  the new HTTP security challenge handler
     */
    public void setChallengeHandler(ChallengeHandler challengeHandler) {
        this.challengeHandler = challengeHandler;
    }

    /**
     * Returns the current application-level HTTP security challenge handler.
     *
     * @return the current HTTP security challenge handler
     */
    public ChallengeHandler getChallengeHandler() {
        return challengeHandler;
    }
}
