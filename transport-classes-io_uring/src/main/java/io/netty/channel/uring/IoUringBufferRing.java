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
import io.netty.util.internal.PlatformDependent;

import java.util.Arrays;

final class IoUringBufferRing {
    private final long ioUringBufRingAddr;
    private final short entries;
    private final short mask;
    private final short bufferGroupId;
    private final int ringFd;
    private final ByteBuf[] buffers;
    private final IoUringIoHandler source;
    private final IoUringBufferRingAllocator allocator;
    private final IoUringBufferRingExhaustedEvent exhaustedEvent;
    private final boolean incremental;
    private boolean hasSpareBuffer;

    IoUringBufferRing(int ringFd, long ioUringBufRingAddr,
                      short entries, short bufferGroupId, boolean incremental, IoUringIoHandler ioUringIoHandler,
                      IoUringBufferRingAllocator allocator) {
        assert entries % 2 == 0;
        this.ioUringBufRingAddr = ioUringBufRingAddr;
        this.entries = entries;
        this.mask = (short) (entries - 1);
        this.bufferGroupId = bufferGroupId;
        this.ringFd = ringFd;
        this.buffers = new ByteBuf[entries];
        this.incremental = incremental;
        this.source = ioUringIoHandler;
        this.allocator = allocator;
        this.exhaustedEvent = new IoUringBufferRingExhaustedEvent(bufferGroupId);
    }

    void fill() {
        for (short i = 0; i < entries; i++) {
            addBuffer(i);
        }
    }

    boolean isExhausted() {
        return !hasSpareBuffer;
    }

    /**
     * Mark this buffer ring as exhausted. This should be done if a read failed because there were no more buffers left
     * to use.
     */
    void markExhausted() {
        if (hasSpareBuffer) {
            hasSpareBuffer = false;
            source.idHandler.notifyAllBuffersUsed(bufferGroupId);
        }
    }

    /**
     * @return the {@link IoUringBufferRingExhaustedEvent} that should be used to signal that there were no buffers
     * left for this buffer ring.
     */
    IoUringBufferRingExhaustedEvent getExhaustedEvent() {
        return exhaustedEvent;
    }

    private void addBuffer(short bid) {
        long tailFieldAddress = ioUringBufRingAddr + Native.IO_URING_BUFFER_RING_TAIL;
        short oldTail = PlatformDependent.getShort(tailFieldAddress);

        ByteBuf byteBuf = allocator.allocate();
        byteBuf.writerIndex(byteBuf.capacity());
        buffers[bid] = byteBuf;
        int ringIndex = oldTail & mask;

        //  see:
        //  https://github.com/axboe/liburing/
        //      blob/19134a8fffd406b22595a5813a3e319c19630ac9/src/include/liburing.h#L1561
        long ioUringBufAddress = ioUringBufRingAddr + (long) Native.SIZEOF_IOURING_BUF * ringIndex;
        PlatformDependent.putLong(ioUringBufAddress + Native.IOURING_BUFFER_OFFSETOF_ADDR,
                byteBuf.memoryAddress() + byteBuf.readerIndex());
        PlatformDependent.putInt(ioUringBufAddress + Native.IOURING_BUFFER_OFFSETOF_LEN, byteBuf.capacity());
        PlatformDependent.putShort(ioUringBufAddress + Native.IOURING_BUFFER_OFFSETOF_BID, bid);
        // Now advanced the tail by the number of buffers that we just added.
        PlatformDependent.putShortOrdered(tailFieldAddress, (short) (oldTail + 1));
        hasSpareBuffer = true;
    }

    /**
     * Use the buffer for the given buffer id. The returned {@link ByteBuf} must be released once not used anymore.
     *
     * @param bid           the id of the buffer
     * @param readableBytes the number of bytes that could be read.
     * @return              the buffer.
     */
    ByteBuf useBuffer(short bid, int readableBytes, boolean more) {
        ByteBuf byteBuf = buffers[bid];
        allocator.lastBytesRead(byteBuf.readableBytes(), readableBytes);
        if (incremental && more) {
            // The buffer will be used later again, just slice out what we did read so far.
            return byteBuf.readRetainedSlice(readableBytes);
        }

        // Let's null it out and call addBuffer(bid) to ensure we add a new buffer that can be used.
        buffers[bid] = null;
        boolean hadSpareBuffer = hasSpareBuffer;
        addBuffer(bid);
        if (!hadSpareBuffer) {
            // This buffer ring was marked as exhausted before. We have space again, let's notify the handler.
            assert hasSpareBuffer;
            source.idHandler.notifyMoreBuffersReady(bufferGroupId);
        }
        return byteBuf.writerIndex(byteBuf.readerIndex() +
                Math.min(readableBytes, byteBuf.readableBytes()));
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
        Native.ioUringUnRegisterBufRing(ringFd, ioUringBufRingAddr, entries, bufferGroupId);
        for (ByteBuf byteBuf : buffers) {
            if (byteBuf != null) {
                byteBuf.release();
            }
        }
        Arrays.fill(buffers, null);
    }
}
