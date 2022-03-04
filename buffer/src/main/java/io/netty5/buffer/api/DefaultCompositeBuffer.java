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
package io.netty5.buffer.api;

import io.netty5.buffer.api.internal.ResourceSupport;
import io.netty5.buffer.api.internal.Statics;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static io.netty5.buffer.api.internal.Statics.bufferIsClosed;
import static io.netty5.buffer.api.internal.Statics.bufferIsReadOnly;
import static io.netty5.buffer.api.internal.Statics.checkLength;
import static java.lang.Math.addExact;
import static java.lang.Math.toIntExact;

/**
 * The default implementation of the {@link CompositeBuffer} interface.
 */
final class DefaultCompositeBuffer extends ResourceSupport<Buffer, DefaultCompositeBuffer> implements CompositeBuffer {
    /**
     * The max array size is JVM implementation dependant, but most seem to settle on {@code Integer.MAX_VALUE - 8}.
     * We set the max composite buffer capacity to the same, since it would otherwise be impossible to create a
     * non-composite copy of the buffer.
     */
    private static final int MAX_CAPACITY = Integer.MAX_VALUE - 8;
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

    /**
     * @see CompositeBuffer#compose(BufferAllocator, Send[])
     */
    @SafeVarargs
    public static CompositeBuffer compose(BufferAllocator allocator, Send<Buffer>... sends) {
        Buffer[] bufs = new Buffer[sends.length];
        RuntimeException ise = null;
        for (int i = 0; i < sends.length; i++) {
            if (ise != null) {
                try {
                    sends[i].close();
                } catch (Exception closeExc) {
                    ise.addSuppressed(closeExc);
                }
            } else {
                try {
                    bufs[i] = sends[i].receive();
                } catch (RuntimeException e) {
                    // We catch RuntimeException instead of IllegalStateException to ensure cleanup always happens
                    // regardless of the exception thrown.
                    ise = e;
                    for (int j = 0; j < i; j++) {
                        try {
                            bufs[j].close();
                        } catch (Exception closeExc) {
                            ise.addSuppressed(closeExc);
                        }
                    }
                }
            }
        }
        if (ise != null) {
            throw ise;
        }
        return new DefaultCompositeBuffer(allocator, filterExternalBufs(Arrays.stream(bufs)), COMPOSITE_DROP);
    }

    /**
     * @see CompositeBuffer#compose(BufferAllocator)
     */
    public static CompositeBuffer compose(BufferAllocator allocator) {
        return new DefaultCompositeBuffer(allocator, EMPTY_BUFFER_ARRAY, COMPOSITE_DROP);
    }

    private static Buffer[] filterExternalBufs(Stream<Buffer> refs) {
        // We filter out all zero-capacity buffers because they wouldn't contribute to the composite buffer anyway,
        // and also, by ensuring that all constituent buffers contribute to the size of the composite buffer,
        // we make sure that the number of composite buffers will never become greater than the number of bytes in
        // the composite buffer.
        // This restriction guarantees that methods like countComponents, forEachReadable and forEachWritable,
        // will never overflow their component counts.
        // Allocating a new array unconditionally also prevents external modification of the array.
        Buffer[] bufs = refs
                .filter(DefaultCompositeBuffer::discardEmpty)
                .flatMap(DefaultCompositeBuffer::flattenBuffer)
                .toArray(Buffer[]::new);
        // Make sure there are no duplicates among the buffers.
        Set<Buffer> duplicatesCheck = Collections.newSetFromMap(new IdentityHashMap<>());
        duplicatesCheck.addAll(Arrays.asList(bufs));
        if (duplicatesCheck.size() < bufs.length) {
            IllegalArgumentException iae = new IllegalArgumentException(
                    "Cannot create composite buffer with duplicate constituent buffer components.");
            for (Buffer buf : bufs) {
                try {
                    buf.close();
                } catch (Exception closeExc) {
                    iae.addSuppressed(closeExc);
                }
            }
            throw iae;
        }
        return bufs;
    }

    private static boolean discardEmpty(Buffer buf) {
        if (buf.capacity() > 0) {
            return true;
        } else {
            // If we filter a buffer out, then we must make sure to close it since it's ownership was sent to us.
            buf.close();
            return false;
        }
    }

    private static Stream<Buffer> flattenBuffer(Buffer buf) {
        if (CompositeBuffer.isComposite(buf)) {
            // Extract components so composite buffers always have non-composite components.
            var composite = (CompositeBuffer) buf;
            return Stream.of(composite.decomposeBuffer());
        }
        return Stream.of(buf);
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
                if (buf.writableBytes() == 0) {
                    woff += buf.capacity();
                } else if (!woffMidpoint) {
                    woff += buf.writerOffset();
                    woffMidpoint = true;
                } else if (buf.writerOffset() != 0) {
                    throw new IllegalArgumentException(
                            "The given buffers cannot be composed because they leave an unwritten gap: " +
                            Arrays.toString(bufs) + '.');
                }
            }
            boolean roffMidpoint = false;
            for (Buffer buf : bufs) {
                if (buf.readableBytes() == 0 && buf.writableBytes() == 0) {
                    roff += buf.capacity();
                } else if (!roffMidpoint) {
                    roff += buf.readerOffset();
                    roffMidpoint = true;
                } else if (buf.readerOffset() != 0) {
                    throw new IllegalArgumentException(
                            "The given buffers cannot be composed because they leave an unread gap: " +
                            Arrays.toString(bufs) + '.');
                }
            }
            assert roff <= woff:
                    "The given buffers place the read offset ahead of the write offset: " + Arrays.toString(bufs) + '.';
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
        if (cap > MAX_CAPACITY) {
            throw new IllegalArgumentException(
                    "Combined size of the constituent buffers is too big. " +
                    "The maximum buffer capacity is " + MAX_CAPACITY + " (Integer.MAX_VALUE - 8), " +
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
        prepRead(index, 0);
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
    public int readableBytes() {
        return writerOffset() - readerOffset();
    }

    @Override
    public int writableBytes() {
        return capacity() - writerOffset();
    }

    @Override
    public void skipReadable(int delta) {
        readerOffset(readerOffset() + delta);
    }

    @Override
    public void skipWritable(int delta) {
        writerOffset(writerOffset() + delta);
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
    public CompositeBuffer copy(int offset, int length) {
        checkLength(length);
        checkGetBounds(offset, length);
        if (closed) {
            throw bufferIsClosed(this);
        }
        Buffer choice = (Buffer) chooseBuffer(offset, 0);
        Buffer[] copies;

        if (length > 0) {
            copies = new Buffer[bufs.length];
            int off = subOffset;
            int cap = length;
            int i;
            int j = 0;
            for (i = searchOffsets(offset); cap > 0; i++) {
                var buf = bufs[i];
                int avail = buf.capacity() - off;
                copies[j++] = buf.copy(off, Math.min(cap, avail));
                cap -= avail;
                off = 0;
            }
            copies = Arrays.copyOf(copies, j);
        } else {
            // Specialize for length == 0, since we must copy from at least one constituent buffer.
            copies = new Buffer[] { choice.copy(subOffset, 0) };
        }

        return new DefaultCompositeBuffer(allocator, copies, COMPOSITE_DROP);
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
        ByteBufferCollector collector = new ByteBufferCollector(countReadableComponents());
        forEachReadable(0, collector);
        ByteBuffer[] byteBuffers = collector.buffers;
        int bufferCount = countAndPrepareBuffersForChannelIO(length, byteBuffers);
        int totalBytesWritten = 0;
        try {
            if (channel instanceof GatheringByteChannel) {
                GatheringByteChannel gatheringChannel = (GatheringByteChannel) channel;
                totalBytesWritten = toIntExact(gatheringChannel.write(byteBuffers, 0, bufferCount));
            } else {
                for (int i = 0; i < bufferCount; i++) {
                    int bytesWritten = channel.write(byteBuffers[i]);
                    totalBytesWritten = addExact(totalBytesWritten, bytesWritten);
                }
            }
        } finally {
            skipReadable(totalBytesWritten);
        }
        return totalBytesWritten;
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
        ByteBufferCollector collector = new ByteBufferCollector(countWritableComponents());
        forEachWritable(0, collector);
        ByteBuffer[] byteBuffers = collector.buffers;
        int bufferCount = countAndPrepareBuffersForChannelIO(length, byteBuffers);
        int totalBytesRead = 0;
        try {
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
                skipWritable(totalBytesRead);
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
    public ByteCursor openCursor() {
        return openCursor(readerOffset(), readableBytes());
    }

    @Override
    public ByteCursor openCursor(int fromOffset, int length) {
        if (fromOffset < 0) {
            throw new IllegalArgumentException("The fromOffset cannot be negative: " + fromOffset + '.');
        }
        checkLength(length);
        if (capacity < addExact(fromOffset, length)) {
            throw new IllegalArgumentException("The fromOffset+length is beyond the end of the buffer: " +
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
            throw new IllegalArgumentException("The fromOffset cannot be negative: " + fromOffset + '.');
        }
        checkLength(length);
        if (fromOffset - length < -1) {
            throw new IllegalArgumentException("The fromOffset-length would underflow the buffer: " +
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
        Statics.assertValidBufferSize(capacity() + (long) growth);
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
        Statics.assertValidBufferSize(newSize);

        Buffer[] restoreTemp = bufs; // We need this to restore our buffer array, in case offset computations fail.
        try {
            if (CompositeBuffer.isComposite(buffer)) {
                // If the extension is itself a composite buffer, then extend this one by all the constituent
                // component buffers.
                CompositeBuffer compositeExtension = (CompositeBuffer) buffer;
                Buffer[] addedBuffers = compositeExtension.decomposeBuffer();
                Set<Buffer> duplicatesCheck = Collections.newSetFromMap(new IdentityHashMap<>());
                duplicatesCheck.addAll(Arrays.asList(bufs));
                duplicatesCheck.addAll(Arrays.asList(addedBuffers));
                if (duplicatesCheck.size() < bufs.length + addedBuffers.length) {
                    throw extensionDuplicatesException();
                }
                int extendAtIndex = bufs.length;
                bufs = Arrays.copyOf(bufs, extendAtIndex + addedBuffers.length);
                System.arraycopy(addedBuffers, 0, bufs, extendAtIndex, addedBuffers.length);
                computeBufferOffsets();
            } else {
                for (Buffer buf : restoreTemp) {
                    if (buf == buffer) {
                        throw extensionDuplicatesException();
                    }
                }
                unsafeExtendWith(buffer);
            }
            if (restoreTemp.length == 0) {
                readOnly = buffer.readOnly();
            }
        } catch (Exception e) {
            bufs = restoreTemp;
            throw e;
        }
        return this;
    }

    private static IllegalArgumentException extensionDuplicatesException() {
        return new IllegalArgumentException(
                "The composite buffer cannot be extended with the given extension," +
                " as it would cause the buffer to have duplicate constituent buffers.");
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
        bufs = Arrays.copyOfRange(bufs, off == bufs[i].capacity()? 1 + i : i, bufs.length);
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
    public <E extends Exception> int forEachReadable(int initialIndex, ReadableComponentProcessor<E> processor)
            throws E {
        if (!isAccessible()) {
            throw attachTrace(bufferIsClosed(this));
        }
        int readableBytes = readableBytes();
        if (readableBytes == 0) {
            return 0;
        }
        checkReadBounds(readerOffset(), readableBytes);
        int visited = 0;
        for (Buffer buf : bufs) {
            if (buf.readableBytes() > 0) {
                int roffBefore = buf.readerOffset();
                int count = buf.forEachReadable(visited + initialIndex, processor);
                int roffAfter = buf.readerOffset();
                if (roffAfter != roffBefore) { // Check if ReadableComponent.skipReadable was called.
                    buf.readerOffset(roffBefore); // Reset component offset.
                    skipReadable(roffAfter - roffBefore); // Then move *composite* buffer offset.
                }
                if (count > 0) {
                    visited += count;
                } else {
                    visited = -visited + count;
                    break;
                }
            }
        }
        return visited;
    }

    @Override
    public <E extends Exception> int forEachWritable(int initialIndex, WritableComponentProcessor<E> processor)
            throws E {
        if (!isAccessible()) {
            throw attachTrace(bufferIsClosed(this));
        }
        int writableBytes = writableBytes();
        if (writableBytes == 0) {
            return 0;
        }
        checkWriteBounds(writerOffset(), writableBytes);
        int visited = 0;
        for (Buffer buf : bufs) {
            if (buf.writableBytes() > 0) {
                int woffBefore = buf.writerOffset();
                int count = buf.forEachWritable(visited + initialIndex, processor);
                int woffAfter = buf.writerOffset();
                if (woffAfter != woffBefore) { // Check if WritableComponent.skipWritable was called.
                    buf.writerOffset(woffBefore);
                    skipWritable(woffAfter - woffBefore);
                }
                if (count > 0) {
                    visited += count;
                } else {
                    visited = -visited + count;
                    break;
                }
            }
        }
        return visited;
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
        return prepRead(Short.BYTES).readShort();
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
        return prepRead(3).readMedium();
    }

    @Override
    public int getUnsignedMedium(int roff) {
        return prepGet(roff, 3).getMedium(subOffset);
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
        return drop -> {
            Buffer[] received = new Buffer[sends.length];
            for (int i = 0; i < sends.length; i++) {
                received[i] = sends[i].receive();
            }
            var composite = new DefaultCompositeBuffer(allocator, received, drop);
            composite.readOnly = readOnly;
            drop.attach(composite);
            return composite;
        };
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
            result &= Statics.isOwned((ResourceSupport<?, ?>) buf);
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
        var buf = prepWrite(woff, size);
        woff += size;
        return buf;
    }

    private BufferAccessor prepWrite(int index, int size) {
        checkWriteBounds(index, size);
        return chooseBuffer(index, size);
    }

    private void checkWriteBounds(int index, int size) {
        if (index < 0 || capacity < index + size) {
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
        return o instanceof Buffer && Statics.equals(this, (Buffer) o);
    }

    @Override
    public int hashCode() {
        return Statics.hashCode(this);
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

    private static final class ByteBufferCollector
            implements ReadableComponentProcessor<RuntimeException>, WritableComponentProcessor<RuntimeException> {
        final ByteBuffer[] buffers;

        private ByteBufferCollector(int expectedBufferCount) {
            buffers = new ByteBuffer[expectedBufferCount];
        }

        @Override
        public boolean process(int index, ReadableComponent component) {
            buffers[index] = component.readableBuffer();
            return true;
        }

        @Override
        public boolean process(int index, WritableComponent component) {
            buffers[index] = component.writableBuffer();
            return true;
        }
    }
}
