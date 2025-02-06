/*
 * Copyright 2025 The Netty Project
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
package io.netty.channel.uring;

import io.netty.buffer.AbstractReferenceCountedByteBuf;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

final class IoUringBufferRing {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(IoUringBufferRing.class);
    // todo 8 doesn't have any particular meaning. It's just my intuition.
    // Maybe we can find a more appropriate value.
    private static final int BATCH_ALLOCATE_SIZE =
            SystemPropertyUtil.getInt("io.netty.iouring.bufferRing.allocate.batch.size", 8);

    private final long ioUringBufRingAddr;
    private final short entries;
    private final short mask;
    private final short bufferGroupId;
    private final int ringFd;
    private final ByteBuf[] buffers;
    private final int chunkSize;
    private final IoUringIoHandler source;
    private final ByteBufAllocator byteBufAllocator;
    private final IoUringBufferRingExhaustedEvent exhaustedEvent;
    private final boolean incremental;
    private short nextIndex;
    private boolean hasSpareBuffer;

    IoUringBufferRing(int ringFd, long ioUringBufRingAddr,
                      short entries, short bufferGroupId,
                      int chunkSize, boolean incremental, IoUringIoHandler ioUringIoHandler,
                      ByteBufAllocator byteBufAllocator) {
        assert entries % 2 == 0;
        this.ioUringBufRingAddr = ioUringBufRingAddr;
        this.entries = entries;
        this.mask = (short) (entries - 1);
        this.bufferGroupId = bufferGroupId;
        this.ringFd = ringFd;
        this.buffers = new ByteBuf[entries];
        this.chunkSize = chunkSize;
        this.incremental = incremental;
        this.source = ioUringIoHandler;
        this.byteBufAllocator = byteBufAllocator;
        this.exhaustedEvent = new IoUringBufferRingExhaustedEvent(bufferGroupId);
    }

    /**
     * Mark this buffer ring as exhausted. This should be done if a read failed because there were no more buffers left
     * to use.
     */
    void markExhausted() {
        hasSpareBuffer = false;
        source.idHandler.exhausted(bufferGroupId);
    }

    /**
     * Returns {@code true} if this buffer ring has buffers left that can be used before the need of returning buffers.
     *
     * @return {@code true} if something spare to use, {@code false} otherwise.
     */
    boolean hasSpareBuffer() {
        return hasSpareBuffer;
    }

    /**
     * @return the {@link IoUringBufferRingExhaustedEvent} that should be used to signal that there were no buffers
     * left for this buffer ring.
     */
    IoUringBufferRingExhaustedEvent getExhaustedEvent() {
        return exhaustedEvent;
    }

    private void addToRing(short bid, boolean needAdvance) {
        ByteBuf byteBuf = buffers[bid];
        byteBuf.setIndex(0, byteBuf.capacity());

        long tailFieldAddress = ioUringBufRingAddr + Native.IO_URING_BUFFER_RING_TAIL;
        short oldTail = PlatformDependent.getShort(tailFieldAddress);
        int ringIndex = oldTail & mask;
        //  see:
        //  https://github.com/axboe/liburing/blob/19134a8fffd406b22595a5813a3e319c19630ac9/src/include/liburing.h#L1561
        long ioUringBufAddress = ioUringBufRingAddr + (long) Native.SIZEOF_IOURING_BUF * ringIndex;
        PlatformDependent.putLong(ioUringBufAddress + Native.IOURING_BUFFER_OFFSETOF_ADDR, byteBuf.memoryAddress());
        PlatformDependent.putInt(ioUringBufAddress + Native.IOURING_BUFFER_OFFSETOF_LEN, (short) byteBuf.capacity());
        PlatformDependent.putShort(ioUringBufAddress + Native.IOURING_BUFFER_OFFSETOF_BID, bid);
        if (needAdvance) {
            advanceTail(1);
        }
    }

    /**
     * Append more buffers to the ring.
     *
     * @param count the number of buffers to add.
     */
    void appendBuffer(int count) {
        int expectedIndex = nextIndex + count;
        if (expectedIndex > entries) {
            throw new IllegalStateException(
                    String.format(
                            "We want append %d buffer, but buffer ring is full. The ring hold %s buffers",
                            count, entries)
            );
        }

        int batchAllocateCount = count / BATCH_ALLOCATE_SIZE;
        initBuffers(batchAllocateCount, BATCH_ALLOCATE_SIZE);
        int countRemain = count % BATCH_ALLOCATE_SIZE;
        if (countRemain != 0) {
            initBuffers(countRemain, countRemain);
        }

        advanceTail(count);
    }

    private void initBuffers(int numBuffers, int multiplier) {
        ByteBuf bigChunk = byteBufAllocator.directBuffer(multiplier * chunkSize);
        for (int j = 0; j < numBuffers; j++) {
            ByteBuf byteBuf = bigChunk.retainedSlice(j * chunkSize, chunkSize);
            short bid = nextIndex;
            buffers[bid] = byteBuf;
            addToRing(bid, false);
            nextIndex++;
        }
        bigChunk.release();
    }

    /**
     * Borrow the buffer for the given buffer id. The returned {@link ByteBuf} must be released once not used anymore.
     *
     * @param bid           the id of the buffer
     * @param readableBytes the number of bytes that could be read.
     * @return              the buffer.
     */
    ByteBuf borrowBuffer(short bid, int readableBytes, boolean more) {
        ByteBuf byteBuf = buffers[bid];
        if (incremental) {
            if (byteBuf.readerIndex() == 0) {
                if (more) {
                    byteBuf.retain();
                }
            } else if (!more) {
                byteBuf.release();
            }
        }
        ByteBuf slice = byteBuf.readRetainedSlice(readableBytes);
        return new IoUringBufferRingByteBuf(this, bid, slice);
    }

    private void advanceTail(int count) {
        long tailFieldAddress = ioUringBufRingAddr + Native.IO_URING_BUFFER_RING_TAIL;
        short oldTail = PlatformDependent.getShort(tailFieldAddress);
        short newTail = (short) (oldTail + count);
        PlatformDependent.putShortOrdered(tailFieldAddress, newTail);
        if (!hasSpareBuffer) {
            hasSpareBuffer = true;
            source.idHandler.moreSpace(bufferGroupId);
        }
    }

    /**
     * The group id that is assigned to this buffer ring.
     *
     * @return group id.
     */
    short bufferGroupId() {
        return bufferGroupId;
    }

    /**
     * This size of the chunks that are allocated.
     *
     * @return chunk size.
     */
    int chunkSize() {
        return chunkSize;
    }

    /**
     * Returns {@code true} if the {@link IoUringBufferRing} is completely filled. This means there
     * can't be any more new buffers allocated for use.
     *
     * @return is full.
     */
    boolean isFull() {
        return nextIndex == entries;
    }

    /**
     * Close this {@link IoUringBufferRing}, using it after this method is called will lead to undefined behaviour.
     */
    void close() {
        Native.ioUringUnRegisterBufRing(ringFd, ioUringBufRingAddr, entries, bufferGroupId);
        for (ByteBuf byteBuf : buffers) {
            if (byteBuf != null) {
                byteBuf.release();
            }
        }
    }

    static final class IoUringBufferRingByteBuf extends AbstractReferenceCountedByteBuf implements Runnable {
        private final IoUringBufferRing ring;
        private final short bid;
        private final ByteBuf wrapped;

        IoUringBufferRingByteBuf(IoUringBufferRing ring, short bid, ByteBuf wrapped) {
            super(wrapped.capacity());
            this.ring = ring;
            this.bid = bid;
            this.wrapped = wrapped;
        }

        @Override
        protected void deallocate() {
            // Hand of to the IoExecutorThread to release and recycle.
            // As we already need to schedule it to the IoExecutorThread we will also just call release there
            // to reduce overhead. We can't reuse the buffer anyway till it was added back to the ring.
            ring.source.runInExecutorThread(this);
        }

        @Override
        public void run() {
            try {
                wrapped.release();
                if (wrapped.refCnt() == 1) {
                    ring.addToRing(bid, true);
                }
            } catch (Throwable t) {
                logger.error("Failed to recycle buffer for bid " + bid, t);
            }
        }

        @Override
        protected byte _getByte(int index) {
            return wrapped.getByte(index);
        }

        @Override
        protected short _getShort(int index) {
            return wrapped.getShort(index);
        }

        @Override
        protected short _getShortLE(int index) {
            return wrapped.getShortLE(index);
        }

        @Override
        protected int _getUnsignedMedium(int index) {
            return wrapped.getUnsignedMedium(index);
        }

        @Override
        protected int _getUnsignedMediumLE(int index) {
            return wrapped.getUnsignedMediumLE(index);
        }

        @Override
        protected int _getInt(int index) {
            return wrapped.getInt(index);
        }

        @Override
        protected int _getIntLE(int index) {
            return wrapped.getIntLE(index);
        }

        @Override
        protected long _getLong(int index) {
            return wrapped.getLong(index);
        }

        @Override
        protected long _getLongLE(int index) {
            return wrapped.getLongLE(index);
        }

        @Override
        protected void _setByte(int index, int value) {
            wrapped.setByte(index, value);
        }

        @Override
        protected void _setShort(int index, int value) {
            wrapped.setShort(index, value);
        }

        @Override
        protected void _setShortLE(int index, int value) {
            wrapped.setShortLE(index, value);
        }

        @Override
        protected void _setMedium(int index, int value) {
            wrapped.setMedium(index, value);
        }

        @Override
        protected void _setMediumLE(int index, int value) {
            wrapped.setMediumLE(index, value);
        }

        @Override
        protected void _setInt(int index, int value) {
            wrapped.setInt(index, value);
        }

        @Override
        protected void _setIntLE(int index, int value) {
            wrapped.setIntLE(index, value);
        }

        @Override
        protected void _setLong(int index, long value) {
            wrapped.setLong(index, value);
        }

        @Override
        protected void _setLongLE(int index, long value) {
            wrapped.setLongLE(index, value);
        }

        @Override
        public int capacity() {
            return maxCapacity();
        }

        @Override
        public ByteBuf capacity(int newCapacity) {
            if (newCapacity <= maxCapacity()) {
                this.maxCapacity(newCapacity);
                setIndex(Math.min(readerIndex(), newCapacity), Math.min(writerIndex(), newCapacity));
                return this;
            }

            throw new IllegalArgumentException(
                    String.format(
                        "minNewCapacity: %d (expected: not greater than maxCapacity(%d)",
                        newCapacity, maxCapacity()
                    )
            );
        }

        @Override
        public ByteBufAllocator alloc() {
            return wrapped.alloc();
        }

        @Override
        public ByteOrder order() {
            return wrapped.order();
        }

        @Override
        public ByteBuf unwrap() {
            return null;
        }

        @Override
        public boolean isDirect() {
            return wrapped.isDirect();
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
            checkIndex(index, length);
            wrapped.getBytes(index, dst, dstIndex, length);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
            checkIndex(index, length);
            wrapped.getBytes(index, dst, dstIndex, length);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuffer dst) {
            checkIndex(index, dst.remaining());
            wrapped.getBytes(index, dst);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, OutputStream out, int length)
                throws IOException {
            checkIndex(index, length);
            wrapped.getBytes(index, out, length);
            return this;
        }

        @Override
        public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
            return wrapped.getBytes(index, out, length);
        }

        @Override
        public int getBytes(int index, FileChannel out, long position, int length) throws IOException {
            return wrapped.getBytes(index, out, position, length);
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
            wrapped.setBytes(index, src, srcIndex, length);
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
            wrapped.setBytes(index, src, srcIndex, length);
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuffer src) {
            wrapped.setBytes(index, src);
            return this;
        }

        @Override
        public int setBytes(int index, InputStream in, int length) throws IOException {
            return wrapped.setBytes(index, in, length);
        }

        @Override
        public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
            return wrapped.setBytes(index, in, length);
        }

        @Override
        public int setBytes(int index, FileChannel in, long position, int length) throws IOException {
            return wrapped.setBytes(index, in, position, length);
        }

        @Override
        public ByteBuf copy(int index, int length) {
            return wrapped.copy(index, length);
        }

        @Override
        public int nioBufferCount() {
            return wrapped.nioBufferCount();
        }

        @Override
        public ByteBuffer nioBuffer(int index, int length) {
            return wrapped.nioBuffer(index, length);
        }

        @Override
        public ByteBuffer internalNioBuffer(int index, int length) {
            return wrapped.internalNioBuffer(index, length);
        }

        @Override
        public ByteBuffer[] nioBuffers(int index, int length) {
            return wrapped.nioBuffers(index, length);
        }

        @Override
        public boolean hasArray() {
            return wrapped.hasArray();
        }

        @Override
        public byte[] array() {
            return wrapped.array();
        }

        @Override
        public int arrayOffset() {
            return wrapped.arrayOffset();
        }

        @Override
        public boolean hasMemoryAddress() {
            return wrapped.hasMemoryAddress();
        }

        @Override
        public long memoryAddress() {
            return wrapped.memoryAddress();
        }
    }
}
