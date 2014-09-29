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

package org.kaazing.net.impl.data;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Collections.singleton;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kaazing.net.URLStreamHandlerFactorySpi;

public final class DataURLStreamHandlerFactorySpi extends URLStreamHandlerFactorySpi {

    @Override
    public Collection<String> getSupportedProtocols() {
        return singleton("data");
    }

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        if (!"data".equals(protocol)) {
            throw new IllegalArgumentException("Unsupported protocol: " + protocol);
        }
        return new DataURLStreamHandler();
    }

    private static final class DataURLStreamHandler extends URLStreamHandler {

        private static final int BASE64_PADDING_BYTE = 0x3d;

        private static Pattern SPECIFIC_PART_PATTERN = Pattern.compile("([^;,]+)?(?:;charset=([^;,]+))?(?:;(base64))?,(.*)");

        @Override
        protected URLConnection openConnection(URL url) throws IOException {
            String schemeSpecificPart = url.getFile();
            Matcher matcher = SPECIFIC_PART_PATTERN.matcher(schemeSpecificPart);
            if (!matcher.matches()) {
                // see http://en.wikipedia.org/wiki/Data_URI_scheme#Format
                throw new MalformedURLException("Required format: data:[MIME-type][;charset=<encoding>][;base64],<data>");
            }

            String mimeType = matcher.group(1);
            if (mimeType == null) {
                mimeType = "text/plain";
            }
            String charsetName = matcher.group(2);
            Charset charset = (charsetName != null) ? Charset.forName(charsetName) : US_ASCII;
            boolean base64 = "base64".equals(matcher.group(3));
            String data = matcher.group(4);

            byte[] bytes = data.getBytes(charset);
            if (base64) {
                if ((bytes.length & 0x03) != 0) {
                    throw new MalformedURLException("Base64 data requires padding");
                }
                return newBase64DataURLConnection(url, mimeType, bytes);
            }

            return new DataURLConnection(url, mimeType, bytes, 0, bytes.length);
        }

        private static DataURLConnection newBase64DataURLConnection(URL url, String mimeType, byte[] encoded) {
            int length = encoded.length;
            assert (length & 0x03) == 0;

            int padding = 0;
            if (encoded[length - 1] == BASE64_PADDING_BYTE) {
                padding++;
                if (encoded[length - 2] == 0x3d) {
                    padding++;
                }
            }

            byte[] decoded = new byte[3 * length / 4 - padding];
            int decodedOffset = 0;
            for (int encodedOffset = 0; encodedOffset < (encoded.length & 0xfd);) {
                int char0 = encoded[encodedOffset++];
                int char1 = encoded[encodedOffset++];
                int char2 = encoded[encodedOffset++];
                int char3 = encoded[encodedOffset++];

                int byte0 = mapped(char0);
                int byte1 = mapped(char1);
                int byte2 = mapped(char2);
                int byte3 = mapped(char3);

                decoded[decodedOffset++] = (byte) (((byte0 << 2) & 0xfc) | ((byte1 >> 4) & 0x03));
                if (char2 != BASE64_PADDING_BYTE) {
                    decoded[decodedOffset++] = (byte) (((byte1 << 4) & 0xf0) | ((byte2 >> 2) & 0x0f));
                    if (char3 != BASE64_PADDING_BYTE) {
                        decoded[decodedOffset++] = (byte) (((byte2 << 6) & 0xc0) | (byte3 & 0x3f));
                    }
                }
            }

            return new DataURLConnection(url, mimeType, decoded, 0, decodedOffset);
        }

        private static int mapped(int ch) {
            if ((ch & 0x40) != 0) {
                if ((ch & 0x20) != 0) {
                    // a(01100001)-z(01111010) -> 26-51
                    assert ch >= 'a';
                    assert ch <= 'z';
                    return ch - 71;
                } else {
                    // A(01000001)-Z(01011010) -> 0-25
                    assert ch >= 'A';
                    assert ch <= 'Z';
                    return ch - 65;
                }
            } else if ((ch & 0x20) != 0) {
                if ((ch & 0x10) != 0) {
                    if ((ch & 0x08) != 0 && (ch & 0x04) != 0) {
                        // =(00111101) -> 0
                        assert ch == '=';
                        return 0;
                    }
                    else {
                        // 0(00110000)-9(00111001) -> 52-61
                        assert ch >= '0';
                        assert ch <= '9';
                        return ch + 4;
                    }
                } else {
                    if ((ch & 0x04) != 0) {
                        // /(00101111) -> 63
                        assert ch == '/';
                        return 63;
                    } else {
                        // +(00101011) -> 62
                        assert ch == '+';
                        return 62;
                    }
                }
            }
            else {
                throw new IllegalArgumentException("Invalid BASE64 string");
            }
        }
    }

    private static final class DataURLConnection extends URLConnection {

        private final String contentType;
        private final byte[] bytes;
        private final int offset;
        private final int length;

        private DataURLConnection(URL url, String contentType, byte[] bytes, int offset, int length) {
            super(url);
            this.contentType = contentType;
            this.bytes = bytes;
            this.offset = offset;
            this.length = length;
        }

        @Override
        public void connect() throws IOException {
            // no-op, already "connected"
        }

        @Override
        public long getContentLengthLong() {
            return length;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(bytes, offset, length);
        }

    }
}
