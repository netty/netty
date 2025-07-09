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
import io.netty.channel.unix.Buffer;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.function.Consumer;

final class IoUringBufferRing {
    private static final VarHandle SHORT_HANDLE =
            MethodHandles.byteBufferViewVarHandle(short[].class, ByteOrder.nativeOrder());
    private final ByteBuffer ioUringBufRing;
    private final int tailFieldPosition;
    private final short entries;
    private final short mask;
    private final short bufferGroupId;
    private final int ringFd;
    private final ByteBuf[] buffers;
    private final IoUringBufferRingAllocator allocator;
    private final IoUringBufferRingExhaustedEvent exhaustedEvent;
    private final RingConsumer ringConsumer;
    private final boolean incremental;
    private final int batchSize;
    private boolean corrupted;
    private boolean closed;
    private int usableBuffers;
    private int allocatedBuffers;
    private boolean needExpand;

    IoUringBufferRing(int ringFd, ByteBuffer ioUringBufRing,
                      short entries, int batchSize, short bufferGroupId, boolean incremental,
                      IoUringBufferRingAllocator allocator) {
        assert entries % 2 == 0;
        assert batchSize % 2 == 0;
        this.batchSize = batchSize;
        this.ioUringBufRing = ioUringBufRing;
        this.tailFieldPosition = Native.IO_URING_BUFFER_RING_TAIL;
        this.entries = entries;
        this.mask = (short) (entries - 1);
        this.bufferGroupId = bufferGroupId;
        this.ringFd = ringFd;
        this.buffers = new ByteBuf[entries];
        this.incremental = incremental;
        this.allocator = allocator;
        this.ringConsumer  = new RingConsumer();
        this.exhaustedEvent = new IoUringBufferRingExhaustedEvent(bufferGroupId);
    }

    boolean isUsable() {
        return !closed && !corrupted;
    }

    void initialize() {
        // We already validated that batchSize is <= ring length.
        refill(batchSize);
    }

    private final class RingConsumer implements Consumer<ByteBuf> {
        private int expectedBuffers;
        private short num;
        private short bid;
        private short oldTail;

        void fill(int numBuffers) {
            // Fetch the tail once before allocate the batch.
            oldTail = (short) SHORT_HANDLE.get(ioUringBufRing, tailFieldPosition);

            // At the moment we always start with bid 0 and so num and bid is the same. As this is more of an
            // implementation detail it is better to still keep both separated.
            this.num = 0;
            this.bid = 0;
            this.expectedBuffers = numBuffers;
            try {
                allocator.allocateBatch(this, numBuffers);
            } catch (Throwable t) {
                corrupted = true;
                for (int i = 0; i < buffers.length; i++) {
                    ByteBuf buffer = buffers[i];
                    if (buffer != null) {
                        buffer.release();
                        buffers[i] = null;
                    }
                }
                throw t;
            }
            // Now advanced the tail by the number of buffers that we just added.
            SHORT_HANDLE.setRelease(ioUringBufRing, tailFieldPosition, (short) (oldTail + num));

            this.num = 0;
            this.bid = 0;
        }

        @Override
        public void accept(ByteBuf byteBuf) {
            if (corrupted || closed) {
                byteBuf.release();
                throw new IllegalStateException("Already closed");
            }
            if (expectedBuffers == num) {
                byteBuf.release();
                throw new IllegalStateException("Produced too many buffers");
            }
            short ringIndex = (short) ((oldTail + num) & mask);
            assert buffers[bid] == null;

            long memoryAddress = IoUring.memoryAddress(byteBuf) + byteBuf.writerIndex();
            int writable = byteBuf.writableBytes();

            //  see:
            //  https://github.com/axboe/liburing/
            //      blob/19134a8fffd406b22595a5813a3e319c19630ac9/src/include/liburing.h#L1561
            int position = Native.SIZEOF_IOURING_BUF * ringIndex;
            ioUringBufRing.putLong(position + Native.IOURING_BUFFER_OFFSETOF_ADDR, memoryAddress);
            ioUringBufRing.putInt(position + Native.IOURING_BUFFER_OFFSETOF_LEN, writable);
            ioUringBufRing.putShort(position + Native.IOURING_BUFFER_OFFSETOF_BID, bid);

            buffers[bid] = byteBuf;

            bid++;
            num++;
        }
    }

    /**
     * Try to expand by adding more buffers to the ring if there is any space left, this will be done lazy.
     */
    void expand() {
        needExpand = true;
    }

    private void refill(int buffers) {
        if (corrupted || closed) {
            return;
        }
        assert buffers % 2 == 0;
        assert usableBuffers == 0;
        ringConsumer.fill(buffers);
        allocatedBuffers = usableBuffers = buffers;
    }

    /**
     * @return the {@link IoUringBufferRingExhaustedEvent} that should be used to signal that there were no buffers
     * left for this buffer ring.
     */
    IoUringBufferRingExhaustedEvent getExhaustedEvent() {
        return exhaustedEvent;
    }

    /**
     * Return the amount of bytes that we attempted to read for the given id.
     * This method must be called before {@link #useBuffer(short, int, boolean)}.
     *
     * @param bid   the id of the buffer.
     * @return      the attempted bytes.
     */
    int attemptedBytesRead(short bid) {
        return buffers[bid].writableBytes();
    }

    /**
     * Use the buffer for the given buffer id. The returned {@link ByteBuf} must be released once not used anymore.
     *
     * @param bid           the id of the buffer
     * @param read          the number of bytes that could be read. This value might be larger then what a single
     *                      {@link ByteBuf} can hold. Because of this, the caller should call
     *                      @link #useBuffer(short, int, boolean)} in a loop (obtaining the next bid to use by calling
     *                      {@link #nextBid(short)}) until all buffers could be obtained.
     * @return              the buffer.
     */
    ByteBuf useBuffer(short bid, int read, boolean more) {
        assert read > 0;
        ByteBuf byteBuf = buffers[bid];

        allocator.lastBytesRead(byteBuf.writableBytes(), read);
        // We always slice so the user will not mess up things later.
        ByteBuf buffer = byteBuf.retainedSlice(byteBuf.writerIndex(), read);
        byteBuf.writerIndex(byteBuf.writerIndex() + read);

        if (incremental && more && byteBuf.isWritable()) {
            // The buffer will be used later again, just slice out what we did read so far.
            return buffer;
        }

        // The buffer is considered to be used, null out the slot.
        buffers[bid] = null;
        byteBuf.release();
        if (--usableBuffers == 0) {
            int numBuffers = allocatedBuffers;
            if (needExpand) {
                // We did get a signal that our buffer ring did not have enough buffers, let's see if we
                // can grow it.
                needExpand = false;
                numBuffers += Math.min(batchSize, entries - allocatedBuffers);
            }
            refill(numBuffers);
        }
        return buffer;
    }

    short nextBid(short bid) {
        return (short) ((bid + 1) & allocatedBuffers - 1);
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
}
