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
package org.kaazing.netx.http.internal.auth;

import java.net.PasswordAuthentication;
import java.nio.charset.Charset;
import java.util.Arrays;

import org.kaazing.netx.http.auth.ChallengeHandler;
import org.kaazing.netx.http.auth.ChallengeResponse;
import org.kaazing.netx.http.internal.Base64;

public final class BasicChallengeResponseFactory {

    private static final Charset US_ASCII = Charset.forName("US-ASCII");

    private BasicChallengeResponseFactory() {
    }

    public static ChallengeResponse create(PasswordAuthentication creds, ChallengeHandler nextChallengeHandler) {
        String unencoded = String.format("%s:%s", creds.getUserName(), new String(creds.getPassword()));
        byte[] encoded = Base64.encode(unencoded.getBytes(US_ASCII));
        String response = String.format("Application Basic %s", new String(encoded, US_ASCII));
        Arrays.fill(creds.getPassword(), (char) 0);
        return new ChallengeResponse(response.toCharArray(), nextChallengeHandler);
    }
}
