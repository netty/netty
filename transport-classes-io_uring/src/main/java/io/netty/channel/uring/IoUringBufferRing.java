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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

final class IoUringBufferRing {

    private final long ioUringBufRingAddr;

    private final short entries;

    private final short mask;

    private final short bufferGroupId;

    private final int ringFd;

    private final ByteBuf[] userspaceBufferHolder;
    private final int chunkSize;
    private final IoUringIoHandler source;
    private final ByteBufAllocator byteBufAllocator;
    private final Runnable[] recycleBufferTasks;

    private short nextIndex;
    private boolean hasSpareBuffer;

    private BufferRingExhaustedEvent exhaustedEvent;

    IoUringBufferRing(int ringFd, long ioUringBufRingAddr,
                      short entries, short bufferGroupId,
                      int chunkSize, IoUringIoHandler ioUringIoHandler,
                      ByteBufAllocator byteBufAllocator
    ) {
        this.ioUringBufRingAddr = ioUringBufRingAddr;
        this.entries = entries;
        this.mask = (short) (entries - 1);
        this.bufferGroupId = bufferGroupId;
        this.ringFd = ringFd;
        this.userspaceBufferHolder = new ByteBuf[entries];
        this.nextIndex = 0;
        this.chunkSize = chunkSize;
        this.hasSpareBuffer = false;
        this.source = ioUringIoHandler;
        this.byteBufAllocator = byteBufAllocator;
        this.recycleBufferTasks = new Runnable[entries];
    }

    void markReadFail() {
        hasSpareBuffer = false;
    }

    boolean hasSpareBuffer() {
        return hasSpareBuffer;
    }

    /**
     * lazy get the event, if the event is null, create a new one
     * @return a BufferRingExhaustedEvent Instance
     */
    BufferRingExhaustedEvent getExhaustedEvent() {
        BufferRingExhaustedEvent res = exhaustedEvent;
        if (res == null) {
            res = new BufferRingExhaustedEvent(bufferGroupId);
            exhaustedEvent = res;
        }
        return res;
    }

    void recycleBuffer(short bid) {
        source.submitBeforeIO(recycleBufferTasks[bid]);
    }

    void addToRing(short bid, boolean needAdvance) {
        ByteBuf byteBuf = userspaceBufferHolder[bid];
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

    void appendBuffer(int count) {
        int expectedIndex = nextIndex + count;
        if (expectedIndex > entries) {
            throw new IllegalStateException(
                    String.format(
                            "We want append %d buffer, but buffer ring is full. The ring hold %s buffers",
                            count, entries)
            );
        }

        for (int i = 0; i < count; i++) {
            ByteBuf buffer = byteBufAllocator.ioBuffer(chunkSize);
            short bid = nextIndex;
            userspaceBufferHolder[bid] = buffer;
            addToRing(nextIndex, false);
            recycleBufferTasks[bid] = new Runnable() {
                @Override
                public void run() {
                    addToRing(bid, true);
                }
            };
            nextIndex++;
        }
        advanceTail(count);
    }

    ByteBuf borrowBuffer(int bid, int maxCap) {
        ByteBuf byteBuf = userspaceBufferHolder[bid];
        ByteBuf slice = byteBuf.retainedSlice(0, maxCap);
        return new UserspaceIoUringBuffer(maxCap, (short) bid, slice);
    }

    private void advanceTail(int count) {
        long tailFieldAddress = ioUringBufRingAddr + Native.IO_URING_BUFFER_RING_TAIL;
        short oldTail = PlatformDependent.getShort(tailFieldAddress);
        short newTail = (short) (oldTail + count);
        PlatformDependent.putShortOrdered(tailFieldAddress, newTail);
        hasSpareBuffer = true;
    }

    int entries() {
        return entries;
    }

    short bufferGroupId() {
        return bufferGroupId;
    }

    int chunkSize() {
        return chunkSize;
    }

    boolean isFull() {
        return nextIndex == entries;
    }

    long address() {
        return ioUringBufRingAddr;
    }

    void close() {
        Native.ioUringUnRegisterBufRing(ringFd, ioUringBufRingAddr, 4, 1);
        for (ByteBuf byteBuf : userspaceBufferHolder) {
            if (byteBuf != null) {
                byteBuf.release();
            }
        }
    }

    class UserspaceIoUringBuffer extends AbstractReferenceCountedByteBuf {

        private final short bid;

        private final ByteBuf userspaceBuffer;

        protected UserspaceIoUringBuffer(int maxCapacity, short bid, ByteBuf userspaceBuffer) {
            super(maxCapacity);
            this.bid = bid;
            this.userspaceBuffer = userspaceBuffer;
        }

        @Override
        protected void deallocate() {
            userspaceBufferHolder[bid].release();
            recycleBuffer(bid);
        }

        @Override
        protected byte _getByte(int index) {
            return userspaceBuffer.getByte(index);
        }

        @Override
        protected short _getShort(int index) {
            return userspaceBuffer.getShort(index);
        }

        @Override
        protected short _getShortLE(int index) {
            return userspaceBuffer.getShortLE(index);
        }

        @Override
        protected int _getUnsignedMedium(int index) {
            return userspaceBuffer.getUnsignedMedium(index);
        }

        @Override
        protected int _getUnsignedMediumLE(int index) {
            return userspaceBuffer.getUnsignedMediumLE(index);
        }

        @Override
        protected int _getInt(int index) {
            return userspaceBuffer.getInt(index);
        }

        @Override
        protected int _getIntLE(int index) {
            return userspaceBuffer.getIntLE(index);
        }

        @Override
        protected long _getLong(int index) {
            return userspaceBuffer.getLong(index);
        }

        @Override
        protected long _getLongLE(int index) {
            return userspaceBuffer.getLongLE(index);
        }

        @Override
        protected void _setByte(int index, int value) {
            userspaceBuffer.setByte(index, value);
        }

        @Override
        protected void _setShort(int index, int value) {
            userspaceBuffer.setShort(index, value);
        }

        @Override
        protected void _setShortLE(int index, int value) {
            userspaceBuffer.setShortLE(index, value);
        }

        @Override
        protected void _setMedium(int index, int value) {
            userspaceBuffer.setMedium(index, value);
        }

        @Override
        protected void _setMediumLE(int index, int value) {
            userspaceBuffer.setMediumLE(index, value);
        }

        @Override
        protected void _setInt(int index, int value) {
            userspaceBuffer.setInt(index, value);
        }

        @Override
        protected void _setIntLE(int index, int value) {
            userspaceBuffer.setIntLE(index, value);
        }

        @Override
        protected void _setLong(int index, long value) {
            userspaceBuffer.setLong(index, value);
        }

        @Override
        protected void _setLongLE(int index, long value) {
            userspaceBuffer.setLongLE(index, value);
        }

        @Override
        public int capacity() {
            return maxCapacity();
        }

        @Override
        public ByteBuf capacity(int newCapacity) {
            if (newCapacity <= maxCapacity()) {
                this.maxCapacity(newCapacity);
                return this;
            }
            ByteBuf newByteBuffer = byteBufAllocator.directBuffer(newCapacity);
            newByteBuffer.writeBytes(userspaceBuffer, 0, maxCapacity());
            userspaceBuffer.release();
            return newByteBuffer;
        }

        @Override
        public ByteBufAllocator alloc() {
            return userspaceBuffer.alloc();
        }

        @Override
        public ByteOrder order() {
            return userspaceBuffer.order();
        }

        @Override
        public ByteBuf unwrap() {
            return null;
        }

        @Override
        public boolean isDirect() {
            return userspaceBuffer.isDirect();
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
            checkIndex(index, length);
            userspaceBuffer.getBytes(index, dst, dstIndex, length);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
            checkIndex(index, length);
            userspaceBuffer.getBytes(index, dst, dstIndex, length);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuffer dst) {
            checkIndex(index, dst.remaining());
            userspaceBuffer.getBytes(index, dst);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, OutputStream out, int length)
                throws IOException {
            checkIndex(index, length);
            userspaceBuffer.getBytes(index, out, length);
            return this;
        }

        @Override
        public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
            return userspaceBuffer.getBytes(index, out, length);
        }

        @Override
        public int getBytes(int index, FileChannel out, long position, int length) throws IOException {
            return userspaceBuffer.getBytes(index, out, position, length);
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
            userspaceBuffer.setBytes(index, src, srcIndex, length);
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
            userspaceBuffer.setBytes(index, src, srcIndex, length);
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuffer src) {
            userspaceBuffer.setBytes(index, src);
            return this;
        }

        @Override
        public int setBytes(int index, InputStream in, int length) throws IOException {
            return userspaceBuffer.setBytes(index, in, length);
        }

        @Override
        public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
            return userspaceBuffer.setBytes(index, in, length);
        }

        @Override
        public int setBytes(int index, FileChannel in, long position, int length) throws IOException {
            return userspaceBuffer.setBytes(index, in, position, length);
        }

        @Override
        public ByteBuf copy(int index, int length) {
            return userspaceBuffer.copy(index, length);
        }

        @Override
        public int nioBufferCount() {
            return userspaceBuffer.nioBufferCount();
        }

        @Override
        public ByteBuffer nioBuffer(int index, int length) {
            return userspaceBuffer.nioBuffer(index, length);
        }

        @Override
        public ByteBuffer internalNioBuffer(int index, int length) {
            return userspaceBuffer.internalNioBuffer(index, length);
        }

        @Override
        public ByteBuffer[] nioBuffers(int index, int length) {
            return userspaceBuffer.nioBuffers(index, length);
        }

        @Override
        public boolean hasArray() {
            return userspaceBuffer.hasArray();
        }

        @Override
        public byte[] array() {
            return userspaceBuffer.array();
        }

        @Override
        public int arrayOffset() {
            return userspaceBuffer.arrayOffset();
        }

        @Override
        public boolean hasMemoryAddress() {
            return userspaceBuffer.hasMemoryAddress();
        }

        @Override
        public long memoryAddress() {
            return userspaceBuffer.memoryAddress();
        }
    }

}
