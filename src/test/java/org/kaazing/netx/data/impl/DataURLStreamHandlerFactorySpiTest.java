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

package org.kaazing.netx.data.impl;

import static org.junit.Assert.assertEquals;
import static org.kaazing.netx.URLFactory.createURL;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import org.junit.Test;

public class DataURLStreamHandlerFactorySpiTest {

    @Test
    public void shouldCreateImagePngBase64DataURL() throws IOException {
        // see http://en.wikipedia.org/wiki/Data_URI_scheme#Examples
        URL url = createURL("data:image/png;charset=US-ASCII;base64," +
                                 "iVBORw0KGgoAAAANSUhEUgAAAAUAAAAFCAYAAACNbyblAAAAHElEQVQI12" +
                                 "P4//8/w38GIAXDIBKE0DHxgljNBAAO9TXL0Y4OHwAAAABJRU5ErkJggg==");

        URLConnection connection = url.openConnection();
        assertEquals("image/png", connection.getContentType());
        assertEquals(85, connection.getContentLength());
    }
}
