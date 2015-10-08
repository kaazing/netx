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
package org.kaazing.netx.http.internal;

import static java.lang.String.format;
import static java.util.ServiceLoader.load;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

public abstract class HttpOriginSecuritySpi {

    private static final String BRIDGE_RESOURCE_NAME = "netx.http.bridge";
    private static final String BRIDGE_RESOURCE_VERSION = "2.0";
    private static final String BRIDGE_RESOURCE_PATH = format("/;resource/%s/%s", BRIDGE_RESOURCE_NAME, BRIDGE_RESOURCE_VERSION);

    public static HttpOriginSecuritySpi newInstance() {
        return new DefaultOriginSecurity();
    }

    @IgnoreJRERequirement
    public final HttpURLConnection openConnection(URL url) throws IOException {
        try {
            return openConnection0(url);
        }
        catch (SecurityException e) {
            try {
                URL bridge = new URL(url, BRIDGE_RESOURCE_PATH);
                ClassLoader parent = getClass().getClassLoader();
                URLClassLoader loader = URLClassLoader.newInstance(new URL[] { bridge }, parent);
                for (HttpOriginSecuritySpi security : load(HttpOriginSecuritySpi.class, loader)) {
                    return security.openConnection(url);
                }
                String message = format("%s not found: %s", BRIDGE_RESOURCE_NAME, bridge);
                e.addSuppressed(new IllegalStateException(message).fillInStackTrace());
                throw e;
            }
            catch (Exception e0) {
                if (e0 != e) {
                    e.addSuppressed(e0);
                }
                throw e;
            }
        }
    }

    public final Socket createSocket(URL url) throws IOException {
        try {
            return createSocket0(url);
        }
        catch (SecurityException e) {
            try {
                URL bridge = new URL(url, BRIDGE_RESOURCE_PATH);
                ClassLoader parent = HttpOriginSecuritySpi.class.getClassLoader();
                URLClassLoader loader = URLClassLoader.newInstance(new URL[] { bridge }, parent);
                for (HttpOriginSecuritySpi security : load(HttpOriginSecuritySpi.class, loader)) {
                    return security.createSocket(url);
                }
                String message = format("%s not found: %s", BRIDGE_RESOURCE_NAME, bridge);
                e.initCause(new IllegalStateException(message).fillInStackTrace());
                throw e;
            }
            catch (Exception e0) {
                e.initCause(e0);
                throw e;
            }
        }
    }

    public static final String getOrigin() {

        if (ORIGIN == null) {
            Class<HttpOriginSecuritySpi> clazz = HttpOriginSecuritySpi.class;
            ClassLoader cl = clazz.getClassLoader();
            String origin = null;

            if (cl instanceof URLClassLoader) {
                @SuppressWarnings("resource")
                URLClassLoader ucl = (URLClassLoader) cl;

                URL[] urls = ucl.getURLs();
                for (URL url : urls) {
                    URLClassLoader candidate = new URLClassLoader(new URL[] { url });

                    try {
                        Class<?> candidateClass = candidate.loadClass(clazz.getName());
                        assert candidateClass != null;
                        if (Arrays.equals(clazz.getSigners(), candidateClass.getSigners())) {
                            origin = asOrigin(url);
                            break;
                        }
                    }
                    catch (ClassNotFoundException e) {
                        // ignore, try next candidate
                    }

                    // clean up in Java7+
                    if (CLOSE_URL_CLASS_LOADER != null) {
                        try {
                            CLOSE_URL_CLASS_LOADER.invoke(candidate);
                        }
                        catch (Exception e) {
                            // ignore
                        }
                    }
                }
            }

            if (origin == null) {
                origin = "null";
            }

            ORIGIN = origin;
        }

        return ORIGIN;
    }

    private static String ORIGIN;

    private static final Method CLOSE_URL_CLASS_LOADER;

    // unit tests
    static String asOrigin(URL url) {
        URI uri = URI.create(url.toString());

        if ("jar".equals(uri.getScheme())) {
            String schemeSpecificPart = uri.getSchemeSpecificPart();
            int endAt = schemeSpecificPart.indexOf("!/");
            if (endAt != -1) {
                schemeSpecificPart = schemeSpecificPart.substring(0, endAt);
            }
            uri = URI.create(schemeSpecificPart);
        }

        if ("file".equals(uri.getScheme())) {
            return "null";
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort();

        return port != -1 ? format("%s://%s:%d", scheme, host, port) : format("%s://%s", scheme, host);
    }

    protected abstract HttpURLConnection openConnection0(URL url) throws IOException;

    protected abstract Socket createSocket0(URL url) throws IOException;

    private static final class DefaultOriginSecurity extends HttpOriginSecuritySpi {

        @Override
        protected HttpURLConnection openConnection0(URL url) throws IOException {

            URL connectionURL = new URL(url.toString());

            final String connectionHost = connectionURL.getHost();
            int connectionPort = connectionURL.getPort();
            if (connectionPort == -1) {
                connectionPort = connectionURL.getDefaultPort();
            }

            SecurityManager security = System.getSecurityManager();
            if (security != null) {
                security.checkConnect(connectionHost, connectionPort);
            }

            return (HttpURLConnection) connectionURL.openConnection();
        }

        @Override
        protected Socket createSocket0(URL url) throws IOException {
            String protocol = url.getProtocol();
            String host = url.getHost();
            int port = url.getPort();
            if (port == -1) {
                port = url.getDefaultPort();
            }

            if ("http".equalsIgnoreCase(protocol)) {
                SocketFactory socketFactory = SocketFactory.getDefault();
                return socketFactory.createSocket(host, port);
            }
            else if ("https".equalsIgnoreCase(protocol)) {
                SocketFactory socketFactory = SSLSocketFactory.getDefault();
                return socketFactory.createSocket(host, port);
            }
            else {
                throw new IllegalStateException(format("Unexpected protocol: %s", protocol));
            }

        }

    }

    static {
        Method close = null;
        try {
            // note: access denied in Applet (!)
            close = URLClassLoader.class.getDeclaredMethod("close");
        }
        catch (Exception e) {
            // ignore
        }
        CLOSE_URL_CLASS_LOADER = close;
    }
}
