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

package org.kaazing.netx;

import static java.lang.Character.isUpperCase;
import static java.util.Collections.unmodifiableMap;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

public final class URLConnectionHelper {

    private final Map<String, URLConnectionHelperSpi> helpers;

    public URL toURL(URI location) throws IOException {
        String scheme = location.getScheme();
        URLConnectionHelperSpi helper = helpers.get(scheme);
        if (helper != null) {
            URLStreamHandler handler = helper.newStreamHandler();
            return new URL(null, location.toString(), handler);
        }
        return location.toURL();
    }

    public URLConnection openConnection(URI location) throws IOException {
        String scheme = location.getScheme();
        URLConnectionHelperSpi helper = helpers.get(scheme);
        if (helper != null) {
            return helper.openConnection(location);
        }
        return location.toURL().openConnection();
    }

    public static URLConnectionHelper newInstance() {
        Class<URLConnectionHelperSpi> clazz = URLConnectionHelperSpi.class;
        ServiceLoader<URLConnectionHelperSpi> loader = ServiceLoader.load(clazz);
        return newInstance(loader);
    }

    public static URLConnectionHelper newInstance(ClassLoader cl) {
        Class<URLConnectionHelperSpi> clazz = URLConnectionHelperSpi.class;
        ServiceLoader<URLConnectionHelperSpi> loader = ServiceLoader.load(clazz, cl);
        return newInstance(loader);
    }

    private URLConnectionHelper(Map<String, URLConnectionHelperSpi> helpers) {
        this.helpers = helpers;
    }

    private static URLConnectionHelper newInstance(ServiceLoader<URLConnectionHelperSpi> loader) {
        Map<String, URLConnectionHelperSpi> helpers = new HashMap<String, URLConnectionHelperSpi>();

        for (URLConnectionHelperSpi factory : loader) {
            Collection<String> protocols = factory.getSupportedProtocols();

            if (protocols != null && !protocols.isEmpty()) {
                for (String protocol : protocols) {
                    helpers.put(protocol, factory);
                }
            }
        }

        URLConnectionHelper helper = new URLConnectionHelper(unmodifiableMap(helpers));

        for (URLConnectionHelperSpi factory : helpers.values()) {
            inject(factory, URLConnectionHelper.class, helper);
        }

        return helper;
    }

    private static <T> void inject(Object target, Class<T> type, T instance) {
        try {
            Class<? extends Object> targetClass = target.getClass();
            Method[] targetMethods = targetClass.getMethods();
            for (Method targetMethod : targetMethods) {
                String targetMethodName = targetMethod.getName();
                if (targetMethodName.length() == 3 && "set".equals(targetMethodName) ||
                        targetMethodName.length() > 3 && targetMethodName.startsWith("set") &&
                        isUpperCase(targetMethodName.charAt(3))) {
                    Class<?>[] targetMethodParameterTypes = targetMethod.getParameterTypes();
                    if (targetMethodParameterTypes.length == 1 && targetMethodParameterTypes[0] == type) {
                        try {
                            targetMethod.invoke(target, instance);
                        }
                        catch (IllegalArgumentException e) {
                            // failed to inject
                        }
                        catch (IllegalAccessException e) {
                            // failed to inject
                        }
                        catch (InvocationTargetException e) {
                            // failed to inject
                        }
                    }
                }
            }
        }
        catch (SecurityException e) {
            // failed to reflect
        }
    }

}
