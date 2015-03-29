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
package org.kaazing.netx.ws.specification.ext.primary;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import org.kaazing.netx.ws.internal.ext.WebSocketContext;
import org.kaazing.netx.ws.internal.ext.WebSocketExtensionSpi;
import org.kaazing.netx.ws.internal.ext.flyweight.Frame;
import org.kaazing.netx.ws.internal.ext.flyweight.Header;
import org.kaazing.netx.ws.internal.ext.flyweight.HeaderRW;
import org.kaazing.netx.ws.internal.ext.flyweight.OpCode;
import org.kaazing.netx.ws.internal.ext.function.WebSocketFrameConsumer;

public class PrimaryExtensionSpi extends WebSocketExtensionSpi {
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final HeaderRW outgoingFrame = new HeaderRW().wrap(ByteBuffer.allocate(1024), 0);
    private final HeaderRW incomingFrame = new HeaderRW().wrap(ByteBuffer.allocate(1024), 0);
    {
        onTextReceived = new WebSocketFrameConsumer() {

            @Override
            public void accept(WebSocketContext context, Frame frame) throws IOException {
                Header srcFrame = (Header) frame;
                OpCode opcode = srcFrame.opCode();
                int payloadLength = srcFrame.payloadLength();
                byte[] payload = new byte[payloadLength];
                int numBytes = srcFrame.payloadGet(payload, 0, payload.length);

                assert numBytes == payloadLength;

                String msg = "nuqneH, " + new String(payload, 0, payloadLength);
                byte[] xformedPayload = msg.getBytes(UTF_8);

                incomingFrame.opCodeAndFin(opcode, true);
                incomingFrame.payloadPut(xformedPayload, 0, xformedPayload.length);

                context.onTextReceived(incomingFrame);
            }
        };

        onTextSent = new WebSocketFrameConsumer() {

            @Override
            public void accept(WebSocketContext context, Frame frame) throws IOException {
                Header srcFrame = (Header) frame;
                OpCode opcode = srcFrame.opCode();
                int payloadLength = srcFrame.payloadLength();
                byte[] payload = new byte[payloadLength];
                int numBytes = srcFrame.payloadGet(payload, 0, payload.length);

                assert numBytes == payloadLength;

                String msg = new String(payload, 0, payloadLength).substring("Hello, ".length());
                byte[] bytes = msg.getBytes(UTF_8);

                outgoingFrame.opCodeAndFin(opcode, true);
                outgoingFrame.maskedPayloadPut(bytes, 0, bytes.length);
                context.onTextSent(outgoingFrame);
            }
        };
    }

    public PrimaryExtensionSpi() {
    }
}
