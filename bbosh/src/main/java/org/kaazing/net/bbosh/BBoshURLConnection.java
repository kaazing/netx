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

package org.kaazing.net.bbosh;

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.Closeable;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import org.kaazing.net.bbosh.BBoshStrategy.LongPolling;
import org.kaazing.net.bbosh.BBoshStrategy.Polling;

public abstract class BBoshURLConnection extends URLConnection implements Closeable {

    private final List<BBoshStrategy> supportedStrategies;
    private BBoshStrategy negotiatedStrategy;

    protected BBoshURLConnection(URL url) {
        super(url);
        supportedStrategies = asList(new Polling(5, SECONDS), new LongPolling(30, SECONDS, 2));
    }

    public void setSupportedStrategies(List<BBoshStrategy> strategies) {
        supportedStrategies.clear();
        supportedStrategies.addAll(strategies);
    }

    public BBoshStrategy getNegotiatedStrategy() {
        return negotiatedStrategy;
    }

    protected List<BBoshStrategy> getSupportedStrategies() {
        return supportedStrategies;
    }

    protected final void setNegotiatedStrategy(BBoshStrategy negotiatedStrategy) {
        this.negotiatedStrategy = negotiatedStrategy;
    }

}