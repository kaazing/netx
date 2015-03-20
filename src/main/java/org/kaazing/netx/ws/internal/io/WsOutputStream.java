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

import static java.lang.String.format;
import static org.kaazing.netx.ws.internal.WebSocketState.CLOSED;

import java.io.FilterOutputStream;
import java.io.IOException;

import org.kaazing.netx.ws.internal.WebSocketOutputStateMachine;
import org.kaazing.netx.ws.internal.WsURLConnectionImpl;
import org.kaazing.netx.ws.internal.ext.frame.Close;
import org.kaazing.netx.ws.internal.ext.frame.Data;
import org.kaazing.netx.ws.internal.ext.frame.Frame;
import org.kaazing.netx.ws.internal.ext.frame.FrameFactory;
import org.kaazing.netx.ws.internal.ext.frame.OpCode;
import org.kaazing.netx.ws.internal.ext.frame.Pong;

public final class WsOutputStream extends FilterOutputStream {
    private static final String MSG_INDEX_OUT_OF_BOUNDS = "offset = %d; (offset + length) = %d; buffer length = %d";

    private final WsURLConnectionImpl connection;

    private final byte[] controlFramePayload;
    private Close closeFrame;
    private Pong pongFrame;
    private Data dataFrame;

    public WsOutputStream(WsURLConnectionImpl connection) throws IOException {
        super(connection.getTcpOutputStream());
        this.connection = connection;
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
        outputStateMachine.processBinary(connection, dataFrame);
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

            controlFramePayload[0] = (byte) ((code >> 8) & 0xFF);
            controlFramePayload[1] = (byte) (code & 0xFF);
            if (reason != null) {
                System.arraycopy(reason, offset, controlFramePayload, 2, length);
            }
        }

        WebSocketOutputStateMachine outputStateMachine = connection.getOutputStateMachine();
        closeFrame = (Close) getFrame(OpCode.CLOSE, true, true, controlFramePayload, 0, payloadLen);
        outputStateMachine.processClose(connection, closeFrame);
    }

    public void writePong(byte[] buf, int offset, int length) throws IOException {
        WebSocketOutputStateMachine outputStateMachine = connection.getOutputStateMachine();
        pongFrame = (Pong) getFrame(OpCode.PONG, true, true, buf, offset, length);
        outputStateMachine.processPong(connection, pongFrame);
    }

    private Frame getFrame(OpCode opcode, boolean fin, boolean masked, byte[] payload, int payloadOffset, long payloadLen)
            throws IOException {
        FrameFactory factory = connection.getOutputStateMachine().getFrameFactory();
        return factory.getFrame(opcode, fin, masked, payload, payloadOffset, payloadLen);
    }
}
