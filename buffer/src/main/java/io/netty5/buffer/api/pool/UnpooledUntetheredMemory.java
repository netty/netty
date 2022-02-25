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
package io.netty5.buffer.api.pool;

import io.netty5.buffer.api.AllocationType;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.Drop;
import io.netty5.buffer.api.MemoryManager;
import io.netty5.buffer.api.internal.DropCaptor;

import static io.netty5.buffer.api.internal.Statics.standardDrop;

@SuppressWarnings("unchecked")
class UnpooledUntetheredMemory implements UntetheredMemory {
    private final MemoryManager manager;
    private final Buffer buffer;
    private final DropCaptor<Buffer> dropCaptor;

    UnpooledUntetheredMemory(PooledBufferAllocator allocator, MemoryManager manager,
                             AllocationType allocationType, int size) {
        this.manager = manager;
        PooledAllocatorControl allocatorControl = new PooledAllocatorControl(allocator);
        dropCaptor = new DropCaptor<>();
        buffer = manager.allocateShared(allocatorControl, size, dropCaptor, allocationType);
    }

    @Override
    public <Memory> Memory memory() {
        return (Memory) manager.unwrapRecoverableMemory(buffer);
    }

    @Override
    public <BufferType extends Buffer> Drop<BufferType> drop() {
        return (Drop<BufferType>) standardDrop(manager).apply(dropCaptor.getDrop());
    }
}
