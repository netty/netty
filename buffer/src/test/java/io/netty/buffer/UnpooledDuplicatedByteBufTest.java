/*
 * Copyright 2026 The Netty Project
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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UnpooledDuplicatedByteBufTest {

    @Test
    public void testReadAccessorsThrowIfParentCapacityShrunkDirectBuffer() {
        testReadAccessorsThrowIfParentCapacityShrunk(Unpooled.directBuffer(64), 8);
    }

    @Test
    public void testReadAccessorsThrowIfParentCapacityShrunkPooledDirectBuffer() {
        testReadAccessorsThrowIfParentCapacityShrunk(
                PooledByteBufAllocator.DEFAULT.directBuffer(512), 64);
    }

    private static void testReadAccessorsThrowIfParentCapacityShrunk(ByteBuf parent, int shrunkCapacity) {
        int capacity = parent.capacity();
        try {
            for (int i = 0; i < capacity; i++) {
                parent.setByte(i, i);
            }
            parent.setIndex(0, capacity);
            // The duplicate takes copies of the parent's indices: readerIndex 0, writerIndex = capacity.
            ByteBuf duplicate = parent.duplicate();

            parent.capacity(shrunkCapacity);

            // Unlike a slice, the capacity of a duplicate tracks the wrapped buffer...
            assertThat(duplicate.capacity()).isEqualTo(shrunkCapacity);
            // ... but its indices are only copies, so it still advertises readable bytes that are no
            // longer backed by the wrapped buffer.
            assertThat(duplicate.readableBytes()).isEqualTo(capacity);

            // Reads within the part that is still backed by the wrapped buffer must keep working.
            for (int i = 0; i < shrunkCapacity; i++) {
                assertThat(duplicate.readByte()).isEqualTo(parent.getByte(i));
            }

            // Reads past the wrapped buffer's current capacity must throw instead of reaching past
            // its actual allocation, which may have been freed and reallocated in the meantime.
            assertThatThrownBy(() -> duplicate.readByte())
                    .isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> duplicate.readShort())
                    .isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> duplicate.readInt())
                    .isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> duplicate.readLong())
                    .isInstanceOf(IndexOutOfBoundsException.class);
        } finally {
            parent.release();
        }
    }
}
