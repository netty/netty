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
package io.netty.channel.uring;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.unix.Buffer;
import io.netty.util.concurrent.FastThreadLocalThread;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class IoUringRecyclingBufferRingAllocatorTest {

    private static final int BUFFER_SIZE = 64;

    @BeforeAll
    public static void loadJNI() {
        // The allocator aligns its region to Native.PAGE_SIZE, which needs the native library.
        assumeTrue(IoUring.isAvailable());
    }

    /**
     * Run the body on a {@link FastThreadLocalThread}, which is what an event loop is: the allocator only serves
     * a region to a thread that cleans up its {@link io.netty.util.concurrent.FastThreadLocal}s.
     */
    private static void onEventLoopThread(Runnable task) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread loop = new FastThreadLocalThread(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        loop.start();
        loop.join();
        Throwable cause = failure.get();
        if (cause instanceof Error) {
            throw (Error) cause;                 // an assertion, unchanged
        }
        if (cause instanceof RuntimeException) {
            throw (RuntimeException) cause;      // an aborted assumption stays a skip, not a failure
        }
        if (cause != null) {
            throw new AssertionError(cause.getMessage(), cause);
        }
    }

    private static IoUringRecyclingBufferRingAllocator newAllocator(int bufferRingSize) {
        return new IoUringRecyclingBufferRingAllocator(
                UnpooledByteBufAllocator.DEFAULT, (short) bufferRingSize, BUFFER_SIZE);
    }

    @Test
    public void rejectsBadArguments() {
        assertThrows(IllegalArgumentException.class, () -> newAllocator(0));
        assertThrows(IllegalArgumentException.class,
                () -> new IoUringRecyclingBufferRingAllocator(UnpooledByteBufAllocator.DEFAULT, (short) 4, 0));
    }

    @Test
    public void allocatedBufferIsEmptyAndFixedSize() throws Exception {
        onEventLoopThread(() -> {
            IoUringRecyclingBufferRingAllocator allocator = newAllocator(4);
            ByteBuf buffer = allocator.allocate();
            assertTrue(buffer.isDirect());
            assertEquals(BUFFER_SIZE, buffer.capacity());
            assertEquals(BUFFER_SIZE, buffer.maxCapacity());
            assertEquals(BUFFER_SIZE, buffer.writableBytes());
            assertEquals(0, buffer.writerIndex());
            assertEquals(1, buffer.refCnt());
            buffer.release();
            assertEquals(0, allocator.fallbackAllocations());
        });
    }

    @Test
    public void releaseReturnsTheSameBufferReArmed() throws Exception {
        onEventLoopThread(() -> {
            IoUringRecyclingBufferRingAllocator allocator = newAllocator(4);
            ByteBuf buffer = allocator.allocate();
            buffer.writeByte('a');
            buffer.release();
            assertEquals(0, buffer.refCnt());

            assertSame(buffer, allocator.allocate());
            assertEquals(1, buffer.refCnt());
            assertEquals(0, buffer.readerIndex());
            assertEquals(0, buffer.writerIndex());
            assertEquals(BUFFER_SIZE, buffer.writableBytes());
            buffer.release();
            assertEquals(0, allocator.fallbackAllocations());
        });
    }

    @Test
    public void bufferReturnsOnlyAfterTheLastSlice() throws Exception {
        onEventLoopThread(() -> {
            IoUringRecyclingBufferRingAllocator allocator = newAllocator(1);
            ByteBuf buffer = allocator.allocate();
            // What IoUringBufferRing does for an incrementally consumed buffer: slice out what was read so far and
            // drop its own reference only once the buffer can not be written into anymore.
            buffer.writeByte('a');
            ByteBuf first = buffer.retainedSlice(0, 1);
            buffer.writeByte('b');
            ByteBuf second = buffer.retainedSlice(1, 1);
            buffer.release();

            first.release();
            ByteBuf other = allocator.allocate();
            assertNotSame(buffer, other);
            other.release();

            second.release();
            assertEquals(0, buffer.refCnt());
            assertSame(buffer, allocator.allocate());
            buffer.release();
        });
    }

    @Test
    public void releaseFromOtherThreadIsReusedByTheOwner() throws Exception {
        onEventLoopThread(() -> {
            IoUringRecyclingBufferRingAllocator allocator = newAllocator(1);
            final ByteBuf buffer = allocator.allocate();

            Thread releaser = new Thread(() -> buffer.release());
            releaser.start();
            try {
                releaser.join();
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
            assertEquals(0, buffer.refCnt());

            assertSame(buffer, allocator.allocate());
            assertEquals(0, allocator.fallbackAllocations());
            buffer.release();
        });
    }

    @Test
    public void extendsOnceAndThenFallsBack() throws Exception {
        onEventLoopThread(() -> {
            IoUringRecyclingBufferRingAllocator allocator = newAllocator(4);
            ByteBuf[] held = new ByteBuf[4 * 2 + 3];
            Set<Long> addresses = new HashSet<Long>();
            // Twice bufferRingSize buffers come out of the region, all different; the region extends only once, so
            // everything beyond that falls back instead of failing.
            for (int i = 0; i < held.length; i++) {
                held[i] = allocator.allocate();
                assertEquals(BUFFER_SIZE, held[i].capacity());
                if (i < 8) {
                    addresses.add(IoUring.memoryAddress(held[i]));
                }
                assertEquals(Math.max(0, i - 7), allocator.fallbackAllocations());
            }
            assertEquals(8, addresses.size());
            for (ByteBuf buffer : held) {
                buffer.release();
            }
        });
    }

    @Test
    public void buffersKeepTheirAddress() throws Exception {
        onEventLoopThread(() -> {
            IoUringRecyclingBufferRingAllocator allocator = newAllocator(4);
            ByteBuf[] held = new ByteBuf[8];
            Set<Long> addresses = new HashSet<Long>();
            // Extend the region first, so the addresses of the extension are expected as well.
            for (int i = 0; i < held.length; i++) {
                held[i] = allocator.allocate();
                addresses.add(IoUring.memoryAddress(held[i]));
            }
            for (int round = 0; round < 8; round++) {
                for (ByteBuf buffer : held) {
                    buffer.release();
                }
                for (int i = 0; i < held.length; i++) {
                    held[i] = allocator.allocate();
                    assertTrue(addresses.contains(IoUring.memoryAddress(held[i])), "buffer moved");
                }
            }
            for (ByteBuf buffer : held) {
                buffer.release();
            }
            assertEquals(8, addresses.size());
            assertEquals(0, allocator.fallbackAllocations());
        });
    }

    @Test
    public void regionIsPageAligned() throws Exception {
        onEventLoopThread(() -> {
            int alignment = Native.PAGE_SIZE;
            IoUringRecyclingBufferRingAllocator allocator = newAllocator(4);
            ByteBuf[] held = new ByteBuf[4];
            for (int i = 0; i < held.length; i++) {
                held[i] = allocator.allocate();
            }
            long base = IoUring.memoryAddress(held[0]);
            assertEquals(0L, base % alignment, "region not aligned to " + alignment);
            for (int i = 0; i < held.length; i++) {
                assertEquals(base + (long) i * BUFFER_SIZE, IoUring.memoryAddress(held[i]),
                        "buffer " + i + " misplaced");
                held[i].release();
            }
            assertEquals(0, allocator.fallbackAllocations());
        });
    }

    @Test
    public void regionIsReleasedWhenTheEventLoopThreadTerminates() throws Exception {
        UnpooledByteBufAllocator allocator = new UnpooledByteBufAllocator(true);
        IoUringRecyclingBufferRingAllocator ringAllocator =
                new IoUringRecyclingBufferRingAllocator(allocator, (short) 4, BUFFER_SIZE);
        Thread loop = new FastThreadLocalThread(() -> ringAllocator.allocate().release());
        loop.start();
        loop.join();
        assertEquals(0, allocator.metric().usedDirectMemory());
        assertEquals(0, ringAllocator.fallbackAllocations());
    }

    @Test
    public void regionIsReleasedWhenTheLastBufferComesBackAfterTheThreadTerminated() throws Exception {
        UnpooledByteBufAllocator allocator = new UnpooledByteBufAllocator(true);
        IoUringRecyclingBufferRingAllocator ringAllocator =
                new IoUringRecyclingBufferRingAllocator(allocator, (short) 4, BUFFER_SIZE);
        BlockingQueue<ByteBuf> inFlight = new ArrayBlockingQueue<ByteBuf>(1);
        Thread loop = new FastThreadLocalThread(() -> inFlight.add(ringAllocator.allocate()));
        loop.start();
        loop.join();

        // The thread that owns the region is gone, but one of its buffers is still in flight.
        assertTrue(allocator.metric().usedDirectMemory() > 0);
        inFlight.take().release();
        assertEquals(0, allocator.metric().usedDirectMemory());
        assertEquals(0, ringAllocator.fallbackAllocations());
    }

    @Test
    public void allocationFromAThreadWithoutFastThreadLocalCleanupFallsBack() throws Exception {
        UnpooledByteBufAllocator allocator = new UnpooledByteBufAllocator(true);
        IoUringRecyclingBufferRingAllocator ringAllocator =
                new IoUringRecyclingBufferRingAllocator(allocator, (short) 4, BUFFER_SIZE);
        BlockingQueue<ByteBuf> allocated = new ArrayBlockingQueue<ByteBuf>(1);
        Thread plain = new Thread(() -> allocated.add(ringAllocator.allocate()));
        plain.start();
        plain.join();

        ByteBuf buffer = allocated.take();
        assertTrue(buffer.isDirect());
        assertEquals(BUFFER_SIZE, buffer.capacity());
        buffer.writeByte('a');
        assertEquals('a', buffer.readByte());
        // No region was reserved for that thread: the only direct memory is the one buffer it was given.
        assertEquals(BUFFER_SIZE, allocator.metric().usedDirectMemory());
        assertEquals(1, ringAllocator.fallbackAllocations());
        buffer.release();
        assertEquals(0, allocator.metric().usedDirectMemory());
    }

    @Test
    public void readsOutOfBufferRing() throws Exception {
        assumeTrue(IoUring.isRegisterBufferRingSupported());
        onEventLoopThread(() -> {
            short entries = 4;
            IoUringRecyclingBufferRingAllocator allocator = newAllocator(entries);
            RingBuffer ringBuffer = Native.createRingBuffer(8, 0);
            try {
                int ringFd = ringBuffer.fd();
                long bufRingAddr = Native.ioUringRegisterBufRing(ringFd, entries, (short) 1, 0);
                assertTrue(bufRingAddr > 0);
                try {
                    IoUringBufferRing bufferRing = new IoUringBufferRing(ringFd,
                            Buffer.wrapMemoryAddressWithNativeOrder(bufRingAddr, Native.ioUringBufRingSize(entries)),
                            entries, 2, (short) 1, false, allocator, false);
                    bufferRing.initialize();

                    Set<Long> addresses = new HashSet<Long>();
                    for (int i = 0; i < 64; i++) {
                        short bid = (short) (i % 2);
                        assertEquals(BUFFER_SIZE, bufferRing.attemptedBytesRead(bid));
                        ByteBuf buffer = bufferRing.useBuffer(bid, BUFFER_SIZE, false);
                        assertEquals(BUFFER_SIZE, buffer.readableBytes());
                        addresses.add(IoUring.memoryAddress(buffer));
                        buffer.release();
                    }
                    assertTrue(bufferRing.isUsable());
                    // Exactly 3 of the 4 buffers of the region serve all 64 reads: the 2 the ring was filled with
                    // plus the one that takes the place of the buffer that is being handed to the caller.
                    assertEquals(3, addresses.size());
                    assertEquals(0, allocator.fallbackAllocations());
                    bufferRing.close();
                } finally {
                    Native.ioUringUnRegisterBufRing(ringFd, bufRingAddr, entries, (short) 1);
                }
            } finally {
                ringBuffer.close();
            }
        });
    }
}
