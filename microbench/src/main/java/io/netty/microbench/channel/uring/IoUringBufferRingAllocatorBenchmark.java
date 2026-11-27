/*
 * Copyright 2026 The Netty Project
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
package io.netty.microbench.channel.uring;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.unix.Buffer;
import io.netty.channel.uring.IoUring;
import io.netty.channel.uring.IoUringAdaptiveBufferRingAllocator;
import io.netty.channel.uring.IoUringBufferRingAllocator;
import io.netty.channel.uring.IoUringFixedBufferRingAllocator;
import io.netty.channel.uring.IoUringRecyclingBufferRingAllocator;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.ByteBuffer;
import java.util.Queue;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Measures what one buffer of an io_uring provided buffer ring costs for each
 * {@link IoUringBufferRingAllocator} that netty ships.
 *
 * <p>One operation is the whole trip of one buffer through the ring, in the order that
 * {@code IoUringBufferRing} does it: the buffer the kernel just consumed is sliced out
 * ({@code retainedSlice}), the ring drops its own reference, a buffer is allocated to take that buffer id
 * again and gets handed to the kernel by address and writable length, and finally the pipeline drops the
 * slice. {@code parkedBuffers} buffers stay in the ring the whole time, so a buffer is always allocated
 * while the previous one is still in flight, just like on a live ring.
 *
 * <p>{@link #foreignReleaseSlice} drops the slice on another thread instead, which is what happens when an
 * inbound buffer is written out on a different event loop. {@link #handoffOnly} pushes a pre-allocated
 * buffer through the same queue without an allocator, so the hand-off is not charged to the allocator.
 */
public class IoUringBufferRingAllocatorBenchmark extends AbstractMicrobenchmark {

    private static final int BUFFER_SIZE = 8192;
    private static final short BUFFER_RING_SIZE = 64;

    /**
     * The number of buffers that stay in the ring while a buffer is allocated.
     */
    @Param({ "1", "32" })
    public int parkedBuffers;

    public enum AllocatorType {
        FIXED,
        ADAPTIVE,
        RECYCLING
    }

    @Param
    public AllocatorType allocatorType;

    private IoUringBufferRingAllocator allocator;
    private ByteBuf[] parked;
    private int nextBid;
    private Queue<ByteBuf> handoff;
    private ByteBuf sentinel;
    private volatile boolean releasing;
    private Thread releaser;

    @Test
    @Override
    public void run() throws Exception {
        assumeTrue(IoUring.isAvailable(), "io_uring is not available");
        super.run();
    }

    @Setup(Level.Trial)
    public void setup() {
        allocator = newAllocator(allocatorType);
        parked = new ByteBuf[parkedBuffers];
        for (int i = 0; i < parked.length; i++) {
            parked[i] = allocator.allocate();
        }
        // Bounds how many slices the releaser may still owe us, so a lagging releaser can not starve the
        // allocator instead of the benchmark measuring it.
        handoff = PlatformDependent.newFixedMpscQueue(Math.max(4, parkedBuffers));
        sentinel = ByteBufAllocator.DEFAULT.directBuffer(BUFFER_SIZE, BUFFER_SIZE);
        releasing = true;
        releaser = new Thread(new Runnable() {
            @Override
            public void run() {
                while (releasing) {
                    ByteBuf buffer = handoff.poll();
                    if (buffer == null) {
                        Thread.yield();
                    } else if (buffer != sentinel) {
                        buffer.release();
                    }
                }
            }
        }, "buffer-ring-releaser");
        releaser.setDaemon(true);
        releaser.start();
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        releasing = false;
        releaser.join();
        for (;;) {
            ByteBuf buffer = handoff.poll();
            if (buffer == null) {
                break;
            }
            if (buffer != sentinel) {
                buffer.release();
            }
        }
        for (int i = 0; i < parked.length; i++) {
            parked[i].release();
            parked[i] = null;
        }
        sentinel.release();
    }

    private static IoUringBufferRingAllocator newAllocator(AllocatorType type) {
        switch (type) {
            case FIXED:
                return new IoUringFixedBufferRingAllocator(ByteBufAllocator.DEFAULT, BUFFER_SIZE);
            case ADAPTIVE:
                return new IoUringAdaptiveBufferRingAllocator(ByteBufAllocator.DEFAULT);
            case RECYCLING:
                return new IoUringRecyclingBufferRingAllocator(
                        ByteBufAllocator.DEFAULT, BUFFER_RING_SIZE, BUFFER_SIZE);
            default:
                throw new IllegalArgumentException("unknown allocator: " + type);
        }
    }

    /**
     * Consume the buffer that sits at the next buffer id, re-fill that id, and return the slice that the
     * pipeline would receive.
     */
    private ByteBuf consumeAndRefill(Blackhole blackhole) {
        int bid = nextBid;
        nextBid = bid + 1 == parked.length ? 0 : bid + 1;
        ByteBuf buffer = parked[bid];
        int read = buffer.writableBytes();
        allocator.lastBytesRead(read, read);
        ByteBuf slice = buffer.retainedSlice(buffer.writerIndex(), read);
        buffer.writerIndex(buffer.writerIndex() + read);
        buffer.release();
        ByteBuf refill = allocator.allocate();
        blackhole.consume(address(refill));
        blackhole.consume(refill.writableBytes());
        parked[bid] = refill;
        return slice;
    }

    /**
     * The address that {@code IoUringBufferRing} writes into the ring entry, obtained the same way it does.
     */
    private static long address(ByteBuf buffer) {
        if (buffer.hasMemoryAddress()) {
            return buffer.memoryAddress();
        }
        ByteBuffer nioBuffer = buffer.internalNioBuffer(0, buffer.capacity());
        return Buffer.memoryAddress(nioBuffer) + nioBuffer.position();
    }

    @Benchmark
    public void releaseSlice(Blackhole blackhole) {
        consumeAndRefill(blackhole).release();
    }

    @Benchmark
    public void foreignReleaseSlice(Blackhole blackhole) {
        offer(consumeAndRefill(blackhole));
    }

    @Benchmark
    public void handoffOnly() {
        offer(sentinel);
    }

    private void offer(ByteBuf buffer) {
        while (!handoff.offer(buffer)) {
            Thread.yield();
        }
    }
}
