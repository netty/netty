/*
 * Copyright 2020 The Netty Project
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
package io.netty.channel.unix.tests;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.buffer.UnpooledDirectByteBuf;
import io.netty.channel.unix.IovArray;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class IovArrayTest {

    @Test
    public void testNotFailsWihtoutMemoryAddress() {
        ByteBuf buffer = new NoMemoryAddressByteBuf(128);
        IovArray array = new IovArray(buffer);

        ByteBuf buf = Unpooled.directBuffer().writeZero(8);
        ByteBuf buf2 = new NoMemoryAddressByteBuf(8).writeZero(8);
        assertTrue(array.add(buf, 0, buf.readableBytes()));
        assertTrue(array.add(buf, 0, buf2.readableBytes()));
        assertEquals(2, array.count());
        assertEquals(16, array.size());
        assertTrue(buf.release());
        assertTrue(buf2.release());
        assertNotEquals(-1, array.memoryAddress(0));
        array.release();
        assertEquals(0, buffer.refCnt());
    }

    private static final class NoMemoryAddressByteBuf extends UnpooledDirectByteBuf {

        NoMemoryAddressByteBuf(int capacity) {
            super(UnpooledByteBufAllocator.DEFAULT, capacity, Integer.MAX_VALUE);
        }

        @Override
        public boolean hasMemoryAddress() {
            return false;
        }

        @Override
        public long memoryAddress() {
            throw new UnsupportedOperationException();
        }
    }
}
