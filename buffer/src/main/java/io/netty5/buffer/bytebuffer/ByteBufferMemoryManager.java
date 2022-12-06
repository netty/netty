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
package io.netty5.buffer.bytebuffer;

import io.netty5.buffer.AllocationType;
import io.netty5.buffer.BufferAllocator;
import io.netty5.buffer.AllocatorControl;
import io.netty5.buffer.DefaultBufferAllocators;
import io.netty5.buffer.Buffer;
import io.netty5.buffer.Drop;
import io.netty5.buffer.MemoryManager;
import io.netty5.buffer.StandardAllocationTypes;
import io.netty5.buffer.internal.InternalBufferUtils;
import io.netty5.buffer.internal.WrappingAllocation;

import java.nio.ByteBuffer;
import java.util.function.Function;

import static io.netty5.buffer.internal.InternalBufferUtils.bbslice;
import static io.netty5.buffer.internal.InternalBufferUtils.convert;

/**
 * This memory manager produces and manages {@link Buffer} instances that are backed by NIO {@link ByteBuffer}
 * instances.
 * <p>
 * Memory managers are normally not used directly.
 * Instead, you likely want to use the {@link DefaultBufferAllocators}, or the static methods on
 * {@link BufferAllocator}.
 */
public final class ByteBufferMemoryManager implements MemoryManager {
    @Override
    public Buffer allocateShared(AllocatorControl allocatorControl, long size,
                                 Function<Drop<Buffer>, Drop<Buffer>> dropDecorator,
                                 AllocationType allocationType) {
        int capacity = Math.toIntExact(size);
        final ByteBuffer buffer;
        if (allocationType == StandardAllocationTypes.OFF_HEAP) {
            buffer = ByteBuffer.allocateDirect(capacity);
        } else if (allocationType == StandardAllocationTypes.ON_HEAP) {
            buffer = ByteBuffer.allocate(capacity);
        } else if (allocationType instanceof WrappingAllocation) {
            buffer = ByteBuffer.wrap(((WrappingAllocation) allocationType).getArray());
        } else {
            throw new IllegalArgumentException("Unknown allocation type: " + allocationType);
        }
        return createBuffer(buffer, allocatorControl, dropDecorator.apply(drop()));
    }

    @Override
    public Buffer allocateConstChild(Buffer readOnlyConstParent) {
        NioBuffer buf = (NioBuffer) readOnlyConstParent;
        return buf.newConstChild();
    }

    private static Drop<Buffer> drop() {
        return InternalBufferUtils.NO_OP_DROP;
    }

    @Override
    public Object unwrapRecoverableMemory(Buffer buf) {
        return ((NioBuffer) buf).recoverable();
    }

    @Override
    public Buffer recoverMemory(AllocatorControl allocatorControl, Object recoverableMemory, Drop<Buffer> drop) {
        ByteBuffer memory = (ByteBuffer) recoverableMemory;
        return createBuffer(memory, allocatorControl, drop);
    }

    private static NioBuffer createBuffer(ByteBuffer memory, AllocatorControl allocatorControl, Drop<Buffer> drop) {
        Drop<NioBuffer> concreteDrop = convert(drop);
        NioBuffer nioBuffer = new NioBuffer(memory, memory, allocatorControl, concreteDrop);
        concreteDrop.attach(nioBuffer);
        return nioBuffer;
    }

    @Override
    public Object sliceMemory(Object memory, int offset, int length) {
        var buffer = (ByteBuffer) memory;
        return bbslice(buffer, offset, length);
    }

    @Override
    public void clearMemory(Object memory) {
        ByteBuffer buffer = (ByteBuffer) memory;
        InternalBufferUtils.setMemory(buffer, buffer.capacity(), (byte) 0);
    }

    @Override
    public int sizeOf(Object memory) {
        ByteBuffer buffer = (ByteBuffer) memory;
        return buffer.capacity();
    }

    @Override
    public String implementationName() {
        return "ByteBuffer";
    }
}
