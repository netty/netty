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

import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.buffer.UnpooledDirectByteBuf;
import io.netty.buffer.UnpooledHeapByteBuf;
import io.netty.channel.unix.Buffer;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /**
     * The address of the slot behind {@code buffer}: a leak-tracked hand-out is a new wrapper every time, so the
     * identity of what {@link IoUringRecyclingBufferRingAllocator#allocate()} returns says nothing.
     */
    private static long slot(ByteBuf buffer) {
        return IoUring.memoryAddress(buffer);
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

            ByteBuf again = allocator.allocate();
            assertEquals(slot(buffer), slot(again));
            assertEquals(1, again.refCnt());
            assertEquals(0, again.readerIndex());
            assertEquals(0, again.writerIndex());
            assertEquals(BUFFER_SIZE, again.writableBytes());
            again.release();
            assertEquals(0, allocator.fallbackAllocations());
        });
    }

    @Test
    public void reusedBufferHasNoMarksOfItsPreviousUse() throws Exception {
        onEventLoopThread(() -> {
            IoUringRecyclingBufferRingAllocator allocator = newAllocator(4);
            ByteBuf buffer = allocator.allocate();
            buffer.writeLong(1).markWriterIndex();
            buffer.readInt();
            buffer.markReaderIndex();
            buffer.release();

            ByteBuf again = allocator.allocate();
            assertEquals(slot(buffer), slot(again));
            again.writeLong(2).writeLong(3);
            // Like a new buffer: without marking first, a reset goes back to 0.
            again.resetReaderIndex();
            assertEquals(0, again.readerIndex());
            again.resetWriterIndex();
            assertEquals(0, again.writerIndex());
            again.release();
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
            assertNotEquals(slot(buffer), slot(other));
            other.release();

            second.release();
            assertEquals(0, buffer.refCnt());
            ByteBuf again = allocator.allocate();
            assertEquals(slot(buffer), slot(again));
            again.release();
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

            ByteBuf again = allocator.allocate();
            assertEquals(slot(buffer), slot(again));
            assertEquals(0, allocator.fallbackAllocations());
            again.release();
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
    public void outOfMemoryWhenUsedUpLeavesTheRegionUsable() throws Exception {
        FailingAllocator allocator = new FailingAllocator();
        IoUringRecyclingBufferRingAllocator ringAllocator =
                new IoUringRecyclingBufferRingAllocator(allocator, (short) 4, BUFFER_SIZE, 1);
        onEventLoopThread(() -> {
            ByteBuf[] held = new ByteBuf[5];
            for (int i = 0; i < held.length; i++) {
                held[i] = ringAllocator.allocate();
            }
            // Used up: extending fails without throwing, and the fallback throws what the allocator throws.
            allocator.fail = true;
            assertThrows(OutOfMemoryError.class, ringAllocator::allocate);
            assertEquals(1, ringAllocator.fallbackAllocations());
            allocator.fail = false;

            long released = slot(held[0]);
            held[0].release();
            held[0] = ringAllocator.allocate();
            assertEquals(released, slot(held[0]));
            for (ByteBuf buffer : held) {
                buffer.release();
            }
        });
    }

    /**
     * Direct buffers from {@link UnpooledByteBufAllocator#DEFAULT}, or an {@link OutOfMemoryError} while
     * {@link #fail} is set.
     */
    private static final class FailingAllocator extends AbstractByteBufAllocator {
        volatile boolean fail;

        FailingAllocator() {
            super(true);
        }

        @Override
        protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
            return UnpooledByteBufAllocator.DEFAULT.heapBuffer(initialCapacity, maxCapacity);
        }

        @Override
        protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
            if (fail) {
                throw new OutOfMemoryError("Direct buffer memory");
            }
            return UnpooledByteBufAllocator.DEFAULT.directBuffer(initialCapacity, maxCapacity);
        }

        @Override
        public boolean isDirectBufferPooled() {
            return false;
        }
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
        UnpooledByteBufAllocator allocator = new UnpooledByteBufAllocator(true);
        assertConcurrentReleasesKeepEveryInvariant(allocator,
                new IoUringRecyclingBufferRingAllocator(allocator, (short) 32, BUFFER_SIZE, 1));
    }

    private static void assertConcurrentReleasesKeepEveryInvariant(
            UnpooledByteBufAllocator allocator, IoUringRecyclingBufferRingAllocator ringAllocator) throws Exception {
        final int releasers = 3;
        final int iterations = 200_000;
        final BlockingQueue<ByteBuf> handoff = new ArrayBlockingQueue<ByteBuf>(32);
        // By address, as a leak-tracked hand-out is a new wrapper of the same slot every time.
        final Set<Long> live = Collections.synchronizedSet(new HashSet<Long>());
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
                    try {
                        assertEquals(1, buffer.refCnt());
                        assertEquals(0, buffer.readableBytes());
                        assertTrue(live.add(slot(buffer)), "handed out while live");
                    } catch (Throwable t) {
                        buffer.release();
                        throw t;
                    }
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
                            buffer.release();
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
                try {
                    for (int i = 0; i < 66; i++) {
                        ByteBuf buffer = ringAllocator.allocate();
                        all.add(buffer);
                        assertTrue(addresses.add(IoUring.memoryAddress(buffer)), "same address twice");
                        assertEquals(1, buffer.refCnt());
                    }
                    assertEquals(fallbacks, ringAllocator.fallbackAllocations());
                } finally {
                    for (ByteBuf buffer : all) {
                        buffer.release();
                    }
                }
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        });
        assertEquals(0, allocator.metric().usedDirectMemory());
        assertEquals(0, ringAllocator.foreignThreadAllocations());
    }

    private static void checkMarkerAndRelease(Set<Long> live, ByteBuf buffer) {
        try {
            if (buffer.readableBytes() >= 16) {
                long marker = buffer.getLong(buffer.readerIndex());
                assertEquals(~marker, buffer.getLong(buffer.readerIndex() + 8), "payload changed under us");
            }
            // Out of the set before the release that may bring the slot home, so the owner can hand it out again.
            assertTrue(live.remove(slot(buffer)), "released but not live");
        } finally {
            buffer.release();
        }
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
    public void trackedHandOutIsALeakAwareWrapperOfItsSlot() throws Exception {
        UnpooledByteBufAllocator memory = new UnpooledByteBufAllocator(true);
        IoUringRecyclingBufferRingAllocator allocator =
                new IoUringRecyclingBufferRingAllocator(memory, (short) 1, BUFFER_SIZE, 1);
        try (LeakCollector leaks = new LeakCollector(ResourceLeakDetector.Level.PARANOID, TEST_FRAMES)) {
            onEventLoopThread(() -> {
                ByteBuf buffer = allocator.allocate();
                ByteBuf wrapped = buffer.unwrap();
                try {
                    assertNotNull(wrapped, "not leak-aware");
                    assertNotSame(buffer, wrapped);
                    assertEquals(slot(wrapped), slot(buffer));
                } finally {
                    buffer.release();
                }
                ByteBuf again = allocator.allocate();
                try {
                    assertNotSame(buffer, again);
                    assertSame(wrapped, again.unwrap(), "the released slot did not go home");
                } finally {
                    again.release();
                }
            });
            assertEquals(0, memory.metric().usedDirectMemory());
            assertEquals(0, leaks.await(1, 20), leaks.records::toString);
        }
    }

    @Test
    public void trackedBuffersReleasedOnAndOffTheOwnerAreNotReported() throws Exception {
        UnpooledByteBufAllocator memory = new UnpooledByteBufAllocator(true);
        IoUringRecyclingBufferRingAllocator allocator =
                new IoUringRecyclingBufferRingAllocator(memory, (short) 1, BUFFER_SIZE, 1);
        try (LeakCollector leaks = new LeakCollector(ResourceLeakDetector.Level.PARANOID, TEST_FRAMES)) {
            onEventLoopThread(() -> assertReleasedBuffersComeBack(allocator));
            // The owner is gone and nothing references its region: a tracker left open is reported now.
            assertEquals(0, memory.metric().usedDirectMemory());
            assertEquals(0, leaks.await(1, 20), leaks.records::toString);
        }
    }

    @Test
    public void trackedBuffersReleasedAfterTheOwnerTerminatedAreNotReported() throws Exception {
        UnpooledByteBufAllocator memory = new UnpooledByteBufAllocator(true);
        IoUringRecyclingBufferRingAllocator allocator =
                new IoUringRecyclingBufferRingAllocator(memory, (short) 4, BUFFER_SIZE, 1);
        try (LeakCollector leaks = new LeakCollector(ResourceLeakDetector.Level.PARANOID, TEST_FRAMES)) {
            BlockingQueue<ByteBuf> inFlight = new ArrayBlockingQueue<ByteBuf>(1);
            Thread loop = new FastThreadLocalThread(() -> inFlight.add(allocator.allocate()));
            loop.start();
            loop.join();
            inFlight.take().release();
            assertEquals(0, memory.metric().usedDirectMemory());

            onEventLoopThread(() -> {
                ByteBuf buffer = allocator.allocate();
                try {
                    FastThreadLocal.removeAll();
                } finally {
                    buffer.release();
                }
                assertEquals(0, memory.metric().usedDirectMemory());
            });
            assertEquals(0, leaks.await(1, 20), leaks.records::toString);
        }
    }

    @Test
    @Timeout(60)
    public void concurrentReleasesOfSampledBuffersKeepEveryInvariant() throws Exception {
        // At SIMPLE only a sample of the hand-outs is tracked, so a slot goes out tracked and untracked in turn.
        UnpooledByteBufAllocator memory = new UnpooledByteBufAllocator(true);
        IoUringRecyclingBufferRingAllocator allocator =
                new IoUringRecyclingBufferRingAllocator(memory, (short) 32, BUFFER_SIZE, 1);
        try (LeakCollector leaks = new LeakCollector(ResourceLeakDetector.Level.SIMPLE, TEST_FRAMES)) {
            assertConcurrentReleasesKeepEveryInvariant(memory, allocator);
            // So that every buffer allocated while waiting has the detector look for collected ones.
            ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
            assertEquals(0, leaks.await(1, 20), leaks.records::toString);
        }
    }

    @Test
    public void untrackedBufferStaysWithTheRegionWhenLeakDetectionIsDisabled() throws Exception {
        IoUringRecyclingBufferRingAllocator allocator =
                new IoUringRecyclingBufferRingAllocator(new UntrackedAllocator(), (short) 1, BUFFER_SIZE, 1);
        try (LeakCollector ignored = new LeakCollector(ResourceLeakDetector.Level.DISABLED, TEST_FRAMES)) {
            onEventLoopThread(() -> {
                // Nothing is wrapped: the very same buffer comes back.
                ByteBuf buffer = allocator.allocate();
                buffer.release();
                assertSame(buffer, allocator.allocate());
                buffer.release();
                assertReleasedBuffersComeBack(allocator);

                WeakReference<ByteBuf> dropped = allocateAndDropASlice(allocator);
                for (int i = 0; i < 5; i++) {
                    System.gc();
                }
                assertNotNull(dropped.get(), "the region let go of an untracked buffer");
            });
        }
    }

    /** What the creation stack of every buffer this class allocates contains. */
    private static final String TEST_FRAMES = IoUringRecyclingBufferRingAllocatorTest.class.getName();

    /**
     * Sets the leak detection level and a {@link ByteBufUtil#setLeakListener leak listener} that collects the
     * records of every leak containing {@code marker}, until closed.
     */
    private static final class LeakCollector implements AutoCloseable {
        final List<String> records = Collections.synchronizedList(new ArrayList<String>());
        private final ResourceLeakDetector.Level previous = ResourceLeakDetector.getLevel();

        LeakCollector(ResourceLeakDetector.Level level, String marker) {
            ResourceLeakDetector.setLevel(level);
            ByteBufUtil.setLeakListener((resourceType, records) -> {
                if (records.contains(marker)) {
                    this.records.add(records);
                }
            });
        }

        /**
         * Collects garbage and has the detector look for collected buffers until {@code expected} leaks were
         * reported or {@code rounds} are over, and returns how many were.
         */
        int await(int expected, int rounds) {
            for (int round = 0; round < rounds && records.size() < expected; round++) {
                System.gc();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
                // The detector reports what was collected when it tracks the next buffer.
                UnpooledByteBufAllocator.DEFAULT.directBuffer(1).release();
            }
            return records.size();
        }

        @Override
        public void close() {
            // There is no getter for the listener that was set before: none is, by default.
            ByteBufUtil.setLeakListener(null);
            ResourceLeakDetector.setLevel(previous);
        }
    }

    /**
     * Owner release, then a release from another thread that the owner can only get back through the hand-back
     * queue: both come back as the same slot. Needs a region of two.
     */
    private static void assertReleasedBuffersComeBack(IoUringRecyclingBufferRingAllocator allocator) {
        ByteBuf buffer = allocator.allocate();
        long address = slot(buffer);
        buffer.writeByte('a');
        buffer.release();
        ByteBuf again = allocator.allocate();
        ByteBuf spare = null;
        try {
            assertEquals(address, slot(again));
            // With both out, the next allocate() is served by the hand-back queue or not at all.
            spare = allocator.allocate();
            Thread releaser = new Thread(again::release);
            again = null;
            releaser.start();
            try {
                releaser.join();
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
            ByteBuf third = allocator.allocate();
            try {
                assertEquals(address, slot(third));
                third.touch("hint").retain().release();
            } finally {
                third.release();
            }
            assertEquals(0, allocator.fallbackAllocations());
        } finally {
            if (again != null) {
                again.release();
            }
            if (spare != null) {
                spare.release();
            }
        }
    }

    /**
     * What IoUringBufferRing hands the pipeline is a slice of the buffer, and the pipeline drops it here. Returns
     * only a weak reference to the buffer, so nothing on the caller's stack keeps it alive.
     */
    private static WeakReference<ByteBuf> allocateAndDropASlice(IoUringRecyclingBufferRingAllocator allocator) {
        ByteBuf buffer = allocator.allocate();
        buffer.writeByte('a');
        buffer.retainedSlice(0, 1);
        buffer.release();
        return new WeakReference<ByteBuf>(buffer);
    }

    /**
     * Direct memory that is not leak-tracked itself, so that a region an unreleased buffer holds on to, which is
     * never released, is freed by the garbage collector without being reported.
     */
    private static final class UntrackedAllocator extends AbstractByteBufAllocator {
        UntrackedAllocator() {
            super(true);
        }

        @Override
        protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
            return new UnpooledHeapByteBuf(this, initialCapacity, maxCapacity);
        }

        @Override
        protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
            return new UnpooledDirectByteBuf(this, initialCapacity, maxCapacity);
        }

        @Override
        public boolean isDirectBufferPooled() {
            return false;
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
