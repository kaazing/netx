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

package org.kaazing.netx.bbosh.internal;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import org.kaazing.netx.bbosh.BBoshStrategy;

final class BBoshSocketFactory {

    private final URL factoryURL;
    private final int initialSequenceNo;

    BBoshSocketFactory(URL factoryURL) throws IOException {
        this(factoryURL, 0);
    }

    BBoshSocketFactory(
        URL factoryURL,
        int initialSequenceNo) throws IOException {

        this.factoryURL = factoryURL;
        this.initialSequenceNo = initialSequenceNo;
    }

    BBoshSocket createSocket(List<BBoshStrategy> strategies, int timeout) throws IOException {

        HttpURLConnection connection = (HttpURLConnection) factoryURL.openConnection();
        connection.setConnectTimeout(timeout);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Accept", "application/octet-stream");
        connection.setRequestProperty("X-Protocol", "bbosh/1.0");
        connection.setRequestProperty("X-Sequence-No", Integer.toString(initialSequenceNo));

        for (BBoshStrategy acceptStrategy : strategies) {
            connection.addRequestProperty("X-Accept-Strategy", acceptStrategy.toString());
        }

        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.getOutputStream().close();

        switch (connection.getResponseCode()) {
        case 201:
            try {
                String location = connection.getHeaderField("Location");
                if (location == null) {
                    throw new IOException("Connection failed");
                }
                URL instanceURL = new URL(factoryURL, location);
                String strategy = connection.getHeaderField("X-Strategy");
                BBoshStrategy negotiatedStrategy = BBoshStrategy.valueOf(strategy);
                switch (negotiatedStrategy.getKind()) {
                case POLLING:
                    return new BBoshPollingSocket(instanceURL, initialSequenceNo + 1, negotiatedStrategy);
                case STREAMING:
                    return new BBoshStreamingSocket(instanceURL, initialSequenceNo + 1, negotiatedStrategy);
                }
            }
            catch (IllegalArgumentException e) {
                throw new IOException("Connection failed", e);
            }
        default:
            throw new IOException("Connection failed");
        }
    }
}
