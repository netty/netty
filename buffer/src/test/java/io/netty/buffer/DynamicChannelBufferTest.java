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

import static org.junit.Assert.*;

import java.nio.ByteOrder;

import org.junit.Test;

/**
 * Tests dynamic channel buffers
 */
public class DynamicChannelBufferTest extends AbstractChannelBufferTest {

    private ByteBuf buffer;

    @Override
    protected ByteBuf newBuffer(int length) {
        buffer = Unpooled.dynamicBuffer(length);

        assertEquals(0, buffer.readerIndex());
        assertEquals(0, buffer.writerIndex());
        assertEquals(length, buffer.capacity());

        return buffer;
    }

    @Override
    protected ByteBuf[] components() {
        return new ByteBuf[] { buffer };
    }

    @Test(expected = NullPointerException.class)
    public void shouldNotAllowNullInConstructor() {
        new DynamicByteBuf(null, 0);
    }

    @Test
    public void shouldNotFailOnInitialIndexUpdate() {
        new DynamicByteBuf(ByteOrder.BIG_ENDIAN, 10).setIndex(0, 10);
    }

    @Test
    public void shouldNotFailOnInitialIndexUpdate2() {
        new DynamicByteBuf(ByteOrder.BIG_ENDIAN, 10).writerIndex(10);
    }

    @Test
    public void shouldNotFailOnInitialIndexUpdate3() {
        ByteBuf buf = new DynamicByteBuf(ByteOrder.BIG_ENDIAN, 10);
        buf.writerIndex(10);
        buf.readerIndex(10);
    }
}
