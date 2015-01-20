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

import static java.lang.String.format;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

/**
 * Internal class. The bridge uses this class to determine the HTTP Origin based on the class loader.
 */
public final class Origin {

    private static String ORIGIN;

    private static final Method CLOSE_URL_CLASS_LOADER;

    public static String get() {

        if (ORIGIN == null) {
            ClassLoader cl = Origin.class.getClassLoader();
            String origin = null;

            if (cl instanceof URLClassLoader) {
                URLClassLoader ucl = (URLClassLoader) cl;

                URL[] urls = ucl.getURLs();
                for (URL url : urls) {
                    URLClassLoader candidate = new URLClassLoader(new URL[] { url });

                    try {
                        Class<?> candidateClass = candidate.loadClass(Origin.class.getName());
                        assert candidateClass != null;
                        if (Arrays.equals(Origin.class.getSigners(), candidateClass.getSigners())) {
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

    private Origin() {
        // utility
    }

    static {
        Method close = null;
        try {
            close = URLClassLoader.class.getDeclaredMethod("close");
        }
        catch (Exception e) {
            // ignore
        }
        CLOSE_URL_CLASS_LOADER = close;
    }
}

