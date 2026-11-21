/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at:
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

public class PooledSlicedByteBufTest {

    private static final int CAPACITY = 512;
    private static final int SHRUNK_CAPACITY = 64;

    @Test
    public void testNumericAccessorsThrowIfParentCapacityShrunkHeapBuffer() {
        testNumericAccessorsThrowIfParentCapacityShrunk(PooledByteBufAllocator.DEFAULT.heapBuffer(CAPACITY));
    }

    @Test
    public void testNumericAccessorsThrowIfParentCapacityShrunkDirectBuffer() {
        testNumericAccessorsThrowIfParentCapacityShrunk(PooledByteBufAllocator.DEFAULT.directBuffer(CAPACITY));
    }

    private static void testNumericAccessorsThrowIfParentCapacityShrunk(ByteBuf parent) {
        for (int i = 0; i < CAPACITY; i++) {
            parent.setByte(i, i);
        }
        try {
            // retainedSlice(...) is required to obtain a PooledSlicedByteBuf. A non-retained slice() would
            // produce an UnpooledSlicedByteBuf, which is validated elsewhere.
            ByteBuf slice = parent.retainedSlice(0, CAPACITY);
            try {
                parent.capacity(SHRUNK_CAPACITY);

                // The capacity of the slice itself stays fixed, even if the wrapped buffer shrank.
                assertThat(slice.capacity()).isEqualTo(CAPACITY);

                // Accesses within the part that is still backed by the wrapped buffer must keep working.
                for (int i = 0; i < SHRUNK_CAPACITY; i++) {
                    assertThat(slice.getByte(i)).isEqualTo(parent.getByte(i));
                }

                // SHRUNK_CAPACITY is chosen so that these accesses are still within the bounds of the slice
                // (and so pass the slice's own checks), but past the capacity the wrapped buffer has left.
                // They must throw instead of reaching past the wrapped buffer's allocation, which was freed
                // and returned to the arena as part of the capacity change.
                assertThatThrownBy(() -> slice.getByte(SHRUNK_CAPACITY))
                        .isInstanceOf(IndexOutOfBoundsException.class);
                assertThatThrownBy(() -> slice.getShort(SHRUNK_CAPACITY))
                        .isInstanceOf(IndexOutOfBoundsException.class);
                assertThatThrownBy(() -> slice.getInt(SHRUNK_CAPACITY))
                        .isInstanceOf(IndexOutOfBoundsException.class);
                assertThatThrownBy(() -> slice.getLong(SHRUNK_CAPACITY))
                        .isInstanceOf(IndexOutOfBoundsException.class);
                assertThatThrownBy(() -> slice.setByte(SHRUNK_CAPACITY, 1))
                        .isInstanceOf(IndexOutOfBoundsException.class);
                assertThatThrownBy(() -> slice.setInt(SHRUNK_CAPACITY, 1))
                        .isInstanceOf(IndexOutOfBoundsException.class);
            } finally {
                slice.release();
            }
        } finally {
            parent.release();
        }
    }

    @Test
    public void testReadWriteAccessorsThrowIfParentCapacityShrunkDirectBuffer() {
        ByteBuf parent = PooledByteBufAllocator.DEFAULT.directBuffer(CAPACITY);
        try {
            ByteBuf slice = parent.retainedSlice(0, CAPACITY);
            try {
                parent.capacity(SHRUNK_CAPACITY);

                // The read/write accessors go through the _get/_set methods: keep the readable / writable checks
                // satisfiable so the access really reaches the wrapped-buffer bounds check under test.
                slice.readerIndex(SHRUNK_CAPACITY);
                slice.writerIndex(SHRUNK_CAPACITY + 8);
                assertThatThrownBy(() -> slice.readByte())
                        .isInstanceOf(IndexOutOfBoundsException.class);
                assertThatThrownBy(() -> slice.readInt())
                        .isInstanceOf(IndexOutOfBoundsException.class);
                assertThatThrownBy(() -> slice.readLong())
                        .isInstanceOf(IndexOutOfBoundsException.class);
                assertThatThrownBy(() -> slice.writeByte(1))
                        .isInstanceOf(IndexOutOfBoundsException.class);
                assertThatThrownBy(() -> slice.writeLong(1))
                        .isInstanceOf(IndexOutOfBoundsException.class);

                // Reads and writes within the part that is still backed by the wrapped buffer must keep working.
                slice.readerIndex(0);
                slice.writerIndex(SHRUNK_CAPACITY - 1);
                assertThat(slice.readByte()).isEqualTo(parent.getByte(0));
                slice.writeByte(42);
                assertThat(parent.getByte(SHRUNK_CAPACITY - 1)).isEqualTo((byte) 42);
            } finally {
                slice.release();
            }
        } finally {
            parent.release();
        }
    }

    @Test
    public void testSliceBoundsStillEnforcedIfParentCapacityGrows() {
        ByteBuf parent = PooledByteBufAllocator.DEFAULT.heapBuffer(CAPACITY, CAPACITY * 2);
        try {
            ByteBuf slice = parent.retainedSlice(0, 16);
            try {
                parent.capacity(CAPACITY * 2);

                // A larger wrapped buffer must not loosen the bounds of the slice itself.
                assertThat(slice.getByte(15)).isEqualTo(parent.getByte(15));
                assertThatThrownBy(() -> slice.getByte(16))
                        .isInstanceOf(IndexOutOfBoundsException.class);
                assertThatThrownBy(() -> slice.getLong(9))
                        .isInstanceOf(IndexOutOfBoundsException.class);
            } finally {
                slice.release();
            }
        } finally {
            parent.release();
        }
    }
}
