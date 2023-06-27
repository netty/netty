/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.buffer;

import static io.netty.buffer.Unpooled.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

import io.netty.util.CharsetUtil;
import io.netty.util.ResourceLeakTracker;

public class AdvancedLeakAwareByteBufTest extends SimpleLeakAwareByteBufTest {

    @Override
    protected Class<? extends ByteBuf> leakClass() {
        return AdvancedLeakAwareByteBuf.class;
    }

    @Override
    protected SimpleLeakAwareByteBuf wrap(ByteBuf buffer, ResourceLeakTracker<ByteBuf> tracker) {
        return new AdvancedLeakAwareByteBuf(buffer, tracker);
    }

    @Test
    public void testAddComponentWithLeakAwareByteBuf() {
        NoopResourceLeakTracker<ByteBuf> tracker = new NoopResourceLeakTracker<ByteBuf>();

        ByteBuf buffer = wrappedBuffer("hello world".getBytes(CharsetUtil.US_ASCII)).slice(6, 5);
        ByteBuf leakAwareBuf = wrap(buffer, tracker);

        CompositeByteBuf composite = compositeBuffer();
        composite.addComponent(true, leakAwareBuf);
        byte[] result = new byte[5];
        ByteBuf bb = composite.component(0);
        bb.readBytes(result);
        assertArrayEquals("world".getBytes(CharsetUtil.US_ASCII), result);
        composite.release();
    }
}
