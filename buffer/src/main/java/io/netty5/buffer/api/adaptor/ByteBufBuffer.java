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
import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.Unpooled;
import io.netty5.buffer.api.AllocationType;
import io.netty5.buffer.api.AllocatorControl;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.BufferClosedException;
import io.netty5.buffer.api.BufferReadOnlyException;
import io.netty5.buffer.api.ByteCursor;
import io.netty5.buffer.api.Drop;
import io.netty5.buffer.api.MemoryManager;
import io.netty5.buffer.api.Owned;
import io.netty5.buffer.api.ReadableComponent;
import io.netty5.buffer.api.ReadableComponentProcessor;
import io.netty5.buffer.api.StandardAllocationTypes;
import io.netty5.buffer.api.WritableComponent;
import io.netty5.buffer.api.WritableComponentProcessor;
import io.netty5.buffer.api.internal.ResourceSupport;
import io.netty5.buffer.api.internal.Statics;
import io.netty5.util.IllegalReferenceCountException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.function.Supplier;

import static io.netty5.buffer.api.internal.Statics.bufferIsClosed;
import static io.netty5.buffer.api.internal.Statics.bufferIsReadOnly;
import static io.netty5.buffer.api.internal.Statics.checkLength;
import static io.netty5.buffer.api.internal.Statics.convert;
import static io.netty5.buffer.api.internal.Statics.nativeAddressOfDirectByteBuffer;
import static io.netty5.buffer.api.internal.Statics.nativeAddressWithOffset;

/**
 * An implementation of the {@link Buffer} interface, that wraps a {@link ByteBuf}.
 * <p>
 * This can be used for bridging {@link ByteBuf} based and {@link Buffer} based handlers.
 * It can also be used as a faithful and complete {@link Buffer} implementation, although some operations may incur
 * some extra overhead compared to the other implementations.
 * <p>
 * To create an instance, you can either use the regular {@link Buffer} and {@link BufferAllocator} APIs,
 * or you can allocate a {@link ByteBuf} yourself, and then {@linkplain #wrap(ByteBuf) wrap} it.
 */
public final class ByteBufBuffer extends ResourceSupport<Buffer, ByteBufBuffer> implements Buffer {

    private final AllocatorControl control;
    private ByteBuf delegate;

    private ByteBufBuffer(AllocatorControl control, ByteBuf delegate, Drop<ByteBufBuffer> drop) {
        super(drop);
        this.control = control;
        this.delegate = delegate;
    }

    static Buffer wrap(ByteBuf byteBuf, AllocatorControl control, Drop<ByteBufBuffer> drop) {
        ByteBufBuffer buf = new ByteBufBuffer(control, byteBuf, drop);
        drop.attach(buf);
        return buf;
    }

    /**
     * Wrap the given {@link ByteBuf} instance in a {@link Buffer} implementation.
     * <p>
     * <strong>Note:</strong> This can be used to create aliased buffers, which violates the {@link Buffer} interface
     * contract! A {@link ByteBuf} should only be wrapped once, and not shared with anything else.
     * <p>
     * <strong>Note:</strong> This method does not increase the reference count of the given {@link ByteBuf}.
     *
     * @param byteBuf The {@link ByteBuf} instance to wrap.
     * @return A new {@link Buffer} instance, backed by the given {@link ByteBuf}.
     */
    public static Buffer wrap(ByteBuf byteBuf) {
        Buffer buffer = ByteBufAdaptor.extract(byteBuf);
        if (buffer == null) {
            buffer = new ByteBufBuffer(new ByteBufAllocatorControl(byteBuf), byteBuf, ByteBufDrop.INSTANCE);
        }
        return buffer;
    }

    /**
     * Unwrap this {@link Buffer} instance by returning the inner {@link ByteBuf} instance.
     * <p>
     * This method destroys this {@link Buffer} instance, as if {@link #close()} is called upon it,
     * but without harm to the returned {@link ByteBuf} instance.
     *
     * @return The inner {@link ByteBuf} instance.
     */
    public ByteBuf unwrapAndClose() {
        try (ByteBufBuffer ignore = this) {
            return delegate.retain();
        }
    }

    @Override
    public String toString() {
        return "Buffer[roff:" + delegate.readerIndex() + ", woff:" + delegate.writerIndex() +
               ", cap:" + delegate.capacity() + ']';
    }

    @Override
    protected RuntimeException createResourceClosedException() {
        return bufferIsClosed(this);
    }

    @Override
    protected Owned<ByteBufBuffer> prepareSend() {
        final ByteBuf delegate = this.delegate;
        return new Owned<ByteBufBuffer>() {
            @Override
            public ByteBufBuffer transferOwnership(Drop<ByteBufBuffer> drop) {
                return new ByteBufBuffer(control, delegate, drop);
            }
        };
    }

    @Override
    protected void makeInaccessible() {
        super.makeInaccessible();
        delegate = ClosedByteBufHolder.closedByteBufInstance();
    }

    @Override
    public int capacity() {
        return delegate.capacity();
    }

    @Override
    public int readerOffset() {
        return delegate.readerIndex();
    }

    @Override
    public void skipReadable(int delta) {
        delegate.readerIndex(delegate.readerIndex() + delta);
    }

    @Override
    public Buffer readerOffset(int offset) {
        delegate.readerIndex(offset);
        return this;
    }

    @Override
    public int writerOffset() {
        return delegate.writerIndex();
    }

    @Override
    public void skipWritable(int delta) {
        delegate.writerIndex(delegate.writerIndex() + delta);
    }

    @Override
    public Buffer writerOffset(int offset) {
        if (readOnly()) {
            throw bufferIsReadOnly(this);
        }
        delegate.writerIndex(offset);
        return this;
    }

    @Override
    public int readableBytes() {
        return delegate.readableBytes();
    }

    @Override
    public int writableBytes() {
        return delegate.capacity() - delegate.writerIndex();
    }

    @Override
    public Buffer fill(byte value) {
        if (!isAccessible()) {
            throw attachTrace(bufferIsClosed(this));
        }
        if (readOnly()) {
            throw bufferIsReadOnly(this);
        }
        int capacity = capacity();
        for (int i = 0; i < capacity; i++) {
            delegate.setByte(i, value);
        }
        return this;
    }

    @Override
    public Buffer makeReadOnly() {
        delegate = delegate.asReadOnly();
        return this;
    }

    @Override
    public boolean readOnly() {
        return delegate.isReadOnly();
    }

    @Override
    public boolean isDirect() {
        return delegate.isDirect();
    }

    @Override
    public void copyInto(int srcPos, byte[] dest, int destPos, int length) {
        if (!isAccessible()) {
            throw attachTrace(bufferIsClosed(this));
        }
        delegate.getBytes(srcPos, dest, destPos, length);
    }

    @Override
    public void copyInto(int srcPos, ByteBuffer dest, int destPos, int length) {
        if (!isAccessible()) {
            throw attachTrace(bufferIsClosed(this));
        }
        if (dest.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }
        dest = dest.duplicate().clear();
        for (int i = 0; i < length; i++) {
            dest.put(destPos + i, getByte(srcPos + i));
        }
    }

    @Override
    public void copyInto(int srcPos, Buffer dest, int destPos, int length) {
        if (!isAccessible()) {
            throw attachTrace(bufferIsClosed(this));
        }
        if (dest.readOnly()) {
            throw bufferIsReadOnly(dest);
        }
        while (--length >= 0) {
            dest.setByte(destPos + length, getByte(srcPos + length));
        }
    }

    @Override
    public int transferTo(WritableByteChannel channel, int length) throws IOException {
        if (!isAccessible()) {
            throw attachTrace(bufferIsClosed(this));
        }
        length = Math.min(readableBytes(), length);
        if (length == 0) {
            return 0;
        }
        if (length < 0) {
            throw new IllegalArgumentException("Length cannot be negative: " + length + '.');
        }
        if (channel instanceof GatheringByteChannel) {
            GatheringByteChannel gatheringByteChannel = (GatheringByteChannel) channel;
            int bytesWritten = delegate.getBytes(readerOffset(), gatheringByteChannel, length);
            if (bytesWritten > 0) {
                skipReadable(bytesWritten);
            }
            return bytesWritten;
        }
        ByteBuffer buf = ByteBuffer.allocateDirect(Math.min(length, readableBytes()));
        copyInto(readerOffset(), buf, 0, buf.remaining());
        int bytesWritten = channel.write(buf);
        if (bytesWritten > 0) {
            skipReadable(bytesWritten);
        }
        return bytesWritten;
    }

    @Override
    public int transferFrom(ReadableByteChannel channel, int length) throws IOException {
        if (!isAccessible()) {
            throw attachTrace(bufferIsClosed(this));
        }
        if (readOnly()) {
            throw bufferIsReadOnly(this);
        }
        length = Math.min(writableBytes(), length);
        if (length == 0) {
            return 0;
        }
        if (length < 0) {
            throw new IllegalArgumentException("Length cannot be negative: " + length + '.');
        }
        if (!channel.isOpen()) {
            throw new ClosedChannelException();
        }
        if (channel instanceof ScatteringByteChannel) {
            ScatteringByteChannel scatteringByteChannel = (ScatteringByteChannel) channel;
            int bytesRead = delegate.setBytes(readerOffset(), scatteringByteChannel, length);
            if (bytesRead > 0) {
                skipWritable(bytesRead);
            }
            return bytesRead;
        }
        ByteBuffer buf = ByteBuffer.allocateDirect(Math.min(length, writableBytes()));
        int bytesRead = channel.read(buf);
        buf.flip();
        while (buf.hasRemaining()) {
            writeByte(buf.get());
        }
        return bytesRead;
    }

    @Override
    public int bytesBefore(byte needle) {
        if (!isAccessible()) {
            throw bufferIsClosed(this);
        }
        return delegate.bytesBefore(needle);
    }

    @Override
    public ByteCursor openCursor() {
        return openCursor(readerOffset(), readableBytes());
    }

    @Override
    public ByteCursor openCursor(int fromOffset, int length) {
        if (!isAccessible()) {
            throw attachTrace(bufferIsClosed(this));
        }
        if (fromOffset < 0) {
            throw new IllegalArgumentException("The fromOffset cannot be negative: " + fromOffset + '.');
        }
        checkLength(length);
        if (capacity() < fromOffset + length) {
            throw new IllegalArgumentException("The fromOffset + length is beyond the end of the buffer: " +
                                               "fromOffset = " + fromOffset + ", length = " + length + '.');
        }
        return new ForwardCursor(delegate, fromOffset, length);
    }

    @Override
    public ByteCursor openReverseCursor(int fromOffset, int length) {
        if (!isAccessible()) {
            throw attachTrace(bufferIsClosed(this));
        }
        if (fromOffset < 0) {
            throw new IllegalArgumentException("The fromOffset cannot be negative: " + fromOffset + '.');
        }
        checkLength(length);
        if (capacity() <= fromOffset) {
            throw new IllegalArgumentException("The fromOffset is beyond the end of the buffer: " + fromOffset + '.');
        }
        if (fromOffset - length < -1) {
            throw new IllegalArgumentException("The fromOffset - length would underflow the buffer: " +
                                               "fromOffset = " + fromOffset + ", length = " + length + '.');
        }
        return new ReverseCursor(delegate, fromOffset, length);
    }

    @Override
    public Buffer ensureWritable(int size, int minimumGrowth, boolean allowCompaction) {
        if (!isAccessible()) {
            throw attachTrace(bufferIsClosed(this));
        }
        if (size < 0) {
            throw new IllegalArgumentException("Cannot ensure writable for a negative size: " + size + '.');
        }
        if (readOnly()) {
            throw bufferIsReadOnly(this);
        }
        if (writableBytes() > size) {
            return this;
        }
        if (allowCompaction) {
            compact();
            if (writableBytes() > size) {
                return this;
            }
        }
        int growBy = Math.max(minimumGrowth, size - writableBytes());
        delegate.ensureWritable(growBy, true);
        if (writableBytes() < size) {
            // The ensureWritable call is not guaranteed to work, in which case we'll have to re-allocate ourselves.
            int newSize = readableBytes() + writableBytes() + growBy;
            Statics.assertValidBufferSize(newSize);
            ByteBufBuffer buffer = (ByteBufBuffer) control.getAllocator().allocate(newSize);

            // Copy contents
            copyInto(0, buffer, 0, capacity());

            // Release the old memory ad install the new:
            Drop<ByteBufBuffer> drop = buffer.unsafeGetDrop();
            int roff = readerOffset();
            int woff = writerOffset();
            unsafeGetDrop().drop(this);
            delegate = buffer.delegate;
            unsafeSetDrop(drop);
            drop.attach(this);
            writerOffset(woff);
            readerOffset(roff);
        }
        return this;
    }

    @Override
    public Buffer copy(int offset, int length) {
        Buffer copy = control.getAllocator().allocate(length);
        try {
            copyInto(offset, copy, 0, length);
            copy.skipWritable(length);
            return copy;
        } catch (Throwable e) {
            copy.close();
            throw e;
        }
    }

    @Override
    public Buffer split(int splitOffset) {
        if (!isAccessible()) {
            throw attachTrace(bufferIsClosed(this));
        }
        if (splitOffset < 0) {
            throw new IllegalArgumentException("The split offset cannot be negative: " + splitOffset + '.');
        }
        if (capacity() < splitOffset) {
            throw new IllegalArgumentException("The split offset cannot be greater than the buffer capacity, " +
                                               "but the split offset was " + splitOffset +
                                               ", and capacity is " + capacity() + '.');
        }
        if (!isOwned()) {
            throw attachTrace(new IllegalStateException("Cannot split a buffer that is not owned."));
        }
        int woff = writerOffset();
        int roff = readerOffset();
        var drop = unsafeGetDrop().fork();
        var splitBuffer = new ByteBufBuffer(control, delegate.slice(0, splitOffset), drop);
        drop.attach(splitBuffer);
        // ByteBuf.slice() have different semantics for the offsets, so we need to re-compute them.
        splitBuffer.delegate.writerIndex(Math.min(woff, splitOffset));
        splitBuffer.delegate.readerIndex(Math.min(roff, splitOffset));
        delegate = delegate.slice(splitOffset, capacity() - splitOffset);
        delegate.writerIndex(Math.max(woff, splitOffset) - splitOffset);
        delegate.readerIndex(Math.max(roff, splitOffset) - splitOffset);
        return splitBuffer;
    }

    @Override
    public Buffer compact() {
        if (!isAccessible()) {
            throw attachTrace(bufferIsClosed(this));
        }
        if (!isOwned()) {
            throw attachTrace(new IllegalStateException("Buffer must be owned in order to compact."));
        }
        if (readOnly()) {
            throw bufferIsReadOnly(this);
        }
        delegate.discardReadBytes();
        return this;
    }

    @Override
    public int countComponents() {
        return Math.max(1, delegate.nioBufferCount());
    }

    @Override
    public int countReadableComponents() {
        return forEachReadable(0, ComponentCounter.INSTANCE);
    }

    @Override
    public int countWritableComponents() {
        return forEachWritable(0, ComponentCounter.INSTANCE);
    }

    @Override
    public <E extends Exception> int forEachReadable(int initialIndex, ReadableComponentProcessor<E> processor)
            throws E {
        if (!isAccessible()) {
            throw attachTrace(bufferIsClosed(this));
        }
        int readableBytes = readableBytes();
        if (readableBytes == 0) {
            return 0;
        }
        if (delegate.nioBufferCount() == 1) {
            ByteBuffer byteBuffer = delegate.nioBuffer(readerOffset(), readableBytes).asReadOnlyBuffer();
            if (processor.process(initialIndex, new ReadableBufferComponent(byteBuffer, this))) {
                return 1;
            } else {
                return -1;
            }
        }
        ByteBuffer[] byteBuffers = delegate.nioBuffers(readerOffset(), readableBytes);
        for (int i = 0; i < byteBuffers.length; i++) {
            ReadableBufferComponent component = new ReadableBufferComponent(byteBuffers[i], this);
            if (!processor.process(initialIndex + i, component)) {
                return -(i + 1);
            }
        }
        return byteBuffers.length;
    }

    @Override
    public <E extends Exception> int forEachWritable(int initialIndex, WritableComponentProcessor<E> processor)
            throws E {
        if (!isAccessible()) {
            throw attachTrace(bufferIsClosed(this));
        }
        if (readOnly()) {
            throw bufferIsReadOnly(this);
        }
        int writableBytes = writableBytes();
        if (writableBytes == 0) {
            return 0;
        }
        if (delegate.nioBufferCount() == 1) {
            ByteBuffer byteBuffer = delegate.nioBuffer(writerOffset(), writableBytes);
            if (processor.process(initialIndex, new WritableBufferComponent(byteBuffer, this))) {
                return 1;
            } else {
                return -1;
            }
        }
        ByteBuffer[] byteBuffers = delegate.nioBuffers(writerOffset(), writableBytes);
        for (int i = 0; i < byteBuffers.length; i++) {
            WritableBufferComponent component = new WritableBufferComponent(byteBuffers[i], this);
            if (!processor.process(initialIndex + i, component)) {
                return -(i + 1);
            }
        }
        return byteBuffers.length;
    }

    @Override
    public byte readByte() {
        try {
            return delegate.readByte();
        } catch (RuntimeException e) {
            throw accessException(e, false);
        }
    }

    @Override
    public byte getByte(int roff) {
        try {
            return delegate.getByte(roff);
        } catch (RuntimeException e) {
            throw accessException(e, false);
        }
    }

    @Override
    public int readUnsignedByte() {
        try {
            return delegate.readUnsignedByte();
        } catch (RuntimeException e) {
            throw accessException(e, false);
        }
    }

    @Override
    public int getUnsignedByte(int roff) {
        try {
            return delegate.getUnsignedByte(roff);
        } catch (RuntimeException e) {
            throw accessException(e, false);
        }
    }

    @Override
    public Buffer writeByte(byte value) {
        try {
            delegate.writeByte(value);
            return this;
        } catch (RuntimeException e) {
            throw accessException(e, true);
        }
    }

    @Override
    public Buffer setByte(int woff, byte value) {
        try {
            delegate.setByte(woff, value);
            return this;
        } catch (RuntimeException e) {
            throw accessException(e, true);
        }
    }

    @Override
    public Buffer writeUnsignedByte(int value) {
        try {
            delegate.writeByte((byte) (value & 0xFF));
            return this;
        } catch (RuntimeException e) {
            throw accessException(e, true);
        }
    }

    @Override
    public Buffer setUnsignedByte(int woff, int value) {
        try {
            delegate.setByte(woff, (byte) (value & 0xFF));
            return this;
        } catch (RuntimeException e) {
            throw accessException(e, true);
        }
    }

    @Override
    public char readChar() {
        try {
            return delegate.readChar();
        } catch (RuntimeException e) {
            throw accessException(e, false);
        }
    }

    @Override
    public char getChar(int roff) {
        try {
            return delegate.getChar(roff);
        } catch (RuntimeException e) {
            throw accessException(e, false);
        }
    }

    @Override
    public Buffer writeChar(char value) {
        try {
            delegate.getChar(writerOffset()); // Force a bounds check
            delegate.writeChar(value);
            return this;
        } catch (RuntimeException e) {
            throw accessException(e, true);
        }
    }

    @Override
    public Buffer setChar(int woff, char value) {
        try {
            delegate.getChar(woff); // Force a bounds check
            delegate.setChar(woff, value);
            return this;
        } catch (RuntimeException e) {
            throw accessException(e, true);
        }
    }

    @Override
    public short readShort() {
        try {
            return delegate.readShort();
        } catch (RuntimeException e) {
            throw accessException(e, false);
        }
    }

    @Override
    public short getShort(int roff) {
        try {
            return delegate.getShort(roff);
        } catch (RuntimeException e) {
            throw accessException(e, false);
        }
    }

    @Override
    public int readUnsignedShort() {
        try {
            return delegate.readUnsignedShort();
        } catch (RuntimeException e) {
            throw accessException(e, false);
        }
    }

    @Override
    public int getUnsignedShort(int roff) {
        try {
            return delegate.getUnsignedShort(roff);
        } catch (RuntimeException e) {
            throw accessException(e, false);
        }
    }

    @Override
    public Buffer writeShort(short value) {
        try {
            delegate.getShort(writerOffset()); // Force a bounds check
            delegate.writeShort(value);
            return this;
        } catch (RuntimeException e) {
            throw accessException(e, true);
        }
    }

    @Override
    public Buffer setShort(int woff, short value) {
        try {
            delegate.getShort(woff); // Force a bounds check
            delegate.setShort(woff, value);
            return this;
        } catch (RuntimeException e) {
            throw accessException(e, true);
        }
    }

    @Override
    public Buffer writeUnsignedShort(int value) {
        try {
            delegate.getShort(writerOffset()); // Force a bounds check
            delegate.writeShort((short) (value & 0xFFFF));
            return this;
        } catch (RuntimeException e) {
            throw accessException(e, true);
        }
    }

    @Override
    public Buffer setUnsignedShort(int woff, int value) {
        try {
            delegate.getShort(woff); // Force a bounds check
            delegate.setShort(woff, (short) (value & 0xFFFF));
            return this;
        } catch (RuntimeException e) {
            throw accessException(e, true);
        }
    }

    @Override
    public int readMedium() {
        try {
            return delegate.readMedium();
        } catch (RuntimeException e) {
            throw accessException(e, false);
        }
    }

    @Override
    public int getMedium(int roff) {
        try {
            return delegate.getMedium(roff);
        } catch (RuntimeException e) {
            throw accessException(e, false);
        }
    }

    @Override
    public int readUnsignedMedium() {
        try {
            return delegate.readUnsignedMedium();
        } catch (RuntimeException e) {
            throw accessException(e, false);
        }
    }

    @Override
    public int getUnsignedMedium(int roff) {
        try {
            return delegate.getUnsignedMedium(roff);
        } catch (RuntimeException e) {
            throw accessException(e, false);
        }
    }

    @Override
    public Buffer writeMedium(int value) {
        try {
            delegate.getMedium(writerOffset()); // Force a bounds check
            delegate.writeMedium(value);
            return this;
        } catch (RuntimeException e) {
            throw accessException(e, true);
        }
    }

    @Override
    public Buffer setMedium(int woff, int value) {
        try {
            delegate.getMedium(woff); // Force a bounds check
            delegate.setMedium(woff, value);
            return this;
        } catch (RuntimeException e) {
            throw accessException(e, true);
        }
    }

    @Override
    public Buffer writeUnsignedMedium(int value) {
        try {
            delegate.getMedium(writerOffset()); // Force a bounds check
            delegate.writeMedium(value);
            return this;
        } catch (RuntimeException e) {
            throw accessException(e, true);
        }
    }

    @Override
    public Buffer setUnsignedMedium(int woff, int value) {
        try {
            delegate.getMedium(woff); // Force a bounds check
            delegate.setMedium(woff, value);
            return this;
        } catch (RuntimeException e) {
            throw accessException(e, true);
        }
    }

    @Override
    public int readInt() {
        try {
            return delegate.readInt();
        } catch (RuntimeException e) {
            throw accessException(e, false);
        }
    }

    @Override
    public int getInt(int roff) {
        try {
            return delegate.getInt(roff);
        } catch (RuntimeException e) {
            throw accessException(e, false);
        }
    }

    @Override
    public long readUnsignedInt() {
        try {
            return delegate.readUnsignedInt();
        } catch (RuntimeException e) {
            throw accessException(e, false);
        }
    }

    @Override
    public long getUnsignedInt(int roff) {
        try {
            return delegate.getUnsignedInt(roff);
        } catch (RuntimeException e) {
            throw accessException(e, false);
        }
    }

    @Override
    public Buffer writeInt(int value) {
        try {
            delegate.getInt(writerOffset()); // Force a bounds check
            delegate.writeInt(value);
            return this;
        } catch (RuntimeException e) {
            throw accessException(e, true);
        }
    }

    @Override
    public Buffer setInt(int woff, int value) {
        try {
            delegate.getInt(woff); // Force a bounds check
            delegate.setInt(woff, value);
            return this;
        } catch (RuntimeException e) {
            throw accessException(e, true);
        }
    }

    @Override
    public Buffer writeUnsignedInt(long value) {
        try {
            delegate.getInt(writerOffset()); // Force a bounds check
            delegate.writeInt((int) (value & 0xFFFFFFFFL));
            return this;
        } catch (RuntimeException e) {
            throw accessException(e, true);
        }
    }

    @Override
    public Buffer setUnsignedInt(int woff, long value) {
        try {
            delegate.getInt(woff); // Force a bounds check
            delegate.setInt(woff, (int) (value & 0xFFFFFFFFL));
            return this;
        } catch (RuntimeException e) {
            throw accessException(e, true);
        }
    }

    @Override
    public float readFloat() {
        try {
            return delegate.readFloat();
        } catch (RuntimeException e) {
            throw accessException(e, false);
        }
    }

    @Override
    public float getFloat(int roff) {
        try {
            return delegate.getFloat(roff);
        } catch (RuntimeException e) {
            throw accessException(e, false);
        }
    }

    @Override
    public Buffer writeFloat(float value) {
        try {
            delegate.getFloat(writerOffset()); // Force a bounds check
            delegate.writeFloat(value);
            return this;
        } catch (RuntimeException e) {
            throw accessException(e, true);
        }
    }

    @Override
    public Buffer setFloat(int woff, float value) {
        try {
            delegate.getFloat(woff); // Force a bounds check
            delegate.setFloat(woff, value);
            return this;
        } catch (RuntimeException e) {
            throw accessException(e, true);
        }
    }

    @Override
    public long readLong() {
        try {
            return delegate.readLong();
        } catch (RuntimeException e) {
            throw accessException(e, false);
        }
    }

    @Override
    public long getLong(int roff) {
        try {
            return delegate.getLong(roff);
        } catch (RuntimeException e) {
            throw accessException(e, false);
        }
    }

    @Override
    public Buffer writeLong(long value) {
        try {
            delegate.getLong(writerOffset()); // Force a bounds check
            delegate.writeLong(value);
            return this;
        } catch (RuntimeException e) {
            throw accessException(e, true);
        }
    }

    @Override
    public Buffer setLong(int woff, long value) {
        try {
            delegate.getLong(woff); // Force a bounds check
            delegate.setLong(woff, value);
            return this;
        } catch (RuntimeException e) {
            throw accessException(e, true);
        }
    }

    @Override
    public double readDouble() {
        try {
            return delegate.readDouble();
        } catch (RuntimeException e) {
            throw accessException(e, false);
        }
    }

    @Override
    public double getDouble(int roff) {
        try {
            return delegate.getDouble(roff);
        } catch (RuntimeException e) {
            throw accessException(e, false);
        }
    }

    @Override
    public Buffer writeDouble(double value) {
        try {
            delegate.getDouble(writerOffset()); // Force a bounds check
            delegate.writeDouble(value);
            return this;
        } catch (RuntimeException e) {
            throw accessException(e, true);
        }
    }

    @Override
    public Buffer setDouble(int woff, double value) {
        try {
            delegate.getDouble(woff); // Force a bounds check
            delegate.setDouble(woff, value);
            return this;
        } catch (RuntimeException e) {
            throw accessException(e, true);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Buffer) {
            return Statics.equals(this, (Buffer) obj);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Statics.hashCode(this);
    }

    Buffer newConstChild() {
        assert readOnly();
        var drop = unsafeGetDrop().fork();
        var child = new ByteBufBuffer(control, delegate.duplicate(), drop);
        drop.attach(child);
        return child;
    }

    ByteBuf unwrapRecoverableMemory() {
        return delegate;
    }

    private RuntimeException accessException(RuntimeException e, boolean isWrite) {
        if (e instanceof IllegalReferenceCountException) {
            BufferClosedException closed = attachTrace(bufferIsClosed(this));
            closed.addSuppressed(e);
            return closed;
        }
        if (e instanceof ReadOnlyBufferException) {
            BufferReadOnlyException readOnly = bufferIsReadOnly(this);
            readOnly.addSuppressed(e);
            return readOnly;
        }
        if (isWrite && readOnly() && e instanceof IndexOutOfBoundsException) {
            return bufferIsReadOnly(this);
        }
        return e;
    }

    private static final class ComponentCounter
            implements ReadableComponentProcessor<RuntimeException>,
                       WritableComponentProcessor<RuntimeException> {
        static final ComponentCounter INSTANCE = new ComponentCounter();

        @Override
        public boolean process(int index, ReadableComponent component) {
            return true;
        }

        @Override
        public boolean process(int index, WritableComponent component) {
            return true;
        }
    }

    private static final class ReadableBufferComponent implements ReadableComponent {
        private final ByteBuffer byteBuffer;
        private final Buffer buffer;
        private final int startByteBufferPosition;
        private final int startBufferReaderOffset;

        ReadableBufferComponent(ByteBuffer byteBuffer, Buffer buffer) {
            this.byteBuffer = byteBuffer;
            this.buffer = buffer;
            startByteBufferPosition = byteBuffer.position();
            startBufferReaderOffset = buffer.readerOffset();
        }

        @Override
        public boolean hasReadableArray() {
            return byteBuffer.hasArray();
        }

        @Override
        public byte[] readableArray() {
            return byteBuffer.array();
        }

        @Override
        public int readableArrayOffset() {
            return byteBuffer.arrayOffset();
        }

        @Override
        public int readableArrayLength() {
            return readableBytes();
        }

        @Override
        public long readableNativeAddress() {
            return nativeAddressWithOffset(nativeAddressOfDirectByteBuffer(byteBuffer), buffer.readerOffset());
        }

        @Override
        public ByteBuffer readableBuffer() {
            return byteBuffer;
        }

        @Override
        public int readableBytes() {
            return buffer.readableBytes();
        }

        @Override
        public ByteCursor openCursor() {
            return buffer.openCursor();
        }

        @Override
        public void skipReadable(int byteCount) {
            buffer.skipReadable(byteCount);
            int delta = buffer.readerOffset() - startBufferReaderOffset;
            byteBuffer.position(startByteBufferPosition + delta);
        }
    }

    private static final class WritableBufferComponent implements WritableComponent {
        private final ByteBuffer byteBuffer;
        private final Buffer buffer;
        private final int startByteBufferPosition;
        private final int startBufferWriterOffset;

        WritableBufferComponent(ByteBuffer byteBuffer, Buffer buffer) {
            this.byteBuffer = byteBuffer;
            this.buffer = buffer;
            startByteBufferPosition = byteBuffer.position();
            startBufferWriterOffset = buffer.writerOffset();
        }

        @Override
        public boolean hasWritableArray() {
            return byteBuffer.hasArray();
        }

        @Override
        public byte[] writableArray() {
            return byteBuffer.array();
        }

        @Override
        public int writableArrayOffset() {
            return byteBuffer.arrayOffset();
        }

        @Override
        public int writableArrayLength() {
            return writableBytes();
        }

        @Override
        public long writableNativeAddress() {
            return nativeAddressWithOffset(nativeAddressOfDirectByteBuffer(byteBuffer), buffer.writerOffset());
        }

        @Override
        public int writableBytes() {
            return buffer.writableBytes();
        }

        @Override
        public ByteBuffer writableBuffer() {
            return byteBuffer;
        }

        @Override
        public void skipWritable(int byteCount) {
            buffer.skipWritable(byteCount);
            int delta = buffer.writerOffset() - startBufferWriterOffset;
            byteBuffer.position(startByteBufferPosition + delta);
        }
    }

    private static final class ForwardCursor implements ByteCursor {
        private final ByteBuf buf;
        int index;
        final int end;
        byte value;

        ForwardCursor(ByteBuf buf, int startIndex, int length) {
            this.buf = buf;
            index = startIndex;
            end = startIndex + length;
            value = -1;
        }

        @Override
        public boolean readByte() {
            if (index < end) {
                value = buf.getByte(index);
                index++;
                return true;
            }
            return false;
        }

        @Override
        public byte getByte() {
            return value;
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

    private static final class ReverseCursor implements ByteCursor {
        private final ByteBuf buf;
        int index;
        final int end;
        byte value;

        ReverseCursor(ByteBuf buf, int startIndex, int length) {
            this.buf = buf;
            index = startIndex;
            end = startIndex - length;
            value = -1;
        }

        @Override
        public boolean readByte() {
            if (index > end) {
                value = buf.getByte(index);
                index--;
                return true;
            }
            return false;
        }

        @Override
        public byte getByte() {
            return value;
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

    private static final class ByteBufAllocatorControl implements AllocatorControl, BufferAllocator {
        private final boolean direct;
        private final ByteBufAllocator allocator;

        private ByteBufAllocatorControl(ByteBuf byteBuf) {
            direct = byteBuf.isDirect();
            allocator = byteBuf.alloc();
        }

        @Override
        public BufferAllocator getAllocator() {
            return this;
        }

        @Override
        public boolean isPooling() {
            return false;
        }

        @Override
        public AllocationType getAllocationType() {
            return direct ? StandardAllocationTypes.OFF_HEAP : StandardAllocationTypes.ON_HEAP;
        }

        @Override
        public Buffer allocate(int size) {
            return wrap(direct ? allocator.directBuffer(size, size) : allocator.heapBuffer(size, size));
        }

        @Override
        public Supplier<Buffer> constBufferSupplier(byte[] bytes) {
            byte[] data = bytes.clone();
            return () -> wrap(Unpooled.wrappedBuffer(data));
        }

        @Override
        public void close() {
        }
    }

    static final class ByteBufDrop implements Drop<ByteBufBuffer> {
        static final ByteBufDrop INSTANCE = new ByteBufDrop();

        @Override
        public void drop(ByteBufBuffer obj) {
            obj.delegate.release();
        }

        @Override
        public Drop<ByteBufBuffer> fork() {
            return this;
        }

        @Override
        public void attach(ByteBufBuffer obj) {
        }
    }

    static final class ClosedByteBufHolder {
        // Using field type 'Object' instead of 'ByteBuf' in order to bypass checkstyle StaticFinalBuffer check.
        private static volatile Object closedByteBufInstance;

        private ClosedByteBufHolder() {
        }

        private static synchronized Object init() {
            Object obj = closedByteBufInstance;
            if (obj != null) {
                return obj;
            }
            ByteBuf buf = Unpooled.wrappedBuffer(new byte[1]);
            buf.release();
            closedByteBufInstance = buf;
            return buf;
        }

        static ByteBuf closedByteBufInstance() {
            // Using double-checked-locking to initialise this at runtime instead of class-load time.
            // This helps with Graal NativeImage building.
            Object closed = closedByteBufInstance;
            if (closed == null) {
                closed = init();
            }
            return (ByteBuf) closed;
        }
    }
}
