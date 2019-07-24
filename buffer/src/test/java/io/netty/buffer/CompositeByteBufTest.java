/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.buffer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static io.netty.buffer.Unpooled.*;
import static org.junit.Assert.*;

public class CompositeByteBufTest {

    @Before
    public void setUp() throws Exception {
        System.setProperty("io.netty.leakDetection.level", "paranoid");
        System.setProperty("io.netty.leakDetection.samplingInterval", "1");
    }

    @After
    public void tearDown() throws Exception {
        System.clearProperty("io.netty.leakDetection.level");
        System.clearProperty("io.netty.leakDetection.samplingInterval");
    }

    @Test
    public void testAddComponentWithLeakAwareByteBuf() {

        ByteBuf buffer = wrappedBuffer("hello world".getBytes()).slice(6, 5);
        ByteBuf leakAwareBuffer = UnpooledByteBufAllocator.toLeakAwareBuffer(buffer);

        CompositeByteBuf composite = compositeBuffer();
        composite.addComponents(true, leakAwareBuffer);
        byte[] result = new byte[5];

        composite.component(0).readBytes(result);
        assertArrayEquals("world".getBytes(), result);
    }


}
