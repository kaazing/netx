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

package org.kaazing.netx.http.bridge.itest.internal;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import java.applet.Applet;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;

import org.kaazing.netx.URLConnectionHelper;

@SuppressWarnings("serial")
public class TestApplet extends Applet {

    @Override
    public void init() {

    }

    @Override
    public void start() {
        try {
            String location = getParameter("location");
            requireNonNull(location, "Missing Applet parameter \"location\"");
            System.out.println(format("Applet parameter \"location\" = %s", location));

            URI locationURI = URI.create(location);
            URLConnectionHelper helper = URLConnectionHelper.newInstance();

            HttpURLConnection connection = (HttpURLConnection) helper.openConnection(locationURI);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("X-Header", "value");
            connection.setDoOutput(true);
            int responseCode = connection.getResponseCode();
            System.out.println("REQUEST URL " + locationURI);
            System.out.println("RESPONSE CODE " + responseCode);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

}
