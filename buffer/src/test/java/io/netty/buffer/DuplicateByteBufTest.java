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

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests duplicated channel buffers
 */
public class DuplicateByteBufTest extends AbstractByteBufTest {

    private ByteBuf buffer;

    @Override
    protected ByteBuf newBuffer(int length) {
        buffer = new DuplicatedByteBuf(Unpooled.buffer(length));
        assertEquals(0, buffer.writerIndex());
        return buffer;
    }

    @Override
    protected ByteBuf[] components() {
        return new ByteBuf[] { buffer };
    }

    @Test(expected = NullPointerException.class)
    public void shouldNotAllowNullInConstructor() {
        new DuplicatedByteBuf(null);
    }

    // See https://github.com/netty/netty/issues/1800
    @Test
    public void testIncreaseCapacityWrapped() {
        ByteBuf wrapped = buffer.unwrap();
        wrapped.writeByte(0);
        wrapped.readerIndex(wrapped.readerIndex() + 1);
        buffer.writerIndex(buffer.writerIndex() + 1);
        wrapped.capacity(wrapped.capacity() * 2);

        assertEquals((byte) 0, buffer.readByte());
    }
}
