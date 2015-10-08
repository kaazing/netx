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
package org.kaazing.netx.http.auth;

import java.util.ServiceLoader;


/**
 * Challenge handler for "Application Basic" authentication or challenge scheme.
 * <p>
 * The ApplicationBasicChallengeHandler can be loaded and instantiated using
 * {@link ApplicationBasicChallengeHandler#create()}, and registered
 * at a location using {@link DispatchChallengeHandler#register(String, ChallengeHandler)}.
 * <p>
 * In addition, one can install general and realm-specific {@link LoginHandler}
 * objects onto this {@link ApplicationBasicChallengeHandler} to assist in handling
 * challenges associated with any or specific realms.  This can be achieved using
 * {@link #setLoginHandler(LoginHandler)} and
 * {@link #setRealmLoginHandler(String, LoginHandler)} methods.
 * <p>
 * The following example loads an instance of a {@link ApplicationBasicChallengeHandler},
 * sets a login handler onto it and registers the basic handler at a URI location.
 * In this way, all attempts to access that URI for which the server issues
 * "Application Basic" challenges are handled by the registered
 * {@link ApplicationBasicChallengeHandler}.
 * <pre>
 * {@code
 * LoginHandler loginHandler = new LoginHandler() {
 *    public PasswordAuthentication getCredentials() {
 *        return new PasswordAuthentication("global", "credentials".toCharArray());
 *    }
 * };
 * ApplicationBasicChallengeHandler  bch = ApplicationBasicChallengeHandler.create();
 * DispatchChallengeHandler dch = DispatchChallengeHandler.create();
 * WebSocketFactory     wsFactory = WebSocketFactory.createWebSocketFactory();
 * wsFactory.setDefaultChallengeHandler(dch.register("ws://localhost:8001",
 *                                                   bch.setLoginHandler(loginHandler)));
 * }
 * </pre>
 *<p>
 * Note that the "Basic" authentication scheme is different from the
 * "Application Basic" scheme. When "Basic" authentication is used, JDK directly
 * uses the system java.net.Authenticator to setup the "Authorization" header.
 * <p>
 * @see <a href="http://tools.ietf.org/html/rfc2616">RFC 2616 - HTTP 1.1</a>
 * @see <a href="http://tools.ietf.org/html/rfc2617#section-2">RFC 2617 Section 2 - Basic Authentication</a>
 */
public abstract class ApplicationBasicChallengeHandler extends ChallengeHandler {

    /**
     * Creates a new instance of {@link ApplicationBasicChallengeHandler} using the
     * {@link ServiceLoader} API with the implementation specified under
     * META-INF/services.
     *
     * @return ApplicationBasicChallengeHandler
     */
    public static ApplicationBasicChallengeHandler create() {
        return create(ApplicationBasicChallengeHandler.class);
    }

    /**
     * Creates a new instance of {@link ApplicationBasicChallengeHandler} with the
     * specified {@link ClassLoader} using the {@link ServiceLoader} API with
     * the implementation specified under META-INF/services.
     *
     * @param  classLoader          ClassLoader to be used to instantiate
     * @return ApplicationBasicChallengeHandler
     */
    public static ApplicationBasicChallengeHandler create(ClassLoader classLoader) {
        return create(ApplicationBasicChallengeHandler.class, classLoader);
    }

    /**
     * Set a Login Handler to be used if and only if a challenge request has
     * a realm parameter matching the provided realm.
     *
     * @param realm  the realm upon which to apply the {@code loginHandler}.
     * @param loginHandler the login handler to use for the provided realm.
     */
    public abstract void setRealmLoginHandler(String realm, LoginHandler loginHandler);


    /**
     * Provide a general (non-realm-specific) login handler to be used in
     * association with this challenge handler. The login handler is used to assist
     * in obtaining credentials to respond to any Basic challenge requests when
     * no realm-specific login handler matches the realm parameter of the request
     * (if any).
     *
     * @param loginHandler a login handler for credentials.
     * @return ApplicationBasicChallengeHandler
     */
    public abstract ApplicationBasicChallengeHandler setLoginHandler(LoginHandler loginHandler);

    /**
     * Get the general (non-realm-specific) login handler associated with this
     * challenge handler. A login handler is used to assist in obtaining credentials
     * to respond to challenge requests.
     *
     * @return a login handler to assist in providing credentials, or {@code null}
     *         if none has been established yet.
     */
    public abstract LoginHandler getLoginHandler() ;
}
