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
package io.netty5.buffer.api.unsafe;

import io.netty5.buffer.api.AllocationType;
import io.netty5.buffer.api.AllocatorControl;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.Drop;
import io.netty5.buffer.api.MemoryManager;
import io.netty5.buffer.api.StandardAllocationTypes;
import io.netty5.buffer.api.internal.Statics;
import io.netty5.buffer.api.internal.WrappingAllocation;
import io.netty5.util.internal.PlatformDependent;

import java.lang.ref.Cleaner;
import java.util.function.Function;

import static io.netty5.buffer.api.internal.Statics.convert;

/**
 * This memory manager produces and manages {@link Buffer} instances that are using {@code Unsafe} to allocate and
 * access memory.
 * <p>
 * Memory managers are normally not used directly.
 * Instead, you likely want to use the {@link io.netty5.buffer.api.DefaultBufferAllocators}, or the static methods on
 * {@link io.netty5.buffer.api.BufferAllocator}.
 */
public final class UnsafeMemoryManager implements MemoryManager {
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
        Cleaner cleaner = Statics.CLEANER;
        if (allocationType == StandardAllocationTypes.OFF_HEAP) {
            base = null;
            address = PlatformDependent.allocateMemory(size);
            Statics.MEM_USAGE_NATIVE.add(size);
            PlatformDependent.setMemory(address, size, (byte) 0);
            memory = new UnsafeMemory(base, address, size32);
            cleaner.register(memory, new FreeAddress(address, size32));
        } else if (allocationType == StandardAllocationTypes.ON_HEAP) {
            base = new byte[size32];
            address = PlatformDependent.byteArrayBaseOffset();
            memory = new UnsafeMemory(base, address, size32);
        } else if (allocationType instanceof WrappingAllocation) {
            base = ((WrappingAllocation) allocationType).getArray();
            address = PlatformDependent.byteArrayBaseOffset();
            memory = new UnsafeMemory(base, address, size32);
        } else {
            throw new IllegalArgumentException("Unknown allocation type: " + allocationType);
        }
        return createBuffer(memory, size32, control, dropDecorator.apply(drop()));
    }

    @Override
    public Buffer allocateConstChild(Buffer readOnlyConstParent) {
        UnsafeBuffer buf = (UnsafeBuffer) readOnlyConstParent;
        return buf.newConstChild();
    }

    private static Drop<Buffer> drop() {
        // We cannot reliably drop unsafe memory. We have to rely on the cleaner to do that.
        return Statics.NO_OP_DROP;
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
    public String implementationName() {
        return "Unsafe";
    }
}
