/*
 * Copyright 2025 The Netty Project
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
 *
 */

package io.netty5.buffer.memseg;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Segment allocator that uses the java FFM arena api.
 */
final class JdkSegmentAllocator implements DirectSegmentAllocator {

    @Override
    public SegmentHolder allocate(long byteSize) {
        final Arena arena = Arena.ofShared();
        final MemorySegment segment = arena.allocate(byteSize);
        return new JdkSegmentHolder(segment, arena);
    }

    private record JdkSegmentHolder(MemorySegment segment, Arena arena) implements SegmentHolder {
        @Override
        public void free() {
            arena.close();
        }
    }
}
