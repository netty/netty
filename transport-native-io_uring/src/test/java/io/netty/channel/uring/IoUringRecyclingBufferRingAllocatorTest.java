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
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.unix.Buffer;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        // One buffer of headroom: the region holds bufferRingSize + 1 and extends once to twice that.
        return new IoUringRecyclingBufferRingAllocator(
                UnpooledByteBufAllocator.DEFAULT, (short) bufferRingSize, BUFFER_SIZE, 1);
    }

    @Test
    public void rejectsBadArguments() {
        assertThrows(IllegalArgumentException.class, () -> newAllocator(0));
        assertThrows(IllegalArgumentException.class,
                () -> new IoUringRecyclingBufferRingAllocator(UnpooledByteBufAllocator.DEFAULT, (short) 4, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new IoUringRecyclingBufferRingAllocator(UnpooledByteBufAllocator.DEFAULT, (short) 4, 64, 0));
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
    public void bufferNeverReallocates() throws Exception {
        onEventLoopThread(() -> {
            IoUringRecyclingBufferRingAllocator allocator = newAllocator(4);
            ByteBuf buffer = allocator.allocate();
            assertSame(buffer, buffer.capacity(BUFFER_SIZE));
            assertThrows(UnsupportedOperationException.class, () -> buffer.capacity(BUFFER_SIZE / 2));
            assertThrows(UnsupportedOperationException.class, () -> buffer.capacity(BUFFER_SIZE * 2));
            assertEquals(BUFFER_SIZE, buffer.capacity());
            buffer.release();
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
            // Take the headroom buffer too, so the only way to serve the next allocate() is the hand-back queue.
            ByteBuf spare = allocator.allocate();

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
            spare.release();
        });
    }

    @Test
    public void extendsOnceAndThenFallsBack() throws Exception {
        onEventLoopThread(() -> {
            IoUringRecyclingBufferRingAllocator allocator = newAllocator(4);
            // Region of 4 + 1, extended once to 10: ten buffers come out of it, all different, and everything
            // beyond that falls back instead of failing.
            ByteBuf[] held = new ByteBuf[10 + 3];
            Set<Long> addresses = new HashSet<Long>();
            for (int i = 0; i < held.length; i++) {
                held[i] = allocator.allocate();
                assertEquals(BUFFER_SIZE, held[i].capacity());
                if (i < 10) {
                    addresses.add(IoUring.memoryAddress(held[i]));
                }
                assertEquals(Math.max(0, i - 9), allocator.fallbackAllocations());
            }
            assertEquals(10, addresses.size());
            assertEquals(0, allocator.foreignThreadAllocations());
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
    public void fullRingPlusOneInFlightReadFitsTheFirstRegion() throws Exception {
        // IoUringBufferRing.useBuffer(bid) allocates the replacement for the consumed bid before the pipeline
        // sees the slice, so a ring at full size needs bufferRingSize + 1 buffers from the region at once. The
        // default headroom (a quarter of the ring, at least one) must cover that without extending.
        UnpooledByteBufAllocator allocator = new UnpooledByteBufAllocator(true);
        IoUringRecyclingBufferRingAllocator ringAllocator =
                new IoUringRecyclingBufferRingAllocator(allocator, (short) 8, BUFFER_SIZE);
        onEventLoopThread(() -> {
            List<ByteBuf> ring = new ArrayList<ByteBuf>();
            for (int i = 0; i < 8; i++) {
                ring.add(ringAllocator.allocate());
            }
            long regionBytes = allocator.metric().usedDirectMemory();
            ByteBuf inFlight = ringAllocator.allocate();
            assertEquals(regionBytes, allocator.metric().usedDirectMemory(), "the region was extended");
            inFlight.release();
            for (ByteBuf buffer : ring) {
                buffer.release();
            }
        });
        assertEquals(0, ringAllocator.fallbackAllocations());
    }

    @Test
    public void regionIsReleasedWhenTheOwnerReturnsTheLastBufferAfterItsRegionWasHandedBack() throws Exception {
        UnpooledByteBufAllocator allocator = new UnpooledByteBufAllocator(true);
        IoUringRecyclingBufferRingAllocator ringAllocator =
                new IoUringRecyclingBufferRingAllocator(allocator, (short) 4, BUFFER_SIZE);
        onEventLoopThread(() -> {
            ByteBuf buffer = ringAllocator.allocate();
            // What a terminating FastThreadLocalThread does: the region is handed back while a buffer is still out.
            FastThreadLocal.removeAll();
            assertTrue(allocator.metric().usedDirectMemory() > 0);
            // Another FastThreadLocal removed after the region's own one can release that buffer, on the owner
            // thread, after the region was already asked to free itself.
            buffer.release();
            assertEquals(0, allocator.metric().usedDirectMemory());
        });
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
        assertEquals(1, ringAllocator.foreignThreadAllocations());
        assertEquals(0, ringAllocator.fallbackAllocations());
        buffer.release();
        assertEquals(0, allocator.metric().usedDirectMemory());
    }

    /**
     * Owner allocating and releasing while other threads release too, with slices in the mix, small region so the
     * stack runs dry (drain), extension and fallback all happen. Checks the invariants a hand-back or count bug breaks:
     * no buffer is handed out while still live, the payload written at allocation is intact at release (no two owners
     * of one slot), nothing is lost (every region slot comes back) and the region is freed once the thread is gone.
     */
    @Test
    @Timeout(60)
    public void concurrentReleasesKeepEveryInvariant() throws Exception {
        final UnpooledByteBufAllocator allocator = new UnpooledByteBufAllocator(true);
        final IoUringRecyclingBufferRingAllocator ringAllocator =
                new IoUringRecyclingBufferRingAllocator(allocator, (short) 32, BUFFER_SIZE, 1);
        final int releasers = 3;
        final int iterations = 200_000;
        final BlockingQueue<ByteBuf> handoff = new ArrayBlockingQueue<ByteBuf>(32);
        // Identity, not content: ByteBuf.equals()/hashCode() look at the bytes, which change after allocation.
        final Set<ByteBuf> live = Collections.synchronizedSet(
                Collections.newSetFromMap(new IdentityHashMap<ByteBuf, Boolean>()));
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        final ByteBuf poison = Unpooled.EMPTY_BUFFER;

        Thread[] threads = new Thread[releasers];
        for (int i = 0; i < releasers; i++) {
            threads[i] = new Thread(() -> {
                try {
                    for (;;) {
                        ByteBuf buffer = handoff.take();
                        if (buffer == poison) {
                            return;
                        }
                        checkMarkerAndRelease(live, buffer);
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            });
            threads[i].start();
        }

        onEventLoopThread(() -> {
            try {
                SplittableRandom random = new SplittableRandom(42);
                for (int i = 0; i < iterations; i++) {
                    ByteBuf buffer = ringAllocator.allocate();
                    assertEquals(1, buffer.refCnt());
                    assertEquals(0, buffer.readableBytes());
                    assertTrue(live.add(buffer), "handed out while live");
                    buffer.writeLong(i).writeLong(~i);
                    switch (random.nextInt(4)) {
                        case 0:
                            // Owner releases it.
                            checkMarkerAndRelease(live, buffer);
                            break;
                        case 1:
                            // Another thread releases it.
                            handoff.put(buffer);
                            break;
                        case 2:
                            // A slice goes to another thread, the owner keeps the parent and releases it first.
                            ByteBuf slice = buffer.retainedSlice(0, 16);
                            live.remove(buffer);
                            buffer.release();
                            live.add(slice);
                            handoff.put(slice);
                            break;
                        default:
                            // Two references: one released here, one over there, in either order.
                            buffer.retain();
                            if (random.nextBoolean()) {
                                buffer.release();
                                handoff.put(buffer);
                            } else {
                                ByteBuf dup = buffer.retainedDuplicate();
                                buffer.release();
                                live.remove(buffer);
                                live.add(dup);
                                handoff.put(dup);
                                buffer.release();
                            }
                    }
                }
                for (int i = 0; i < releasers; i++) {
                    handoff.put(poison);
                }
                for (Thread t : threads) {
                    t.join();
                }
                assertNull(failure.get());
                assertTrue(live.isEmpty(), "still live: " + live.size());
                // Nothing lost: the whole region (extended once to 2 * 33) comes back out without a new fallback.
                long fallbacks = ringAllocator.fallbackAllocations();
                List<ByteBuf> all = new ArrayList<ByteBuf>();
                Set<Long> addresses = new HashSet<Long>();
                for (int i = 0; i < 66; i++) {
                    ByteBuf buffer = ringAllocator.allocate();
                    all.add(buffer);
                    assertTrue(addresses.add(IoUring.memoryAddress(buffer)), "same address twice");
                    assertEquals(1, buffer.refCnt());
                }
                assertEquals(fallbacks, ringAllocator.fallbackAllocations());
                for (ByteBuf buffer : all) {
                    buffer.release();
                }
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        });
        assertEquals(0, allocator.metric().usedDirectMemory());
        assertEquals(0, ringAllocator.foreignThreadAllocations());
    }

    private static void checkMarkerAndRelease(Set<ByteBuf> live, ByteBuf buffer) {
        if (buffer.readableBytes() >= 16) {
            long marker = buffer.getLong(buffer.readerIndex());
            assertEquals(~marker, buffer.getLong(buffer.readerIndex() + 8), "payload changed under us");
        }
        assertTrue(live.remove(buffer), "released but not live");
        buffer.release();
    }

    /**
     * The owner thread dies while other threads still hold its buffers and release them at random moments: the
     * region must be released exactly once and only after the last of them, every time.
     */
    @Test
    @Timeout(60)
    public void regionSurvivesRacesBetweenTheDyingOwnerAndForeignReleases() throws Exception {
        for (int round = 0; round < 200; round++) {
            final UnpooledByteBufAllocator allocator = new UnpooledByteBufAllocator(true);
            final IoUringRecyclingBufferRingAllocator ringAllocator =
                    new IoUringRecyclingBufferRingAllocator(allocator, (short) 8, BUFFER_SIZE, 8);
            final int inFlight = 12;
            final BlockingQueue<ByteBuf> handoff = new ArrayBlockingQueue<ByteBuf>(inFlight);
            final CountDownLatch start = new CountDownLatch(1);
            Thread[] releasers = new Thread[3];
            final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
            for (int i = 0; i < releasers.length; i++) {
                releasers[i] = new Thread(() -> {
                    try {
                        start.await();
                        for (ByteBuf buffer = handoff.poll(); buffer != null; buffer = handoff.poll()) {
                            if (ThreadLocalRandom.current().nextBoolean()) {
                                Thread.yield();
                            }
                            buffer.release();
                        }
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    }
                });
                releasers[i].start();
            }
            Thread owner = new FastThreadLocalThread(() -> {
                for (int i = 0; i < inFlight; i++) {
                    ByteBuf buffer = ringAllocator.allocate();
                    if ((i & 1) == 0) {
                        // Half of them go over as slices, so the owner's own release of the parent is in the race too.
                        ByteBuf slice = buffer.retainedSlice(0, 8);
                        buffer.release();
                        buffer = slice;
                    }
                    handoff.add(buffer);
                }
                start.countDown();
                // Dies now: FastThreadLocal.removeAll() runs free() while the releasers are working.
            });
            owner.start();
            owner.join();
            for (Thread t : releasers) {
                t.join();
            }
            assertNull(failure.get(), "round " + round);
            assertEquals(0, allocator.metric().usedDirectMemory(), "round " + round);
            assertEquals(0, ringAllocator.fallbackAllocations(), "round " + round);
        }
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
