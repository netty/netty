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
package io.netty.buffer.api.internal;

import io.netty.buffer.api.Buffer;
import io.netty.buffer.api.BufferClosedException;
import io.netty.buffer.api.BufferReadOnlyException;
import io.netty.buffer.api.Drop;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.LongAdder;

public interface Statics {
    LongAdder MEM_USAGE_NATIVE = new LongAdder();
    Cleaner CLEANER = Cleaner.create();
    Drop<Buffer> NO_OP_DROP = new Drop<Buffer>() {
        @Override
        public void drop(Buffer obj) {
        }

        @Override
        public String toString() {
            return "NO_OP_DROP";
        }
    };
    MethodHandle BB_SLICE_OFFSETS = getByteBufferSliceOffsetsMethodHandle();
    MethodHandle BB_PUT_OFFSETS = getByteBufferPutOffsetsMethodHandle();

    static MethodHandle getByteBufferSliceOffsetsMethodHandle() {
        try {
            Lookup lookup = MethodHandles.lookup();
            MethodType type = MethodType.methodType(ByteBuffer.class, int.class, int.class);
            return lookup.findVirtual(ByteBuffer.class, "slice", type);
        } catch (Exception ignore) {
            return null;
        }
    }

    @SuppressWarnings("JavaLangInvokeHandleSignature")
    static MethodHandle getByteBufferPutOffsetsMethodHandle() {
        try {
            Lookup lookup = MethodHandles.lookup();
            MethodType type = MethodType.methodType(
                    ByteBuffer.class, int.class, ByteBuffer.class, int.class, int.class);
            return lookup.findVirtual(ByteBuffer.class, "put", type);
        } catch (Exception ignore) {
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "unused"})
    static <T extends Buffer> Drop<T> noOpDrop() {
        return (Drop<T>) NO_OP_DROP;
    }

    static VarHandle findVarHandle(Lookup lookup, Class<?> recv, String name, Class<?> type) {
        try {
            return lookup.findVarHandle(recv, name, type);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @SuppressWarnings("unchecked")
    static <T, R> Drop<R> convert(Drop<T> drop) {
        return (Drop<R>) drop;
    }

    static void copyToViaReverseCursor(Buffer src, int srcPos, Buffer dest, int destPos, int length) {
        // Iterate in reverse to account for src and dest buffer overlap.
        var itr = src.openReverseCursor(srcPos + length - 1, length);
        ByteOrder prevOrder = dest.order();
        // We read longs in BE, in reverse, so they need to be flipped for writing.
        dest.order(ByteOrder.LITTLE_ENDIAN);
        try {
            while (itr.readLong()) {
                long val = itr.getLong();
                length -= Long.BYTES;
                dest.setLong(destPos + length, val);
            }
            while (itr.readByte()) {
                dest.setByte(destPos + --length, itr.getByte());
            }
        } finally {
            dest.order(prevOrder);
        }
    }

    /**
     * The ByteBuffer slice-with-offset-and-length method is only available from Java 13 and onwards, but we need to
     * support Java 11.
     */
    static ByteBuffer bbslice(ByteBuffer buffer, int fromOffset, int length) {
        if (BB_SLICE_OFFSETS != null) {
            return bbsliceJdk13(buffer, fromOffset, length);
        }
        return bbsliceFallback(buffer, fromOffset, length);
    }

    private static ByteBuffer bbsliceJdk13(ByteBuffer buffer, int fromOffset, int length) {
        try {
            return (ByteBuffer) BB_SLICE_OFFSETS.invokeExact(buffer, fromOffset, length);
        } catch (RuntimeException re) {
            throw re;
        } catch (Throwable throwable) {
            throw new LinkageError("Unexpected exception from ByteBuffer.slice(int,int).", throwable);
        }
    }

    private static ByteBuffer bbsliceFallback(ByteBuffer buffer, int fromOffset, int length) {
        if (fromOffset < 0) {
            throw new IndexOutOfBoundsException("The fromOffset must be positive: " + fromOffset + '.');
        }
        int newLimit = fromOffset + length;
        if (newLimit > buffer.capacity()) {
            throw new IndexOutOfBoundsException(
                    "The limit of " + newLimit + " would be greater than capacity: " + buffer.capacity() + '.');
        }
        try {
            return buffer.position(fromOffset).limit(newLimit).slice();
        } finally {
            buffer.clear();
        }
    }

    /**
     * The ByteBuffer put-buffer-with-offset-and-length method is not available in Java 11.
     */
    static void bbput(ByteBuffer dest, int destPos, ByteBuffer src, int srcPos, int length) {
        if (BB_PUT_OFFSETS != null) {
            bbputJdk16(dest, destPos, src, srcPos, length);
        } else {
            bbputFallback(dest, destPos, src, srcPos, length);
        }
    }

    private static void bbputJdk16(ByteBuffer dest, int destPos, ByteBuffer src, int srcPos, int length) {
        try {
            @SuppressWarnings("unused") // We need to cast the return type in order to invokeExact.
            ByteBuffer ignore = (ByteBuffer) BB_PUT_OFFSETS.invokeExact(dest, destPos, src, srcPos, length);
        } catch (RuntimeException re) {
            throw re;
        } catch (Throwable throwable) {
            throw new LinkageError("Unexpected exception from ByteBuffer.put(int,ByteBuffer,int,int).", throwable);
        }
    }

    private static void bbputFallback(ByteBuffer dest, int destPos, ByteBuffer src, int srcPos, int length) {
        dest.position(destPos).put(bbslice(src, srcPos, length));
    }

    static BufferClosedException bufferIsClosed(Buffer buffer) {
        return new BufferClosedException("This buffer is closed: " + buffer);
    }

    static BufferReadOnlyException bufferIsReadOnly(Buffer buffer) {
        return new BufferReadOnlyException("This buffer is read-only: " + buffer);
    }

    static <T> T acquire(ResourceSupport<?, ?> obj) {
        return ResourceSupport.acquire(obj);
    }

    static boolean isOwned(ResourceSupport<?, ?> obj) {
        return ResourceSupport.isOwned(obj);
    }

    static int countBorrows(ResourceSupport<?, ?> obj) {
        return ResourceSupport.countBorrows(obj);
    }
}
