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

package org.kaazing.netx.http.internal.auth;


import java.net.PasswordAuthentication;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.kaazing.netx.http.auth.ApplicationBasicChallengeHandler;
import org.kaazing.netx.http.auth.ChallengeRequest;
import org.kaazing.netx.http.auth.ChallengeResponse;
import org.kaazing.netx.http.auth.LoginHandler;
import org.kaazing.netx.http.auth.LoginHandlerProvider;

/**
 * Challenge handler for Basic authentication. See RFC 2617.
 */
public class DefaultApplicationBasicChallengeHandler extends ApplicationBasicChallengeHandler implements LoginHandlerProvider {

// ------------------------------ FIELDS ------------------------------

    private static final String CLASS_NAME = DefaultApplicationBasicChallengeHandler.class.getName();
    private static final Logger LOG = Logger.getLogger(CLASS_NAME);
    private static final String AUTHENTICATION_SCHEME = "Application Basic";

    private final Map<String, LoginHandler> loginHandlersByRealm = new ConcurrentHashMap<String, LoginHandler>();

    @Override
    public void setRealmLoginHandler(String realm, LoginHandler loginHandler) {
        if (realm == null) {
            throw new NullPointerException("realm");
        }
        if (loginHandler == null) {
            throw new NullPointerException("loginHandler");
        }

        loginHandlersByRealm.put(realm, loginHandler);
    }

    /**
     * If specified, this login handler is responsible for assisting in the
     * production of challenge responses.
     */
    private LoginHandler loginHandler;

    /**
     * Provide a login handler to be used in association with this challenge handler.
     * The login handler is used to assist in obtaining credentials to respond to challenge requests.
     *
     * @param loginHandler a login handler for credentials.
     */
    @Override
    public ApplicationBasicChallengeHandler setLoginHandler(LoginHandler loginHandler) {
        this.loginHandler = loginHandler;
        return this;
    }

    /**
     * Get the login handler associated with this challenge handler.
     * A login handler is used to assist in obtaining credentials to respond to challenge requests.
     *
     * @return a login handler to assist in providing credentials, or {@code null} if none has been established yet.
     */
    @Override
    public LoginHandler getLoginHandler() {
        return loginHandler;
    }

    @Override
    public boolean canHandle(ChallengeRequest challengeRequest) {
        return challengeRequest != null &&
                AUTHENTICATION_SCHEME.equals(challengeRequest.getAuthenticationScheme());
    }

    @Override
    public ChallengeResponse handle(ChallengeRequest challengeRequest) {

        LOG.entering(CLASS_NAME, "handle", new String[]{challengeRequest.getLocation(),
                                                        challengeRequest.getAuthenticationParameters()});

        if (challengeRequest.getLocation() != null) {



            // Start by using this generic ApplicationBasic handler
            LoginHandler loginHandler = getLoginHandler();

            // Try to delegate to a realm-specific login handler if we can
            String realm = RealmUtils.getRealm(challengeRequest);
            if (realm != null && loginHandlersByRealm.get(realm) != null) {
                loginHandler = loginHandlersByRealm.get(realm);
            }
            LOG.finest("ApplicationBasicChallengeHandler.getResponse: login handler = " + loginHandler);
            if (loginHandler != null) {
                PasswordAuthentication creds = loginHandler.getCredentials();
                if (creds != null && creds.getUserName() != null && creds.getPassword() != null) {
                    return BasicChallengeResponseFactory.create(creds, this);
                }
            }
        }
        return null;
    }

}
