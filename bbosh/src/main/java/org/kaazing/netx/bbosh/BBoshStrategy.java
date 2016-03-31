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
package org.kaazing.netx.bbosh;

import static java.lang.Character.toLowerCase;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Indicates the strategy to use for BBOSH connections, either {@code POLLING} or {@code STREAMING}.
 */
public abstract class BBoshStrategy {

    /**
     * The BBOSH connection strategy kind.
     */
    public static enum Kind {

        /**
         * The polling BBOSH connection strategy kind.
         */
        POLLING,

        /**
         * The streaming BBOSH connection strategy kind.
         */
        STREAMING
    }

    /**
     * Returns the BBOSH connection strategy kind.
     *
     * @return  the BBOSH connection strategy kind
     */
    public abstract Kind getKind();

    /**
     * Returns the maximum number of concurrent in-flight HTTP requests.
     *
     * @return  the maximum number of concurrent in-flight HTTP requests
     */
    public abstract int getRequests();

    /**
     * Returns the BBOSH connection strategy parsed from string format.
     *
     * @param strategy  the string formatted BBOSH connection strategy
     *
     * @return  the BBOSH connection strategy
     * @throws IllegalArgumentException  if the strategy cannot be parsed
     */
    public static BBoshStrategy valueOf(String strategy) throws IllegalArgumentException {

        if (strategy != null && !strategy.isEmpty()) {
            switch (strategy.charAt(0)) {
            case 'p':
                Matcher pollingMatcher = Polling.PATTERN.matcher(strategy);
                if (pollingMatcher.matches()) {
                    int interval = parseInt(pollingMatcher.group(1));
                    TimeUnit intervalUnit = SECONDS;
                    return new Polling(interval, intervalUnit);
                }
                break;
            case 's':
                Matcher streamingMatcher = Streaming.PATTERN.matcher(strategy);
                if (streamingMatcher.matches()) {
                    return new Streaming();
                }
                break;
            default:
                break;
            }
        }

        throw new IllegalArgumentException(strategy);
    }

    /**
     * The {@code Polling} BBOSH connection strategy.
     *
     * An HTTP request is repeatedly made to the server at a specific interval.  When the client needs to send data to the
     * server, then the HTTP request body is present.  When the server needs to send data to the client, then the response
     * body is present.
     */
    public static final class Polling extends BBoshStrategy {

        private static final Pattern PATTERN = Pattern.compile("polling;interval=([0-9]+)s");

        private final int interval;
        private final TimeUnit intervalUnit;

        /**
         * Creates a new {@code Polling} BBOSH connection strategy.
         *
         * @param interval  the time interval count
         * @param intervalUnit  the time interval unit
         */
        public Polling(int interval, TimeUnit intervalUnit) {
            this.interval = interval;
            this.intervalUnit = intervalUnit;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Kind getKind() {
            return Kind.POLLING;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getRequests() {
            return 1;
        }

        /**
         * Returns a string representation of this BBOSH connection strategy.
         *
         * @return  a string representation such as {@code "polling;interval=30s"}
         */
        public String toString() {
            return format("polling;interval=%d%s", interval, toLowerCase(intervalUnit.name().charAt(0)));
        }
    }

    /**
     * The {@code Streaming} BBOSH connection strategy.
     *
     * An HTTP request is streamed as chunks to the server and the HTTP response is streamed as chunks back to the client.
     * When the client needs to send data to the server, then a new chunk is sent on the HTTP request body.
     * When the server needs to send data to the client, then a new chunk is sent on the HTTP response body.
     */
    public static final class Streaming extends BBoshStrategy {

        private static final Pattern PATTERN = Pattern.compile("streaming;request=chunked");

        /**
         * Creates a new {@code Streaming} BBOSH connections strategy.
         */
        public Streaming() {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Kind getKind() {
            return Kind.STREAMING;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getRequests() {
            return 1;
        }

        /**
         * Returns a string representation of this BBOSH connection strategy.
         *
         * @return  the string representation {@code "streaming;request=chunked"}
         */
        public String toString() {
            return "streaming;request=chunked";
        }
    }

}
