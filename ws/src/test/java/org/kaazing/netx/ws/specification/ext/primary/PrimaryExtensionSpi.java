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
package org.kaazing.netx.ws.specification.ext.primary;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import org.kaazing.netx.ws.internal.ext.WebSocketContext;
import org.kaazing.netx.ws.internal.ext.WebSocketExtensionSpi;
import org.kaazing.netx.ws.internal.ext.flyweight.Frame;
import org.kaazing.netx.ws.internal.ext.flyweight.FrameRW;
import org.kaazing.netx.ws.internal.ext.flyweight.Opcode;
import org.kaazing.netx.ws.internal.ext.function.WebSocketFrameConsumer;

public class PrimaryExtensionSpi extends WebSocketExtensionSpi {
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final FrameRW outgoingFrame = new FrameRW().wrap(ByteBuffer.allocate(1024), 0);
    private final FrameRW incomingFrame = new FrameRW().wrap(ByteBuffer.allocate(1024), 0);
    {
        onTextReceived = new WebSocketFrameConsumer() {

            @Override
            public void accept(WebSocketContext context, Frame frame) throws IOException {
                Opcode opcode = frame.opcode();
                int payloadLength = frame.payloadLength();
                int payloadOffset = frame.payloadOffset();
                byte[] payload = new byte[payloadLength];

                for (int i = 0; i < payloadLength; i++) {
                    payload[i] = frame.buffer().get(payloadOffset++);
                }

                String msg = "nuqneH, " + new String(payload, 0, payloadLength);
                byte[] xformedPayload = msg.getBytes(UTF_8);

                incomingFrame.fin(true);
                incomingFrame.opcode(opcode);
                incomingFrame.payloadPut(xformedPayload, 0, xformedPayload.length);

                context.onTextReceived(incomingFrame);
            }
        };

        onTextSent = new WebSocketFrameConsumer() {

            @Override
            public void accept(WebSocketContext context, Frame frame) throws IOException {
                Opcode opcode = frame.opcode();
                int payloadLength = frame.payloadLength();
                int payloadOffset = frame.payloadOffset();
                byte[] payload = new byte[payloadLength];

                for (int i = 0; i < payloadLength; i++) {
                    payload[i] = frame.buffer().get(payloadOffset++);
                }

                String msg = new String(payload, 0, payloadLength).substring("Hello, ".length());
                byte[] bytes = msg.getBytes(UTF_8);

                outgoingFrame.fin(true);
                outgoingFrame.opcode(opcode);
                outgoingFrame.payloadPut(bytes, 0, bytes.length);
                context.onTextSent(outgoingFrame);
            }
        };
    }

    public PrimaryExtensionSpi() {
    }
}
