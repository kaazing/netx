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

package org.kaazing.netx.http.auth;


import java.util.Collection;
import java.util.ServiceLoader;

/**
 * An ApplicationNegotiableChallengeHandler can be used to directly respond to
 * "Application Negotiate" challenges, and in addition, can be used indirectly in conjunction
 * with a {@link ApplicationNegotiateChallengeHandler}
 * to assist in the construction of a challenge response using object identifiers.
 *<p>
 * Note that the "Negotiate" authentication scheme is different from the
 * "Application Negotiate" scheme. When "Negotiate" authentication is used, JDK directly
 * uses the system java.net.Authenticator to setup the "Authorization" header.
 * <p>
 *
 * @see <a href="http://tools.ietf.org/html/rfc4178#section-4.2.1">RFC 4178 Section 4.2.1</a> for details
 *      about how the supported object identifiers contribute towards the initial context token in the challenge response.
 *
 * <p/>
 *
 */
public abstract class ApplicationNegotiableChallengeHandler extends ChallengeHandler {

    /**
     * Creates a new instance of {@link ApplicationNegotiableChallengeHandler} using
     * the {@link ServiceLoader} API with the implementation specified under
     * META-INF/services.
     *
     * @return NegotiableChallengeHandler
     */
    public static ApplicationNegotiableChallengeHandler create() {
        return create(ApplicationNegotiableChallengeHandler.class);
    }

    /**
     * Creates a new instance of {@link ApplicationNegotiableChallengeHandler} with
     * the specified {@link ClassLoader} using the {@link ServiceLoader} API with
     * the implementation specified under META-INF/services.
     *
     * @param  classLoader          ClassLoader to be used to instantiate
     * @return NegotiableChallengeHandler
     */
    public static ApplicationNegotiableChallengeHandler create(ClassLoader classLoader) {
        return create(ApplicationNegotiableChallengeHandler.class, classLoader);
    }

    /**
     * Default constructor.
     */
    protected ApplicationNegotiableChallengeHandler() {
    }

    /**
     * Return a collection of string representations of object identifiers
     * supported by this challenge handler implementation, in dot-separated notation.
     * For example, {@literal 1.3.5.1.5.2}.
     *
     * @return a collection of string representations of object identifiers
     *         supported by this challenge handler implementation.
     */
    public abstract Collection<String> getSupportedOids();

    /**
     * Provide a general login handler to be used in association with this challenge handler.
     * The login handler is used to assist in obtaining credentials to respond to any
     * challenge requests when this challenge handler handles the request.
     *
     * @param loginHandler a login handler for credentials.
     *
     * @return this challenge handler object, to support chained calls
     */
    public abstract ApplicationNegotiableChallengeHandler setLoginHandler(LoginHandler loginHandler);

    /**
     * Get the general login handler associated with this challenge handler.
     * A login handler is used to assist in obtaining credentials to respond to challenge requests.
     *
     * @return a login handler to assist in providing credentials, or {@code null} if none has been established yet.
     */
    public abstract LoginHandler getLoginHandler() ;
}
