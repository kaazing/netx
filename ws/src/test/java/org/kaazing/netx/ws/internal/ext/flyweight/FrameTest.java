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
package org.kaazing.netx.ws.internal.ext.flyweight;

import java.nio.ByteBuffer;
import java.util.Random;

import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import org.kaazing.netx.ws.internal.ext.flyweight.DataTest.Fin;

@RunWith(Theories.class)
public class FrameTest {
    private static final int BUFFER_CAPACITY = 64 * 1024;
    private static final int WS_MAX_MESSAGE_SIZE = 20 * 1024;

    @DataPoint
    public static final int ZERO_OFFSET = 0;

    @DataPoint
    public static final int NON_ZERO_OFFSET = new Random().nextInt(BUFFER_CAPACITY - WS_MAX_MESSAGE_SIZE);

    @DataPoint
    public static final boolean MASKED = true;

    @DataPoint
    public static final boolean UNMASKED = false;

    protected final ByteBuffer buffer = ByteBuffer.wrap(new byte[BUFFER_CAPACITY]);

    @Theory
    public void dummyTest(int offset, boolean masked, Fin fin) throws Exception {
    }
}
