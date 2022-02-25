/*
 * Copyright 2022 The Netty Project
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
package io.netty5.buffer.api.tests;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

public class BufferAllocateTest extends BufferTestSupport {

    @ParameterizedTest
    @MethodSource("allocators")
    public void allocateDifferentSizes(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            // Allocate zero size buffers.
            allocateBufferPair(allocator,  0);

            // Allocate buffer with the default element size of the subpage
            allocateBufferPair(allocator,  16);

            // Allocate buffer with a size bigger as the default element size of the subpage but still smaller than
            // the alignment (if any is used).
            allocateBufferPair(allocator,  17);

            allocateBufferPair(allocator,  128);
            allocateBufferPair(allocator,  512);
            allocateBufferPair(allocator,  1024);
            allocateBufferPair(allocator,  8 * 1024);
            allocateBufferPair(allocator,  16 * 1024);
            allocateBufferPair(allocator,  32 * 1024);
            allocateBufferPair(allocator,  1000 * 1024);
            allocateBufferPair(allocator,  8 * 1000 * 1024);
            allocateBufferPair(allocator,  32 * 1000 * 1024);
        }
    }

    private static void allocateBufferPair(BufferAllocator allocator, int size) {
        try (Buffer buf = allocator.allocate(size);
             Buffer buf2 = allocator.allocate(size)) {
            assertThat(buf.capacity()).isGreaterThanOrEqualTo(size);
            assertThat(buf2.capacity()).isGreaterThanOrEqualTo(size);
        }
    }
}
