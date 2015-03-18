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
import static org.kaazing.netx.ws.internal.WebSocketState.CLOSED;

import java.io.FilterOutputStream;
import java.io.IOException;

import org.kaazing.netx.ws.internal.WebSocketOutputStateMachine;
import org.kaazing.netx.ws.internal.WsURLConnectionImpl;
import org.kaazing.netx.ws.internal.ext.frame.Close;
import org.kaazing.netx.ws.internal.ext.frame.Control;
import org.kaazing.netx.ws.internal.ext.frame.Data;
import org.kaazing.netx.ws.internal.ext.frame.Frame;
import org.kaazing.netx.ws.internal.ext.frame.FrameFactory;
import org.kaazing.netx.ws.internal.ext.frame.OpCode;
import org.kaazing.netx.ws.internal.ext.frame.Pong;

public final class WsOutputStream extends FilterOutputStream {
    private static final String MSG_INDEX_OUT_OF_BOUNDS = "offset = %d; (offset + length) = %d; buffer length = %d";
    private static final int MAX_COMMAND_FRAME_PAYLOAD = 125;

    private final byte[] mask;
    private final WsURLConnectionImpl connection;

    private final byte[] controlFramePayload;
    private Close closeFrame;
    private Control controlFrame;
    private Data dataFrame;

    public WsOutputStream(WsURLConnectionImpl connection) throws IOException {
        super(connection.getTcpOutputStream());
        this.connection = connection;
        this.mask = new byte[4];
        this.controlFramePayload = new byte[150]; // To handle negative tests. Have some extra bytes.
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
        if (buf == null) {
            throw new NullPointerException("Null buffer passed in");
        }
        else if ((offset < 0) || (length < 0) || (offset + length > buf.length)) {
            throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, offset + length, buf.length));
        }


        dataFrame = (Data) getFrame(OpCode.BINARY, true, true, buf, offset, length);
        WebSocketOutputStateMachine outputStateMachine = connection.getOutputStateMachine();
        outputStateMachine.sendBinaryFrame(connection, dataFrame);
    }

    public void writeClose(int code, byte[] reason, int offset, int length) throws IOException {
        if (connection.getOutputState() == CLOSED) {
            throw new IOException("Connection closed");
        }

        if (reason != null) {
            if ((offset < 0) || (length < 0) || (offset + length > reason.length)) {
                throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, offset + length, reason.length));
            }
        }

        int payloadLen = 0;

        if (code > 0) {
            payloadLen += 2;
            payloadLen += length;

            if (payloadLen > MAX_COMMAND_FRAME_PAYLOAD) {
                out.write(0x88);
                encodePayloadLength(payloadLen);
                connection.getRandom().nextBytes(mask);
                out.write(mask);

                for (int i = 0; i < payloadLen; i++) {
                    switch (i) {
                    case 0:
                        out.write((byte) (((code >> 8) & 0xFF) ^ mask[i % mask.length]));
                        break;
                    case 1:
                        out.write((byte) (((code >> 0) & 0xFF) ^ mask[i % mask.length]));
                        break;
                    default:
                        out.write((byte) (reason[offset++] ^ mask[i % mask.length]));
                        break;
                    }
                }

                out.flush();
                out.close();

                throw new IOException("Protocol Violation: CLOSE frame payload execeds the maximum allowed size of 125");
            }

            controlFramePayload[0] = (byte) ((code >> 8) & 0xFF);
            controlFramePayload[1] = (byte) (code & 0xFF);
            if (reason != null) {
                System.arraycopy(reason, offset, controlFramePayload, 2, length);
            }
        }

        WebSocketOutputStateMachine outputStateMachine = connection.getOutputStateMachine();
        closeFrame = (Close) getFrame(OpCode.CLOSE, true, true, controlFramePayload, 0, payloadLen);
        outputStateMachine.sendCloseFrame(connection, closeFrame);
    }

    public void writePong(byte[] buf, int offset, int length) throws IOException {
        WebSocketOutputStateMachine outputStateMachine = connection.getOutputStateMachine();
        controlFrame = (Control) getFrame(OpCode.PONG, true, true, buf, offset, length);
        outputStateMachine.sendPongFrame(connection, (Pong) controlFrame);
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

    private Frame getFrame(OpCode opcode, boolean fin, boolean masked, byte[] payload, int payloadOffset, long payloadLen)
            throws IOException {
        FrameFactory factory = connection.getOutputStateMachine().getFrameFactory();
        return factory.getFrame(opcode, fin, masked, payload, payloadOffset, payloadLen);
    }
}
