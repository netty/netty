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
package io.netty5.buffer.internal;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferClosedException;
import io.netty5.buffer.BufferComponent;
import io.netty5.buffer.BufferReadOnlyException;
import io.netty5.buffer.Drop;
import io.netty5.buffer.LeakInfo;
import io.netty5.buffer.MemoryManager;
import io.netty5.util.AsciiString;
import io.netty5.util.internal.PlatformDependent;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

import static io.netty5.util.internal.ObjectUtil.checkPositiveOrZero;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;

public interface InternalBufferUtils {
    LongAdder MEM_USAGE_NATIVE = new LongAdder();

    static Cleaner getCleaner() {
        return CleanerPool.INSTANCE.getCleaner();
    }

    Drop<Buffer> NO_OP_DROP = new Drop<>() {
        @Override
        public void drop(Buffer obj) {
        }

        @Override
        public Drop<Buffer> fork() {
            return this;
        }

        @Override
        public void attach(Buffer obj) {
        }

        @Override
        public String toString() {
            return "NO_OP_DROP";
        }
    };
    MethodHandle BB_SLICE_OFFSETS = getByteBufferSliceOffsetsMethodHandle();
    MethodHandle BB_PUT_OFFSETS = getByteBufferPutOffsetsMethodHandle();
    MethodHandle BB_PUT_ARRAY_OFFSETS = getByteBufferPutArrayOffsetsMethodHandle();
    /**
     * The maximum buffer size we support is the maximum array length generally supported by JVMs,
     * because on-heap buffers will be backed by byte-arrays.
     */
    int MAX_BUFFER_SIZE = Integer.MAX_VALUE - 8;

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

    @SuppressWarnings("JavaLangInvokeHandleSignature")
    static MethodHandle getByteBufferPutArrayOffsetsMethodHandle() {
        try {
            Lookup lookup = MethodHandles.lookup();
            MethodType type = MethodType.methodType(
                    ByteBuffer.class, int.class, byte[].class, int.class, int.class);
            return lookup.findVirtual(ByteBuffer.class, "put", type);
        } catch (Exception ignore) {
            return null;
        }
    }

    static Function<Drop<Buffer>, Drop<Buffer>> standardDrop(MemoryManager manager) {
        return drop -> CleanerDrop.wrap(drop, manager);
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

    /**
     * Check the given {@code size} argument is a valid buffer size, or throw an {@link IllegalArgumentException}.
     *
     * @param size The size to check.
     * @throws IllegalArgumentException if the size is not positive, or if the size is too big (over ~2 GB) for a
     * buffer to accommodate.
     */
    static void assertValidBufferSize(long size) {
        if (size < 0) {
            throw bufferSizeNegative(size);
        }
        if (size > MAX_BUFFER_SIZE) {
            throw bufferSizeTooBig(size);
        }
    }

    @NotNull
    private static IllegalArgumentException bufferSizeNegative(long size) {
        return new IllegalArgumentException("Buffer size must not be negative, but was " + size + '.');
    }

    @NotNull
    private static IllegalArgumentException bufferSizeTooBig(long size) {
        return new IllegalArgumentException(
                "Buffer size cannot be greater than " + MAX_BUFFER_SIZE + ", but was " + size + '.');
    }

    static void checkImplicitCapacity(int implicitCapacity, int currentCapacity) {
        if (implicitCapacity < currentCapacity) {
            throw new IndexOutOfBoundsException(
                    "Implicit capacity limit (" + implicitCapacity +
                    ") cannot be less than capacity (" + currentCapacity + ')');
        }
        if (implicitCapacity > MAX_BUFFER_SIZE) {
            throw new IndexOutOfBoundsException(
                    "Implicit capacity limit (" + implicitCapacity +
                    ") cannot be greater than max buffer size (" + MAX_BUFFER_SIZE + ')');
        }
    }

    static void checkLength(int length) {
        if (length < 0) {
            throw new IndexOutOfBoundsException("The length cannot be negative: " + length + '.');
        }
    }

    static void copyToViaReverseLoop(Buffer src, int srcPos, Buffer dest, int destPos, int length) {
        checkLength(length);
        if (length == 0) {
            return;
        }
        // Iterate in reverse to account for src and dest buffer overlap.
        int i = length;
        while (i >= Long.BYTES) {
            i -= Long.BYTES;
            dest.setLong(destPos + i, src.getLong(srcPos + i));
        }
        while (i > 0) {
            i--;
            dest.setByte(destPos + i, src.getByte(srcPos + i));
        }
    }

    static ByteBuffer tryGetWritableBufferFromReadableComponent(BufferComponent component) {
        if (component instanceof NotReadOnlyReadableComponent) {
            return ((NotReadOnlyReadableComponent) component).mutableReadableBuffer();
        }
        return null;
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

    @SuppressWarnings("DataFlowIssue")
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
        return buffer.duplicate().clear().position(fromOffset).limit(newLimit).slice();
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

    /**
     * The ByteBuffer put-with-array-offset-and-length method is not available in Java 11.
     */
    static void bbput(ByteBuffer dest, int destPos, byte[] src, int srcPos, int length) {
        if (BB_PUT_ARRAY_OFFSETS != null) {
            bbputJdk16(dest, destPos, src, srcPos, length);
        } else {
            bbputFallback(dest, destPos, src, srcPos, length);
        }
    }

    private static void bbputJdk16(ByteBuffer dest, int destPos, byte[] src, int srcPos, int length) {
        try {
            @SuppressWarnings("unused") // We need to cast the return type in order to invokeExact.
            ByteBuffer ignore = (ByteBuffer) BB_PUT_ARRAY_OFFSETS.invokeExact(dest, destPos, src, srcPos, length);
        } catch (RuntimeException re) {
            throw re;
        } catch (Throwable throwable) {
            throw new LinkageError("Unexpected exception from ByteBuffer.put(int,byte[],int,int).", throwable);
        }
    }

    private static void bbputFallback(ByteBuffer dest, int destPos, byte[] src, int srcPos, int length) {
        dest.position(destPos).put(src, srcPos, length);
    }

    static void setMemory(ByteBuffer buffer, int length, byte value) {
        if (!buffer.hasArray()) {
            long address;
            if (PlatformDependent.hasUnsafe() && (address = nativeAddressOfDirectByteBuffer(buffer)) != 0) {
                PlatformDependent.setMemory(address, length, value);
            } else {
                final int intFillValue = (value & 0xFF) * 0x01010101;
                final int intCount = length >>> 2;
                for (int i = 0; i < intCount; i++) {
                    buffer.putInt(i << 2, intFillValue);
                }
                final int byteCount = length & 3;
                final int bytesOffset = intCount << 2;
                for (int i = 0; i < byteCount; i++) {
                    buffer.put(bytesOffset + i, value);
                }
            }
        } else {
            final int start = buffer.arrayOffset();
            if (PlatformDependent.hasUnsafe()) {
                PlatformDependent.setMemory(buffer.array(), start, length, value);
            } else {
                final int end = start + length;
                Arrays.fill(buffer.array(), start, end, value);
            }
        }
    }

    static BufferClosedException bufferIsClosed(Buffer buffer) {
        return new BufferClosedException("This buffer is closed: " + buffer);
    }

    static BufferReadOnlyException bufferIsReadOnly(Buffer buffer) {
        return new BufferReadOnlyException("This buffer is read-only: " + buffer);
    }

    static IllegalStateException allocatorClosedException() {
        return new IllegalStateException("This allocator has been closed.");
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

    static <E extends Throwable> E attachTrace(ResourceSupport<?, ?> obj, E throwable) {
        return ResourceSupport.getTracer(obj).attachTrace(throwable);
    }

    static Collection<LeakInfo.TracePoint> collectLifecycleTrace(ResourceSupport<?, ?> obj) {
        return ResourceSupport.getTracer(obj).collectTraces();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    static void unsafeSetDrop(ResourceSupport<?, ?> obj, Drop<?> replacement) {
        obj.unsafeSetDrop((Drop) replacement);
    }

    static CharSequence readCharSequence(Buffer source, int length, Charset charset) {
        final CharSequence charSequence = copyToCharSequence(source, source.readerOffset(), length, charset);
        source.skipReadableBytes(length);
        return charSequence;
    }

    static String toString(Buffer source, Charset charset) {
        return copyToCharSequence(source, source.readerOffset(), source.readableBytes(), charset).toString();
    }

    static CharSequence copyToCharSequence(Buffer source, int srcIdx, int length, Charset charset) {
        byte[] data = new byte[length];
        source.copyInto(srcIdx, data, 0, length);
        if (US_ASCII.equals(charset)) {
            return new AsciiString(data).toString();
        }
        return new String(data, 0, length, charset);
    }

    static void writeCharSequence(CharSequence source, Buffer destination, Charset charset) {
        if (US_ASCII.equals(charset) && source instanceof AsciiString) {
            AsciiString asciiString = (AsciiString) source;
            destination.writeBytes(asciiString.array(), asciiString.arrayOffset(), source.length());
            return;
        }
        // TODO: Copy optimized writes from BufferUtil
        byte[] bytes = source.toString().getBytes(charset);
        destination.writeBytes(bytes);
    }

    static boolean equals(Buffer bufferA, Buffer bufferB) {
        if (bufferA == null && bufferB != null || bufferB == null && bufferA != null) {
            return false;
        }
        if (bufferA == bufferB) {
            return true;
        }
        final int aLen = bufferA.readableBytes();
        if (aLen != bufferB.readableBytes()) {
            return false;
        }
        return equals(bufferA, bufferA.readerOffset(), bufferB, bufferB.readerOffset(), aLen);
    }

    static boolean equals(Buffer a, int aStartIndex, Buffer b, int bStartIndex, int length) {
        requireNonNull(a, "a");
        requireNonNull(b, "b");
        // All indexes and lengths must be non-negative
        checkPositiveOrZero(aStartIndex, "aStartIndex");
        checkPositiveOrZero(bStartIndex, "bStartIndex");
        checkPositiveOrZero(length, "length");

        if (a.writerOffset() - length < aStartIndex || b.writerOffset() - length < bStartIndex) {
            return false;
        }

        return equalsInner(a, aStartIndex, b, bStartIndex, length);
    }

    private static boolean equalsInner(Buffer a, int aStartIndex, Buffer b, int bStartIndex, int length) {
        final int longCount = length >>> 3;
        final int byteCount = length & 7;

        for (int i = longCount; i > 0; i --) {
            if (a.getLong(aStartIndex) != b.getLong(bStartIndex)) {
                return false;
            }
            aStartIndex += 8;
            bStartIndex += 8;
        }

        for (int i = byteCount; i > 0; i --) {
            if (a.getByte(aStartIndex) != b.getByte(bStartIndex)) {
                return false;
            }
            aStartIndex++;
            bStartIndex++;
        }

        return true;
    }

    static int hashCode(Buffer buffer) {
        final int aLen = buffer.readableBytes();
        final int intCount = aLen >>> 2;
        final int byteCount = aLen & 3;

        int hashCode = 0;
        int arrayIndex = buffer.readerOffset();
        for (int i = intCount; i > 0; i --) {
            hashCode = 31 * hashCode + buffer.getInt(arrayIndex);
            arrayIndex += 4;
        }

        for (int i = byteCount; i > 0; i --) {
            hashCode = 31 * hashCode + buffer.getByte(arrayIndex ++);
        }

        if (hashCode == 0) {
            hashCode = 1;
        }

        return hashCode;
    }

    /**
     * Compute an offset into a native address.
     * Zero is used as a marker for when a native address is not available,
     * and an offset into a zero address will remain zero.
     *
     * @param address The native address, or zero if no native address is available.
     * @param offset The offset into the native address we wish to compute.
     * @return An offsetted native address, or zero if no native address was available.
     */
    static long nativeAddressWithOffset(long address, int offset) {
        if (address == 0) {
            return 0;
        }
        return address + offset;
    }

    static long nativeAddressOfDirectByteBuffer(ByteBuffer byteBuffer) {
        if (!byteBuffer.isDirect()) {
            return 0;
        }
        if (PlatformDependent.hasUnsafe()) {
            return PlatformDependent.directBufferAddress(byteBuffer);
        }
        return MemorySegment.ofBuffer(byteBuffer).address();
    }

    /**
     * This interface provides the fastest possible offsetted byte-access to a buffer.
     * Used by {@link #bytesBefore(Buffer, UncheckedLoadByte, Buffer, UncheckedLoadByte)} to access memory faster.
     */
    interface UncheckedLoadByte {
        byte load(Buffer buffer, int offset);
    }

    UncheckedLoadByte UNCHECKED_LOAD_BYTE_BUFFER = Buffer::getByte;

    static int bytesBefore(Buffer haystack, UncheckedLoadByte hl,
                           Buffer needle, UncheckedLoadByte nl) {
        if (!haystack.isAccessible()) {
            throw bufferIsClosed(haystack);
        }
        if (!needle.isAccessible()) {
            throw bufferIsClosed(needle);
        }

        if (needle.readableBytes() > haystack.readableBytes()) {
            return -1;
        }

        if (hl == null) {
            hl = UNCHECKED_LOAD_BYTE_BUFFER;
        }
        if (nl == null) {
            nl = UNCHECKED_LOAD_BYTE_BUFFER;
        }

        int haystackLen = haystack.readableBytes();
        int needleLen = needle.readableBytes();
        if (needleLen == 0) {
            return 0;
        }

        // When the needle has only one byte that can be read,
        // the Buffer.bytesBefore() method can be used
        if (needleLen == 1) {
            return haystack.bytesBefore(needle.getByte(needle.readerOffset()));
        }

        int needleStart = needle.readerOffset();
        int haystackStart = haystack.readerOffset();
        long suffixes =  maxFixes(needle, nl, needleLen, needleStart, true);
        long prefixes = maxFixes(needle, nl, needleLen, needleStart, false);
        int maxSuffix = Math.max((int) (suffixes >> 32), (int) (prefixes >> 32));
        int period = Math.max((int) suffixes, (int) prefixes);
        int length = Math.min(needleLen - period, maxSuffix + 1);

        if (equalsInner(needle, needleStart, needle, needleStart + period, length)) {
            return bytesBeforeInnerPeriodic(
                    haystack, hl, needle, nl, haystackLen, needleLen, needleStart, haystackStart, maxSuffix, period);
        }
        return bytesBeforeInnerNonPeriodic(
                haystack, hl, needle, nl, haystackLen, needleLen, needleStart, haystackStart, maxSuffix);
    }

    private static int bytesBeforeInnerPeriodic(Buffer haystack, UncheckedLoadByte hl,
                                                Buffer needle, UncheckedLoadByte nl,
                                                int haystackLen, int needleLen, int needleStart, int haystackStart,
                                                int maxSuffix, int period) {
        int j = 0;
        int memory = -1;
        while (j <= haystackLen - needleLen) {
            int i = Math.max(maxSuffix, memory) + 1;
            while (i < needleLen && nl.load(needle, i + needleStart) == hl.load(haystack, i + j + haystackStart)) {
                ++i;
            }
            if (i > haystackLen) {
                return -1;
            }
            if (i >= needleLen) {
                i = maxSuffix;
                while (i > memory && nl.load(needle, i + needleStart) == hl.load(haystack, i + j + haystackStart)) {
                    --i;
                }
                if (i <= memory) {
                    return j;
                }
                j += period;
                memory = needleLen - period - 1;
            } else {
                j += i - maxSuffix;
                memory = -1;
            }
        }
        return -1;
    }

    private static int bytesBeforeInnerNonPeriodic(Buffer haystack, UncheckedLoadByte hl,
                                                   Buffer needle, UncheckedLoadByte nl,
                                                   int haystackLen, int needleLen, int needleStart, int haystackStart,
                                                   int maxSuffix) {
        int j = 0;
        int period = Math.max(maxSuffix + 1, needleLen - maxSuffix - 1) + 1;
        while (j <= haystackLen - needleLen) {
            int i = maxSuffix + 1;
            while (i < needleLen && nl.load(needle, i + needleStart) == hl.load(haystack, i + j + haystackStart)) {
                ++i;
            }
            if (i > haystackLen) {
                return -1;
            }
            if (i >= needleLen) {
                i = maxSuffix;
                while (i >= 0 && nl.load(needle, i + needleStart) == hl.load(haystack, i + j + haystackStart)) {
                    --i;
                }
                if (i < 0) {
                    return j;
                }
                j += period;
            } else {
                j += i - maxSuffix;
            }
        }
        return -1;
    }

    private static long maxFixes(Buffer needle, UncheckedLoadByte nl, int needleLen, int start, boolean isSuffix) {
        int period = 1;
        int maxSuffix = -1;
        int lastRest = start;
        int k = 1;
        while (lastRest + k < needleLen) {
            byte a = nl.load(needle, lastRest + k);
            byte b = nl.load(needle, maxSuffix + k);
            boolean suffix = isSuffix ? a < b : a > b;
            if (suffix) {
                lastRest += k;
                k = 1;
                period = lastRest - maxSuffix;
            } else if (a == b) {
                if (k != period) {
                    ++k;
                } else {
                    lastRest += period;
                    k = 1;
                }
            } else {
                maxSuffix = lastRest;
                lastRest = maxSuffix + 1;
                k = period = 1;
            }
        }
        return ((long) maxSuffix << 32) + period;
    }
}
