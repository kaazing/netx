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

import java.net.URL;

class HttpUpgradeableURLConnectionImpl extends HttpURLConnectionImpl {

    private static final String HEADER_UPGRADE = "Upgrade";

    public HttpUpgradeableURLConnectionImpl(URL url) {
        super(url);
    }

    @Override
    public void setRequestProperty(String key, String value) {
        detectUpgradeRequestHeader(key);
        super.setRequestProperty(key, value);
    }

    @Override
    public void addRequestProperty(String key, String value) {
        detectUpgradeRequestHeader(key);
        super.addRequestProperty(key, value);
    }

    private void detectUpgradeRequestHeader(String key) {
        if (HEADER_UPGRADE.equalsIgnoreCase(key) && !(connection(false) instanceof HttpUpgradeURLConnectionImpl)) {
            // "Upgrade" header requires Upgrade-capable HttpURLConnection
            connection = new HttpUpgradeURLConnectionImpl(url, connection(false));
        }
    }

}
