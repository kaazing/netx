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

package org.kaazing.netx.ws.internal.util;

import java.net.URI;
import java.net.URL;

import org.kaazing.netx.URLConnectionHelper;
import org.kaazing.netx.ws.WsURLConnection;

public final class SimpleTest {

    private SimpleTest() {
        // utility
    }

    public static void main(String[] args) {
        try {
            URLConnectionHelper helper = URLConnectionHelper.newInstance();
            URI location = URI.create("ws://localhost:8000/echo");
            URL locationURL = helper.toURL(location);

            WsURLConnection conn = (WsURLConnection) locationURL.openConnection();
            conn.connect();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
