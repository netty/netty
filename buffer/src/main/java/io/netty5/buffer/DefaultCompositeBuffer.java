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
package io.netty5.buffer;

import io.netty5.buffer.ComponentIterator.Next;
import io.netty5.buffer.internal.InternalBufferUtils;
import io.netty5.buffer.internal.ResourceSupport;
import io.netty5.util.SafeCloseable;
import io.netty5.util.Send;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static io.netty5.buffer.internal.InternalBufferUtils.MAX_BUFFER_SIZE;
import static io.netty5.buffer.internal.InternalBufferUtils.bufferIsClosed;
import static io.netty5.buffer.internal.InternalBufferUtils.bufferIsReadOnly;
import static io.netty5.buffer.internal.InternalBufferUtils.checkImplicitCapacity;
import static io.netty5.buffer.internal.InternalBufferUtils.checkLength;
import static io.netty5.util.internal.ObjectUtil.checkPositiveOrZero;
import static io.netty5.util.internal.PlatformDependent.roundToPowerOfTwo;
import static java.lang.Math.addExact;
import static java.lang.Math.toIntExact;

/**
 * The default implementation of the {@link CompositeBuffer} interface.
 */
final class DefaultCompositeBuffer extends ResourceSupport<Buffer, DefaultCompositeBuffer> implements CompositeBuffer {
    private static final Drop<DefaultCompositeBuffer> COMPOSITE_DROP = new Drop<>() {
        @Override
        public void drop(DefaultCompositeBuffer buf) {
            RuntimeException re = null;
            for (Buffer b : buf.bufs) {
                try {
                    b.close();
                } catch (RuntimeException e) {
                    if (re == null) {
                        re = e;
                    } else {
                        re.addSuppressed(e);
                    }
                }
            }
            if (re != null) {
                throw re;
            }
        }

        @Override
        public Drop<DefaultCompositeBuffer> fork() {
            return this;
        }

        @Override
        public void attach(DefaultCompositeBuffer obj) {
        }

        @Override
        public String toString() {
            return "COMPOSITE_DROP";
        }
    };
    private static final Buffer[] EMPTY_BUFFER_ARRAY = new Buffer[0];
    /**
     * The various write*() methods may automatically grow the buffer, if there is not enough capacity to service the
     * write operation.
     * When this happens, we have to choose some reasonable amount to grow the buffer by. For normal buffers, we just
     * double their capacity to amortise this cost. For composite buffers, this doubling doesn't quite make as much
     * sense, as we are adding components rather than reallocating the entire innards of the buffer.
     * In the normal case, the composite buffer will grow by the average size of its components.
     * However, when the composite buffer is empty, we have no basis on which to compute an average.
     * We have to pick a number out of thin air.
     * We have chosen a size of 256 bytes for this purpose. This is the same as the initial capacity used, when
     * allocating a ByteBuf of an unspecified size, in Netty 4.1.
     */
    private static final int FIRST_AUTOMATIC_COMPONENT_SIZE = 256;

    private final BufferAllocator allocator;
    private final TornBufferAccessor tornBufAccessors;
    private Buffer[] bufs;
    private int[] offsets; // The offset, for the composite buffer, where each constituent buffer starts.
    private int capacity;
    private int roff;
    private int woff;
    private int subOffset; // The next offset *within* a constituent buffer to read from or write to.
    private boolean closed;
    private boolean readOnly;
    private int implicitCapacityLimit;

    /**
     * @see BufferAllocator#compose(Iterable)
     */
    public static CompositeBuffer compose(BufferAllocator allocator, Iterable<Send<Buffer>> sends) {
        final List<Buffer> bufs;
        if (sends instanceof Collection) {
            bufs = new ArrayList<>(((Collection<?>) sends).size());
        } else {
            bufs = new ArrayList<>(4);
        }
        RuntimeException receiveException = null;
        for (Send<Buffer> buf: sends) {
            if (receiveException != null) {
                try {
                    buf.close();
                } catch (Exception closeExc) {
                    receiveException.addSuppressed(closeExc);
                }
            } else {
                try {
                    bufs.add(buf.receive());
                } catch (RuntimeException e) {
                    // We catch RuntimeException instead of IllegalStateException to ensure cleanup always happens
                    // regardless of the exception thrown.
                    receiveException = e;
                    for (Buffer b: bufs) {
                        try {
                            b.close();
                        } catch (Exception closeExc) {
                            receiveException.addSuppressed(closeExc);
                        }
                    }
                }
            }
        }
        if (receiveException != null) {
            throw receiveException;
        }
        return new DefaultCompositeBuffer(allocator, filterExternalBufs(bufs), COMPOSITE_DROP);
    }

    /**
     * @see CompositeBuffer#compose(BufferAllocator)
     */
    public static CompositeBuffer compose(BufferAllocator allocator) {
        Objects.requireNonNull(allocator, "BufferAllocator cannot be null.");
        if (allocator.isClosed()) {
            throw new IllegalStateException("Allocator is closed: " + allocator);
        }
        return new DefaultCompositeBuffer(allocator, EMPTY_BUFFER_ARRAY, COMPOSITE_DROP);
    }

    private static Buffer[] filterExternalBufs(Iterable<Buffer> externals) {
        // We filter out all zero-capacity buffers because they wouldn't contribute to the composite buffer anyway,
        // and also, by ensuring that all constituent buffers contribute to the size of the composite buffer,
        // we make sure the number of composite buffers will never become greater than the number of bytes in
        // the composite buffer.
        // We also filter out middle buffers that don't contribute any readable bytes, due to their trimming.
        // This restriction guarantees that methods like countComponents, forEachReadable and forEachWritable,
        // will never overflow their component counts.
        // Allocating a new array unconditionally also prevents external modification of the array.
        Collector collector = new Collector(externals);
        collector.collect(externals);
        return collector.toArray();
    }

    private static final class Collector {
        private Buffer[] array;
        private int index;

        Collector(Iterable<Buffer> externals) {
            int size = 0;
            for (Buffer buf : externals) {
                size += buf.countComponents();
            }
            array = new Buffer[size];
        }

        void add(Buffer buffer) {
            if (index == array.length) {
                array = Arrays.copyOf(array, array.length * 2);
            }
            array[index] = buffer;
            index++;
        }

        void collect(Iterable<Buffer> externals) {
            for (Buffer buf : externals) {
                if (buf.capacity() == 0) {
                    buf.close();
                } else if (CompositeBuffer.isComposite(buf)) {
                    CompositeBuffer cbuf = (CompositeBuffer) buf;
                    collect(Arrays.asList(cbuf.decomposeBuffer()));
                } else {
                    add(buf);
                }
            }
        }

        Buffer[] toArray() {
            int firstReadable = -1;
            int lastReadable = -1;
            for (int i = 0; i < index; i++) {
                if (array[i].readableBytes() != 0) {
                    if (firstReadable == -1) {
                        firstReadable = i;
                    }
                    lastReadable = i;
                }
            }
            // Bytes already read, in components after the first readable section, must be trimmed off.
            // Likewise, writable bytes prior to the last readable section must be trimmed off.
            if (firstReadable != -1) {
                // Remove middle buffers entirely that have no readable bytes.
                for (int i = firstReadable + 1; i < lastReadable; i++) {
                    Buffer buf = array[i];
                    if (buf.readableBytes() == 0) {
                        buf.close();
                        if (i <= index - 2) {
                            System.arraycopy(array, i + 1, array, i, index - i - 1);
                        }
                        i--;
                        index--;
                        lastReadable--;
                    }
                }
                // Remove already-read bytes from middle and end buffers.
                for (int i = firstReadable + 1; i < index; i++) {
                    Buffer buf = array[i];
                    if (buf.readerOffset() > 0) {
                        buf.readSplit(0).close();
                    }
                }
                // Remove writable-bytes from front and middle buffers.
                for (int i = 0; i < lastReadable; i++) {
                    Buffer buf = array[i];
                    if (buf.writerOffset() > 0 && buf.writerOffset() < buf.capacity()) {
                        array[i] = buf.split();
                        buf.close();
                    }
                }
            }
            return array.length == index? array : Arrays.copyOf(array, index);
        }
    }

    private DefaultCompositeBuffer(BufferAllocator allocator, Buffer[] bufs, Drop<DefaultCompositeBuffer> drop) {
        super(drop);
        try {
            this.allocator = Objects.requireNonNull(allocator, "BufferAllocator cannot be null.");
            if (bufs.length > 0) {
                boolean targetReadOnly = bufs[0].readOnly();
                for (Buffer buf : bufs) {
                    if (buf.readOnly() != targetReadOnly) {
                        throw new IllegalArgumentException("Constituent buffers have inconsistent read-only state.");
                    }
                }
                readOnly = targetReadOnly;
            }
            this.bufs = bufs;
            computeBufferOffsets();
            implicitCapacityLimit = MAX_BUFFER_SIZE;
            tornBufAccessors = new TornBufferAccessor(this);
        } catch (Exception e) {
            // Always close bufs on exception, since we've taken ownership of them at this point.
            for (Buffer buf : bufs) {
                try {
                    buf.close();
                } catch (Exception closeExc) {
                    e.addSuppressed(closeExc);
                }
            }
            throw e;
        }
    }

    private void computeBufferOffsets() {
        int woff = 0;
        int roff = 0;
        if (bufs.length > 0) {
            boolean woffMidpoint = false;
            for (Buffer buf : bufs) {
                if (!woffMidpoint) {
                    // First region, before the composite writer-offset.
                    woff += buf.writerOffset();
                    if (buf.writableBytes() > 0) {
                        // We have writable bytes, so the composite writer-offset is in here.
                        woffMidpoint = true;
                    }
                } else if (buf.writerOffset() != 0) {
                    // We're past the composite write-offset, so all component writer-offsets must be zero from here.
                    throw new AssertionError(
                            "The given buffers cannot be composed because they leave an unwritten gap: " +
                            Arrays.toString(bufs) + '.');
                }
            }
            boolean roffMidpoint = false;
            for (Buffer buf : bufs) {
                if (!roffMidpoint) {
                    // First region, before we've found the composite reader-offset.
                    roff += buf.readerOffset();
                    if (buf.readableBytes() > 0 || buf.writableBytes() > 0) {
                        roffMidpoint = true;
                    }
                } else if (buf.readerOffset() != 0) {
                    throw new AssertionError(
                            "The given buffers cannot be composed because they leave an unread gap: " +
                            Arrays.toString(bufs) + '.');
                }
            }
        }
        // Commit computed offsets, if any
        this.woff = woff;
        this.roff = roff;

        offsets = new int[bufs.length];
        long cap = 0;
        for (int i = 0; i < bufs.length; i++) {
            offsets[i] = (int) cap;
            cap += bufs[i].capacity();
        }
        if (cap > MAX_BUFFER_SIZE) {
            throw new IllegalArgumentException(
                    "Combined size of the constituent buffers is too big. " +
                    "The maximum buffer capacity is " + MAX_BUFFER_SIZE + " (Integer.MAX_VALUE - 8), " +
                    "but the sum of the constituent buffer capacities was " + cap + '.');
        }
        capacity = (int) cap;
    }

    @Override
    public String toString() {
        return "Buffer[roff:" + roff + ", woff:" + woff + ", cap:" + capacity + ']';
    }

    @Override
    protected RuntimeException createResourceClosedException() {
        return bufferIsClosed(this);
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public int readerOffset() {
        return roff;
    }

    @Override
    public CompositeBuffer readerOffset(int index) {
        checkReadBounds(index, 0);
        int indexLeft = index;
        for (Buffer buf : bufs) {
            buf.readerOffset(Math.min(indexLeft, buf.capacity()));
            indexLeft = Math.max(0, indexLeft - buf.capacity());
        }
        roff = index;
        return this;
    }

    @Override
    public int writerOffset() {
        return woff;
    }

    @Override
    public CompositeBuffer writerOffset(int index) {
        checkWriteBounds(index, 0);
        int indexLeft = index;
        for (Buffer buf : bufs) {
            buf.writerOffset(Math.min(indexLeft, buf.capacity()));
            indexLeft = Math.max(0, indexLeft - buf.capacity());
        }
        woff = index;
        return this;
    }

    @Override
    public CompositeBuffer fill(byte value) {
        if (closed) {
            throw bufferIsClosed(this);
        }
        for (Buffer buf : bufs) {
            buf.fill(value);
        }
        return this;
    }

    @Override
    public CompositeBuffer makeReadOnly() {
        for (Buffer buf : bufs) {
            buf.makeReadOnly();
        }
        readOnly = true;
        return this;
    }

    @Override
    public boolean readOnly() {
        return readOnly;
    }

    @Override
    public boolean isDirect() {
        // A composite buffer is direct, if all components are direct.
        for (Buffer buf : bufs) {
            if (!buf.isDirect()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public CompositeBuffer implicitCapacityLimit(int limit) {
        checkImplicitCapacity(limit,  capacity());
        implicitCapacityLimit = limit;
        return this;
    }

    @Override
    public int implicitCapacityLimit() {
        return implicitCapacityLimit;
    }

    @Override
    public CompositeBuffer copy(int offset, int length, boolean readOnly) {
        checkLength(length);
        checkGetBounds(offset, length);
        if (closed) {
            throw bufferIsClosed(this);
        }
        Buffer[] copies;

        if (bufs.length == 0) {
            // Specialise for the empty buffer.
            assert length == 0 && offset == 0;
            copies = bufs;
        } else {
            Buffer choice = (Buffer) chooseBuffer(offset, 0);
            if (length > 0) {
                copies = new Buffer[bufs.length];
                int off = subOffset;
                int cap = length;
                int i;
                int j = 0;
                for (i = searchOffsets(offset); cap > 0; i++) {
                    var buf = bufs[i];
                    int avail = buf.capacity() - off;
                    copies[j++] = buf.copy(off, Math.min(cap, avail), readOnly);
                    cap -= avail;
                    off = 0;
                }
                copies = Arrays.copyOf(copies, j);
            } else {
                // Specialize for length == 0.
                copies = new Buffer[] { choice.copy(subOffset, 0) };
            }
        }

        return new DefaultCompositeBuffer(allocator, copies, COMPOSITE_DROP);
    }

    @Override
    public CompositeBuffer moveAndClose() {
        return (CompositeBuffer) super.moveAndClose();
    }

    @Override
    public void copyInto(int srcPos, byte[] dest, int destPos, int length) {
        copyInto(srcPos, (s, b, d, l) -> b.copyInto(s, dest, d, l), destPos, length);
    }

    @Override
    public void copyInto(int srcPos, ByteBuffer dest, int destPos, int length) {
        if (dest.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }
        copyInto(srcPos, (s, b, d, l) -> b.copyInto(s, dest, d, l), destPos, length);
    }

    private void copyInto(int srcPos, CopyInto dest, int destPos, int length) {
        if (!isAccessible()) {
            throw attachTrace(bufferIsClosed(this));
        }
        if (length < 0) {
            throw new IndexOutOfBoundsException("Length cannot be negative: " + length + '.');
        }
        if (srcPos < 0) {
            throw indexOutOfBounds(srcPos, false);
        }
        if (srcPos + length > capacity) {
            throw indexOutOfBounds(srcPos + length, false);
        }
        while (length > 0) {
            var buf = (Buffer) chooseBuffer(srcPos, 0);
            int toCopy = Math.min(buf.capacity() - subOffset, length);
            dest.copyInto(subOffset, buf, destPos, toCopy);
            srcPos += toCopy;
            destPos += toCopy;
            length -= toCopy;
        }
    }

    @FunctionalInterface
    private interface CopyInto {
        void copyInto(int srcPos, Buffer src, int destPos, int length);
    }

    @Override
    public void copyInto(int srcPos, Buffer dest, int destPos, int length) {
        if (!isAccessible()) {
            throw attachTrace(bufferIsClosed(this));
        }
        if (length < 0) {
            throw new IndexOutOfBoundsException("Length cannot be negative: " + length + '.');
        }
        if (srcPos < 0) {
            throw indexOutOfBounds(srcPos, false);
        }
        if (addExact(srcPos, length) > capacity) {
            throw indexOutOfBounds(srcPos + length, false);
        }
        if (dest.readOnly()) {
            throw bufferIsReadOnly(dest);
        }
        if (length == 0) {
            return;
        }

        // Iterate in reverse to account for src and dest buffer overlap.
        // todo optimise by delegating to constituent buffers.
        var cursor = openReverseCursor(srcPos + length - 1, length);
        while (cursor.readByte()) {
            dest.setByte(destPos + --length, cursor.getByte());
        }
    }

    @Override
    public int transferTo(WritableByteChannel channel, int length) throws IOException {
        if (!isAccessible()) {
            throw bufferIsClosed(this);
        }
        length = Math.min(readableBytes(), length);
        if (length == 0) {
            return 0;
        }
        checkReadBounds(readerOffset(), length);
        int totalBytesWritten = 0;
        try (var iterator = forEachComponent()) {
            ByteBuffer[] byteBuffers = new ByteBuffer[countReadableComponents()];
            int counter = 0;
            for (var c = iterator.firstReadable(); c != null; c = c.nextReadable()) {
                byteBuffers[counter++] = c.readableBuffer();
            }
            int bufferCount = countAndPrepareBuffersForChannelIO(length, byteBuffers);
            if (channel instanceof GatheringByteChannel) {
                GatheringByteChannel gatheringChannel = (GatheringByteChannel) channel;
                totalBytesWritten = toIntExact(gatheringChannel.write(byteBuffers, 0, bufferCount));
            } else {
                for (int i = 0; i < bufferCount; i++) {
                    int expectWritten = byteBuffers[i].remaining();
                    int bytesWritten = channel.write(byteBuffers[i]);
                    totalBytesWritten = addExact(totalBytesWritten, bytesWritten);
                    if (bytesWritten < expectWritten) {
                        break;
                    }
                }
            }
        } finally {
            skipReadableBytes(totalBytesWritten);
        }
        return totalBytesWritten;
    }

    @Override
    public int transferTo(FileChannel channel, long position, int length) throws IOException {
        if (!isAccessible()) {
            throw bufferIsClosed(this);
        }
        length = Math.min(readableBytes(), length);
        if (length == 0) {
            return 0;
        }
        checkReadBounds(readerOffset(), length);
        int totalBytesWritten = 0;
        try (var iterator = forEachComponent()) {
            ByteBuffer[] byteBuffers = new ByteBuffer[countReadableComponents()];
            int counter = 0;
            for (var c = iterator.firstReadable(); c != null; c = c.nextReadable()) {
                byteBuffers[counter++] = c.readableBuffer();
            }
            int bufferCount = countAndPrepareBuffersForChannelIO(length, byteBuffers);
            for (int i = 0; i < bufferCount; i++) {
                int expectWritten = byteBuffers[i].remaining();
                int bytesWritten = channel.write(byteBuffers[i], addExact(position, totalBytesWritten));
                totalBytesWritten = addExact(totalBytesWritten, bytesWritten);
                if (bytesWritten < expectWritten) {
                    break;
                }
            }
        } finally {
            skipReadableBytes(totalBytesWritten);
        }
        return totalBytesWritten;
    }

    @Override
    public int transferFrom(FileChannel channel, long position, int length) throws IOException {
        checkPositiveOrZero(position, "position");
        checkPositiveOrZero(length, "length");
        if (!isAccessible()) {
            throw bufferIsClosed(this);
        }
        if (readOnly()) {
            throw bufferIsReadOnly(this);
        }
        length = Math.min(writableBytes(), length);
        if (length == 0) {
            return 0;
        }
        checkWriteBounds(writerOffset(), length);
        int totalBytesRead = 0;
        try (var iteration = forEachComponent()) {
            ByteBuffer[] byteBuffers = new ByteBuffer[countWritableComponents()];
            int counter = 0;
            for (var c = iteration.firstWritable(); c != null; c = c.nextWritable()) {
                byteBuffers[counter++] = c.writableBuffer();
            }
            int bufferCount = countAndPrepareBuffersForChannelIO(length, byteBuffers);
            for (int i = 0; i < bufferCount; i++) {
                int bytesRead = channel.read(byteBuffers[i], position + totalBytesRead);
                if (bytesRead == -1) {
                    if (i == 0) {
                        return -1; // If we're end-of-stream on the first read, immediately return -1.
                    }
                    break;
                }
                totalBytesRead = addExact(totalBytesRead, bytesRead);
            }
        } finally {
            if (totalBytesRead > 0) { // Don't skipWritable if total is 0 or -1
                skipWritableBytes(totalBytesRead);
            }
        }
        return totalBytesRead;
    }

    @Override
    public int transferFrom(ReadableByteChannel channel, int length) throws IOException {
        if (!isAccessible()) {
            throw bufferIsClosed(this);
        }
        if (readOnly()) {
            throw bufferIsReadOnly(this);
        }
        length = Math.min(writableBytes(), length);
        if (length == 0) {
            return 0;
        }
        checkWriteBounds(writerOffset(), length);
        int totalBytesRead = 0;
        ByteBuffer[] byteBuffers = new ByteBuffer[countWritableComponents()];
        try (var iteration = forEachComponent()) {
            int counter = 0;
            for (var c = iteration.firstWritable(); c != null; c = c.nextWritable()) {
                byteBuffers[counter++] = c.writableBuffer();
            }
            int bufferCount = countAndPrepareBuffersForChannelIO(length, byteBuffers);
            if (channel instanceof ScatteringByteChannel) {
                ScatteringByteChannel scatteringChannel = (ScatteringByteChannel) channel;
                totalBytesRead = toIntExact(scatteringChannel.read(byteBuffers, 0, bufferCount));
            } else {
                for (int i = 0; i < bufferCount; i++) {
                    int bytesRead = channel.read(byteBuffers[i]);
                    if (bytesRead == -1) {
                        if (i == 0) {
                            return -1; // If we're end-of-stream on the first read, immediately return -1.
                        }
                        break;
                    }
                    totalBytesRead = addExact(totalBytesRead, bytesRead);
                }
            }
        } finally {
            if (totalBytesRead > 0) { // Don't skipWritable if total is 0 or -1
                skipWritableBytes(totalBytesRead);
            }
        }
        return totalBytesRead;
    }

    private static int countAndPrepareBuffersForChannelIO(int byteLength, ByteBuffer[] byteBuffers) {
        int bufferCount = 0;
        int byteSum = 0;
        for (ByteBuffer buffer : byteBuffers) {
            byteSum += buffer.remaining();
            bufferCount++;
            if (byteSum >= byteLength) {
                int diff = byteSum - byteLength;
                if (diff > 0) {
                    buffer.limit(buffer.limit() - diff);
                }
                break;
            }
        }
        return bufferCount;
    }

    @Override
    public int bytesBefore(byte needle) {
        if (!isAccessible()) {
            throw bufferIsClosed(this);
        }
        final int length = readableBytes();
        for (int i = searchOffsets(readerOffset()), skip = 0; skip < length; i++) {
            Buffer buf = bufs[i];
            int found = buf.bytesBefore(needle);
            if (found != -1) {
                return skip + found;
            }
            skip += buf.readableBytes();
        }
        return -1;
    }

    @Override
    public int bytesBefore(Buffer needle) {
        return InternalBufferUtils.bytesBefore(this, null, needle, null);
    }

    @Override
    public ByteCursor openCursor() {
        return openCursor(readerOffset(), readableBytes());
    }

    @Override
    public ByteCursor openCursor(int fromOffset, int length) {
        if (fromOffset < 0) {
            throw new IndexOutOfBoundsException("The fromOffset cannot be negative: " + fromOffset + '.');
        }
        checkLength(length);
        if (capacity < addExact(fromOffset, length)) {
            throw new IndexOutOfBoundsException("The fromOffset+length is beyond the end of the buffer: " +
                                               "fromOffset=" + fromOffset + ", length=" + length + '.');
        }
        if (closed) {
            throw bufferIsClosed(this);
        }
        int startBufferIndex = searchOffsets(fromOffset);
        int off = fromOffset - offsets[startBufferIndex];
        Buffer startBuf = bufs[startBufferIndex];
        ByteCursor startCursor = startBuf.openCursor(off, Math.min(startBuf.capacity() - off, length));
        return new ForwardCompositeByteCursor(bufs, fromOffset, length, startBufferIndex, startCursor);
    }

    @Override
    public ByteCursor openReverseCursor(int fromOffset, int length) {
        if (fromOffset < 0) {
            throw new IndexOutOfBoundsException("The fromOffset cannot be negative: " + fromOffset + '.');
        }
        checkLength(length);
        if (fromOffset - length < -1) {
            throw new IndexOutOfBoundsException("The fromOffset-length would underflow the buffer: " +
                                               "fromOffset=" + fromOffset + ", length=" + length + '.');
        }
        if (closed) {
            throw bufferIsClosed(this);
        }
        int startBufferIndex = searchOffsets(fromOffset);
        int off = fromOffset - offsets[startBufferIndex];
        Buffer startBuf = bufs[startBufferIndex];
        ByteCursor startCursor = startBuf.openReverseCursor(off, Math.min(off + 1, length));
        return new ReverseCompositeByteCursor(bufs, fromOffset, length, startBufferIndex, startCursor);
    }

    @Override
    public CompositeBuffer ensureWritable(int size, int minimumGrowth, boolean allowCompaction) {
        if (!isAccessible()) {
            throw bufferIsClosed(this);
        }
        if (!isOwned()) {
            throw new IllegalStateException("Buffer is not owned. Only owned buffers can call ensureWritable.");
        }
        if (size < 0) {
            throw new IllegalArgumentException("Cannot ensure writable for a negative size: " + size + '.');
        }
        if (minimumGrowth < 0) {
            throw new IllegalArgumentException("The minimum growth cannot be negative: " + minimumGrowth + '.');
        }
        if (readOnly) {
            throw bufferIsReadOnly(this);
        }
        if (writableBytes() >= size) {
            // We already have enough space.
            return this;
        }

        if (allowCompaction && size <= roff) {
            // Let's see if we can solve some or all of the requested size with compaction.
            // We always compact as much as is possible, regardless of size. This amortizes our work.
            int compactableBuffers = 0;
            for (Buffer buf : bufs) {
                if (buf.capacity() != buf.readerOffset()) {
                    break;
                }
                compactableBuffers++;
            }
            if (compactableBuffers > 0) {
                Buffer[] compactable;
                if (compactableBuffers < bufs.length) {
                    compactable = new Buffer[compactableBuffers];
                    System.arraycopy(bufs, 0, compactable, 0, compactable.length);
                    System.arraycopy(bufs, compactable.length, bufs, 0, bufs.length - compactable.length);
                    System.arraycopy(compactable, 0, bufs, bufs.length - compactable.length, compactable.length);
                } else {
                    compactable = bufs;
                }
                for (Buffer buf : compactable) {
                    buf.resetOffsets();
                }
                computeBufferOffsets();
                if (writableBytes() >= size) {
                    // Now we have enough space.
                    return this;
                }
            } else if (bufs.length == 1) {
                // If we only have a single component buffer, then we can safely compact that in-place.
                bufs[0].compact();
                computeBufferOffsets();
                if (writableBytes() >= size) {
                    // Now we have enough space.
                    return this;
                }
            }
        }

        int growth = Math.max(size - writableBytes(), minimumGrowth);
        InternalBufferUtils.assertValidBufferSize(capacity() + (long) growth);
        Buffer extension = allocator.allocate(growth);
        unsafeExtendWith(extension);
        return this;
    }

    @Override
    public CompositeBuffer extendWith(Send<Buffer> extension) {
        Buffer buffer = Objects.requireNonNull(extension, "Extension buffer cannot be null.").receive();
        if (!isAccessible() || !isOwned()) {
            buffer.close();
            if (!isAccessible()) {
                throw bufferIsClosed(this);
            }
            throw new IllegalStateException("This buffer cannot be extended because it is not in an owned state.");
        }
        if (bufs.length > 0 && buffer.readOnly() != readOnly()) {
            buffer.close();
            throw new IllegalArgumentException(
                    "This buffer is " + (readOnly? "read-only" : "writable") + ", " +
                    "and cannot be extended with a buffer that is " +
                    (buffer.readOnly()? "read-only." : "writable."));
        }

        long extensionCapacity = buffer.capacity();
        if (extensionCapacity == 0) {
            // Extending by a zero-sized buffer makes no difference. Especially since it's not allowed to change the
            // capacity of buffers that are constituents of composite buffers.
            // This also ensures that methods like countComponents, and forEachReadable, do not have to worry about
            // overflow in their component counters.
            buffer.close();
            return this;
        }

        long newSize = capacity() + extensionCapacity;
        InternalBufferUtils.assertValidBufferSize(newSize);

        Buffer[] restoreTemp = bufs; // We need this to restore our buffer array, in case offset computations fail.
        try {
            Buffer[] concatArray = Arrays.copyOf(bufs, bufs.length + 1);
            concatArray[bufs.length] = buffer;
            bufs = filterExternalBufs(Arrays.asList(concatArray));
            computeBufferOffsets();
            if (restoreTemp.length == 0) {
                readOnly = buffer.readOnly();
            }
        } catch (Exception e) {
            bufs = restoreTemp;
            throw e;
        }
        return this;
    }

    private void unsafeExtendWith(Buffer extension) {
        bufs = Arrays.copyOf(bufs, bufs.length + 1);
        bufs[bufs.length - 1] = extension;
        computeBufferOffsets();
    }

    private void checkSplit(int splitOffset) {
        if (splitOffset < 0) {
            throw new IllegalArgumentException("The split offset cannot be negative: " + splitOffset + '.');
        }
        if (capacity() < splitOffset) {
            throw new IllegalArgumentException("The split offset cannot be greater than the buffer capacity, " +
                    "but the split offset was " + splitOffset + ", and capacity is " + capacity() + '.');
        }
        if (!isAccessible()) {
            throw attachTrace(bufferIsClosed(this));
        }
        if (!isOwned()) {
            throw new IllegalStateException("Cannot split a buffer that is not owned.");
        }
    }

    @Override
    public CompositeBuffer split() {
        return split(writerOffset());
    }

    @Override
    public CompositeBuffer split(int splitOffset) {
        checkSplit(splitOffset);
        if (bufs.length == 0) {
            // Splitting a zero-length buffer is trivial.
            return new DefaultCompositeBuffer(allocator, bufs, unsafeGetDrop());
        }

        int i = searchOffsets(splitOffset);
        int off = splitOffset - offsets[i];
        Buffer[] splits = Arrays.copyOf(bufs, off == 0? i : 1 + i);
        bufs = Arrays.copyOfRange(bufs, off > 0 && off == bufs[i].capacity()? 1 + i : i, bufs.length);
        if (off > 0 && splits.length > 0 && off < splits[splits.length - 1].capacity()) {
            splits[splits.length - 1] = bufs[0].split(off);
        }
        computeBufferOffsets();
        return buildSplitBuffer(splits);
    }

    private CompositeBuffer buildSplitBuffer(Buffer[] splits) {
        // TODO do we need to preserve read-only state of empty buffer?
        return new DefaultCompositeBuffer(allocator, splits, unsafeGetDrop());
    }

    @Override
    public CompositeBuffer splitComponentsFloor(int splitOffset) {
        checkSplit(splitOffset);
        if (bufs.length == 0) {
            // Splitting a zero-length buffer is trivial.
            return new DefaultCompositeBuffer(allocator, bufs, unsafeGetDrop());
        }

        int i = searchOffsets(splitOffset);
        int off = splitOffset - offsets[i];
        if (off == bufs[i].capacity()) {
            i++;
        }
        Buffer[] splits = Arrays.copyOf(bufs, i);
        bufs = Arrays.copyOfRange(bufs, i, bufs.length);
        computeBufferOffsets();
        return buildSplitBuffer(splits);
    }

    @Override
    public CompositeBuffer splitComponentsCeil(int splitOffset) {
        checkSplit(splitOffset);
        if (bufs.length == 0) {
            // Splitting a zero-length buffer is trivial.
            return new DefaultCompositeBuffer(allocator, bufs, unsafeGetDrop());
        }

        int i = searchOffsets(splitOffset);
        int off = splitOffset - offsets[i];
        if (0 < off && off <= bufs[i].capacity()) {
            i++;
        }
        Buffer[] splits = Arrays.copyOf(bufs, i);
        bufs = Arrays.copyOfRange(bufs, i, bufs.length);
        computeBufferOffsets();
        return buildSplitBuffer(splits);
    }

    @Override
    public Buffer[] decomposeBuffer() {
        Buffer[] result = bufs;
        bufs = EMPTY_BUFFER_ARRAY;
        try {
            close();
        } catch (Throwable e) {
            for (Buffer buffer : result) {
                try {
                    buffer.close();
                } catch (Throwable ex) {
                    e.addSuppressed(ex);
                }
            }
            throw e;
        }
        return result;
    }

    @Override
    public CompositeBuffer compact() {
        if (!isAccessible()) {
            throw attachTrace(bufferIsClosed(this));
        }
        if (!isOwned()) {
            throw attachTrace(new IllegalStateException("Buffer must be owned in order to compact."));
        }
        if (readOnly()) {
            throw new BufferReadOnlyException("Buffer must be writable in order to compact, but was read-only.");
        }
        int distance = roff;
        if (distance == 0) {
            return this;
        }
        int pos = 0;
        // TODO maybe we can delegate to a copyInto method, once it's more optimised
        var cursor = openCursor();
        while (cursor.readByte()) {
            setByte(pos, cursor.getByte());
            pos++;
        }
        readerOffset(0);
        writerOffset(woff - distance);
        return this;
    }

    @Override
    public int countComponents() {
        int sum = 0;
        for (Buffer buf : bufs) {
            sum += buf.countComponents();
        }
        return sum;
    }

    @Override
    public int countReadableComponents() {
        int sum = 0;
        for (Buffer buf : bufs) {
            sum += buf.countReadableComponents();
        }
        return sum;
    }

    @Override
    public int countWritableComponents() {
        int sum = 0;
        for (Buffer buf : bufs) {
            sum += buf.countWritableComponents();
        }
        return sum;
    }

    @Override
    public <T extends BufferComponent & Next> ComponentIterator<T> forEachComponent() {
        return new CompositeComponentIterator<>(acquire());
    }

    // <editor-fold defaultstate="collapsed" desc="Primitive accessors.">
    @Override
    public byte readByte() {
        return prepRead(Byte.BYTES).readByte();
    }

    @Override
    public byte getByte(int roff) {
        return prepGet(roff, Byte.BYTES).getByte(subOffset);
    }

    @Override
    public int readUnsignedByte() {
        return prepRead(Byte.BYTES).readUnsignedByte();
    }

    @Override
    public int getUnsignedByte(int roff) {
        return prepGet(roff, Byte.BYTES).getUnsignedByte(subOffset);
    }

    @Override
    public CompositeBuffer writeByte(byte value) {
        prepWrite(Byte.BYTES).writeByte(value);
        return this;
    }

    @Override
    public CompositeBuffer setByte(int woff, byte value) {
        prepWrite(woff, Byte.BYTES).setByte(subOffset, value);
        return this;
    }

    @Override
    public CompositeBuffer writeUnsignedByte(int value) {
        prepWrite(Byte.BYTES).writeUnsignedByte(value);
        return this;
    }

    @Override
    public CompositeBuffer setUnsignedByte(int woff, int value) {
        prepWrite(woff, Byte.BYTES).setUnsignedByte(subOffset, value);
        return this;
    }

    @Override
    public char readChar() {
        return prepRead(Character.BYTES).readChar();
    }

    @Override
    public char getChar(int roff) {
        return prepGet(roff, Character.BYTES).getChar(subOffset);
    }

    @Override
    public CompositeBuffer writeChar(char value) {
        prepWrite(Character.BYTES).writeChar(value);
        return this;
    }

    @Override
    public CompositeBuffer setChar(int woff, char value) {
        prepWrite(woff, Character.BYTES).setChar(subOffset, value);
        return this;
    }

    @Override
    public short readShort() {
        return prepRead(Short.BYTES).readShort();
    }

    @Override
    public short getShort(int roff) {
        return prepGet(roff, Short.BYTES).getShort(subOffset);
    }

    @Override
    public int readUnsignedShort() {
        return prepRead(Short.BYTES).readUnsignedShort();
    }

    @Override
    public int getUnsignedShort(int roff) {
        return prepGet(roff, Short.BYTES).getUnsignedShort(subOffset);
    }

    @Override
    public CompositeBuffer writeShort(short value) {
        prepWrite(Short.BYTES).writeShort(value);
        return this;
    }

    @Override
    public CompositeBuffer setShort(int woff, short value) {
        prepWrite(woff, Short.BYTES).setShort(subOffset, value);
        return this;
    }

    @Override
    public CompositeBuffer writeUnsignedShort(int value) {
        prepWrite(Short.BYTES).writeUnsignedShort(value);
        return this;
    }

    @Override
    public CompositeBuffer setUnsignedShort(int woff, int value) {
        prepWrite(woff, Short.BYTES).setUnsignedShort(subOffset, value);
        return this;
    }

    @Override
    public int readMedium() {
        return prepRead(3).readMedium();
    }

    @Override
    public int getMedium(int roff) {
        return prepGet(roff, 3).getMedium(subOffset);
    }

    @Override
    public int readUnsignedMedium() {
        return prepRead(3).readUnsignedMedium();
    }

    @Override
    public int getUnsignedMedium(int roff) {
        return prepGet(roff, 3).getUnsignedMedium(subOffset);
    }

    @Override
    public CompositeBuffer writeMedium(int value) {
        prepWrite(3).writeMedium(value);
        return this;
    }

    @Override
    public CompositeBuffer setMedium(int woff, int value) {
        prepWrite(woff, 3).setMedium(subOffset, value);
        return this;
    }

    @Override
    public CompositeBuffer writeUnsignedMedium(int value) {
        prepWrite(3).writeUnsignedMedium(value);
        return this;
    }

    @Override
    public CompositeBuffer setUnsignedMedium(int woff, int value) {
        prepWrite(woff, 3).setUnsignedMedium(subOffset, value);
        return this;
    }

    @Override
    public int readInt() {
        return prepRead(Integer.BYTES).readInt();
    }

    @Override
    public int getInt(int roff) {
        return prepGet(roff, Integer.BYTES).getInt(subOffset);
    }

    @Override
    public long readUnsignedInt() {
        return prepRead(Integer.BYTES).readUnsignedInt();
    }

    @Override
    public long getUnsignedInt(int roff) {
        return prepGet(roff, Integer.BYTES).getUnsignedInt(subOffset);
    }

    @Override
    public CompositeBuffer writeInt(int value) {
        prepWrite(Integer.BYTES).writeInt(value);
        return this;
    }

    @Override
    public CompositeBuffer setInt(int woff, int value) {
        prepWrite(woff, Integer.BYTES).setInt(subOffset, value);
        return this;
    }

    @Override
    public CompositeBuffer writeUnsignedInt(long value) {
        prepWrite(Integer.BYTES).writeUnsignedInt(value);
        return this;
    }

    @Override
    public CompositeBuffer setUnsignedInt(int woff, long value) {
        prepWrite(woff, Integer.BYTES).setUnsignedInt(subOffset, value);
        return this;
    }

    @Override
    public float readFloat() {
        return prepRead(Float.BYTES).readFloat();
    }

    @Override
    public float getFloat(int roff) {
        return prepGet(roff, Float.BYTES).getFloat(subOffset);
    }

    @Override
    public CompositeBuffer writeFloat(float value) {
        prepWrite(Float.BYTES).writeFloat(value);
        return this;
    }

    @Override
    public CompositeBuffer setFloat(int woff, float value) {
        prepWrite(woff, Float.BYTES).setFloat(subOffset, value);
        return this;
    }

    @Override
    public long readLong() {
        return prepRead(Long.BYTES).readLong();
    }

    @Override
    public long getLong(int roff) {
        return prepGet(roff, Long.BYTES).getLong(subOffset);
    }

    @Override
    public CompositeBuffer writeLong(long value) {
        prepWrite(Long.BYTES).writeLong(value);
        return this;
    }

    @Override
    public CompositeBuffer setLong(int woff, long value) {
        prepWrite(woff, Long.BYTES).setLong(subOffset, value);
        return this;
    }

    @Override
    public double readDouble() {
        return prepRead(Double.BYTES).readDouble();
    }

    @Override
    public double getDouble(int roff) {
        return prepGet(roff, Double.BYTES).getDouble(subOffset);
    }

    @Override
    public CompositeBuffer writeDouble(double value) {
        prepWrite(Double.BYTES).writeDouble(value);
        return this;
    }

    @Override
    public CompositeBuffer setDouble(int woff, double value) {
        prepWrite(woff, Double.BYTES).setDouble(subOffset, value);
        return this;
    }
    // </editor-fold>

    @Override
    protected Owned<DefaultCompositeBuffer> prepareSend() {
        @SuppressWarnings("unchecked")
        Send<Buffer>[] sends = new Send[bufs.length];
        try {
            for (int i = 0; i < bufs.length; i++) {
                sends[i] = bufs[i].send();
            }
        } catch (Throwable throwable) {
            // Repair our bufs array.
            for (int i = 0; i < sends.length; i++) {
                if (sends[i] != null) {
                    try {
                        bufs[i] = sends[i].receive();
                    } catch (Exception e) {
                        throwable.addSuppressed(e);
                    }
                }
            }
            throw throwable;
        }
        boolean readOnly = this.readOnly;
        int implicitCapacityLimit = this.implicitCapacityLimit;
        return drop -> {
            Buffer[] received = new Buffer[sends.length];
            for (int i = 0; i < sends.length; i++) {
                received[i] = sends[i].receive();
            }
            var composite = new DefaultCompositeBuffer(allocator, received, drop);
            composite.readOnly = readOnly;
            composite.implicitCapacityLimit = implicitCapacityLimit;
            return composite;
        };
    }

    @Override
    protected DefaultCompositeBuffer moveOwnership(Drop<DefaultCompositeBuffer> drop) {
        var composite = new DefaultCompositeBuffer(allocator, bufs, drop);
        composite.readOnly = readOnly;
        composite.implicitCapacityLimit = implicitCapacityLimit;
        return composite;
    }

    @Override
    protected void makeInaccessible() {
        capacity = 0;
        roff = 0;
        woff = 0;
        readOnly = false;
        closed = true;
    }

    @Override
    protected boolean isOwned() {
        return super.isOwned() && allConstituentsAreOwned();
    }

    @Override
    public CompositeBuffer touch(Object hint) {
        super.touch(hint);
        for (Buffer buf : bufs) {
            buf.touch(hint);
        }
        return this;
    }

    private boolean allConstituentsAreOwned() {
        boolean result = true;
        for (Buffer buf : bufs) {
            result &= InternalBufferUtils.isOwned((ResourceSupport<?, ?>) buf);
        }
        return result;
    }

    long readPassThrough() {
        var buf = choosePassThroughBuffer(subOffset++);
        assert buf != tornBufAccessors: "Recursive call to torn buffer.";
        return buf.readUnsignedByte();
    }

    void writePassThrough(int value) {
        var buf = choosePassThroughBuffer(subOffset++);
        assert buf != tornBufAccessors: "Recursive call to torn buffer.";
        buf.writeUnsignedByte(value);
    }

    long getPassThrough(int roff) {
        var buf = chooseBuffer(roff, 1);
        assert buf != tornBufAccessors: "Recursive call to torn buffer.";
        return buf.getUnsignedByte(subOffset);
    }

    void setPassThrough(int woff, int value) {
        var buf = chooseBuffer(woff, 1);
        assert buf != tornBufAccessors: "Recursive call to torn buffer.";
        buf.setUnsignedByte(subOffset, value);
    }

    private BufferAccessor prepRead(int size) {
        var buf = prepRead(roff, size);
        roff += size;
        return buf;
    }

    private BufferAccessor prepRead(int index, int size) {
        checkReadBounds(index, size);
        return chooseBuffer(index, size);
    }

    private void checkReadBounds(int index, int size) {
        if (index < 0 || woff < index + size) {
            throw indexOutOfBounds(index, false);
        }
    }

    private BufferAccessor prepGet(int index, int size) {
        checkGetBounds(index, size);
        return chooseBuffer(index, size);
    }

    private void checkGetBounds(int index, int size) {
        if (index < 0 || capacity < index + size) {
            throw indexOutOfBounds(index, false);
        }
    }

    private BufferAccessor prepWrite(int size) {
        if (writableBytes() < size && woff + size <= implicitCapacityLimit && isOwned()) {
            final int minGrowth;
            if (bufs.length == 0) {
                minGrowth = Math.min(implicitCapacityLimit, FIRST_AUTOMATIC_COMPONENT_SIZE);
            } else {
                minGrowth = Math.min(
                        Math.max(roundToPowerOfTwo(capacity() / bufs.length), size),
                        implicitCapacityLimit - capacity);
            }
            ensureWritable(size, minGrowth, false);
        }
        var buf = prepWrite(woff, size);
        woff += size;
        return buf;
    }

    private BufferAccessor prepWrite(int index, int size) {
        checkWriteBounds(index, size);
        return chooseBuffer(index, size);
    }

    private void checkWriteBounds(int index, int size) {
        if (index < 0 || capacity < index + size || readOnly) {
            throw indexOutOfBounds(index, true);
        }
    }

    private RuntimeException indexOutOfBounds(int index, boolean write) {
        if (closed) {
            return bufferIsClosed(this);
        }
        if (write && readOnly) {
            return bufferIsReadOnly(this);
        }
        return new IndexOutOfBoundsException(
                "Index " + index + " is out of bounds: [read 0 to " + woff + ", write 0 to " +
                capacity + "].");
    }

    private BufferAccessor chooseBuffer(int index, int size) {
        int i = searchOffsets(index);
        if (i == bufs.length) {
            // This happens when the read/write offsets are parked 1 byte beyond the end of the buffer.
            // In that case it should not matter what buffer is returned, because it shouldn't be used anyway.
            return null;
        }
        int off = index - offsets[i];
        Buffer candidate = bufs[i];
        if (off + size <= candidate.capacity()) {
            subOffset = off;
            return candidate;
        }
        subOffset = index;
        return tornBufAccessors;
    }

    private BufferAccessor choosePassThroughBuffer(int index) {
        int i = searchOffsets(index);
        return bufs[i];
    }

    private int searchOffsets(int index) {
        int i = Arrays.binarySearch(offsets, index);
        return i < 0? -(i + 2) : i;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Buffer && InternalBufferUtils.equals(this, (Buffer) o);
    }

    @Override
    public int hashCode() {
        return InternalBufferUtils.hashCode(this);
    }

    // <editor-fold defaultstate="collapsed" desc="Torn buffer access.">
    private static final class TornBufferAccessor implements BufferAccessor {
        private final DefaultCompositeBuffer buf;

        private TornBufferAccessor(DefaultCompositeBuffer buf) {
            this.buf = buf;
        }

        @Override
        public byte readByte() {
            throw new AssertionError("Method should not be used.");
        }

        @Override
        public byte getByte(int roff) {
            throw new AssertionError("Method should not be used.");
        }

        @Override
        public int readUnsignedByte() {
            throw new AssertionError("Method should not be used.");
        }

        @Override
        public int getUnsignedByte(int roff) {
            throw new AssertionError("Method should not be used.");
        }

        @Override
        public Buffer writeByte(byte value) {
            throw new AssertionError("Method should not be used.");
        }

        @Override
        public Buffer setByte(int woff, byte value) {
            throw new AssertionError("Method should not be used.");
        }

        @Override
        public Buffer writeUnsignedByte(int value) {
            throw new AssertionError("Method should not be used.");
        }

        @Override
        public Buffer setUnsignedByte(int woff, int value) {
            throw new AssertionError("Method should not be used.");
        }

        @Override
        public char readChar() {
            return (char) (read() << 8 | read());
        }

        @Override
        public char getChar(int roff) {
            return (char) (read(roff) << 8 | read(roff + 1));
        }

        @Override
        public Buffer writeChar(char value) {
            write(value >>> 8);
            write(value & 0xFF);
            return buf;
        }

        @Override
        public Buffer setChar(int woff, char value) {
            write(woff, value >>> 8);
            write(woff + 1, value & 0xFF);
            return buf;
        }

        @Override
        public short readShort() {
            return (short) (read() << 8 | read());
        }

        @Override
        public short getShort(int roff) {
            return (short) (read(roff) << 8 | read(roff + 1));
        }

        @Override
        public int readUnsignedShort() {
            return (int) (read() << 8 | read()) & 0xFFFF;
        }

        @Override
        public int getUnsignedShort(int roff) {
            return (int) (read(roff) << 8 | read(roff + 1)) & 0xFFFF;
        }

        @Override
        public Buffer writeShort(short value) {
            write(value >>> 8);
            write(value & 0xFF);
            return buf;
        }

        @Override
        public Buffer setShort(int woff, short value) {
            write(woff, value >>> 8);
            write(woff + 1, value & 0xFF);
            return buf;
        }

        @Override
        public Buffer writeUnsignedShort(int value) {
            write(value >>> 8);
            write(value & 0xFF);
            return buf;
        }

        @Override
        public Buffer setUnsignedShort(int woff, int value) {
            write(woff, value >>> 8);
            write(woff + 1, value & 0xFF);
            return buf;
        }

        @Override
        public int readMedium() {
            return (int) (read() << 16 | read() << 8 | read());
        }

        @Override
        public int getMedium(int roff) {
            return (int) (read(roff) << 16 | read(roff + 1) << 8 | read(roff + 2));
        }

        @Override
        public int readUnsignedMedium() {
            return (int) (read() << 16 | read() << 8 | read()) & 0xFFFFFF;
        }

        @Override
        public int getUnsignedMedium(int roff) {
            return (int) (read(roff) << 16 | read(roff + 1) << 8 | read(roff + 2)) & 0xFFFFFF;
        }

        @Override
        public Buffer writeMedium(int value) {
            write(value >>> 16);
            write(value >>> 8 & 0xFF);
            write(value & 0xFF);
            return buf;
        }

        @Override
        public Buffer setMedium(int woff, int value) {
            write(woff, value >>> 16);
            write(woff + 1, value >>> 8 & 0xFF);
            write(woff + 2, value & 0xFF);
            return buf;
        }

        @Override
        public Buffer writeUnsignedMedium(int value) {
            write(value >>> 16);
            write(value >>> 8 & 0xFF);
            write(value & 0xFF);
            return buf;
        }

        @Override
        public Buffer setUnsignedMedium(int woff, int value) {
            write(woff, value >>> 16);
            write(woff + 1, value >>> 8 & 0xFF);
            write(woff + 2, value & 0xFF);
            return buf;
        }

        @Override
        public int readInt() {
            return (int) (read() << 24 | read() << 16 | read() << 8 | read());
        }

        @Override
        public int getInt(int roff) {
            return (int) (read(roff) << 24 | read(roff + 1) << 16 | read(roff + 2) << 8 | read(roff + 3));
        }

        @Override
        public long readUnsignedInt() {
            return (read() << 24 | read() << 16 | read() << 8 | read()) & 0xFFFFFFFFL;
        }

        @Override
        public long getUnsignedInt(int roff) {
            return (read(roff) << 24 | read(roff + 1) << 16 | read(roff + 2) << 8 | read(roff + 3)) & 0xFFFFFFFFL;
        }

        @Override
        public Buffer writeInt(int value) {
            write(value >>> 24);
            write(value >>> 16 & 0xFF);
            write(value >>> 8 & 0xFF);
            write(value & 0xFF);
            return buf;
        }

        @Override
        public Buffer setInt(int woff, int value) {
            write(woff, value >>> 24);
            write(woff + 1, value >>> 16 & 0xFF);
            write(woff + 2, value >>> 8 & 0xFF);
            write(woff + 3, value & 0xFF);
            return buf;
        }

        @Override
        public Buffer writeUnsignedInt(long value) {
            write((int) (value >>> 24));
            write((int) (value >>> 16 & 0xFF));
            write((int) (value >>> 8 & 0xFF));
            write((int) (value & 0xFF));
            return buf;
        }

        @Override
        public Buffer setUnsignedInt(int woff, long value) {
            write(woff, (int) (value >>> 24));
            write(woff + 1, (int) (value >>> 16 & 0xFF));
            write(woff + 2, (int) (value >>> 8 & 0xFF));
            write(woff + 3, (int) (value & 0xFF));
            return buf;
        }

        @Override
        public float readFloat() {
            return Float.intBitsToFloat(readInt());
        }

        @Override
        public float getFloat(int roff) {
            return Float.intBitsToFloat(getInt(roff));
        }

        @Override
        public Buffer writeFloat(float value) {
            return writeUnsignedInt(Float.floatToRawIntBits(value));
        }

        @Override
        public Buffer setFloat(int woff, float value) {
            return setUnsignedInt(woff, Float.floatToRawIntBits(value));
        }

        @Override
        public long readLong() {
            return read() << 56 | read() << 48 | read() << 40 | read() << 32 |
                   read() << 24 | read() << 16 | read() << 8 | read();
        }

        @Override
        public long getLong(int roff) {
            return read(roff) << 56 | read(roff + 1) << 48 | read(roff + 2) << 40 | read(roff + 3) << 32 |
                   read(roff + 4) << 24 | read(roff + 5) << 16 | read(roff + 6) << 8 | read(roff + 7);
        }

        @Override
        public Buffer writeLong(long value) {
            write((int) (value >>> 56));
            write((int) (value >>> 48 & 0xFF));
            write((int) (value >>> 40 & 0xFF));
            write((int) (value >>> 32 & 0xFF));
            write((int) (value >>> 24 & 0xFF));
            write((int) (value >>> 16 & 0xFF));
            write((int) (value >>> 8 & 0xFF));
            write((int) (value & 0xFF));
            return buf;
        }

        @Override
        public Buffer setLong(int woff, long value) {
            write(woff, (int) (value >>> 56));
            write(woff + 1, (int) (value >>> 48 & 0xFF));
            write(woff + 2, (int) (value >>> 40 & 0xFF));
            write(woff + 3, (int) (value >>> 32 & 0xFF));
            write(woff + 4, (int) (value >>> 24 & 0xFF));
            write(woff + 5, (int) (value >>> 16 & 0xFF));
            write(woff + 6, (int) (value >>> 8 & 0xFF));
            write(woff + 7, (int) (value & 0xFF));
            return buf;
        }

        @Override
        public double readDouble() {
            return Double.longBitsToDouble(readLong());
        }

        @Override
        public double getDouble(int roff) {
            return Double.longBitsToDouble(getLong(roff));
        }

        @Override
        public Buffer writeDouble(double value) {
            return writeLong(Double.doubleToRawLongBits(value));
        }

        @Override
        public Buffer setDouble(int woff, double value) {
            return setLong(woff, Double.doubleToRawLongBits(value));
        }

        private long read() {
            return buf.readPassThrough();
        }

        private void write(int value) {
            buf.writePassThrough(value);
        }

        private long read(int roff) {
            return buf.getPassThrough(roff);
        }

        private void write(int woff, int value) {
            buf.setPassThrough(woff, value);
        }
    }
    // </editor-fold>

    private static final class ForwardCompositeByteCursor implements ByteCursor {
        final Buffer[] bufs;
        int index;
        final int end;
        int bufferIndex;
        int initOffset;
        ByteCursor cursor;
        byte byteValue;

        ForwardCompositeByteCursor(Buffer[] bufs, int fromOffset, int length, int startBufferIndex,
                                   ByteCursor startCursor) {
            this.bufs = bufs;
            index = fromOffset;
            end = fromOffset + length;
            bufferIndex = startBufferIndex;
            initOffset = startCursor.currentOffset();
            cursor = startCursor;
            byteValue = -1;
        }

        @Override
        public boolean readByte() {
            if (cursor.readByte()) {
                byteValue = cursor.getByte();
                return true;
            }
            if (bytesLeft() > 0) {
                nextCursor();
                cursor.readByte();
                byteValue = cursor.getByte();
                return true;
            }
            return false;
        }

        private void nextCursor() {
            bufferIndex++;
            Buffer nextBuf = bufs[bufferIndex];
            cursor = nextBuf.openCursor(0, Math.min(nextBuf.capacity(), bytesLeft()));
            initOffset = 0;
        }

        @Override
        public byte getByte() {
            return byteValue;
        }

        @Override
        public int currentOffset() {
            int currOff = cursor.currentOffset();
            index += currOff - initOffset;
            initOffset = currOff;
            return index;
        }

        @Override
        public int bytesLeft() {
            return end - currentOffset();
        }
    }

    private static final class ReverseCompositeByteCursor implements ByteCursor {
        final Buffer[] bufs;
        int index;
        final int end;
        int bufferIndex;
        int initOffset;
        ByteCursor cursor;
        byte byteValue;

        ReverseCompositeByteCursor(Buffer[] bufs, int fromOffset, int length,
                                   int startBufferIndex, ByteCursor startCursor) {
            this.bufs = bufs;
            index = fromOffset;
            end = fromOffset - length;
            bufferIndex = startBufferIndex;
            initOffset = startCursor.currentOffset();
            cursor = startCursor;
            byteValue = -1;
        }

        @Override
        public boolean readByte() {
            if (cursor.readByte()) {
                byteValue = cursor.getByte();
                return true;
            }
            if (bytesLeft() > 0) {
                nextCursor();
                cursor.readByte();
                byteValue = cursor.getByte();
                return true;
            }
            return false;
        }

        private void nextCursor() {
            bufferIndex--;
            Buffer nextBuf = bufs[bufferIndex];
            int length = Math.min(nextBuf.capacity(), bytesLeft());
            int offset = nextBuf.capacity() - 1;
            cursor = nextBuf.openReverseCursor(offset, length);
            initOffset = offset;
        }

        @Override
        public byte getByte() {
            return byteValue;
        }

        @Override
        public int currentOffset() {
            int currOff = cursor.currentOffset();
            index -= initOffset - currOff;
            initOffset = currOff;
            return index;
        }

        @Override
        public int bytesLeft() {
            return currentOffset() - end;
        }
    }

    private static final class CompositeComponentIterator<T extends Next & BufferComponent>
            implements ComponentIterator<T> {
        private final DefaultCompositeBuffer compositeBuffer;
        NextComponent<T> readableNext;

        private CompositeComponentIterator(DefaultCompositeBuffer compositeBuffer) {
            this.compositeBuffer = compositeBuffer;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T first() {
            return compositeBuffer.bufs.length > 0 ?
                    (T) (readableNext = new NextComponent<>(compositeBuffer)) : null;
        }

        @Override
        public void close() {
            if (readableNext != null) {
                readableNext.close();
            }
            compositeBuffer.close();
        }
    }

    private static final class NextComponent<T extends Next & BufferComponent>
            implements BufferComponent, Next, SafeCloseable {
        private final DefaultCompositeBuffer compositeBuffer;
        private final Buffer[] bufs;
        int nextIndex;
        ComponentIterator<T> currentItr;
        T currentComponent;
        int currentReadSkip;
        int currentWriteSkip;

        private NextComponent(DefaultCompositeBuffer compositeBuffer) {
            this.compositeBuffer = compositeBuffer;
            bufs = compositeBuffer.bufs;
            nextComponent();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <N extends Next & BufferComponent> N next() {
            if (currentComponent == null) {
                return null; // Already at the end of the iteration.
            }
            nextComponent();
            return currentComponent != null? (N) this : null;
        }

        private void nextComponent() {
            if (currentComponent != null) {
                currentComponent = currentComponent.next();
            }
            while (currentComponent == null) {
                if (currentItr != null) {
                    currentItr.close();
                    currentItr = null;
                }
                if (nextIndex >= bufs.length) {
                    return;
                }
                currentItr = bufs[nextIndex].forEachComponent();
                nextIndex++;
                currentComponent = currentItr.first();
            }
        }

        @Override
        public boolean hasReadableArray() {
            return currentComponent.hasReadableArray();
        }

        @Override
        public byte[] readableArray() {
            return currentComponent.readableArray();
        }

        @Override
        public int readableArrayOffset() {
            return currentComponent.readableArrayOffset();
        }

        @Override
        public int readableArrayLength() {
            return currentComponent.readableArrayLength();
        }

        @Override
        public long baseNativeAddress() {
            return currentComponent.baseNativeAddress();
        }

        @Override
        public long readableNativeAddress() {
            return currentComponent.readableNativeAddress();
        }

        @Override
        public ByteBuffer readableBuffer() {
            return currentComponent.readableBuffer();
        }

        @Override
        public int readableBytes() {
            return currentComponent.readableBytes();
        }

        @Override
        public ByteCursor openCursor() {
            return currentComponent.openCursor();
        }

        @Override
        public BufferComponent skipReadableBytes(int byteCount) {
            currentComponent.skipReadableBytes(byteCount);
            compositeBuffer.readerOffset(currentReadSkip + byteCount);
            currentReadSkip += byteCount; // This needs to be after the bounds-checks.
            return this;
        }

        @Override
        public boolean hasWritableArray() {
            return currentComponent.hasWritableArray();
        }

        @Override
        public byte[] writableArray() {
            return currentComponent.writableArray();
        }

        @Override
        public int writableArrayOffset() {
            return currentComponent.writableArrayOffset();
        }

        @Override
        public int writableArrayLength() {
            return currentComponent.writableArrayLength();
        }

        @Override
        public long writableNativeAddress() {
            return currentComponent.writableNativeAddress();
        }

        @Override
        public int writableBytes() {
            return currentComponent.writableBytes();
        }

        @Override
        public ByteBuffer writableBuffer() {
            return currentComponent.writableBuffer();
        }

        @Override
        public BufferComponent skipWritableBytes(int byteCount) {
            currentComponent.skipWritableBytes(byteCount);
            compositeBuffer.writerOffset(currentWriteSkip + byteCount);
            currentWriteSkip += byteCount; // This needs to be after the bounds-checks.
            return this;
        }

        @Override
        public void close() {
            currentComponent = null;
            if (currentItr != null) {
                currentItr.close();
                currentItr = null;
            }
        }
    }
}
