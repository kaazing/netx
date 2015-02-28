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
import static org.kaazing.netx.ws.WsURLConnection.WS_ENDPOINT_GOING_AWAY;
import static org.kaazing.netx.ws.WsURLConnection.WS_INCONSISTENT_DATA_MESSAGE_TYPE;
import static org.kaazing.netx.ws.WsURLConnection.WS_INCORRECT_MESSAGE_TYPE;
import static org.kaazing.netx.ws.WsURLConnection.WS_MESSAGE_TOO_BIG;
import static org.kaazing.netx.ws.WsURLConnection.WS_NORMAL_CLOSE;
import static org.kaazing.netx.ws.WsURLConnection.WS_PROTOCOL_ERROR;
import static org.kaazing.netx.ws.WsURLConnection.WS_SERVER_TERMINATED_CONNECTION;
import static org.kaazing.netx.ws.WsURLConnection.WS_UNSUCCESSFUL_EXTENSION_NEGOTIATION;
import static org.kaazing.netx.ws.WsURLConnection.WS_UNSUCCESSFUL_TLS_HANDSHAKE;
import static org.kaazing.netx.ws.WsURLConnection.WS_VIOLATE_POLICY;
import static org.kaazing.netx.ws.internal.WebSocketState.CLOSED;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.kaazing.netx.ws.internal.WsURLConnectionImpl;

public final class WsOutputStream extends FilterOutputStream {
    private static final String MSG_CLOSE_FRAME_VIOLATION = "Protocol Violation: CLOSE Frame - Code = %d; Reason Length = %d";
    private static final byte[] EMPTY_MASK = new byte[] {0x00, 0x00, 0x00, 0x00};
    private static final int MAX_PAYLOAD_LENGTH = 8192;

    private final byte[] mask;
    private final WsURLConnectionImpl connection;

    private byte[] maskedBuffer;

    public WsOutputStream(WsURLConnectionImpl connection) throws IOException {
        super(connection.getTcpOutputStream());
        this.connection = connection;
        this.mask = new byte[4];
        this.maskedBuffer = new byte[MAX_PAYLOAD_LENGTH];
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[] { (byte) b });
    }

    @Override
    public void write(byte[] buf, int offset, int length) throws IOException {
        ByteBuffer payload = ByteBuffer.wrap(buf, offset, length);
        ByteBuffer transformedPayload = connection.getOutputStateMachine().sendBinaryFrame(connection, (byte) 0x82, payload);
        int remaining = transformedPayload.remaining();

        if (maskedBuffer.length < remaining) {
            maskedBuffer = new byte[remaining];
        }

        out.write(0x82);

        encodePayloadLength(length);

        connection.getRandom().nextBytes(mask);
        out.write(mask);

        for (int i = 0; i < remaining; i++) {
            maskedBuffer[i] = (byte) (transformedPayload.get() ^ mask[i % mask.length]);
        }

        out.write(maskedBuffer, 0, remaining);
    }

    public void writeClose(int code, byte[] reason) throws IOException {
        if (connection.getWebSocketOutputState() == CLOSED) {
            throw new IOException("Connection closed");
        }

        int len = 0;
        int closeCode = code;
        int capacity = 0;
        ByteBuffer payload = null;
        IOException exception = null;

        if (code > 0) {
            capacity += 2;
            if (reason != null) {
                capacity += reason.length;
            }

            payload = ByteBuffer.allocate(capacity);
            payload.putShort((short) code);
            if (reason != null) {
                payload.put(reason);
            }
            payload.flip();
        }

        ByteBuffer transformedPayload = connection.getOutputStateMachine().sendCloseFrame(connection, (byte) 0x88, payload);
        if ((transformedPayload != null) && (transformedPayload.remaining() > 0)) {
            closeCode = transformedPayload.getShort();
        }

        if (closeCode != 0) {
            switch (closeCode) {
            case WS_NORMAL_CLOSE:
            case WS_ENDPOINT_GOING_AWAY:
            case WS_PROTOCOL_ERROR:
            case WS_INCORRECT_MESSAGE_TYPE:
            case WS_INCONSISTENT_DATA_MESSAGE_TYPE:
            case WS_VIOLATE_POLICY:
            case WS_MESSAGE_TOO_BIG:
            case WS_UNSUCCESSFUL_EXTENSION_NEGOTIATION:
            case WS_SERVER_TERMINATED_CONNECTION:
            case WS_UNSUCCESSFUL_TLS_HANDSHAKE:
                len += 2;
                break;
            default:
                if ((closeCode >= 3000) && (closeCode <= 4999)) {
                    len += 2;
                }

                throw new IOException(format("Invalid CLOSE code %d", code));
            }

            int reasonLength = transformedPayload.remaining();
            if (reasonLength > 0) {
                if (reasonLength > 123) {
                    exception = new IOException(format(MSG_CLOSE_FRAME_VIOLATION, closeCode, reasonLength));
                }

                len += reasonLength;
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

            for (int i = 0; i < len; i++) {
                switch (i) {
                case 0:
                    maskedBuffer[i] = (byte) (((closeCode >> 8) & 0xFF) ^ mask[i % mask.length]);
                    break;
                case 1:
                    maskedBuffer[i] = (byte) (((closeCode >> 0) & 0xFF) ^ mask[i % mask.length]);
                    break;
                default:
                    maskedBuffer[i] = (byte) (transformedPayload.get() ^ mask[i % mask.length]);
                    break;
                }
            }

            out.write(maskedBuffer, 0, len);
            out.flush();
            out.close();

            if (exception != null) {
                throw exception;
            }
        }
    }

    public void writePong(byte[] buf) throws IOException {
        int len = 0;
        ByteBuffer transformedPayload = null;

        if (buf != null) {
            ByteBuffer payload = ByteBuffer.wrap(buf);
            transformedPayload = connection.getOutputStateMachine().sendPongFrame(connection, (byte) 0x8A, payload);
            len = transformedPayload.remaining();
        }

        out.write(0x8A);
        encodePayloadLength(len);

        if (transformedPayload == null) {
            out.write(EMPTY_MASK);
            return;
        }

        connection.getRandom().nextBytes(mask);
        out.write(mask);

        int length = transformedPayload.remaining();
        for (int i = 0; i < length; i++) {
            maskedBuffer[i] = (byte) (transformedPayload.get() ^ mask[i % mask.length]);
        }

        out.write(maskedBuffer, 0, length);
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
}
