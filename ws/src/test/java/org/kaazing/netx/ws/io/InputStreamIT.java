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
package org.kaazing.netx.ws.io;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.rules.RuleChain.outerRule;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.kaazing.k3po.junit.annotation.Specification;
import org.kaazing.k3po.junit.rules.K3poRule;
import org.kaazing.netx.URLConnectionHelper;
import org.kaazing.netx.ws.WsURLConnection;

public class InputStreamIT {
    private final K3poRule k3po = new K3poRule().setScriptRoot("org/kaazing/netx/ws/io");

    private final TestRule timeout = new DisableOnDebug(new Timeout(5, SECONDS));

    @Rule
    public final TestRule chain = outerRule(k3po).around(timeout);

    @Test
    @Specification({
    "binary.frame.length.bytes.split.across.chunks/handshake.response.and.frame" })
    public void shouldReadBinaryFrameWithLengthBytesSplitAcrossChunks() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        connection.setMaxFramePayloadLength(8188);

        InputStream input = connection.getInputStream();
        OutputStream output = connection.getOutputStream();

        try {
            byte[] readBytes1 = new byte[8185];
            int offset = 0;
            int length = readBytes1.length;
            int bytesRead = 0;

            while ((bytesRead != -1) && (length > 0)) {
                bytesRead = input.read(readBytes1, offset, length);
                if (bytesRead != -1) {
                    offset += bytesRead;
                    length -= bytesRead;
                }
            }

            assert offset == readBytes1.length;

            byte[] readBytes2 = new byte[150];
            length = readBytes2.length;
            offset = 0;
            bytesRead = 0;

            while ((bytesRead != -1) && (length > 0)) {
                bytesRead = input.read(readBytes2, offset, length);
                if (bytesRead != -1) {
                    offset += bytesRead;
                    length -= bytesRead;
                }
            }

            assert offset == readBytes2.length;

            output.write(readBytes1);
            output.write(readBytes2);
        }
        finally {
            k3po.finish();
        }
    }

    @Test
    @Specification({
    "binary.frame.length.indicator.in.chunk1.length.bytes.in.chunk2/handshake.response.and.frame" })
    public void shouldReadBinaryFrameWithLengthBytesFromNextChunk() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        connection.setMaxFramePayloadLength(8188);

        InputStream input = connection.getInputStream();
        OutputStream output = connection.getOutputStream();

        try {
            byte[] readBytes1 = new byte[8186];
            int offset = 0;
            int length = readBytes1.length;
            int bytesRead = 0;

            while ((bytesRead != -1) && (length > 0)) {
                bytesRead = input.read(readBytes1, offset, length);
                if (bytesRead != -1) {
                    offset += bytesRead;
                    length -= bytesRead;
                }
            }

            assert offset == readBytes1.length;

            byte[] readBytes2 = new byte[150];
            length = readBytes2.length;
            offset = 0;
            bytesRead = 0;

            while ((bytesRead != -1) && (length > 0)) {
                bytesRead = input.read(readBytes2, offset, length);
                if (bytesRead != -1) {
                    offset += bytesRead;
                    length -= bytesRead;
                }
            }

            assert offset == readBytes2.length;

            output.write(readBytes1);
            output.write(readBytes2);
        }
        finally {
            k3po.finish();
        }
    }

    @Test
    @Specification({
    "binary.frame.metadata.in.chunk1.payload.in.chunk2/handshake.response.and.frame" })
    public void shouldReadBinaryFrameWithMetadataAndPayloadInDifferentChunks() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        connection.setMaxFramePayloadLength(8188);

        InputStream input = connection.getInputStream();
        OutputStream output = connection.getOutputStream();

        try {
            byte[] readBytes1 = new byte[8184];
            int offset = 0;
            int length = readBytes1.length;
            int bytesRead = 0;

            while ((bytesRead != -1) && (length > 0)) {
                bytesRead = input.read(readBytes1, offset, length);
                if (bytesRead != -1) {
                    offset += bytesRead;
                    length -= bytesRead;
                }
            }

            assert offset == readBytes1.length;

            byte[] readBytes2 = new byte[150];
            length = readBytes2.length;
            offset = 0;
            bytesRead = 0;

            while ((bytesRead != -1) && (length > 0)) {
                bytesRead = input.read(readBytes2, offset, length);
                if (bytesRead != -1) {
                    offset += bytesRead;
                    length -= bytesRead;
                }
            }

            assert offset == readBytes2.length;

            output.write(readBytes1);
            output.write(readBytes2);
        }
        finally {
            k3po.finish();
        }
    }

    @Test
    @Specification({
    "binary.frame.opcode.in.chunk1.length.in.chunk2/handshake.response.and.frame" })
    public void shouldReadBinaryFrameWithOpcodeAndLengthBytesFromDifferentChunks() throws Exception {
        URLConnectionHelper helper = URLConnectionHelper.newInstance();
        URI location = URI.create("ws://localhost:8080/path");

        WsURLConnection connection = (WsURLConnection) helper.openConnection(location);
        connection.setMaxFramePayloadLength(8188);

        InputStream input = connection.getInputStream();
        OutputStream output = connection.getOutputStream();

        try {
            byte[] readBytes1 = new byte[8187];
            int offset = 0;
            int length = readBytes1.length;
            int bytesRead = 0;

            while ((bytesRead != -1) && (length > 0)) {
                bytesRead = input.read(readBytes1, offset, length);
                if (bytesRead != -1) {
                    offset += bytesRead;
                    length -= bytesRead;
                }
            }

            assert offset == readBytes1.length;

            byte[] readBytes2 = new byte[150];
            length = readBytes2.length;
            offset = 0;
            bytesRead = 0;

            while ((bytesRead != -1) && (length > 0)) {
                bytesRead = input.read(readBytes2, offset, length);
                if (bytesRead != -1) {
                    offset += bytesRead;
                    length -= bytesRead;
                }
            }

            assert offset == readBytes2.length;

            output.write(readBytes1);
            output.write(readBytes2);
        }
        finally {
            k3po.finish();
        }
    }
}
