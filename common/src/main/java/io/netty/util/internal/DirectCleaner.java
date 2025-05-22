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
 */
package io.netty.util.internal;

import java.nio.ByteBuffer;

final class DirectCleaner implements Cleaner {
    @Override
    public CleanableDirectBuffer allocate(int capacity) {
        return new CleanableDirectBufferImpl(PlatformDependent.allocateDirectNoCleaner(capacity));
    }

    @Override
    public void freeDirectBuffer(ByteBuffer buffer) {
        PlatformDependent.freeDirectNoCleaner(buffer);
    }

    CleanableDirectBuffer reallocate(CleanableDirectBuffer buffer, int capacity) {
        ByteBuffer newByteBuffer = PlatformDependent.reallocateDirectNoCleaner(buffer.buffer(), capacity);
        return new CleanableDirectBufferImpl(newByteBuffer);
    }

    private static final class CleanableDirectBufferImpl implements CleanableDirectBuffer {
        private final ByteBuffer buffer;

        private CleanableDirectBufferImpl(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public ByteBuffer buffer() {
            return buffer;
        }

        @Override
        public void clean() {
            PlatformDependent.freeDirectNoCleaner(buffer);
        }
    }
}
