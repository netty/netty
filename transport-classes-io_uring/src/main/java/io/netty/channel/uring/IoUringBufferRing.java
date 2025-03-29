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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DuplicatedByteBuf;
import io.netty.buffer.SlicedByteBuf;
import io.netty.buffer.SwappedByteBuf;
import io.netty.buffer.WrappedByteBuf;
import io.netty.channel.unix.Buffer;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

final class IoUringBufferRing {
    private static final VarHandle SHORT_HANDLE =
            MethodHandles.byteBufferViewVarHandle(short[].class, ByteOrder.nativeOrder());
    private final ByteBuffer ioUringBufRing;
    private final int tailFieldPosition;
    private final short entries;
    private final int batchSize;
    private final int maxUnreleasedBuffers;
    private final short mask;
    private final short bufferGroupId;
    private final int ringFd;
    private final IoUringBufferRingByteBuf[] buffers;
    private final IoUringBufferRingAllocator allocator;
    private final IoUringBufferRingExhaustedEvent exhaustedEvent;
    private final boolean incremental;
    private final AtomicInteger unreleasedBuffers = new AtomicInteger();
    private volatile boolean usable;
    private boolean corrupted;
    private boolean closed;
    private int numBuffers;
    private boolean expanded;

    IoUringBufferRing(int ringFd, ByteBuffer ioUringBufRing,
                      short entries, int batchSize, int maxUnreleasedBuffers, short bufferGroupId, boolean incremental,
                      IoUringBufferRingAllocator allocator) {
        assert entries % 2 == 0;
        this.ioUringBufRing = ioUringBufRing;
        this.tailFieldPosition = Native.IO_URING_BUFFER_RING_TAIL;
        this.entries = entries;
        this.batchSize = batchSize;
        this.maxUnreleasedBuffers = maxUnreleasedBuffers;
        this.mask = (short) (entries - 1);
        this.bufferGroupId = bufferGroupId;
        this.ringFd = ringFd;
        this.buffers = new IoUringBufferRingByteBuf[entries];
        this.incremental = incremental;
        this.allocator = allocator;
        this.exhaustedEvent = new IoUringBufferRingExhaustedEvent(bufferGroupId);
    }

    boolean isUsable() {
        return !corrupted && usable;
    }

    void initialize() {
        fillBuffers();
        usable = true;
    }

    /**
     * Try to expand by adding more buffers to the ring if there is any space left.
     * This method might be called multiple times before we call {@link #fillBuffer()} again.
     */
    void expand() {
        // TODO: We could also shrink the number of elements again if we find out we not use all of it frequently.
        if (!expanded) {
            // Only expand once before we reset expanded in fillBuffer() which is called once a buffer was completely
            // used and moved out of the buffer ring.
            fillBuffers();
            expanded = true;
        }
    }

    private void fillBuffers() {
        int num = Math.min(batchSize, entries - numBuffers);
        for (short i = 0; i < num; i++) {
            fillBuffer();
        }
    }

    /**
     * @return the {@link IoUringBufferRingExhaustedEvent} that should be used to signal that there were no buffers
     * left for this buffer ring.
     */
    IoUringBufferRingExhaustedEvent getExhaustedEvent() {
        return exhaustedEvent;
    }

    void fillBuffer() {
        if (corrupted || closed) {
            return;
        }
        short oldTail = (short) SHORT_HANDLE.get(ioUringBufRing, tailFieldPosition);
        short ringIndex = (short) (oldTail & mask);
        assert buffers[ringIndex] == null;
        final ByteBuf byteBuf;
        try {
            byteBuf = allocator.allocate();
        } catch (OutOfMemoryError e) {
            // We did run out of memory, This buffer ring should be considered corrupted.
            // TODO: In the future we could try to recover it later by trying to refill it after some time and so
            //       bring it back to a non-corrupted state.
            corrupted = true;
            throw e;
        }
        byteBuf.writerIndex(byteBuf.capacity());
        buffers[ringIndex] = new IoUringBufferRingByteBuf(byteBuf);

        //  see:
        //  https://github.com/axboe/liburing/
        //      blob/19134a8fffd406b22595a5813a3e319c19630ac9/src/include/liburing.h#L1561
        int  position = Native.SIZEOF_IOURING_BUF * ringIndex;
        ioUringBufRing.putLong(position + Native.IOURING_BUFFER_OFFSETOF_ADDR,
                IoUring.memoryAddress(byteBuf) + byteBuf.readerIndex());
        ioUringBufRing.putInt(position + Native.IOURING_BUFFER_OFFSETOF_LEN, byteBuf.capacity());
        ioUringBufRing.putShort(position + Native.IOURING_BUFFER_OFFSETOF_BID, ringIndex);

        // Now advanced the tail by the number of buffers that we just added.
        SHORT_HANDLE.setRelease(ioUringBufRing, tailFieldPosition, (short) (oldTail + 1));
        numBuffers++;
        // We added a buffer to the ring, let's reset the expanded variable so we can expand it if we receive
        // ENOBUFS.
        expanded = false;
    }

    /**
     * Return the amount of bytes that we attempted to read for the given id.
     * This method must be called before {@link #useBuffer(short, int, boolean)}.
     *
     * @param bid   the id of the buffer.
     * @return      the attempted bytes.
     */
    int attemptedBytesRead(short bid) {
        return buffers[bid].readableBytes();
    }

    /**
     * Use the buffer for the given buffer id. The returned {@link ByteBuf} must be released once not used anymore.
     *
     * @param bid           the id of the buffer
     * @param readableBytes the number of bytes that could be read. This value might be larger then what a single
     *                      {@link ByteBuf} can hold. Because of this, the caller should call
     *                      @link #useBuffer(short, int, boolean)} in a loop (obtaining the next bid to use by calling
     *                      {@link #nextBid(short)}) until all buffers could be obtained.
     * @return              the buffer.
     */
    ByteBuf useBuffer(short bid, int readableBytes, boolean more) {
        assert readableBytes > 0;
        IoUringBufferRingByteBuf byteBuf = buffers[bid];

        allocator.lastBytesRead(byteBuf.readableBytes(), readableBytes);
        if (incremental && more && byteBuf.readableBytes() > readableBytes) {
            // The buffer will be used later again, just slice out what we did read so far.
            return byteBuf.readRetainedSlice(readableBytes);
        }

        // The buffer is considered to be used, null out the slot.
        buffers[bid] = null;
        numBuffers--;
        byteBuf.markUsed();
        return byteBuf.writerIndex(byteBuf.readerIndex() +
                Math.min(readableBytes, byteBuf.readableBytes()));
    }

    short nextBid(short bid) {
        return (short) ((bid + 1) & mask);
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
     * Close this {@link IoUringBufferRing}, using it after this method is called will lead to undefined behaviour.
     */
    void close() {
        if (closed) {
            return;
        }
        closed = true;
        Native.ioUringUnRegisterBufRing(ringFd, Buffer.memoryAddress(ioUringBufRing), entries, bufferGroupId);
        for (ByteBuf byteBuf : buffers) {
            if (byteBuf != null) {
                byteBuf.release();
            }
        }
        Arrays.fill(buffers, null);
    }

    // Package-private for testing
    final class IoUringBufferRingByteBuf extends WrappedByteBuf {
        IoUringBufferRingByteBuf(ByteBuf buf) {
            super(buf);
        }

        void markUsed() {
            if (unreleasedBuffers.incrementAndGet() == maxUnreleasedBuffers) {
                usable = false;
            }
        }

        @SuppressWarnings("deprecation")
        @Override
        public ByteBuf order(ByteOrder endianness) {
            if (endianness == order()) {
                return this;
            }
            return new SwappedByteBuf(this);
        }

        @Override
        public ByteBuf slice() {
            return slice(readerIndex(), readableBytes());
        }

        @Override
        public ByteBuf retainedSlice() {
            return slice().retain();
        }

        @SuppressWarnings("deprecation")
        @Override
        public ByteBuf slice(int index, int length) {
            return new SlicedByteBuf(this, index, length);
        }

        @Override
        public ByteBuf retainedSlice(int index, int length) {
            return slice(index, length).retain();
        }

        @Override
        public ByteBuf readSlice(int length) {
            ByteBuf slice = slice(readerIndex(), length);
            skipBytes(length);
            return slice;
        }

        @Override
        public ByteBuf readRetainedSlice(int length) {
            ByteBuf slice = retainedSlice(readerIndex(), length);
            try {
                skipBytes(length);
            } catch (Throwable cause) {
                slice.release();
                throw cause;
            }
            return slice;
        }

        @SuppressWarnings("deprecation")
        @Override
        public ByteBuf duplicate() {
            return new DuplicatedByteBuf(this);
        }

        @Override
        public ByteBuf retainedDuplicate() {
            return duplicate().retain();
        }

        @Override
        public boolean release() {
            if (super.release()) {
                released();
                return true;
            }
            return false;
        }

        @Override
        public boolean release(int decrement) {
            if (super.release(decrement)) {
                released();
                return true;
            }
            return false;
        }

        private void released() {
            if (unreleasedBuffers.decrementAndGet() == maxUnreleasedBuffers / 2) {
                usable = true;
            }
        }
    }
}
