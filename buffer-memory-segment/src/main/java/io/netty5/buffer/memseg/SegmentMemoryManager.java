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
package io.netty5.buffer.memseg;

import io.netty5.buffer.AllocationType;
import io.netty5.buffer.AllocatorControl;
import io.netty5.buffer.Buffer;
import io.netty5.buffer.Drop;
import io.netty5.buffer.MemoryManager;
import io.netty5.buffer.StandardAllocationTypes;
import io.netty5.buffer.internal.ArcDrop;
import io.netty5.buffer.internal.InternalBufferUtils;
import io.netty5.buffer.internal.WrappingAllocation;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.function.Function;

public class SegmentMemoryManager implements MemoryManager {
    private static Buffer createHeapBuffer(
            long size, Function<Drop<Buffer>, Drop<Buffer>> adaptor, AllocatorControl control) {
        var segment = MemorySegment.ofArray(new byte[Math.toIntExact(size)]);
        var drop = adaptor.apply(InternalBufferUtils.NO_OP_DROP);
        return createBuffer(segment, drop, control);
    }

    private static Buffer createNativeBuffer(
            long size, Function<Drop<Buffer>, Drop<Buffer>> adaptor, AllocatorControl control) {
        Arena arena = Arena.ofShared();
        InternalBufferUtils.MEM_USAGE_NATIVE.add(size);
        var segment = arena.allocate(size);
        var drop = adaptor.apply(drop(arena, size));
        return createBuffer(segment, drop, control);
    }

    @Override
    public Buffer allocateShared(AllocatorControl control, long size, Function<Drop<Buffer>, Drop<Buffer>> adaptor,
                                 AllocationType type) {
        if (type instanceof StandardAllocationTypes stype) {
            return switch (stype) {
                case ON_HEAP -> createHeapBuffer(size, adaptor, control);
                case OFF_HEAP -> createNativeBuffer(size, adaptor, control);
            };
        }
        if (type instanceof WrappingAllocation allocation) {
            var seg = MemorySegment.ofArray(allocation.getArray());
            return createBuffer(seg, adaptor.apply(InternalBufferUtils.NO_OP_DROP), control);
        }
        throw new IllegalArgumentException("Unknown allocation type: " + type);
    }

    @Override
    public Buffer allocateConstChild(Buffer readOnlyConstParent) {
        assert readOnlyConstParent.readOnly();
        MemSegBuffer buf = (MemSegBuffer) readOnlyConstParent;
        return buf.newConstChild();
    }

    private static Drop<Buffer> drop(Arena arena, long nativeMemoryReserved) {
        var drop = new MemorySessionCloseDrop(arena, nativeMemoryReserved);
        // Wrap in an ArcDrop because closing the session will close all associated memory segments.
        return ArcDrop.wrap(drop);
    }

    @Override
    public Object unwrapRecoverableMemory(Buffer buf) {
        var b = (MemSegBuffer) buf;
        return b.recoverableMemory();
    }

    @Override
    public Buffer recoverMemory(AllocatorControl allocatorControl, Object recoverableMemory, Drop<Buffer> drop) {
        var segment = (MemorySegment) recoverableMemory;
        return createBuffer(segment, drop, allocatorControl);
    }

    private static MemSegBuffer createBuffer(MemorySegment segment, Drop<Buffer> drop, AllocatorControl control) {
        Drop<MemSegBuffer> concreteDrop = InternalBufferUtils.convert(drop);
        MemSegBuffer buffer = new MemSegBuffer(segment, segment, control, concreteDrop);
        concreteDrop.attach(buffer);
        return buffer;
    }

    @Override
    public Object sliceMemory(Object memory, int offset, int length) {
        var segment = (MemorySegment) memory;
        return segment.asSlice(offset, length);
    }

    @Override
    public void clearMemory(Object memory) {
        var segment = (MemorySegment) memory;
        segment.fill((byte) 0);
    }

    @Override
    public int sizeOf(Object memory) {
        var segment = (MemorySegment) memory;
        return Math.toIntExact(segment.byteSize());
    }

    @Override
    public String implementationName() {
        return "MemorySegment";
    }

    private record MemorySessionCloseDrop(Arena arena, long nativeMemoryReserved) implements Drop<Buffer> {
        @Override
        public void drop(Buffer obj) {
            arena.close();
            InternalBufferUtils.MEM_USAGE_NATIVE.add(-nativeMemoryReserved);
        }

        @Override
        public Drop<Buffer> fork() {
            return this;
        }

        @Override
        public void attach(Buffer obj) {
        }
    }
}
