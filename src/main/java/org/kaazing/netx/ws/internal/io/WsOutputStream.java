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

package org.kaazing.netx.ws.internal.io;

import static java.lang.Integer.highestOneBit;
import static java.lang.String.format;

import java.io.FilterOutputStream;
import java.io.IOException;

import org.kaazing.netx.ws.internal.WsURLConnectionImpl;
import org.kaazing.netx.ws.internal.WsURLConnectionImpl.ReadyState;

public final class WsOutputStream extends FilterOutputStream {
    private static final byte[] EMPTY_MASK = new byte[] {0x00, 0x00, 0x00, 0x00};

    private final byte[] mask;
    private final WsURLConnectionImpl connection;

    public WsOutputStream(WsURLConnectionImpl connection) throws IOException {
        super(connection.getTcpOutputStream());
        this.connection = connection;
        this.mask = new byte[4];
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[] { (byte) b });
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(0x82);

        encodePayloadLength(len);

        connection.getRandom().nextBytes(mask);
        out.write(mask);

        byte[] masked = new byte[len];
        for (int i = 0; i < len; i++) {
            int ioff = off + i;
            masked[i] = (byte) (b[ioff] ^ mask[i % mask.length]);
        }

        out.write(masked);
    }

    public void writeClose(int code, byte[] reason) throws IOException {
        if (connection.getReadyState() == ReadyState.CLOSED) {
            throw new IOException("Connection closed");
        }

        int len = 0;

        if (code != 0) {
            switch (code) {
            case 1000:
            case 1001:
            case 1002:
            case 1003:
            case 1007:
            case 1008:
            case 1009:
            case 1010:
            case 1011:
            case 1005:
                len += 2;
                break;
            default:
                if ((code >= 3000) && (code <= 4999)) {
                    len += 2;
                }

                throw new IOException(format("Invalid CLOSE code %d", code));
            }

            if (reason != null) {
                // By this time, the reson.length being smaller than 123 must be validated. So, no need to repeat the check.
                len += reason.length;
            }
        }

        out.write(0x88);

        encodePayloadLength(len);

        if (len == 0) {
            out.write(EMPTY_MASK);
        }
        else {
            assert len >= 2;

            connection.getRandom().nextBytes(mask);
            out.write(mask);

            byte[] masked = new byte[len];
            for (int i = 0; i < len; i++) {
                switch (i) {
                case 0:
                    masked[i] = (byte) (((code >> 8) & 0xFF) ^ mask[i % mask.length]);
                    break;
                case 1:
                    masked[i] = (byte) (((code >> 0) & 0xFF) ^ mask[i % mask.length]);
                    break;
                default:
                    masked[i] = (byte) (reason[i - 2] ^ mask[i % mask.length]);
                    break;
                }
            }

            out.write(masked);

            // ### TODO: Check if there are better alternatives.
            // Give opportunity to the server to complete the CLOSE handshake by sleeping for 100ms. Without this K3PO is
            // complaints as the client closes the connection after sending the CLOSE frame.
//            try {
//                Thread.sleep(100);
//            } catch (InterruptedException e) {
//                // TODO Auto-generated catch block
//                e.printStackTrace();
//            }
        }
    }

    public void writePing(byte[] payload) throws IOException {
        out.write(0x89);

        int len = payload == null ? 0 : payload.length;
        encodePayloadLength(len);

        encodePayload(payload);
    }

    public void writePong(byte[] payload) throws IOException {
        out.write(0x8A);

        int len = payload == null ? 0 : payload.length;
        encodePayloadLength(len);

        encodePayload(payload);
    }

    private void encodePayloadLength(int len) throws IOException {
        switch (highestOneBit(len)) {
        case 0x0000:
        case 0x0001:
        case 0x0002:
        case 0x0004:
        case 0x0008:
        case 0x0010:
        case 0x0020:
            out.write(0x80 | len);
            break;
        case 0x0040:
            switch (len) {
            case 126:
                out.write(0x80 | 126);
                out.write(0x00);
                out.write(126);
                break;
            case 127:
                out.write(0x80 | 126);
                out.write(0x00);
                out.write(127);
                break;
            default:
                out.write(0x80 | len);
                break;
            }
            break;
        case 0x0080:
        case 0x0100:
        case 0x0200:
        case 0x0400:
        case 0x0800:
        case 0x1000:
        case 0x2000:
        case 0x4000:
        case 0x8000:
            out.write(0x80 | 126);
            out.write((len >> 8) & 0xff);
            out.write((len >> 0) & 0xff);
            break;
        default:
            // 65536+
            out.write(0x80 | 127);

            long length = len;
            out.write((int) ((length >> 56) & 0xff));
            out.write((int) ((length >> 48) & 0xff));
            out.write((int) ((length >> 40) & 0xff));
            out.write((int) ((length >> 32) & 0xff));
            out.write((int) ((length >> 24) & 0xff));
            out.write((int) ((length >> 16) & 0xff));
            out.write((int) ((length >> 8) & 0xff));
            out.write((int) ((length >> 0) & 0xff));
            break;
        }
    }

    private void encodePayload(byte[] payload) throws IOException {
        if (payload == null) {
            out.write(EMPTY_MASK);
            return;
        }

        connection.getRandom().nextBytes(mask);
        out.write(mask);

        byte[] masked = new byte[payload.length];
        for (int i = 0; i < payload.length; i++) {
            masked[i] = (byte) (payload[i] ^ mask[i % mask.length]);
        }

        out.write(masked);
    }
}
