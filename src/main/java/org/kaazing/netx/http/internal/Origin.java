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

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

/**
 * Internal class. The bridge uses this class to determine the HTTP Origin based on the class loader.
 */
public final class Origin {

    private static String ORIGIN;

    public static String get() throws IOException {

        if (ORIGIN == null) {
            ClassLoader cl = Origin.class.getClassLoader();
            String origin = null;

            if (cl instanceof URLClassLoader) {
                @SuppressWarnings("resource")
                URLClassLoader ucl = (URLClassLoader) cl;

                URL[] urls = ucl.getURLs();
                if (urls.length == 1) {
                    origin = asOrigin(urls[0]);
                }
                else {
                    for (URL url : urls) {
                        try (URLClassLoader candidate = new URLClassLoader(new URL[] { url })) {
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
    static String asOrigin(URL url) throws IOException {
        if ("jar".equals(url.getProtocol())) {
            String file = url.getFile();
            int endAt = file.indexOf("!/");
            if (endAt != -1) {
                file = file.substring(0, endAt);
            }
            url = new URL(file);
        }

        if ("file".equals(url.getProtocol())) {
            return "null";
        }

        String protocol = url.getProtocol();
        String host = url.getHost();
        int port = url.getPort();
        if (port == -1) {
            port = url.getDefaultPort();
        }
        return format("%s://%s:%d", protocol, host, port);
    }

    private Origin() {
        // utility
    }
}

