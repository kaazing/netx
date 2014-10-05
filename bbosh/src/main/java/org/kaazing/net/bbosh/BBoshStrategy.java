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

import static java.lang.Character.toLowerCase;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BBoshStrategy {

    public static enum Kind {
        POLLING,
        LONG_POLLING
    }

    public abstract Kind getKind();

    public abstract int getRequests();

    public static BBoshStrategy valueOf(String strategy) {

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
            case 'l':
                Matcher longPollingMatcher = LongPolling.PATTERN.matcher(strategy);
                if (longPollingMatcher.matches()) {
                    int interval = parseInt(longPollingMatcher.group(1));
                    TimeUnit intervalUnit = SECONDS;
                    int requests = parseInt(unlessNonNull(longPollingMatcher.group(1), "2"));
                    return new LongPolling(interval, intervalUnit, requests);
                }
                break;
            default:
                break;
            }
        }

        throw new IllegalArgumentException(strategy);
    }

    private static <T> T unlessNonNull(T maybeNull, T defaultValue) {
        return (maybeNull != null) ? maybeNull : defaultValue;
    }

    public static final class Polling extends BBoshStrategy {

        private static final Pattern PATTERN = Pattern.compile("polling;interval=([0-9]+)(s)");

        private final int interval;
        private final TimeUnit intervalUnit;

        public Polling(int interval, TimeUnit intervalUnit) {
            this.interval = interval;
            this.intervalUnit = intervalUnit;
        }

        @Override
        public Kind getKind() {
            return Kind.POLLING;
        }

        @Override
        public int getRequests() {
            return 1;
        }

        public String toString() {
            return format("polling;interval=%d%s", interval, toLowerCase(intervalUnit.name().charAt(0)));
        }
    }

    public static final class LongPolling extends BBoshStrategy {

        private static final Pattern PATTERN = Pattern.compile("long-polling;interval=([0-9]+)(s)(:?;request=([0-9]+))");

        private final int interval;
        private final TimeUnit intervalUnit;
        private final int requests;

        public LongPolling(int interval, TimeUnit intervalUnit, int requests) {
            this.interval = interval;
            this.intervalUnit = intervalUnit;
            this.requests = requests;
        }

        @Override
        public Kind getKind() {
            return Kind.LONG_POLLING;
        }

        @Override
        public int getRequests() {
            return requests;
        }

        public String toString() {
            char intervalUnitChar = toLowerCase(intervalUnit.name().charAt(0));
            if (requests == 2) {
                return format("long-polling;interval=%d%s", interval, intervalUnitChar);
            }
            else {
                return format("long-polling;interval=%d%s;requests=%d", interval, intervalUnitChar, requests);
            }
        }
    }
}
