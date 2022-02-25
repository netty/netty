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
package io.netty5.buffer.api.adaptor;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.Unpooled;
import io.netty5.buffer.UnpooledByteBufAllocator;
import io.netty5.buffer.api.AllocationType;
import io.netty5.buffer.api.AllocatorControl;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.Drop;
import io.netty5.buffer.api.MemoryManager;
import io.netty5.buffer.api.StandardAllocationTypes;
import io.netty5.buffer.api.internal.WrappingAllocation;

import java.util.function.Function;

import static io.netty5.buffer.api.internal.Statics.convert;

/**
 * This memory manager produce and manage {@link Buffer} instances that are backed by {@link ByteBuf} instances.
 * <p>
 * Memory managers are normally not used directly.
 * Instead, you likely want to use the {@link io.netty5.buffer.api.DefaultBufferAllocators}, or the static methods on
 * {@link io.netty5.buffer.api.BufferAllocator}.
 * <p>
 * If you want to get a {@link Buffer} from a {@link ByteBuf}, take a look at {@link ByteBufBuffer#wrap(ByteBuf)}.
 */
public final class ByteBufMemoryManager implements MemoryManager {
    // Disable leak detection and cleaner, if possible, because the Buffer machinery will take care of these concerns.
    private final UnpooledByteBufAllocator unpooledDirectAllocator = new UnpooledByteBufAllocator(true, true, true);

    @Override
    public Buffer allocateShared(AllocatorControl control, long size,
                                 Function<Drop<Buffer>, Drop<Buffer>> dropDecorator,
                                 AllocationType allocationType) {
        int capacity = Math.toIntExact(size);
        if (allocationType == StandardAllocationTypes.OFF_HEAP) {
            ByteBuf byteBuf = unpooledDirectAllocator.directBuffer(capacity, capacity);
            byteBuf.setZero(0, capacity);
            return ByteBufBuffer.wrap(byteBuf, control, convert(dropDecorator.apply(drop())));
        }
        if (allocationType == StandardAllocationTypes.ON_HEAP) {
            ByteBuf byteBuf = Unpooled.wrappedBuffer(new byte[capacity]);
            byteBuf.setIndex(0, 0);
            return ByteBufBuffer.wrap(byteBuf, control, convert(dropDecorator.apply(drop())));
        }
        if (allocationType instanceof WrappingAllocation) {
            byte[] array = ((WrappingAllocation) allocationType).getArray();
            ByteBuf byteBuf = Unpooled.wrappedBuffer(array);
            byteBuf.setIndex(0, 0);
            return ByteBufBuffer.wrap(byteBuf, control, convert(dropDecorator.apply(drop())));
        }
        throw new IllegalArgumentException("Unknown allocation type: " + allocationType);
    }

    @Override
    public Buffer allocateConstChild(Buffer readOnlyConstParent) {
        ByteBufBuffer buf = (ByteBufBuffer) readOnlyConstParent;
        return buf.newConstChild();
    }

    private static Drop<Buffer> drop() {
        return convert(ByteBufBuffer.ByteBufDrop.INSTANCE);
    }

    @Override
    public Object unwrapRecoverableMemory(Buffer buf) {
        return ((ByteBufBuffer) buf).unwrapRecoverableMemory();
    }

    @Override
    public Buffer recoverMemory(AllocatorControl control, Object recoverableMemory, Drop<Buffer> drop) {
        ByteBuf buf = (ByteBuf) recoverableMemory;
        buf.setIndex(0, 0);
        return ByteBufBuffer.wrap(buf, control, convert(drop));
    }

    @Override
    public Object sliceMemory(Object memory, int offset, int length) {
        ByteBuf slice = ((ByteBuf) memory).slice(offset, length);
        slice.setIndex(0, 0);
        return slice;
    }

    @Override
    public String implementationName() {
        return "Netty ByteBuf";
    }
}
