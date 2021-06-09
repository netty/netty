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
package io.netty.buffer.api.unsafe;

import io.netty.buffer.api.AllocationType;
import io.netty.buffer.api.AllocatorControl;
import io.netty.buffer.api.Buffer;
import io.netty.buffer.api.Drop;
import io.netty.buffer.api.MemoryManager;
import io.netty.buffer.api.StandardAllocationTypes;
import io.netty.buffer.api.internal.Statics;
import io.netty.util.internal.PlatformDependent;

import java.lang.ref.Cleaner;

import static io.netty.buffer.api.internal.Statics.convert;

public class UnsafeMemoryManager implements MemoryManager {
    @Override
    public Buffer allocateShared(AllocatorControl allocatorControl, long size, Drop<Buffer> drop, Cleaner cleaner,
                                 AllocationType allocationType) {
        final Object base;
        final long address;
        final UnsafeMemory memory;
        final int size32 = Math.toIntExact(size);
        if (cleaner == null) {
            cleaner = Statics.CLEANER;
        }
        if (allocationType == StandardAllocationTypes.OFF_HEAP) {
            base = null;
            address = PlatformDependent.allocateMemory(size);
            PlatformDependent.setMemory(address, size, (byte) 0);
            memory = new UnsafeMemory(base, address, size32);
            drop = new CleanerDrop(memory, drop, cleaner);
        } else if (allocationType == StandardAllocationTypes.ON_HEAP) {
            base = new byte[size32];
            address = PlatformDependent.byteArrayBaseOffset();
            memory = new UnsafeMemory(base, address, size32);
        } else {
            throw new IllegalArgumentException("Unknown allocation type: " + allocationType);
        }
        return new UnsafeBuffer(memory, 0, size32, allocatorControl, convert(drop));
    }

    @Override
    public Buffer allocateConstChild(Buffer readOnlyConstParent) {
        assert readOnlyConstParent.readOnly();
        UnsafeBuffer buf = (UnsafeBuffer) readOnlyConstParent;
        return new UnsafeBuffer(buf);
    }

    @Override
    public Drop<Buffer> drop() {
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
        return new UnsafeBuffer(memory, 0, memory.size, allocatorControl, convert(drop));
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
