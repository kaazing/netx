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

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.Charset;

import org.kaazing.netx.ws.internal.WebSocketOutputStateMachine;
import org.kaazing.netx.ws.internal.WsURLConnectionImpl;
import org.kaazing.netx.ws.internal.ext.WebSocketContext;
import org.kaazing.netx.ws.internal.ext.WebSocketExtensionHooks;
import org.kaazing.netx.ws.internal.ext.frame.Data;
import org.kaazing.netx.ws.internal.ext.frame.Frame;
import org.kaazing.netx.ws.internal.ext.frame.Frame.Payload;
import org.kaazing.netx.ws.internal.ext.frame.FrameFactory;
import org.kaazing.netx.ws.internal.ext.frame.OpCode;
import org.kaazing.netx.ws.internal.ext.function.WebSocketFrameSupplier;
import org.kaazing.netx.ws.internal.util.FrameUtil;

public class WsWriter extends Writer {
    private static final String MSG_INDEX_OUT_OF_BOUNDS = "offset = %d; (offset + length) = %d; buffer length = %d";
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final WsURLConnectionImpl connection;
    private final OutputStream out;
    private final byte[] mask;

    private Data dataFrame;

    public WsWriter(WsURLConnectionImpl connection) throws IOException {
        this.connection = connection;
        this.out = connection.getTcpOutputStream();
        this.mask = new byte[4];
    }

    @Override
    public void write(char[] cbuf, int offset, int length) throws IOException {
        if (connection.getOutputState() == CLOSED) {
            throw new IOException("Connection closed");
        }

        if (cbuf == null) {
            throw new NullPointerException("Null buffer passed in");
        }
        else if ((offset < 0) || (length < 0) || (offset + length > cbuf.length)) {
            throw new IndexOutOfBoundsException(format(MSG_INDEX_OUT_OF_BOUNDS, offset, offset + length, cbuf.length));
        }

        WebSocketExtensionHooks sentinelHooks = new WebSocketExtensionHooks() {
            {
                whenTextFrameSend = new WebSocketFrameSupplier() {
                    @Override
                    public void apply(WebSocketContext context, Frame frame) throws IOException {
                        Data sourceFrame = (Data) frame;
                        if (sourceFrame == dataFrame) {
                            return;
                        }

                        FrameUtil.copy(sourceFrame, dataFrame);
                    }
                };
            }
        };

        byte[] bytesPayload = String.valueOf(cbuf, offset, length).getBytes(UTF_8);
        dataFrame = (Data) getFrame(OpCode.TEXT, true, true, bytesPayload, 0, bytesPayload.length);
        WebSocketOutputStateMachine outputStateMachine = connection.getOutputStateMachine();
        outputStateMachine.sendTextFrame(connection.getContext(sentinelHooks, true), dataFrame);

        Payload payload = dataFrame.getPayload();
        int payloadLen = dataFrame.getLength();
        int payloadOffset = payload.offset();

        out.write(0x81);

        switch (highestOneBit(payloadLen)) {
        case 0x0000:
        case 0x0001:
        case 0x0002:
        case 0x0004:
        case 0x0008:
        case 0x0010:
        case 0x0020:
            out.write(0x80 | payloadLen);
            break;
        case 0x0040:
            switch (length) {
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
                out.write(0x80 | payloadLen);
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
            out.write((length >> 8) & 0xff);
            out.write((length >> 0) & 0xff);
            break;
        default:
            // 65536+
            out.write(0x80 | 127);

            long lengthL = payloadLen;
            out.write((int) ((lengthL >> 56) & 0xff));
            out.write((int) ((lengthL >> 48) & 0xff));
            out.write((int) ((lengthL >> 40) & 0xff));
            out.write((int) ((lengthL >> 32) & 0xff));
            out.write((int) ((lengthL >> 24) & 0xff));
            out.write((int) ((lengthL >> 16) & 0xff));
            out.write((int) ((lengthL >> 8) & 0xff));
            out.write((int) ((lengthL >> 0) & 0xff));
            break;
        }

        // Create the masking key.
        connection.getRandom().nextBytes(mask);
        out.write(mask);

        // Mask the payload and write it out.
        for (int i = 0; i < payloadLen; i++) {
            out.write((byte) (payload.buffer().get(payloadOffset++) ^ mask[i % mask.length]));
        }
    }

    @Override
    public void flush() throws IOException {
        // No-op
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    private Frame getFrame(OpCode opcode, boolean fin, boolean masked, byte[] payload, int payloadOffset, long payloadLen)
            throws IOException {
        FrameFactory factory = connection.getOutputStateMachine().getFrameFactory();
        return factory.createFrame(opcode, fin, masked, payload, payloadOffset, payloadLen);
    }
}
