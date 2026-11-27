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

public class PooledDuplicatedByteBufTest {

    private static final int CAPACITY = 512;
    private static final int SHRUNK_CAPACITY = 64;

    @Test
    public void testReadAccessorsThrowIfParentCapacityShrunkDirectBuffer() {
        testReadAccessorsThrowIfParentCapacityShrunk(PooledByteBufAllocator.DEFAULT.directBuffer(CAPACITY));
    }

    @Test
    public void testReadAccessorsThrowIfParentCapacityShrunkHeapBuffer() {
        testReadAccessorsThrowIfParentCapacityShrunk(PooledByteBufAllocator.DEFAULT.heapBuffer(CAPACITY));
    }

    private static void testReadAccessorsThrowIfParentCapacityShrunk(ByteBuf parent) {
        for (int i = 0; i < CAPACITY; i++) {
            parent.setByte(i, i);
        }
        parent.setIndex(0, CAPACITY);
        try {
            // retainedDuplicate(...) is required to obtain a PooledDuplicatedByteBuf. A non-retained
            // duplicate() would produce an UnpooledDuplicatedByteBuf, which is validated elsewhere.
            ByteBuf duplicate = parent.retainedDuplicate();
            try {
                parent.capacity(SHRUNK_CAPACITY);

                // The capacity of the duplicate tracks the wrapped buffer, but its indices are only
                // copies, so it still advertises readable bytes that are no longer backed by it.
                assertThat(duplicate.capacity()).isEqualTo(SHRUNK_CAPACITY);
                assertThat(duplicate.readableBytes()).isEqualTo(CAPACITY);

                // Reads within the part that is still backed by the wrapped buffer must keep working.
                for (int i = 0; i < SHRUNK_CAPACITY; i++) {
                    assertThat(duplicate.readByte()).isEqualTo(parent.getByte(i));
                }

                // Reads past the wrapped buffer's current capacity must throw instead of reaching past
                // its actual allocation, which was freed and returned to the arena as part of the
                // capacity change.
                assertThatThrownBy(() -> duplicate.readByte())
                        .isInstanceOf(IndexOutOfBoundsException.class);
                assertThatThrownBy(() -> duplicate.readShort())
                        .isInstanceOf(IndexOutOfBoundsException.class);
                assertThatThrownBy(() -> duplicate.readInt())
                        .isInstanceOf(IndexOutOfBoundsException.class);
                assertThatThrownBy(() -> duplicate.readLong())
                        .isInstanceOf(IndexOutOfBoundsException.class);
            } finally {
                duplicate.release();
            }
        } finally {
            parent.release();
        }
    }
}
