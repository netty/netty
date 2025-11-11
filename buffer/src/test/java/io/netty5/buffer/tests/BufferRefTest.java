/*
 * Copyright 2021 The Netty Project
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
package io.netty5.buffer.tests;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.buffer.BufferClosedException;
import io.netty5.buffer.BufferRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BufferRefTest {
    @Test
    public void closingBufRefMustCloseOwnedBuf() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled()) {
            BufferRef ref;
            try (Buffer b = allocator.allocate(8)) {
                ref = new BufferRef(b);
            }
            ref.content().writeInt(42);
            assertThat(ref.content().readInt()).isEqualTo(42);
            ref.close();
            assertThrows(BufferClosedException.class, () -> ref.content().writeInt(32));
        }
    }

    @Test
    public void mustCloseOwnedBufferWhenReplaced() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled()) {
            BufferRef ref = new BufferRef(allocator.allocate(8));

            Buffer orig = ref.content();
            orig.writeInt(42);
            assertThat(orig.readInt()).isEqualTo(42);

            try (Buffer buf = allocator.allocate(8)) {
                ref.replace(buf);
            }

            assertThrows(BufferClosedException.class, () -> orig.writeInt(32));
            ref.content().writeInt(42);
            assertThat(ref.content().readInt()).isEqualTo(42);
            ref.close();
            assertThrows(BufferClosedException.class, () -> ref.content().writeInt(32));
        }
    }
}
