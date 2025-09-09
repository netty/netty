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

import io.netty5.buffer.AllocatorControl;
import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.buffer.BufferClosedException;
import io.netty5.buffer.BufferComponent;
import io.netty5.buffer.BufferReadOnlyException;
import io.netty5.buffer.ByteCursor;
import io.netty5.buffer.ComponentIterator;
import io.netty5.buffer.Drop;
import io.netty5.buffer.Owned;
import io.netty5.buffer.internal.AdaptableBuffer;
import io.netty5.buffer.internal.InternalBufferUtils;
import io.netty5.buffer.internal.NotReadOnlyReadableComponent;
import io.netty5.buffer.internal.InternalBufferUtils.UncheckedLoadByte;
import io.netty5.util.internal.PlatformDependent;
import io.netty5.util.internal.SWARUtil;
import io.netty5.util.internal.UnsafeAccess;
import sun.misc.Unsafe;

import java.io.IOException;
import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import static io.netty5.buffer.internal.InternalBufferUtils.MAX_BUFFER_SIZE;
import static io.netty5.buffer.internal.InternalBufferUtils.bufferIsReadOnly;
import static io.netty5.buffer.internal.InternalBufferUtils.checkImplicitCapacity;
import static io.netty5.buffer.internal.InternalBufferUtils.checkLength;
import static io.netty5.buffer.internal.InternalBufferUtils.nativeAddressWithOffset;
import static io.netty5.util.internal.ObjectUtil.checkPositiveOrZero;
import static io.netty5.util.internal.PlatformDependent.roundToPowerOfTwo;

@UnsafeAccess
final class UnsafeBuffer extends AdaptableBuffer<UnsafeBuffer>
        implements BufferComponent, NotReadOnlyReadableComponent, ComponentIterator<UnsafeBuffer>,
                   ComponentIterator.Next {
    private static final int CLOSED_SIZE = -1;
    private static final boolean ACCESS_UNALIGNED = PlatformDependent.isUnaligned();
    private static final boolean FLIP_BYTES = ByteOrder.BIG_ENDIAN != ByteOrder.nativeOrder();
    private static final Unsafe UNSAFE = (Unsafe) PlatformDependent.unwrapUnsafeOrNull();
    private UnsafeMemory memory; // The memory liveness; monitored by Cleaner.
    private Object base; // On-heap address reference object, or null for off-heap.
    private long baseOffset; // Offset of this buffer into the memory.
    private long address; // Resolved address (baseOffset + memory.address).
    private int rsize;
    private int wsize;
    private boolean readOnly;
    private int roff;
    private int woff;
    private int implicitCapacityLimit;

    UnsafeBuffer(UnsafeMemory memory, long offset, int size, AllocatorControl control,
                        Drop<UnsafeBuffer> drop) {
        super(drop, control);
        this.memory = memory;
        base = memory.base;
        baseOffset = offset;
        address = memory.address + offset;
        rsize = size;
        wsize = size;
        implicitCapacityLimit = MAX_BUFFER_SIZE;
    }

    /**
     * Constructor for {@linkplain BufferAllocator#constBufferSupplier(byte[]) const buffers}.
     */
    private UnsafeBuffer(UnsafeBuffer parent, Drop<UnsafeBuffer> drop) {
        super(drop, parent.control);
        implicitCapacityLimit = parent.implicitCapacityLimit;
        memory = parent.memory;
        base = parent.base;
        baseOffset = parent.baseOffset;
        address = parent.address;
        rsize = parent.rsize;
        wsize = parent.wsize;
        readOnly = parent.readOnly;
        roff = parent.roff;
        woff = parent.woff;
    }

    @Override
    public String toString() {
        return "Buffer[roff:" + roff + ", woff:" + woff + ", cap:" + rsize + ']';
    }

    @Override
    protected RuntimeException createResourceClosedException() {
        return InternalBufferUtils.bufferIsClosed(this);
    }

    @Override
    public int capacity() {
        return Math.max(0, rsize); // Use Math.max to make capacity of closed buffer equal to zero.
    }

    @Override
    public int readerOffset() {
        return roff;
    }

    @Override
    public Buffer readerOffset(int offset) {
        checkRead(offset, 0);
        roff = offset;
        return this;
    }

    @Override
    public int writerOffset() {
        return woff;
    }

    @Override
    public Buffer writerOffset(int offset) {
        checkWrite(offset, 0, false);
        woff = offset;
        return this;
    }

    @Override
    public int readableBytes() {
        return super.readableBytes();
    }

    @Override
    public int writableBytes() {
        return super.writableBytes();
    }

    @Override
    public UnsafeBuffer skipReadableBytes(int delta) {
        return (UnsafeBuffer) super.skipReadableBytes(delta);
    }

    @Override
    public UnsafeBuffer skipWritableBytes(int delta) {
        return (UnsafeBuffer) super.skipWritableBytes(delta);
    }

    @Override
    public Buffer fill(byte value) {
        checkSet(0, capacity());
        if (rsize == CLOSED_SIZE) {
            throw bufferIsClosed();
        }
        try {
            PlatformDependent.setMemory(base, address, rsize, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    private long nativeAddress() {
        return base == null? address : 0;
    }

    @Override
    public Buffer makeReadOnly() {
        readOnly = true;
        wsize = CLOSED_SIZE;
        return this;
    }

    @Override
    public boolean readOnly() {
        return readOnly;
    }

    @Override
    public boolean isDirect() {
        return base == null;
    }

    @Override
    public Buffer implicitCapacityLimit(int limit) {
        checkImplicitCapacity(limit,  capacity());
        implicitCapacityLimit = limit;
        return this;
    }

    @Override
    public int implicitCapacityLimit() {
        return implicitCapacityLimit;
    }

    @Override
    public Buffer copy(int offset, int length, boolean readOnly) {
        checkLength(length);
        checkGet(offset, length);
        if (readOnly && readOnly()) {
            // If both this buffer and the copy are read-only, they can safely share the memory.
            UnsafeBuffer copy = newConstChild();
            copy.baseOffset += offset;
            copy.address += offset;
            copy.rsize = length;
            copy.roff = 0;
            copy.woff = length;
            return copy;
        }
        Buffer copy = control.getAllocator().allocate(length);
        try {
            copyInto(offset, copy, 0, length);
            copy.writerOffset(length);
            if (readOnly) {
                copy.makeReadOnly();
            }
            return copy;
        } catch (Throwable e) {
            copy.close();
            throw e;
        }
    }

    @Override
    public void copyInto(int srcPos, byte[] dest, int destPos, int length) {
        checkCopyIntoArgs(srcPos, length, destPos, dest.length);
        copyIntoArray(srcPos, dest, destPos, length);
    }

    private void copyIntoArray(int srcPos, byte[] dest, int destPos, int length) {
        long destOffset = PlatformDependent.byteArrayBaseOffset();
        try {
            PlatformDependent.copyMemory(base, address + srcPos, dest, destOffset + destPos, length);
        } finally {
            Reference.reachabilityFence(memory);
            Reference.reachabilityFence(dest);
        }
    }

    @Override
    public void copyInto(int srcPos, ByteBuffer dest, int destPos, int length) {
        checkCopyIntoArgs(srcPos, length, destPos, dest.capacity());
        if (dest.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }
        if (dest.hasArray()) {
            copyIntoArray(srcPos, dest.array(), dest.arrayOffset() + destPos, length);
        } else {
            assert dest.isDirect();
            long destAddr = PlatformDependent.directBufferAddress(dest);
            try {
                PlatformDependent.copyMemory(base, address + srcPos, null, destAddr + destPos, length);
            } finally {
                Reference.reachabilityFence(memory);
                Reference.reachabilityFence(dest);
            }
        }
    }

    private void checkCopyIntoArgs(int srcPos, int length, int destPos, int destLength) {
        if (rsize == CLOSED_SIZE) {
            throw bufferIsClosed();
        }
        if (srcPos < 0) {
            throw new IndexOutOfBoundsException("The srcPos cannot be negative: " + srcPos + '.');
        }
        checkLength(length);
        if (rsize < srcPos + length) {
            throw new IndexOutOfBoundsException("The srcPos + length is beyond the end of the buffer: " +
                    "srcPos = " + srcPos + ", length = " + length + '.');
        }
        if (destPos < 0) {
            throw new IndexOutOfBoundsException("The destPos cannot be negative: " + destPos + '.');
        }
        if (destLength < destPos + length) {
            throw new IndexOutOfBoundsException("The destPos + length is beyond the end of the destination: " +
                    "destPos = " + destPos + ", length = " + length + '.');
        }
    }

    @Override
    public void copyInto(int srcPos, Buffer dest, int destPos, int length) {
        if (!dest.isAccessible()) {
            throw InternalBufferUtils.bufferIsClosed(dest);
        }
        checkCopyIntoArgs(srcPos, length, destPos, dest.capacity());
        if (dest.readOnly()) {
            throw bufferIsReadOnly(this);
        }
        try {
            if (dest instanceof UnsafeBuffer) {
                UnsafeBuffer destUnsafe = (UnsafeBuffer) dest;
                long nativeAddress = destUnsafe.nativeAddress();
                if (nativeAddress != 0) {
                    PlatformDependent.copyMemory(base, address + srcPos, null, nativeAddress + destPos, length);
                } else {
                    PlatformDependent.copyMemory(
                            base, address + srcPos, destUnsafe.base, destUnsafe.address + destPos, length);
                }
            } else {
                InternalBufferUtils.copyToViaReverseLoop(this, srcPos, dest, destPos, length);
            }
        } finally {
            Reference.reachabilityFence(memory);
            Reference.reachabilityFence(dest);
        }
    }

    @Override
    public int transferTo(WritableByteChannel channel, int length) throws IOException {
        if (!isAccessible()) {
            throw bufferIsClosed();
        }
        length = Math.min(readableBytes(), length);
        if (length == 0) {
            return 0;
        }
        checkGet(readerOffset(), length);
        int bytesWritten = channel.write(readableBuffer().limit(length));
        skipReadableBytes(bytesWritten);
        return bytesWritten;
    }

    @Override
    public int transferTo(FileChannel channel, long position, int length) throws IOException {
        if (!isAccessible()) {
            throw bufferIsClosed();
        }
        length = Math.min(readableBytes(), length);
        if (length == 0) {
            return 0;
        }
        checkGet(readerOffset(), length);
        int bytesWritten = channel.write(readableBuffer().limit(length), position);
        skipReadableBytes(bytesWritten);
        return bytesWritten;
    }

    @Override
    public int transferFrom(FileChannel channel, long position, int length) throws IOException {
        checkPositiveOrZero(position, "position");
        checkPositiveOrZero(length, "length");
        if (!isAccessible()) {
            throw bufferIsClosed();
        }
        if (readOnly()) {
            throw bufferIsReadOnly(this);
        }
        length = Math.min(writableBytes(), length);
        if (length == 0) {
            return 0;
        }
        checkSet(writerOffset(), length);
        int bytesRead = channel.read(writableBuffer().limit(length), position);
        if (bytesRead > 0) { // Don't skipWritable if bytesRead is 0 or -1
            skipWritableBytes(bytesRead);
        }
        return bytesRead;
    }

    @Override
    public int transferFrom(ReadableByteChannel channel, int length) throws IOException {
        if (!isAccessible()) {
            throw bufferIsClosed();
        }
        if (readOnly()) {
            throw bufferIsReadOnly(this);
        }
        length = Math.min(writableBytes(), length);
        if (length == 0) {
            return 0;
        }
        checkSet(writerOffset(), length);
        int bytesRead = channel.read(writableBuffer().limit(length));
        if (bytesRead != -1) {
            skipWritableBytes(bytesRead);
        }
        return bytesRead;
    }

    /**
     * Most deployment platforms support unaligned access, and are little-endian.
     * This allows us to micro-optimise for this common case.
     */
    private static final boolean BYTES_BEFORE_USE_LITTLE_ENDIAN =
            PlatformDependent.isUnaligned() && ByteOrder.nativeOrder() != ByteOrder.BIG_ENDIAN;

    @Override
    public int bytesBefore(byte needle) {
        // For the details of this algorithm, see Hacker's Delight, Chapter 6, Searching Words.
        // Richard Startin also describes this on his blog: https://richardstartin.github.io/posts/finding-bytes
        if (!isAccessible()) {
            throw bufferIsClosed();
        }
        try {
            int offset = roff;
            final int length = woff - roff;
            final int end = woff;
            final long addr = address;

            if (length > 7) {
                final long pattern = SWARUtil.compilePattern(needle);
                for (final int longEnd = offset + (length >>> 3) * Long.BYTES;
                     offset < longEnd;
                     offset += Long.BYTES) {
                    final long word = BYTES_BEFORE_USE_LITTLE_ENDIAN?
                            PlatformDependent.getLong(base, addr + offset) :
                            loadLong(addr + offset);

                    final long result = SWARUtil.applyPattern(word, pattern);
                    if (result != 0) {
                        return offset - roff + SWARUtil.getIndex(result, !BYTES_BEFORE_USE_LITTLE_ENDIAN);
                    }
                }
            }
            for (; offset < end; offset++) {
                if (loadByte(addr + offset) == needle) {
                    return offset - roff;
                }
            }

            return -1;
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public int bytesBefore(Buffer needle) {
        try {
            UncheckedLoadByte uncheckedLoadByte = UnsafeBuffer::uncheckedLoadByte;
            return InternalBufferUtils.bytesBefore(this, uncheckedLoadByte,
                                                   needle, needle instanceof UnsafeBuffer ? uncheckedLoadByte : null);
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    /**
     * Used by {@link #bytesBefore(Buffer)}.
     */
    private static byte uncheckedLoadByte(Buffer buffer, int offset) {
        UnsafeBuffer unsafeBuffer = (UnsafeBuffer) buffer;
        return UNSAFE.getByte(unsafeBuffer.base, unsafeBuffer.address + offset);
    }

    @Override
    public ByteCursor openCursor() {
        return openCursor(readerOffset(), readableBytes());
    }

    @Override
    public ByteCursor openCursor(int fromOffset, int length) {
        if (rsize == CLOSED_SIZE) {
            throw bufferIsClosed();
        }
        if (fromOffset < 0) {
            throw new IndexOutOfBoundsException("The fromOffset cannot be negative: " + fromOffset + '.');
        }
        checkLength(length);
        if (capacity() < fromOffset + length) {
            throw new IndexOutOfBoundsException("The fromOffset + length is beyond the end of the buffer: " +
                    "fromOffset = " + fromOffset + ", length = " + length + '.');
        }
        return new ForwardUnsafeByteCursor(memory, base, address, fromOffset, length);
    }

    @Override
    public ByteCursor openReverseCursor(int fromOffset, int length) {
        if (rsize == CLOSED_SIZE) {
            throw bufferIsClosed();
        }
        if (fromOffset < 0) {
            throw new IndexOutOfBoundsException("The fromOffset cannot be negative: " + fromOffset + '.');
        }
        checkLength(length);
        if (capacity() <= fromOffset) {
            throw new IndexOutOfBoundsException("The fromOffset is beyond the end of the buffer: " + fromOffset + '.');
        }
        if (fromOffset - length < -1) {
            throw new IndexOutOfBoundsException("The fromOffset - length would underflow the buffer: " +
                    "fromOffset = " + fromOffset + ", length = " + length + '.');
        }
        return new ReverseUnsafeByteCursor(memory, base, address, fromOffset, length);
    }

    @Override
    public Buffer ensureWritable(int size, int minimumGrowth, boolean allowCompaction) {
        if (!isAccessible()) {
            throw bufferIsClosed();
        }
        if (!isOwned()) {
            throw attachTrace(new IllegalStateException(
                    "Buffer is not owned. Only owned buffers can call ensureWritable."));
        }
        if (size < 0) {
            throw new IllegalArgumentException("Cannot ensure writable for a negative size: " + size + '.');
        }
        if (minimumGrowth < 0) {
            throw new IllegalArgumentException("The minimum growth cannot be negative: " + minimumGrowth + '.');
        }
        if (rsize != wsize) {
            throw bufferIsReadOnly(this);
        }
        if (writableBytes() >= size) {
            // We already have enough space.
            return this;
        }

        if (allowCompaction && writableBytes() + readerOffset() >= size) {
            // We can solve this with compaction.
            return compact();
        }

        // Allocate a bigger buffer.
        long newSize = capacity() + (long) Math.max(size - writableBytes(), minimumGrowth);
        InternalBufferUtils.assertValidBufferSize(newSize);
        UnsafeBuffer buffer = (UnsafeBuffer) control.getAllocator().allocate((int) newSize);

        // Copy contents.
        try {
            copyInto(0, buffer, 0, capacity());
        } finally {
            Reference.reachabilityFence(memory);
            Reference.reachabilityFence(buffer.memory);
        }

        // Release the old memory, and install the new memory:
        Drop<UnsafeBuffer> drop = buffer.unsafeGetDrop();
        disconnectDrop(drop);
        attachNewMemory(buffer, drop);
        return this;
    }

    private Drop<UnsafeBuffer> disconnectDrop(Drop<UnsafeBuffer> newDrop) {
        var drop = (Drop<UnsafeBuffer>) unsafeGetDrop();
        int roff = this.roff;
        int woff = this.woff;
        drop.drop(this);
        unsafeSetDrop(newDrop);
        this.roff = roff;
        this.woff = woff;
        return drop;
    }

    private void attachNewMemory(UnsafeBuffer source, Drop<UnsafeBuffer> drop) {
        memory = source.memory;
        base = source.base;
        baseOffset = source.baseOffset;
        address = source.address;
        rsize = source.rsize;
        wsize = source.wsize;
        drop.attach(this);
    }

    @Override
    public Buffer split(int splitOffset) {
        if (splitOffset < 0) {
            throw new IllegalArgumentException("The split offset cannot be negative: " + splitOffset + '.');
        }
        if (capacity() < splitOffset) {
            throw new IllegalArgumentException("The split offset cannot be greater than the buffer capacity, " +
                    "but the split offset was " + splitOffset + ", and capacity is " + capacity() + '.');
        }
        if (!isAccessible()) {
            throw bufferIsClosed();
        }
        if (!isOwned()) {
            throw attachTrace(new IllegalStateException("Cannot split a buffer that is not owned."));
        }
        var drop = unsafeGetDrop().fork();
        var splitBuffer = new UnsafeBuffer(memory, baseOffset, splitOffset, control, drop);
        drop.attach(splitBuffer);
        splitBuffer.woff = Math.min(woff, splitOffset);
        splitBuffer.roff = Math.min(roff, splitOffset);
        boolean readOnly = readOnly();
        if (readOnly) {
            splitBuffer.makeReadOnly();
        }
        // Split preserves const-state.
        rsize -= splitOffset;
        baseOffset += splitOffset;
        address += splitOffset;
        if (!readOnly) {
            wsize = rsize;
        }
        woff = Math.max(woff, splitOffset) - splitOffset;
        roff = Math.max(roff, splitOffset) - splitOffset;
        return splitBuffer;
    }

    @Override
    public Buffer compact() {
        if (!isAccessible()) {
            throw bufferIsClosed();
        }
        if (!isOwned()) {
            throw attachTrace(new IllegalStateException("Buffer must be owned in order to compact."));
        }
        if (readOnly()) {
            throw new BufferReadOnlyException("Buffer must be writable in order to compact, but was read-only.");
        }
        if (roff == 0) {
            return this;
        }
        try {
            PlatformDependent.copyMemory(base, address + roff, base, address, woff - roff);
        } finally {
            Reference.reachabilityFence(memory);
        }
        woff -= roff;
        roff = 0;
        return this;
    }

    @Override
    public int countComponents() {
        return 1;
    }

    @Override
    public int countReadableComponents() {
        return readableBytes() > 0? 1 : 0;
    }

    @Override
    public int countWritableComponents() {
        return writableBytes() > 0? 1 : 0;
    }

    // <editor-fold defaultstate="collapsed" desc="Readable/WritableComponent implementation.">
    @Override
    public boolean hasReadableArray() {
        return base instanceof byte[];
    }

    @Override
    public byte[] readableArray() {
        checkHasReadableArray();
        return (byte[]) base;
    }

    @Override
    public int readableArrayOffset() {
        checkHasReadableArray();
        return Math.toIntExact(address + roff - PlatformDependent.byteArrayBaseOffset());
    }

    private void checkHasReadableArray() {
        if (!hasReadableArray()) {
            throw new UnsupportedOperationException("No readable array available.");
        }
    }

    @Override
    public int readableArrayLength() {
        return woff - roff;
    }

    @Override
    public long baseNativeAddress() {
        return nativeAddress();
    }

    @Override
    public long readableNativeAddress() {
        return nativeAddressWithOffset(nativeAddress(), roff);
    }

    @Override
    public ByteBuffer readableBuffer() {
        return mutableReadableBuffer().asReadOnlyBuffer();
    }

    @Override
    public ByteBuffer mutableReadableBuffer() {
        final ByteBuffer buf;
        if (hasReadableArray()) {
            buf = ByteBuffer.wrap(readableArray()).slice(readableArrayOffset(), readableArrayLength());
        } else {
            buf = PlatformDependent.directBuffer(address + roff, readableBytes(), memory);
        }
        return buf;
    }

    @Override
    public boolean hasWritableArray() {
        return hasReadableArray();
    }

    @Override
    public byte[] writableArray() {
        checkHasWritableArray();
        return (byte[]) base;
    }

    @Override
    public int writableArrayOffset() {
        checkHasWritableArray();
        return Math.toIntExact(address + woff - PlatformDependent.byteArrayBaseOffset());
    }

    private void checkHasWritableArray() {
        if (!hasReadableArray()) {
            throw new UnsupportedOperationException("No writable array available.");
        }
    }

    @Override
    public int writableArrayLength() {
        return writableBytes();
    }

    @Override
    public long writableNativeAddress() {
        return nativeAddressWithOffset(nativeAddress(), woff);
    }

    @Override
    public ByteBuffer writableBuffer() {
        final ByteBuffer buf;
        if (hasWritableArray()) {
            buf = ByteBuffer.wrap(writableArray()).slice(writableArrayOffset(), writableArrayLength());
        } else {
            buf = PlatformDependent.directBuffer(address + woff, writableBytes(), memory);
        }
        return buf;
    }

    @Override
    public UnsafeBuffer first() {
        return this;
    }

    @Override
    public <N extends Next & BufferComponent> N next() {
        return null; // There is no "next" component in our external-iteration of components.
    }
    // </editor-fold>

    @SuppressWarnings("unchecked")
    @Override
    public <T extends BufferComponent & Next> ComponentIterator<T> forEachComponent() {
        return (ComponentIterator<T>) acquire();
    }

    // <editor-fold defaultstate="collapsed" desc="Primitive accessors implementation.">
    @Override
    public byte readByte() {
        checkRead(roff, Byte.BYTES);
        try {
            var value = loadByte(address + roff);
            roff += Byte.BYTES;
            return value;
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public byte getByte(int roff) {
        checkGet(roff, Byte.BYTES);
        try {
            return loadByte(address + roff);
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public int readUnsignedByte() {
        return readByte() & 0xFF;
    }

    @Override
    public int getUnsignedByte(int roff) {
        return getByte(roff) & 0xFF;
    }

    @Override
    public Buffer writeByte(byte value) {
        checkWrite(woff, Byte.BYTES, true);
        long offset = address + woff;
        woff += Byte.BYTES;
        try {
            storeByte(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public Buffer setByte(int woff, byte value) {
        checkSet(woff, Byte.BYTES);
        long offset = address + woff;
        try {
            storeByte(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public Buffer writeUnsignedByte(int value) {
        checkWrite(woff, Byte.BYTES, true);
        long offset = address + woff;
        woff += Byte.BYTES;
        try {
            storeByte(offset, (byte) (value & 0xFF));
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public Buffer setUnsignedByte(int woff, int value) {
        checkSet(woff, Byte.BYTES);
        long offset = address + woff;
        try {
            storeByte(offset, (byte) (value & 0xFF));
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public char readChar() {
        checkRead(roff, Character.BYTES);
        try {
            long offset = address + roff;
            roff += Character.BYTES;
            return loadChar(offset);
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public char getChar(int roff) {
        checkGet(roff, Character.BYTES);
        try {
            long offset = address + roff;
            return loadChar(offset);
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public Buffer writeChar(char value) {
        checkWrite(woff, Character.BYTES, true);
        long offset = address + woff;
        woff += Character.BYTES;
        try {
            storeChar(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public Buffer setChar(int woff, char value) {
        checkSet(woff, Character.BYTES);
        long offset = address + woff;
        try {
            storeChar(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public short readShort() {
        checkRead(roff, Short.BYTES);
        try {
            long offset = address + roff;
            roff += Short.BYTES;
            return loadShort(offset);
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public short getShort(int roff) {
        checkGet(roff, Short.BYTES);
        try {
            long offset = address + roff;
            return loadShort(offset);
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public int readUnsignedShort() {
        return readShort() & 0xFFFF;
    }

    @Override
    public int getUnsignedShort(int roff) {
        return getShort(roff) & 0xFFFF;
    }

    @Override
    public Buffer writeShort(short value) {
        checkWrite(woff, Short.BYTES, true);
        long offset = address + woff;
        woff += Short.BYTES;
        try {
            storeShort(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public Buffer setShort(int woff, short value) {
        checkSet(woff, Short.BYTES);
        long offset = address + woff;
        try {
            storeShort(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public Buffer writeUnsignedShort(int value) {
        checkWrite(woff, Short.BYTES, true);
        long offset = address + woff;
        woff += Short.BYTES;
        try {
            storeShort(offset, (short) (value & 0xFFFF));
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public Buffer setUnsignedShort(int woff, int value) {
        checkSet(woff, Short.BYTES);
        long offset = address + woff;
        try {
            storeShort(offset, (short) (value & 0xFFFF));
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public int readMedium() {
        checkRead(roff, 3);
        long offset = address + roff;
        int value = loadByte(offset) << 16 | (loadByte(offset + 1) & 0xFF) << 8 | loadByte(offset + 2) & 0xFF;
        roff += 3;
        return value;
    }

    @Override
    public int getMedium(int roff) {
        checkGet(roff, 3);
        long offset = address + roff;
        return loadByte(offset) << 16 | (loadByte(offset + 1) & 0xFF) << 8 | loadByte(offset + 2) & 0xFF;
    }

    @Override
    public int readUnsignedMedium() {
        checkRead(roff, 3);
        long offset = address + roff;
        int value =
                (loadByte(offset) << 16 | (loadByte(offset + 1) & 0xFF) << 8 | loadByte(offset + 2) & 0xFF) & 0xFFFFFF;
        roff += 3;
        return value;
    }

    @Override
    public int getUnsignedMedium(int roff) {
        checkGet(roff, 3);
        long offset = address + roff;
        return (loadByte(offset) << 16 | (loadByte(offset + 1) & 0xFF) << 8 | loadByte(offset + 2) & 0xFF) & 0xFFFFFF;
    }

    @Override
    public Buffer writeMedium(int value) {
        checkWrite(woff, 3, true);
        long offset = address + woff;
        storeByte(offset, (byte) (value >> 16));
        storeByte(offset + 1, (byte) (value >> 8 & 0xFF));
        storeByte(offset + 2, (byte) (value & 0xFF));
        woff += 3;
        return this;
    }

    @Override
    public Buffer setMedium(int woff, int value) {
        checkSet(woff, 3);
        long offset = address + woff;
        storeByte(offset, (byte) (value >> 16));
        storeByte(offset + 1, (byte) (value >> 8 & 0xFF));
        storeByte(offset + 2, (byte) (value & 0xFF));
        return this;
    }

    @Override
    public Buffer writeUnsignedMedium(int value) {
        checkWrite(woff, 3, true);
        long offset = address + woff;
        storeByte(offset, (byte) (value >> 16));
        storeByte(offset + 1, (byte) (value >> 8 & 0xFF));
        storeByte(offset + 2, (byte) (value & 0xFF));
        woff += 3;
        return this;
    }

    @Override
    public Buffer setUnsignedMedium(int woff, int value) {
        checkSet(woff, 3);
        long offset = address + woff;
        storeByte(offset, (byte) (value >> 16));
        storeByte(offset + 1, (byte) (value >> 8 & 0xFF));
        storeByte(offset + 2, (byte) (value & 0xFF));
        return this;
    }

    @Override
    public int readInt() {
        checkRead(roff, Integer.BYTES);
        try {
            long offset = address + roff;
            roff += Integer.BYTES;
            return loadInt(offset);
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public int getInt(int roff) {
        checkGet(roff, Integer.BYTES);
        try {
            long offset = address + roff;
            return loadInt(offset);
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public long readUnsignedInt() {
        return readInt() & 0x0000_0000_FFFF_FFFFL;
    }

    @Override
    public long getUnsignedInt(int roff) {
        return getInt(roff) & 0x0000_0000_FFFF_FFFFL;
    }

    @Override
    public Buffer writeInt(int value) {
        checkWrite(woff, Integer.BYTES, true);
        long offset = address + woff;
        woff += Integer.BYTES;
        try {
            storeInt(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public Buffer setInt(int woff, int value) {
        checkSet(woff, Integer.BYTES);
        long offset = address + woff;
        try {
            storeInt(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public Buffer writeUnsignedInt(long value) {
        checkWrite(woff, Integer.BYTES, true);
        long offset = address + woff;
        woff += Integer.BYTES;
        try {
            storeInt(offset, (int) (value & 0xFFFF_FFFFL));
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public Buffer setUnsignedInt(int woff, long value) {
        checkSet(woff, Integer.BYTES);
        long offset = address + woff;
        try {
            storeInt(offset, (int) (value & 0xFFFF_FFFFL));
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public float readFloat() {
        checkRead(roff, Float.BYTES);
        try {
            long offset = address + roff;
            roff += Float.BYTES;
            return loadFloat(offset);
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public float getFloat(int roff) {
        checkGet(roff, Float.BYTES);
        try {
            long offset = address + roff;
            return loadFloat(offset);
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public Buffer writeFloat(float value) {
        checkWrite(woff, Float.BYTES, true);
        long offset = address + woff;
        woff += Float.BYTES;
        try {
            storeFloat(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public Buffer setFloat(int woff, float value) {
        checkSet(woff, Float.BYTES);
        long offset = address + woff;
        try {
            storeFloat(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public long readLong() {
        checkRead(roff, Long.BYTES);
        try {
            long offset = address + roff;
            roff += Long.BYTES;
            return loadLong(offset);
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public long getLong(int roff) {
        checkGet(roff, Long.BYTES);
        try {
            long offset = address + roff;
            return loadLong(offset);
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public Buffer writeLong(long value) {
        checkWrite(woff, Long.BYTES, true);
        long offset = address + woff;
        woff += Long.BYTES;
        try {
            storeLong(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public Buffer setLong(int woff, long value) {
        checkSet(woff, Long.BYTES);
        long offset = address + woff;
        try {
            storeLong(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public double readDouble() {
        checkRead(roff, Double.BYTES);
        try {
            long offset = address + roff;
            roff += Double.BYTES;
            return loadDouble(offset);
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public double getDouble(int roff) {
        checkGet(roff, Double.BYTES);
        try {
            long offset = address + roff;
            return loadDouble(offset);
        } finally {
            Reference.reachabilityFence(memory);
        }
    }

    @Override
    public Buffer writeDouble(double value) {
        checkWrite(woff, Double.BYTES, true);
        long offset = address + woff;
        woff += Double.BYTES;
        try {
            storeDouble(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }

    @Override
    public Buffer setDouble(int woff, double value) {
        checkSet(woff, Double.BYTES);
        long offset = address + woff;
        try {
            storeDouble(offset, value);
        } finally {
            Reference.reachabilityFence(memory);
        }
        return this;
    }
    // </editor-fold>

    @Override
    protected Owned<UnsafeBuffer> prepareSend() {
        int roff = this.roff;
        int woff = this.woff;
        boolean readOnly = readOnly();
        int implicitCapacityLimit = this.implicitCapacityLimit;
        UnsafeMemory memory = this.memory;
        AllocatorControl control = this.control;
        long baseOffset = this.baseOffset;
        int rsize = this.rsize;
        return drop -> {
            UnsafeBuffer copy = new UnsafeBuffer(memory, baseOffset, rsize, control, drop);
            copy.roff = roff;
            copy.woff = woff;
            copy.implicitCapacityLimit = implicitCapacityLimit;
            if (readOnly) {
                copy.makeReadOnly();
            }
            return copy;
        };
    }

    @Override
    protected void makeInaccessible() {
        roff = 0;
        woff = 0;
        rsize = CLOSED_SIZE;
        wsize = CLOSED_SIZE;
        readOnly = false;
    }

    private void checkRead(int index, int size) {
        if (index < 0 | woff < index + size) {
            throw readAccessCheckException(index, size);
        }
    }

    private void checkGet(int index, int size) {
        if (index < 0 | rsize < index + size) {
            throw readAccessCheckException(index, size);
        }
    }

    private void checkWrite(int index, int size, boolean mayExpand) {
        if (index < roff | wsize < index + size) {
            handleWriteAccessBoundsFailure(index, size, mayExpand);
        }
    }

    private void checkSet(int index, int size) {
        if (index < 0 | wsize < index + size) {
            handleWriteAccessBoundsFailure(index, size, false);
        }
    }

    private RuntimeException readAccessCheckException(int index, int size) {
        if (rsize == CLOSED_SIZE) {
            throw bufferIsClosed();
        }
        return outOfBounds(index, size);
    }

    private void handleWriteAccessBoundsFailure(int index, int size, boolean mayExpand) {
        if (rsize == CLOSED_SIZE) {
            throw bufferIsClosed();
        }
        if (wsize != rsize) {
            throw bufferIsReadOnly(this);
        }
        int capacity = capacity();
        if (mayExpand && index >= 0 && index <= capacity && woff + size <= implicitCapacityLimit && isOwned()) {
            // Grow into next power-of-two, but not beyond the implicit limit.
            int minimumGrowth = Math.min(
                    Math.max(roundToPowerOfTwo(capacity * 2), size),
                    implicitCapacityLimit) - capacity;
            ensureWritable(size, minimumGrowth, false);
            checkSet(index, size); // Verify writing is now possible, without recursing.
            return;
        }
        throw outOfBounds(index, size);
    }

    private BufferClosedException bufferIsClosed() {
        return attachTrace(InternalBufferUtils.bufferIsClosed(this));
    }

    private IndexOutOfBoundsException outOfBounds(int index, int size) {
        return new IndexOutOfBoundsException(
                "Access at index " + index + " of size " + size + " is out of bounds: " +
                "[read 0 to " + woff + ", write 0 to " + rsize + "].");
    }

    private byte loadByte(long off) {
        return PlatformDependent.getByte(base, off);
    }

    private char loadChar(long offset) {
        if (ACCESS_UNALIGNED) {
            var value = PlatformDependent.getChar(base, offset);
            return FLIP_BYTES? Character.reverseBytes(value) : value;
        }
        return loadCharUnaligned(offset);
    }

    private char loadCharUnaligned(long offset) {
        final char value;
        Object b = base;
        if ((offset & 1) == 0) {
            value = PlatformDependent.getChar(b, offset);
        } else {
            value = (char) (PlatformDependent.getByte(b, offset) << 8 |
                    PlatformDependent.getByte(b, offset + 1));
        }
        return FLIP_BYTES? Character.reverseBytes(value) : value;
    }

    private short loadShort(long offset) {
        if (ACCESS_UNALIGNED) {
            var value = PlatformDependent.getShort(base, offset);
            return FLIP_BYTES? Short.reverseBytes(value) : value;
        }
        return loadShortUnaligned(offset);
    }

    private short loadShortUnaligned(long offset) {
        final short value;
        Object b = base;
        if ((offset & 1) == 0) {
            value = PlatformDependent.getShort(b, offset);
        } else {
            value = (short) (PlatformDependent.getByte(b, offset) << 8 |
                    PlatformDependent.getByte(b, offset + 1));
        }
        return FLIP_BYTES? Short.reverseBytes(value) : value;
    }

    private int loadInt(long offset) {
        if (ACCESS_UNALIGNED) {
            var value = PlatformDependent.getInt(base, offset);
            return FLIP_BYTES? Integer.reverseBytes(value) : value;
        }
        return loadIntUnaligned(offset);
    }

    private int loadIntUnaligned(long offset) {
        final int value;
        Object b = base;
        if ((offset & 3) == 0) {
            value = PlatformDependent.getInt(b, offset);
        } else if ((offset & 1) == 0) {
            value = PlatformDependent.getShort(b, offset) << 16 |
                    PlatformDependent.getShort(b, offset + 2);
        } else {
            value = PlatformDependent.getByte(b, offset) << 24 |
                    PlatformDependent.getByte(b, offset + 1) << 16 |
                    PlatformDependent.getByte(b, offset + 2) << 8 |
                    PlatformDependent.getByte(b, offset + 3);
        }
        return FLIP_BYTES? Integer.reverseBytes(value) : value;
    }

    private float loadFloat(long offset) {
        if (ACCESS_UNALIGNED) {
            if (FLIP_BYTES) {
                var value = PlatformDependent.getInt(base, offset);
                return Float.intBitsToFloat(Integer.reverseBytes(value));
            }
            return PlatformDependent.getFloat(base, offset);
        }
        return loadFloatUnaligned(offset);
    }

    private float loadFloatUnaligned(long offset) {
        return Float.intBitsToFloat(loadIntUnaligned(offset));
    }

    private long loadLong(long offset) {
        if (ACCESS_UNALIGNED) {
            var value = PlatformDependent.getLong(base, offset);
            return FLIP_BYTES? Long.reverseBytes(value) : value;
        }
        return loadLongUnaligned(offset);
    }

    private long loadLongUnaligned(long offset) {
        final long value;
        Object b = base;
        if ((offset & 7) == 0) {
            value = PlatformDependent.getLong(b, offset);
        } else if ((offset & 3) == 0) {
            value = (long) PlatformDependent.getInt(b, offset) << 32 |
                    PlatformDependent.getInt(b, offset + 4);
        } else if ((offset & 1) == 0) {
            value = (long) PlatformDependent.getShort(b, offset) << 48 |
                    (long) PlatformDependent.getShort(b, offset + 2) << 32 |
                    (long) PlatformDependent.getShort(b, offset + 4) << 16 |
                    PlatformDependent.getShort(b, offset + 6);
        } else {
            value = (long) PlatformDependent.getByte(b, offset) << 54 |
                    (long) PlatformDependent.getByte(b, offset + 1) << 48 |
                    (long) PlatformDependent.getByte(b, offset + 2) << 40 |
                    (long) PlatformDependent.getByte(b, offset + 3) << 32 |
                    (long) PlatformDependent.getByte(b, offset + 4) << 24 |
                    (long) PlatformDependent.getByte(b, offset + 5) << 16 |
                    (long) PlatformDependent.getByte(b, offset + 6) << 8 |
                    PlatformDependent.getByte(b, offset + 7);
        }
        return FLIP_BYTES? Long.reverseBytes(value) : value;
    }

    private double loadDouble(long offset) {
        if (ACCESS_UNALIGNED) {
            if (FLIP_BYTES) {
                var value = PlatformDependent.getLong(base, offset);
                return Double.longBitsToDouble(Long.reverseBytes(value));
            }
            return PlatformDependent.getDouble(base, offset);
        }
        return loadDoubleUnaligned(offset);
    }

    private double loadDoubleUnaligned(long offset) {
        return Double.longBitsToDouble(loadLongUnaligned(offset));
    }

    private void storeByte(long offset, byte value) {
        PlatformDependent.putByte(base, offset, value);
    }

    private void storeChar(long offset, char value) {
        if (FLIP_BYTES) {
            value = Character.reverseBytes(value);
        }
        if (ACCESS_UNALIGNED) {
            PlatformDependent.putChar(base, offset, value);
        } else {
            storeCharUnaligned(offset, value);
        }
    }

    private void storeCharUnaligned(long offset, char value) {
        Object b = base;
        if ((offset & 1) == 0) {
            PlatformDependent.putChar(b, offset, value);
        } else {
            PlatformDependent.putByte(b, offset, (byte) (value >> 8));
            PlatformDependent.putByte(b, offset + 1, (byte) value);
        }
    }

    private void storeShort(long offset, short value) {
        if (FLIP_BYTES) {
            value = Short.reverseBytes(value);
        }
        if (ACCESS_UNALIGNED) {
            PlatformDependent.putShort(base, offset, value);
        } else {
            storeShortUnaligned(offset, value);
        }
    }

    private void storeShortUnaligned(long offset, short value) {
        Object b = base;
        if ((offset & 1) == 0) {
            PlatformDependent.putShort(b, offset, value);
        } else {
            PlatformDependent.putByte(b, offset, (byte) (value >> 8));
            PlatformDependent.putByte(b, offset + 1, (byte) value);
        }
    }

    private void storeInt(long offset, int value) {
        if (FLIP_BYTES) {
            value = Integer.reverseBytes(value);
        }
        if (ACCESS_UNALIGNED) {
            PlatformDependent.putInt(base, offset, value);
        } else {
            storeIntUnaligned(offset, value);
        }
    }

    private void storeIntUnaligned(long offset, int value) {
        Object b = base;
        if ((offset & 3) == 0) {
            PlatformDependent.putInt(b, offset, value);
        } else if ((offset & 1) == 0) {
            PlatformDependent.putShort(b, offset, (short) (value >> 16));
            PlatformDependent.putShort(b, offset + 2, (short) value);
        } else {
            PlatformDependent.putByte(b, offset, (byte) (value >> 24));
            PlatformDependent.putByte(b, offset + 1, (byte) (value >> 16));
            PlatformDependent.putByte(b, offset + 2, (byte) (value >> 8));
            PlatformDependent.putByte(b, offset + 3, (byte) value);
        }
    }

    private void storeFloat(long offset, float value) {
        storeInt(offset, Float.floatToRawIntBits(value));
    }

    private void storeLong(long offset, long value) {
        if (FLIP_BYTES) {
            value = Long.reverseBytes(value);
        }
        if (ACCESS_UNALIGNED) {
            PlatformDependent.putLong(base, offset, value);
        } else {
            storeLongUnaligned(offset, value);
        }
    }

    private void storeLongUnaligned(long offset, long value) {
        Object b = base;
        if ((offset & 7) == 0) {
            PlatformDependent.putLong(b, offset, value);
        } else if ((offset & 3) == 0) {
            PlatformDependent.putInt(b, offset, (int) (value >> 32));
            PlatformDependent.putInt(b, offset + 4, (int) value);
        } else if ((offset & 1) == 0) {
            PlatformDependent.putShort(b, offset, (short) (value >> 48));
            PlatformDependent.putShort(b, offset + 16, (short) (value >> 32));
            PlatformDependent.putShort(b, offset + 32, (short) (value >> 16));
            PlatformDependent.putShort(b, offset + 48, (short) value);
        } else {
            PlatformDependent.putByte(b, offset, (byte) (value >> 56));
            PlatformDependent.putByte(b, offset + 1, (byte) (value >> 48));
            PlatformDependent.putByte(b, offset + 2, (byte) (value >> 40));
            PlatformDependent.putByte(b, offset + 3, (byte) (value >> 32));
            PlatformDependent.putByte(b, offset + 4, (byte) (value >> 24));
            PlatformDependent.putByte(b, offset + 5, (byte) (value >> 16));
            PlatformDependent.putByte(b, offset + 6, (byte) (value >> 8));
            PlatformDependent.putByte(b, offset + 7, (byte) value);
        }
    }

    private void storeDouble(long offset, double value) {
        storeLong(offset, Double.doubleToRawLongBits(value));
    }

    Object recover() {
        return memory;
    }

    UnsafeBuffer newConstChild() {
        assert readOnly();
        Drop<UnsafeBuffer> drop = unsafeGetDrop().fork();
        UnsafeBuffer child = new UnsafeBuffer(this, drop);
        drop.attach(child);
        return child;
    }

    private static final class ForwardUnsafeByteCursor implements ByteCursor {
        final UnsafeMemory memory; // Keep memory alive.
        final Object baseObj;
        final long baseAddress;
        int index;
        final int end;
        byte byteValue;

        ForwardUnsafeByteCursor(UnsafeMemory memory, Object base, long address, int fromOffset, int length) {
            this.memory = memory;
            baseObj = base;
            baseAddress = address;
            index = fromOffset;
            end = index + length;
            byteValue = -1;
        }

        @Override
        public boolean readByte() {
            if (index < end) {
                try {
                    byteValue = PlatformDependent.getByte(baseObj, baseAddress + index);
                } finally {
                    Reference.reachabilityFence(memory);
                }
                index++;
                return true;
            }
            return false;
        }

        @Override
        public byte getByte() {
            return byteValue;
        }

        @Override
        public int currentOffset() {
            return index;
        }

        @Override
        public int bytesLeft() {
            return end - index;
        }
    }

    private static final class ReverseUnsafeByteCursor implements ByteCursor {
        final UnsafeMemory memory; // Keep memory alive.
        final Object baseObj;
        final long baseAddress;
        int index;
        final int end;
        byte byteValue;

        ReverseUnsafeByteCursor(UnsafeMemory memory, Object base, long address, int fromOffset, int length) {
            this.memory = memory;
            baseObj = base;
            baseAddress = address;
            index = fromOffset;
            end = index - length;
            byteValue = -1;
        }

        @Override
        public boolean readByte() {
            if (index > end) {
                try {
                    byteValue = PlatformDependent.getByte(baseObj, baseAddress + index);
                } finally {
                    Reference.reachabilityFence(memory);
                }
                index--;
                return true;
            }
            return false;
        }

        @Override
        public byte getByte() {
            return byteValue;
        }

        @Override
        public int currentOffset() {
            return index;
        }

        @Override
        public int bytesLeft() {
            return index - end;
        }
    }
}
