/*
 * Copyright 2020 The Netty Project
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

import java.foreign.memory.Pointer;
import java.nio.ByteBuffer;

public final class JniByteBufAllocator extends UnpooledByteBufAllocator {

    private UnpooledByteBufAllocatorMetric metric = new UnpooledByteBufAllocatorMetric();

    public JniByteBufAllocator(boolean preferDirect) {
        super(preferDirect);
    }

    @Override
    public ByteBufAllocatorMetric metric() {
        return super.metric();
    }

    @Override
    protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
        try (java.foreign.Scope scope = io.netty.buffer.jni.jemalloc_lib.scope().fork()) {
            metric.directCounter.add(initialCapacity);
            java.foreign.memory.Pointer<?> ptr =
                    io.netty.buffer.jni.jemalloc_lib.jni_je_malloc(initialCapacity);
            return new JniDirectByteBuf(ptr.asDirectByteBuffer(initialCapacity), maxCapacity);
        }
    }

    @Override
    protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
        metric.heapCounter.add(initialCapacity);
        return super.newHeapBuffer(initialCapacity, maxCapacity);
    }

    private final class JniDirectByteBuf extends UnpooledDirectByteBuf {
        JniDirectByteBuf(
                ByteBuffer initialBuffer, int maxCapacity) {
            super(JniByteBufAllocator.this, initialBuffer, maxCapacity, /* doFree= */ false, false);
        }

        @Override
        protected void freeDirect(ByteBuffer buffer) {
            Pointer<?> pointer = Pointer.fromByteBuffer(buffer);
            io.netty.buffer.jni.jemalloc_lib.jni_je_free(pointer);
        }
    }
}
