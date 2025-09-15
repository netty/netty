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
import io.netty5.util.internal.SWARUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import static io.netty5.buffer.internal.InternalBufferUtils.MAX_BUFFER_SIZE;
import static io.netty5.buffer.internal.InternalBufferUtils.bufferIsReadOnly;
import static io.netty5.buffer.internal.InternalBufferUtils.checkImplicitCapacity;
import static io.netty5.buffer.internal.InternalBufferUtils.checkLength;
import static io.netty5.buffer.internal.InternalBufferUtils.nativeAddressWithOffset;
import static io.netty5.buffer.internal.InternalBufferUtils.setMemory;
import static io.netty5.util.internal.ObjectUtil.checkPositiveOrZero;
import static io.netty5.util.internal.PlatformDependent.roundToPowerOfTwo;

final class NioBuffer extends AdaptableBuffer<NioBuffer>
        implements BufferComponent, NotReadOnlyReadableComponent, ComponentIterator<NioBuffer>, ComponentIterator.Next {
    private static final ByteBuffer CLOSED_BUFFER = ByteBuffer.allocate(0);

    private ByteBuffer base;
    private ByteBuffer rmem; // For reading.
    private ByteBuffer wmem; // For writing.

    private int roff;
    private int woff;
    private int implicitCapacityLimit;

    NioBuffer(ByteBuffer base, ByteBuffer memory, AllocatorControl control, Drop<NioBuffer> drop) {
        super(drop, control);
        this.base = base;
        rmem = memory;
        wmem = memory;
        implicitCapacityLimit = MAX_BUFFER_SIZE;
    }

    /**
     * Constructor for {@linkplain BufferAllocator#constBufferSupplier(byte[]) const buffers}.
     */
    private NioBuffer(NioBuffer parent, Drop<NioBuffer> drop) {
        super(drop, parent.control);
        implicitCapacityLimit = parent.implicitCapacityLimit;
        base = parent.base;
        rmem = parent.rmem.duplicate();
        wmem = CLOSED_BUFFER;
        roff = parent.roff;
        woff = parent.woff;
    }

    @Override
    public String toString() {
        return "Buffer[roff:" + roff + ", woff:" + woff + ", cap:" + rmem.capacity() + ']';
    }

    @Override
    protected RuntimeException createResourceClosedException() {
        return InternalBufferUtils.bufferIsClosed(this);
    }

    @Override
    public int capacity() {
        return rmem.capacity();
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
        if (readOnly()) {
            throw bufferIsReadOnly(this);
        }
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
    public NioBuffer skipReadableBytes(int delta) {
        return (NioBuffer) super.skipReadableBytes(delta);
    }

    @Override
    public NioBuffer skipWritableBytes(int delta) {
        return (NioBuffer) super.skipWritableBytes(delta);
    }

    @Override
    public Buffer fill(byte value) {
        int capacity = capacity();
        checkSet(0, capacity);
        if (rmem == CLOSED_BUFFER) {
            throw bufferIsClosed();
        }
        final ByteBuffer wmem = this.wmem;
        setMemory(wmem, capacity, value);
        return this;
    }

    private long nativeAddress() {
        return InternalBufferUtils.nativeAddressOfDirectByteBuffer(rmem);
    }

    @Override
    public Buffer makeReadOnly() {
        wmem = CLOSED_BUFFER;
        return this;
    }

    @Override
    public boolean readOnly() {
        return wmem == CLOSED_BUFFER && rmem != CLOSED_BUFFER;
    }

    @Override
    public boolean isDirect() {
        return rmem.isDirect();
    }

    @Override
    public Buffer implicitCapacityLimit(int limit) {
        checkImplicitCapacity(limit, capacity());
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
            NioBuffer copy = newConstChild();
            if (offset > 0 || length < capacity()) {
                copy.rmem = copy.rmem.slice(offset, length);
            }
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
        copyInto(srcPos, ByteBuffer.wrap(dest), destPos, length);
    }

    @Override
    public void copyInto(int srcPos, ByteBuffer dest, int destPos, int length) {
        if (rmem == CLOSED_BUFFER) {
            throw bufferIsClosed();
        }
        if (srcPos < 0) {
            throw new IndexOutOfBoundsException("The srcPos cannot be negative: " + srcPos + '.');
        }
        if (destPos < 0) {
            throw new IndexOutOfBoundsException("The destination position cannot be negative: " + destPos);
        }
        checkLength(length);
        if (capacity() < srcPos + length) {
            throw new IndexOutOfBoundsException("The srcPos + length is beyond the end of the buffer: " +
                                               "srcPos = " + srcPos + ", length = " + length + '.');
        }
        if (dest.capacity() < destPos + length) {
            throw new IndexOutOfBoundsException("The destPos + length is beyond the end of the buffer: " +
                                                "destPos = " + destPos + ", length = " + length + '.');
        }
        if (dest.hasArray() && hasReadableArray()) {
            final byte[] srcArray = rmem.array();
            final int srcStart = rmem.arrayOffset() + srcPos;
            final byte[] dstArray = dest.array();
            final int dstStart = dest.arrayOffset() + destPos;
            System.arraycopy(srcArray, srcStart, dstArray, dstStart, length);
            return;
        }
        dest = dest.duplicate().clear();
        dest.put(destPos, rmem, srcPos, length);
    }

    @Override
    public void copyInto(int srcPos, Buffer dest, int destPos, int length) {
        if (!isAccessible()) {
            throw bufferIsClosed();
        }
        if (dest.readOnly()) {
            throw bufferIsReadOnly(dest);
        }
        if (dest instanceof NioBuffer) {
            var nb = (NioBuffer) dest;
            nb.checkSet(destPos, length);
            copyInto(srcPos, nb.wmem, destPos, length);
            return;
        }

        InternalBufferUtils.copyToViaReverseLoop(this, srcPos, dest, destPos, length);
    }

    @Override
    public Buffer writeBytes(byte[] source, int srcPos, int length) {
        if (source.length < srcPos + length) {
            throw new ArrayIndexOutOfBoundsException(
                    "Access at index " + srcPos + " of length " + length +
                    " is outsidethe bounds of array with length " + source.length);
        }

        checkWrite(woff, length, true);

        if (hasWritableArray()) {
            System.arraycopy(source, srcPos, writableArray(), writableArrayOffset(), length);
        } else {
            wmem.put(woff, source, srcPos, length);
        }

        skipWritableBytes(length);

        return this;
    }

    @Override
    public Buffer writeBytes(ByteBuffer source) {
        final int length = source.remaining();

        checkWrite(woff, length, true);

        if (hasWritableArray()) {
            source.get(writableArray(), writableArrayOffset(), length);
        } else {
            wmem.put(woff, source, source.position(), length);
            source.position(source.position() + length);
        }

        skipWritableBytes(length);

        return this;
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

    @Override
    public int bytesBefore(byte needle) {
        // For the details of this algorithm, see Hacker's Delight, Chapter 6, Searching Words.
        // Richard Startin also describes this on his blog: https://richardstartin.github.io/posts/finding-bytes
        if (!isAccessible()) {
            throw bufferIsClosed();
        }
        int offset = roff;
        final int length = woff - roff;
        final int end = woff;

        if (length > 7) {
            final long pattern = SWARUtil.compilePattern(needle);
            for (final int longEnd = offset + (length >>> 3) * Long.BYTES;
                 offset < longEnd;
                 offset += Long.BYTES) {
                final long word = rmem.getLong(offset);
                final long result = SWARUtil.applyPattern(word, pattern);
                if (result != 0) {
                    return offset - roff + SWARUtil.getIndex(result, true);
                }
            }
        }
        for (; offset < end; offset++) {
            if (rmem.get(offset) == needle) {
                return offset - roff;
            }
        }

        return -1;
    }

    @Override
    public int bytesBefore(Buffer needle) {
        UncheckedLoadByte uncheckedLoadByte = NioBuffer::uncheckedLoadByte;
        return InternalBufferUtils.bytesBefore(this, uncheckedLoadByte,
                                               needle, needle instanceof NioBuffer ? uncheckedLoadByte : null);
    }

    /**
     * Used by {@link #bytesBefore(Buffer)}.
     */
    private static byte uncheckedLoadByte(Buffer buffer, int offset) {
        return ((NioBuffer) buffer).rmem.get(offset);
    }

    @Override
    public ByteCursor openCursor() {
        return openCursor(readerOffset(), readableBytes());
    }

    @Override
    public ByteCursor openCursor(int fromOffset, int length) {
        if (rmem == CLOSED_BUFFER) {
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
        return new ForwardNioByteCursor(rmem, fromOffset, length);
    }

    @Override
    public ByteCursor openReverseCursor(int fromOffset, int length) {
        if (rmem == CLOSED_BUFFER) {
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
        return new ReverseNioByteCursor(rmem, fromOffset, length);
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
        if (rmem != wmem) {
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
        NioBuffer buffer = (NioBuffer) control.getAllocator().allocate((int) newSize);

        // Copy contents.
        copyInto(0, buffer, 0, capacity());

        // Release the old memory and install the new:
        Drop<NioBuffer> drop = buffer.unsafeGetDrop();
        disconnectDrop(drop);
        attachNewBuffer(buffer, drop);
        return this;
    }

    private void disconnectDrop(Drop<NioBuffer> newDrop) {
        var drop = (Drop<NioBuffer>) unsafeGetDrop();
        int roff = this.roff;
        int woff = this.woff;
        drop.drop(this);
        unsafeSetDrop(newDrop);
        this.roff = roff;
        this.woff = woff;
    }

    private void attachNewBuffer(NioBuffer buffer, Drop<NioBuffer> drop) {
        base = buffer.base;
        rmem = buffer.rmem;
        wmem = buffer.wmem;
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
        var splitByteBuffer = rmem.slice(0, splitOffset);
        var splitBuffer = new NioBuffer(base, splitByteBuffer, control, drop);
        drop.attach(splitBuffer);
        splitBuffer.woff = Math.min(woff, splitOffset);
        splitBuffer.roff = Math.min(roff, splitOffset);
        boolean readOnly = readOnly();
        if (readOnly) {
            splitBuffer.makeReadOnly();
        }
        // Split preserves const-state.
        rmem = rmem.slice(splitOffset, rmem.capacity() - splitOffset);
        if (!readOnly) {
            wmem = rmem;
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
        rmem.limit(woff).position(roff).compact().clear();
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
        return rmem.hasArray();
    }

    @Override
    public byte[] readableArray() {
        return rmem.array();
    }

    @Override
    public int readableArrayOffset() {
        return rmem.arrayOffset() + roff;
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
        return rmem.asReadOnlyBuffer().slice(readerOffset(), readableBytes());
    }

    @Override
    public ByteBuffer mutableReadableBuffer() {
        return rmem.slice(readerOffset(), readableBytes());
    }

    @Override
    public boolean hasWritableArray() {
        return wmem.hasArray();
    }

    @Override
    public byte[] writableArray() {
        return wmem.array();
    }

    @Override
    public int writableArrayOffset() {
        return wmem.arrayOffset() + woff;
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
        return wmem.slice(writerOffset(), writableBytes());
    }

    @Override
    public NioBuffer first() {
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
        var value = rmem.get(roff);
        roff += Byte.BYTES;
        return value;
    }

    @Override
    public byte getByte(int roff) {
        checkGet(roff, Byte.BYTES);
        return rmem.get(roff);
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
        try {
            wmem.put(woff, value);
            woff += Byte.BYTES;
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw checkWriteState(e, woff, Byte.BYTES);
        } catch (ReadOnlyBufferException e) {
            throw bufferIsReadOnly(this);
        }
    }

    @Override
    public Buffer setByte(int woff, byte value) {
        try {
            wmem.put(woff, value);
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw checkWriteState(e, woff, Byte.BYTES);
        } catch (ReadOnlyBufferException e) {
            throw bufferIsReadOnly(this);
        }
    }

    @Override
    public Buffer writeUnsignedByte(int value) {
        checkWrite(woff, Byte.BYTES, true);
        try {
            wmem.put(woff, (byte) (value & 0xFF));
            woff += Byte.BYTES;
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw checkWriteState(e, woff, Byte.BYTES);
        } catch (ReadOnlyBufferException e) {
            throw bufferIsReadOnly(this);
        }
    }

    @Override
    public Buffer setUnsignedByte(int woff, int value) {
        try {
            wmem.put(woff, (byte) (value & 0xFF));
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw checkWriteState(e, woff, Byte.BYTES);
        } catch (ReadOnlyBufferException e) {
            throw bufferIsReadOnly(this);
        }
    }

    @Override
    public char readChar() {
        checkRead(roff, Character.BYTES);
        var value = rmem.getChar(roff);
        roff += Character.BYTES;
        return value;
    }

    @Override
    public char getChar(int roff) {
        checkGet(roff, Character.BYTES);
        return rmem.getChar(roff);
    }

    @Override
    public Buffer writeChar(char value) {
        checkWrite(woff, Character.BYTES, true);
        try {
            wmem.putChar(woff, value);
            woff += Character.BYTES;
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw checkWriteState(e, woff, Character.BYTES);
        } catch (ReadOnlyBufferException e) {
            throw bufferIsReadOnly(this);
        }
    }

    @Override
    public Buffer setChar(int woff, char value) {
        try {
            wmem.putChar(woff, value);
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw checkWriteState(e, woff, Character.BYTES);
        } catch (ReadOnlyBufferException e) {
            throw bufferIsReadOnly(this);
        }
    }

    @Override
    public short readShort() {
        checkRead(roff, Short.BYTES);
        var value = rmem.getShort(roff);
        roff += Short.BYTES;
        return value;
    }

    @Override
    public short getShort(int roff) {
        checkGet(roff, Short.BYTES);
        return rmem.getShort(roff);
    }

    @Override
    public int readUnsignedShort() {
        checkRead(roff, Short.BYTES);
        var value = rmem.getShort(roff) & 0xFFFF;
        roff += Short.BYTES;
        return value;
    }

    @Override
    public int getUnsignedShort(int roff) {
        checkGet(roff, Short.BYTES);
        return rmem.getShort(roff) & 0xFFFF;
    }

    @Override
    public Buffer writeShort(short value) {
        checkWrite(woff, Short.BYTES, true);
        try {
            wmem.putShort(woff, value);
            woff += Short.BYTES;
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw checkWriteState(e, woff, Short.BYTES);
        } catch (ReadOnlyBufferException e) {
            throw bufferIsReadOnly(this);
        }
    }

    @Override
    public Buffer setShort(int woff, short value) {
        try {
            wmem.putShort(woff, value);
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw checkWriteState(e, woff, Short.BYTES);
        } catch (ReadOnlyBufferException e) {
            throw bufferIsReadOnly(this);
        }
    }

    @Override
    public Buffer writeUnsignedShort(int value) {
        checkWrite(woff, Short.BYTES, true);
        try {
            wmem.putShort(woff, (short) (value & 0xFFFF));
            woff += Short.BYTES;
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw checkWriteState(e, woff, Short.BYTES);
        } catch (ReadOnlyBufferException e) {
            throw bufferIsReadOnly(this);
        }
    }

    @Override
    public Buffer setUnsignedShort(int woff, int value) {
        try {
            wmem.putShort(woff, (short) (value & 0xFFFF));
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw checkWriteState(e, woff, Short.BYTES);
        } catch (ReadOnlyBufferException e) {
            throw bufferIsReadOnly(this);
        }
    }

    @Override
    public int readMedium() {
        checkRead(roff, 3);
        int value = rmem.get(roff) << 16 | (rmem.get(roff + 1) & 0xFF) << 8 | rmem.get(roff + 2) & 0xFF;
        roff += 3;
        return value;
    }

    @Override
    public int getMedium(int roff) {
        checkGet(roff, 3);
        return rmem.get(roff) << 16 | (rmem.get(roff + 1) & 0xFF) << 8 | rmem.get(roff + 2) & 0xFF;
    }

    @Override
    public int readUnsignedMedium() {
        checkRead(roff, 3);
        int value = (rmem.get(roff) << 16 | (rmem.get(roff + 1) & 0xFF) << 8 | rmem.get(roff + 2) & 0xFF) & 0xFFFFFF;
        roff += 3;
        return value;
    }

    @Override
    public int getUnsignedMedium(int roff) {
        checkGet(roff, 3);
        return (rmem.get(roff) << 16 | (rmem.get(roff + 1) & 0xFF) << 8 | rmem.get(roff + 2) & 0xFF) & 0xFFFFFF;
    }

    @Override
    public Buffer writeMedium(int value) {
        checkWrite(woff, 3, true);
        wmem.put(woff, (byte) (value >> 16));
        wmem.put(woff + 1, (byte) (value >> 8 & 0xFF));
        wmem.put(woff + 2, (byte) (value & 0xFF));
        woff += 3;
        return this;
    }

    @Override
    public Buffer setMedium(int woff, int value) {
        checkSet(woff, 3);
        wmem.put(woff, (byte) (value >> 16));
        wmem.put(woff + 1, (byte) (value >> 8 & 0xFF));
        wmem.put(woff + 2, (byte) (value & 0xFF));
        return this;
    }

    @Override
    public Buffer writeUnsignedMedium(int value) {
        checkWrite(woff, 3, true);
        wmem.put(woff, (byte) (value >> 16));
        wmem.put(woff + 1, (byte) (value >> 8 & 0xFF));
        wmem.put(woff + 2, (byte) (value & 0xFF));
        woff += 3;
        return this;
    }

    @Override
    public Buffer setUnsignedMedium(int woff, int value) {
        checkSet(woff, 3);
        wmem.put(woff, (byte) (value >> 16));
        wmem.put(woff + 1, (byte) (value >> 8 & 0xFF));
        wmem.put(woff + 2, (byte) (value & 0xFF));
        return this;
    }

    @Override
    public int readInt() {
        checkRead(roff, Integer.BYTES);
        var value = rmem.getInt(roff);
        roff += Integer.BYTES;
        return value;
    }

    @Override
    public int getInt(int roff) {
        checkGet(roff, Integer.BYTES);
        return rmem.getInt(roff);
    }

    @Override
    public long readUnsignedInt() {
        checkRead(roff, Integer.BYTES);
        var value = rmem.getInt(roff) & 0xFFFFFFFFL;
        roff += Integer.BYTES;
        return value;
    }

    @Override
    public long getUnsignedInt(int roff) {
        checkGet(roff, Integer.BYTES);
        return rmem.getInt(roff) & 0xFFFFFFFFL;
    }

    @Override
    public Buffer writeInt(int value) {
        checkWrite(woff, Integer.BYTES, true);
        try {
            wmem.putInt(woff, value);
            woff += Integer.BYTES;
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw checkWriteState(e, woff, Integer.BYTES);
        } catch (ReadOnlyBufferException e) {
            throw bufferIsReadOnly(this);
        }
    }

    @Override
    public Buffer setInt(int woff, int value) {
        try {
            wmem.putInt(woff, value);
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw checkWriteState(e, this.woff, Integer.BYTES);
        } catch (ReadOnlyBufferException e) {
            throw bufferIsReadOnly(this);
        }
    }

    @Override
    public Buffer writeUnsignedInt(long value) {
        checkWrite(woff, Integer.BYTES, true);
        try {
            wmem.putInt(woff, (int) (value & 0xFFFFFFFFL));
            woff += Integer.BYTES;
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw checkWriteState(e, woff, Integer.BYTES);
        } catch (ReadOnlyBufferException e) {
            throw bufferIsReadOnly(this);
        }
    }

    @Override
    public Buffer setUnsignedInt(int woff, long value) {
        try {
            wmem.putInt(woff, (int) (value & 0xFFFFFFFFL));
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw checkWriteState(e, this.woff, Integer.BYTES);
        } catch (ReadOnlyBufferException e) {
            throw bufferIsReadOnly(this);
        }
    }

    @Override
    public float readFloat() {
        checkRead(roff, Float.BYTES);
        var value = rmem.getFloat(roff);
        roff += Float.BYTES;
        return value;
    }

    @Override
    public float getFloat(int roff) {
        checkGet(roff, Float.BYTES);
        return rmem.getFloat(roff);
    }

    @Override
    public Buffer writeFloat(float value) {
        checkWrite(woff, Float.BYTES, true);
        try {
            wmem.putFloat(woff, value);
            woff += Float.BYTES;
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw checkWriteState(e, woff, Float.BYTES);
        } catch (ReadOnlyBufferException e) {
            throw bufferIsReadOnly(this);
        }
    }

    @Override
    public Buffer setFloat(int woff, float value) {
        try {
            wmem.putFloat(woff, value);
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw checkWriteState(e, woff, Float.BYTES);
        } catch (ReadOnlyBufferException e) {
            throw bufferIsReadOnly(this);
        }
    }

    @Override
    public long readLong() {
        checkRead(roff, Long.BYTES);
        var value = rmem.getLong(roff);
        roff += Long.BYTES;
        return value;
    }

    @Override
    public long getLong(int roff) {
        checkGet(roff, Long.BYTES);
        return rmem.getLong(roff);
    }

    @Override
    public Buffer writeLong(long value) {
        checkWrite(woff, Long.BYTES, true);
        try {
            wmem.putLong(woff, value);
            woff += Long.BYTES;
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw checkWriteState(e, woff, Long.BYTES);
        } catch (ReadOnlyBufferException e) {
            throw bufferIsReadOnly(this);
        }
    }

    @Override
    public Buffer setLong(int woff, long value) {
        try {
            wmem.putLong(woff, value);
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw checkWriteState(e, woff, Long.BYTES);
        } catch (ReadOnlyBufferException e) {
            throw bufferIsReadOnly(this);
        }
    }

    @Override
    public double readDouble() {
        checkRead(roff, Double.BYTES);
        var value = rmem.getDouble(roff);
        roff += Double.BYTES;
        return value;
    }

    @Override
    public double getDouble(int roff) {
        checkGet(roff, Double.BYTES);
        return rmem.getDouble(roff);
    }

    @Override
    public Buffer writeDouble(double value) {
        checkWrite(woff, Double.BYTES, true);
        try {
            wmem.putDouble(woff, value);
            woff += Double.BYTES;
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw checkWriteState(e, woff, Double.BYTES);
        } catch (ReadOnlyBufferException e) {
            throw bufferIsReadOnly(this);
        }
    }

    @Override
    public Buffer setDouble(int woff, double value) {
        try {
            wmem.putDouble(woff, value);
            return this;
        } catch (IndexOutOfBoundsException e) {
            throw checkWriteState(e, woff, Double.BYTES);
        } catch (ReadOnlyBufferException e) {
            throw bufferIsReadOnly(this);
        }
    }
    // </editor-fold>

    @Override
    protected Owned<NioBuffer> prepareSend() {
        int roff = this.roff;
        int woff = this.woff;
        boolean readOnly = readOnly();
        int implicitCapacityLimit = this.implicitCapacityLimit;
        ByteBuffer base = this.base;
        ByteBuffer rmem = this.rmem;
        return drop -> {
            NioBuffer copy = new NioBuffer(base, rmem, control, drop);
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
        base = CLOSED_BUFFER;
        rmem = CLOSED_BUFFER;
        wmem = CLOSED_BUFFER;
        roff = 0;
        woff = 0;
    }

    private void checkRead(int index, int size) {
        if (index < 0 | woff < index + size) {
            throw readAccessCheckException(index, size);
        }
    }

    private void checkGet(int index, int size) {
        if (index < 0 | capacity() < index + size) {
            throw readAccessCheckException(index, size);
        }
    }

    private void checkWrite(int index, int size, boolean mayExpand) {
        if (index < roff | wmem.capacity() < index + size) {
            handleWriteAccessBoundsFailure(index, size, mayExpand);
        }
    }

    private void checkSet(int index, int size) {
        if (index < 0 | wmem.capacity() < index + size) {
            handleWriteAccessBoundsFailure(index, size, false);
        }
    }

    private RuntimeException checkWriteState(IndexOutOfBoundsException ioobe, int offset, int size) {
        if (rmem == CLOSED_BUFFER) {
            return bufferIsClosed();
        }
        if (wmem != rmem) {
            return bufferIsReadOnly(this);
        }

        IndexOutOfBoundsException exception = outOfBounds(offset, size);
        exception.addSuppressed(ioobe);
        return exception;
    }

    private RuntimeException readAccessCheckException(int index, int size) {
        if (rmem == CLOSED_BUFFER) {
            return bufferIsClosed();
        }
        return outOfBounds(index, size);
    }

    private void handleWriteAccessBoundsFailure(int index, int size, boolean mayExpand) {
        if (rmem == CLOSED_BUFFER) {
            throw bufferIsClosed();
        }
        if (wmem != rmem) {
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
                "[read 0 to " + woff + ", write 0 to " + rmem.capacity() + "].");
    }

    ByteBuffer recoverable() {
        return base;
    }

    NioBuffer newConstChild() {
        assert readOnly();
        Drop<NioBuffer> drop = unsafeGetDrop().fork();
        NioBuffer child = new NioBuffer(this, drop);
        drop.attach(child);
        return child;
    }

    private static final class ForwardNioByteCursor implements ByteCursor {
        // Duplicate source buffer to keep our own byte order state.
        final ByteBuffer buffer;
        int index;
        final int end;
        byte byteValue;

        ForwardNioByteCursor(ByteBuffer rmem, int fromOffset, int length) {
            buffer = rmem;
            index = fromOffset;
            end = index + length;
            byteValue = -1;
        }

        @Override
        public boolean readByte() {
            if (index < end) {
                byteValue = buffer.get(index);
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

    private static final class ReverseNioByteCursor implements ByteCursor {
        final ByteBuffer buffer;
        int index;
        final int end;
        byte byteValue;

        ReverseNioByteCursor(ByteBuffer rmem, int fromOffset, int length) {
            buffer = rmem;
            index = fromOffset;
            end = index - length;
            byteValue = -1;
        }

        @Override
        public boolean readByte() {
            if (index > end) {
                byteValue = buffer.get(index);
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
