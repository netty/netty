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

public class UnpooledSlicedByteBufTest {

    private static final int CAPACITY = 64;
    private static final int SHRUNK_CAPACITY = 8;

    @Test
    public void testNumericAccessorsThrowIfParentCapacityShrunkHeapBuffer() {
        testNumericAccessorsThrowIfParentCapacityShrunk(Unpooled.buffer(CAPACITY));
    }

    @Test
    public void testNumericAccessorsThrowIfParentCapacityShrunkDirectBuffer() {
        testNumericAccessorsThrowIfParentCapacityShrunk(Unpooled.directBuffer(CAPACITY));
    }

    private static void testNumericAccessorsThrowIfParentCapacityShrunk(ByteBuf parent) {
        for (int i = 0; i < CAPACITY; i++) {
            parent.setByte(i, i);
        }
        try {
            ByteBuf slice = parent.slice(0, CAPACITY);
            parent.capacity(SHRUNK_CAPACITY);

            // The capacity of the slice itself stays fixed, even if the wrapped buffer shrank.
            assertThat(slice.capacity()).isEqualTo(CAPACITY);

            // Accesses within the part that is still backed by the wrapped buffer must keep working.
            for (int i = 0; i < SHRUNK_CAPACITY; i++) {
                assertThat(slice.getByte(i)).isEqualTo(parent.getByte(i));
            }
            assertThat(slice.getShort(0)).isEqualTo(parent.getShort(0));
            assertThat(slice.getInt(0)).isEqualTo(parent.getInt(0));

            slice.setByte(0, 42);
            assertThat(parent.getByte(0)).isEqualTo((byte) 42);

            // SHRUNK_CAPACITY is chosen so that these accesses are still within the bounds of the slice
            // (and so pass the slice's own checks), but past the capacity the wrapped buffer has left.
            // They must throw instead of reaching past the wrapped buffer's actual allocation, which may
            // even have been freed and reallocated as part of the capacity change.
            assertThatThrownBy(() -> slice.getUnsignedByte(SHRUNK_CAPACITY))
                    .isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> slice.getByte(SHRUNK_CAPACITY))
                    .isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> slice.getShort(SHRUNK_CAPACITY))
                    .isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> slice.getShortLE(SHRUNK_CAPACITY))
                    .isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> slice.getUnsignedMedium(SHRUNK_CAPACITY))
                    .isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> slice.getUnsignedMediumLE(SHRUNK_CAPACITY))
                    .isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> slice.getInt(SHRUNK_CAPACITY))
                    .isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> slice.getIntLE(SHRUNK_CAPACITY))
                    .isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> slice.getLong(SHRUNK_CAPACITY))
                    .isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> slice.getLongLE(SHRUNK_CAPACITY))
                    .isInstanceOf(IndexOutOfBoundsException.class);

            assertThatThrownBy(() -> slice.setByte(SHRUNK_CAPACITY, 1))
                    .isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> slice.setShort(SHRUNK_CAPACITY, 1))
                    .isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> slice.setShortLE(SHRUNK_CAPACITY, 1))
                    .isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> slice.setMedium(SHRUNK_CAPACITY, 1))
                    .isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> slice.setMediumLE(SHRUNK_CAPACITY, 1))
                    .isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> slice.setInt(SHRUNK_CAPACITY, 1))
                    .isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> slice.setIntLE(SHRUNK_CAPACITY, 1))
                    .isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> slice.setLong(SHRUNK_CAPACITY, 1))
                    .isInstanceOf(IndexOutOfBoundsException.class);
            assertThatThrownBy(() -> slice.setLongLE(SHRUNK_CAPACITY, 1))
                    .isInstanceOf(IndexOutOfBoundsException.class);
        } finally {
            parent.release();
        }
    }
}
