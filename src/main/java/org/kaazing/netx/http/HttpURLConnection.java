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

import static org.kaazing.netx.http.HttpRedirectPolicy.SAME_ORIGIN;

import java.net.URL;

import org.kaazing.netx.http.auth.ChallengeHandler;

public abstract class HttpURLConnection extends java.net.HttpURLConnection {

    /**
     * HTTP Status-Code 101: Switching Protocols.
     */
    public static final int HTTP_SWITCHING_PROTOCOLS = 101;

    private ChallengeHandler   challengeHandler;
    private HttpRedirectPolicy redirectPolicy;

    protected HttpURLConnection(URL u) {
        super(u);

        this.redirectPolicy = SAME_ORIGIN;
    }

    public void setRedirectPolicy(HttpRedirectPolicy redirectPolicy) {
        this.redirectPolicy = redirectPolicy;
    }

    public HttpRedirectPolicy getRedirectPolicy() {
        return redirectPolicy;
    }

    public void setChallengeHandler(ChallengeHandler challengeHandler) {
        this.challengeHandler = challengeHandler;
    }

    public ChallengeHandler getChallengeHandler() {
        return challengeHandler;
    }
}
