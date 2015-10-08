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
package org.kaazing.netx;

import java.io.IOException;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Collection;
import java.util.ServiceLoader;

import javax.annotation.Resource;

/**
 * {@code URLConnectionHelperSpi} provides behavior that can be registered with {@link URLConnectionHelper} and used to
 * open a {@link URI} by protocol scheme.
 *
 * Concrete {@code URLConnectionHelperSpi} subclass implementations are discovered using the {@link ServiceLoader} facility.
 *
 * The {@code URLConnectionHelper} is injected into {@code URLConnectionHelperSpi} implementations using a public non-static
 * method annotated with the {@link Resource} annotation.
 */
public abstract class URLConnectionHelperSpi {

    /**
     * Returns the list of protocol schemes supported by this {@code URLConnectionHelperSpi} service implementation.
     *
     * @return the list of supported protocol schemes
     */
    public abstract Collection<String> getSupportedProtocols();

    /**
     * Opens a connection to a {@code URI} with behavior provided by this {@code URLConnectionHelperSpi}.
     *
     * @param location the location to open
     *
     * @return the opened {@code URLConnection}
     *
     * @throws IOException if an I/O error occurs while opening the connection
     */
    public abstract URLConnection openConnection(URI location) throws IOException;

    /**
     * Creates a new {@code URLStreamHandler} that can open connections to a {@code URI} with behavior
     * provided by this {@code URLConnectionHelperSpi}.
     *
     * @return a new {@code URLStreamHandler}
     *
     * @throws IOException if an I/O error occurs while opening the connection
     */
    public abstract URLStreamHandler newStreamHandler() throws IOException;

    /**
     * Needed by service implementation subclasses.
     */
    protected URLConnectionHelperSpi() {
    }
}
