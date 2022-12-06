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
package io.netty5.buffer.unsafe;

import io.netty5.buffer.AllocationType;
import io.netty5.buffer.AllocatorControl;
import io.netty5.buffer.BufferAllocator;
import io.netty5.buffer.DefaultBufferAllocators;
import io.netty5.buffer.Buffer;
import io.netty5.buffer.Drop;
import io.netty5.buffer.MemoryManager;
import io.netty5.buffer.StandardAllocationTypes;
import io.netty5.buffer.internal.ArcDrop;
import io.netty5.buffer.internal.InternalBufferUtils;
import io.netty5.buffer.internal.WrappingAllocation;
import io.netty5.util.internal.PlatformDependent;
import io.netty5.util.internal.SystemPropertyUtil;

import java.lang.ref.Cleaner;
import java.util.function.Function;

import static io.netty5.buffer.internal.InternalBufferUtils.convert;

/**
 * This memory manager produces and manages {@link Buffer} instances that are using {@code Unsafe} to allocate and
 * access memory.
 * <p>
 * Memory managers are normally not used directly.
 * Instead, you likely want to use the {@link DefaultBufferAllocators}, or the static methods on
 * {@link BufferAllocator}.
 */
public final class UnsafeMemoryManager implements MemoryManager {
    private static final boolean FREE_IMMEDIATELY = SystemPropertyUtil.getBoolean(
            "io.netty5.buffer.unsafe.UnsafeMemoryManager.freeDirectMemoryImmediately", true);

    public UnsafeMemoryManager() {
        if (!PlatformDependent.hasUnsafe()) {
            UnsupportedOperationException notSupported = new UnsupportedOperationException("Unsafe is not available.");
            notSupported.addSuppressed(PlatformDependent.getUnsafeUnavailabilityCause());
            throw notSupported;
        }
        if (!PlatformDependent.hasDirectBufferNoCleanerConstructor()) {
            throw new UnsupportedOperationException("DirectByteBuffer internal constructor is not available.");
        }
    }

    @Override
    public Buffer allocateShared(AllocatorControl control, long size,
                                 Function<Drop<Buffer>, Drop<Buffer>> dropDecorator,
                                 AllocationType allocationType) {
        final Object base;
        final long address;
        final UnsafeMemory memory;
        final int size32 = Math.toIntExact(size);
        Cleaner cleaner = InternalBufferUtils.getCleaner();
        Drop<Buffer> drop = InternalBufferUtils.NO_OP_DROP;
        if (allocationType == StandardAllocationTypes.OFF_HEAP) {
            base = null;
            address = PlatformDependent.allocateMemory(size);
            InternalBufferUtils.MEM_USAGE_NATIVE.add(size);
            memory = new UnsafeMemory(base, address, size32);
            FreeAddress freeAddress = new FreeAddress(address, size32);
            if (FREE_IMMEDIATELY) {
                drop = ArcDrop.wrap(freeAddress);
            } else {
                cleaner.register(memory, freeAddress);
            }
        } else if (allocationType == StandardAllocationTypes.ON_HEAP) {
            base = PlatformDependent.allocateUninitializedArray(size32);
            address = PlatformDependent.byteArrayBaseOffset();
            memory = new UnsafeMemory(base, address, size32);
        } else if (allocationType instanceof WrappingAllocation) {
            base = ((WrappingAllocation) allocationType).getArray();
            address = PlatformDependent.byteArrayBaseOffset();
            memory = new UnsafeMemory(base, address, size32);
        } else {
            throw new IllegalArgumentException("Unknown allocation type: " + allocationType);
        }
        return createBuffer(memory, size32, control, dropDecorator.apply(drop));
    }

    @Override
    public Buffer allocateConstChild(Buffer readOnlyConstParent) {
        UnsafeBuffer buf = (UnsafeBuffer) readOnlyConstParent;
        return buf.newConstChild();
    }

    @Override
    public Object unwrapRecoverableMemory(Buffer buf) {
        return ((UnsafeBuffer) buf).recover();
    }

    @Override
    public Buffer recoverMemory(AllocatorControl allocatorControl, Object recoverableMemory, Drop<Buffer> drop) {
        UnsafeMemory memory = (UnsafeMemory) recoverableMemory;
        int size = memory.size;
        return createBuffer(memory, size, allocatorControl, drop);
    }

    private static UnsafeBuffer createBuffer(UnsafeMemory memory, int size, AllocatorControl allocatorControl,
                                             Drop<Buffer> drop) {
        Drop<UnsafeBuffer> concreteDrop = convert(drop);
        UnsafeBuffer unsafeBuffer = new UnsafeBuffer(memory, 0, size, allocatorControl, concreteDrop);
        concreteDrop.attach(unsafeBuffer);
        return unsafeBuffer;
    }

    @Override
    public Object sliceMemory(Object memory, int offset, int length) {
        return ((UnsafeMemory) memory).slice(offset, length);
    }

    @Override
    public void clearMemory(Object memory) {
        ((UnsafeMemory) memory).clearMemory();
    }

    @Override
    public int sizeOf(Object memory) {
        return ((UnsafeMemory) memory).size;
    }

    @Override
    public String implementationName() {
        return "Unsafe";
    }
}
