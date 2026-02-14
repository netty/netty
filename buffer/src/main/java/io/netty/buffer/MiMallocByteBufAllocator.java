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
package io.netty.buffer;

import io.netty.util.ByteProcessor;
import io.netty.util.CharsetUtil;
import io.netty.util.IllegalReferenceCountException;
import io.netty.util.NettyRuntime;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;
import io.netty.util.internal.MathUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.UnstableApi;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.StampedLock;
import static io.netty.buffer.MiMallocByteBufAllocator.CollectType.ABANDON;
import static io.netty.buffer.MiMallocByteBufAllocator.CollectType.FORCE;
import static io.netty.buffer.MiMallocByteBufAllocator.CollectType.NORMAL;
import static io.netty.buffer.MiMallocByteBufAllocator.DelayedFlag.DELAYED_FREEING;
import static io.netty.buffer.MiMallocByteBufAllocator.DelayedFlag.NEVER_DELAYED_FREE;
import static io.netty.buffer.MiMallocByteBufAllocator.DelayedFlag.NO_DELAYED_FREE;
import static io.netty.buffer.MiMallocByteBufAllocator.DelayedFlag.USE_DELAYED_FREE;
import static io.netty.buffer.MiMallocByteBufAllocator.SegmentKind.SEGMENT_HUGE;
import static io.netty.buffer.MiMallocByteBufAllocator.SegmentKind.SEGMENT_NORMAL;

@UnstableApi
final class MiMallocByteBufAllocator {

    // 8 bytes
    private static final int WORD_SIZE = 8;
    private static final int WORD_SIZE_MASK = WORD_SIZE - 1;

    // 64 KiB
    private static final int SEGMENT_SLICE_SHIFT = 16;
    // 4 MiB
    private static final int DEFAULT_SEGMENT_SHIFT = SEGMENT_SLICE_SHIFT + 6;
    // 4 MiB
    private static final int DEFAULT_SEGMENT_SIZE = 1 << DEFAULT_SEGMENT_SHIFT;

    // 64 KiB
    private static final int SMALL_PAGE_SHIFT = SEGMENT_SLICE_SHIFT;
    // 512 KiB
    private static final int MEDIUM_PAGE_SHIFT = 3 + SMALL_PAGE_SHIFT;

    // 4 KiB
    private static final int DEFAULT_OS_PAGE_SHIFT = 12;

    // Max size for fast path allocation: 1 KiB
    private static final int PAGES_FREE_DIRECT_SIZE_MAX = 1024;
    // Fast path allocation array size
    private static final int PAGES_FREE_DIRECT_ARRAY_SIZE = 128;

    // 64 KiB
    private static final int SEGMENT_SLICE_SIZE = 1 << SEGMENT_SLICE_SHIFT;

    // 64
    private static final int DEFAULT_SLICE_COUNT = DEFAULT_SEGMENT_SIZE / SEGMENT_SLICE_SIZE;

    // Small page size: 64 KiB, for small objects: [1 byte, 8 KiB]
    private static final int SMALL_PAGE_SIZE = 1 << SMALL_PAGE_SHIFT;
    // Medium page size: 512 KiB, for medium objects: (8 KiB, MEDIUM_BLOCK_SIZE_MAX KiB]
    private static final int MEDIUM_PAGE_SIZE = 1 << MEDIUM_PAGE_SHIFT;

    // 4 KiB
    private static final int DEFAULT_OS_PAGE_SIZE = 1 << DEFAULT_OS_PAGE_SHIFT;

    // 8 KiB
    private static final int SMALL_BLOCK_SIZE_MAX = SMALL_PAGE_SIZE >> 3;

    // 128 KiB
    private static final int MEDIUM_BLOCK_SIZE_MAX = MEDIUM_PAGE_SIZE >> 2;
    private static final int MEDIUM_BLOCK_WORD_SIZE_MAX = MEDIUM_BLOCK_SIZE_MAX >> 3;

    // DEFAULT_SEGMENT_SIZE / 2
    private static final int LARGE_BLOCK_SIZE_MAX = DEFAULT_SEGMENT_SIZE >> 1;

    private static final int SPAN_QUEUE_MAX_INDEX = 19;

    private static final boolean PAGE_USE_BEST_FIT_SEARCH = SystemPropertyUtil.getBoolean(
            "io.netty.allocator.mimalloc.pageUseBestFitSearch", true);

    private static final int MAX_PAGE_CANDIDATE_SEARCH = 4;

    private static final Page EMPTY_PAGE = new Page();
    // 4 KiB
    private static final int PAGE_MAX_EXTEND_SIZE = 4 * 1024;

    private static final int PAGE_MIN_EXTEND_BLOCKS = 4;

    private static final int PAGE_QUEUE_BIN_LARGE_INDEX = 53;

    private static final int PAGE_QUEUE_BIN_FULL_INDEX = PAGE_QUEUE_BIN_LARGE_INDEX + 1;

    private final FastThreadLocal<LocalHeap> THREAD_LOCAL_HEAP;

    private final ConcurrentLinkedDeque<Segment> abandonedSegmentDeque;
    // Count of abandoned segments for this allocator.
    private final AtomicLong abandonedSegmentCount = new AtomicLong();

    private final ChunkAllocator chunkAllocator;

    private static final Object RECLAIMED_SEGMENT_FLAG = new Object();

    private static final int KiB = 1024;
    private static final int MiB = KiB * KiB;

    private static final byte DEFAULT_PAGE_RETIRE_CYCLES_HIGH = 16;
    private static final byte DEFAULT_PAGE_RETIRE_CYCLES_LOW = 4;
    private static final byte DEFAULT_PAGE_RETIRE_EXPIRE_INIT = 7;

    // Collect heaps every N(default 10000) generic allocation calls.
    private static final int HEAP_OPTION_GENERIC_COLLECT = 10000;

    private static final long DEFAULT_RESERVED_SEGMENT_RETIRE_NANO = TimeUnit.SECONDS.toNanos(60);

    private final AtomicLong usedMemory = new AtomicLong();

    private final SharedHeapWrap[] sharedHeapWraps;

    private static final int MAX_SHARED_HEAP_WRAPS_LENGTH =
            MathUtil.safeFindNextPositivePowerOfTwo(NettyRuntime.availableProcessors() * 2);

    private final AtomicInteger heapsScanLength;

    MiMallocByteBufAllocator(ChunkAllocator chunkAllocator) {
        this.chunkAllocator = chunkAllocator;
        this.abandonedSegmentDeque = new ConcurrentLinkedDeque<Segment>();
        this.THREAD_LOCAL_HEAP = new FastThreadLocal<LocalHeap>() {
            @Override
            protected LocalHeap initialValue() {
                return new LocalHeap(MiMallocByteBufAllocator.this, null);
            }

            @Override
            protected void onRemoval(LocalHeap heap) {
                // Cleanup if needed
                heap.heapCollect(ABANDON);
            }
        };
        this.sharedHeapWraps = new SharedHeapWrap[MAX_SHARED_HEAP_WRAPS_LENGTH];
        for (int i = 0; i < sharedHeapWraps.length; i++) {
            sharedHeapWraps[i] = new SharedHeapWrap();
        }
        // Init the first heap.
        sharedHeapWraps[0].heap = new LocalHeap(this, sharedHeapWraps[0].lock);
        this.heapsScanLength = new AtomicInteger(1);
    }

    @SuppressWarnings("FinalizeDeclaration")
    @Override
    protected void finalize() throws Throwable {
        try {
            freeAllSharedSegments();
            freeAllAbandonedSegments();
        } finally {
            super.finalize();
        }
    }

    static final class SharedHeapWrap {
        private final StampedLock lock = new StampedLock();
        private LocalHeap heap;
    }

    private void freeAllAbandonedSegments() {
        Segment segment;
        while ((segment = this.abandonedSegmentDeque.poll()) != null) {
            this.abandonedSegmentCount.decrementAndGet();
            segment.deallocate();
        }
    }

    private void freeAllSharedSegments() {
        StampedLock lock;
        long lockStamp;
        LocalHeap heap;
        for (SharedHeapWrap sharedHeapWrap : this.sharedHeapWraps) {
            lock = sharedHeapWrap.lock;
            lockStamp = lock.writeLock();
            heap = sharedHeapWrap.heap;
            if (heap != null) {
                try {
                    heap.heapCollect(ABANDON);
                } finally {
                    lock.unlockWrite(lockStamp);
                }
            }
        }
    }

    static final class SegmentTld {
        int segmentsCount;        // current number of segments;
        int segmentsPeakCount;   // peak number of segments
        long segmentsCurrentSize; // current size of all segments
        long segmentsPeakSize;    // peak size of all segments
        int normalSegmentsCount;
        private final SpanQueue[] spanQueues = new SpanQueue[] {
            new SpanQueue(1, 0), // placeholder, not used.
            new SpanQueue(1, 1), new SpanQueue(2, 2),
            new SpanQueue(3, 3), new SpanQueue(4, 4),
            new SpanQueue(5, 5), new SpanQueue(6, 6),
            new SpanQueue(7, 7), new SpanQueue(10, 8),
            new SpanQueue(12, 9), new SpanQueue(14, 10),
            new SpanQueue(16, 11), new SpanQueue(20, 12),
            new SpanQueue(24, 13), new SpanQueue(28, 14),
            new SpanQueue(32, 15), new SpanQueue(40, 16),
            new SpanQueue(48, 17), new SpanQueue(56, 18),
            new SpanQueue(64, SPAN_QUEUE_MAX_INDEX)
        };
    }

    enum CollectType {
        NORMAL,
        FORCE,
        ABANDON
    }

    static final class LocalHeap {
        final SegmentTld segmentTld;
        int pageCount; // total number of pages in the `pages` queues.
        int pageRetiredMin; // smallest retired index (retired pages are fully free, but still in the page queues)
        int pageRetiredMax; // largest retired index into the `pages` array.
        int genericCount; // how often is `allocateGeneric` called
        int genericCollectCount; // how often is `allocateGeneric` called without collecting.
        final Page[] pagesFreeDirect;
        // 8 bytes to 2 MiB.
        final PageQueue[] pageQueues;
        final MiMallocByteBufAllocator allocator;
        final AtomicReference<Block> threadDelayedFreeList;
        private static final byte VISIT_TYPE_PAGE_MARK = 0;
        private static final byte VISIT_TYPE_PAGE_COLLECT = 1;

        private static final int MAX_BLOCK_QUEUE_SIZE = 1024;
        private final ArrayDeque<Block> blockDeque;
        private Segment reservedNormalSegment;
        private long reservedNormalSegmentNano;
        private final StampedLock sharedLock;

        LocalHeap(MiMallocByteBufAllocator allocator, StampedLock sharedLock) {
            segmentTld = new SegmentTld();
            pagesFreeDirect = new Page[PAGES_FREE_DIRECT_ARRAY_SIZE + 1];
            Arrays.fill(pagesFreeDirect, EMPTY_PAGE);
            this.allocator = allocator;
            this.threadDelayedFreeList = new AtomicReference<>();
            this.blockDeque = new ArrayDeque<Block>(32);
            this.sharedLock = sharedLock;
            pageQueues = new PageQueue[] {
                    new PageQueue(1, 0), // placeholder, not used.
                    new PageQueue(1, 1), new PageQueue(2, 2),
                    new PageQueue(3, 3), new PageQueue(4, 4),
                    new PageQueue(5, 5), new PageQueue(6, 6),
                    new PageQueue(7, 7), new PageQueue(8, 8),
                    new PageQueue(10, 9), new PageQueue(12, 10),
                    new PageQueue(14, 11), new PageQueue(16, 12),
                    new PageQueue(20, 13), new PageQueue(24, 14),
                    new PageQueue(28, 15), new PageQueue(32, 16),
                    new PageQueue(40, 17), new PageQueue(48, 18),
                    new PageQueue(56, 19), new PageQueue(64, 20),
                    new PageQueue(80, 21), new PageQueue(96, 22),
                    new PageQueue(112, 23), new PageQueue(128, 24),
                    new PageQueue(160, 25), new PageQueue(192, 26),
                    new PageQueue(224, 27), new PageQueue(256, 28),
                    new PageQueue(320, 29), new PageQueue(384, 30),
                    new PageQueue(448, 31), new PageQueue(512, 32),
                    new PageQueue(640, 33), new PageQueue(768, 34),
                    new PageQueue(896, 35), new PageQueue(1024, 36),
                    new PageQueue(1280, 37), new PageQueue(1536, 38),
                    new PageQueue(1792, 39), new PageQueue(2048, 40),
                    new PageQueue(2560, 41), new PageQueue(3072, 42),
                    new PageQueue(3584, 43), new PageQueue(4096, 44),
                    new PageQueue(5120, 45), new PageQueue(6144, 46),
                    new PageQueue(7168, 47), new PageQueue(8192, 48), // 64KiB
                    new PageQueue(10240, 49), new PageQueue(12288, 50),
                    new PageQueue(14336, 51), new PageQueue(16384, 52), //128KiB
                    // Large queue
                    new PageQueue(MEDIUM_BLOCK_WORD_SIZE_MAX + 1, PAGE_QUEUE_BIN_LARGE_INDEX),
                    // Full queue
                    new PageQueue(MEDIUM_BLOCK_WORD_SIZE_MAX + 2, PAGE_QUEUE_BIN_FULL_INDEX)
            };
        }

        private void heapPageCollect(PageQueue pq, Page page, CollectType collectType) {
            boolean isForce = isForceCollect(collectType);
            page.pageFreeCollect(isForce);
            if (page.usedBlocks == 0) {
                // No more used blocks, free the page.
                // Note: this will free retired pages.
                pageFree(page, pq);
            } else if (collectType == ABANDON) {
                // There are still used blocks, but the thread is done, abandon the page.
                page.pageAbandon(pq, this);
            }
        }

        private boolean isForceCollect(CollectType collectType) {
            return collectType == FORCE || collectType == ABANDON;
        }

        private void heapCollect(CollectType collectType) {
            // If during abandoning, mark all pages to no longer add to the delayed-free list
            if (collectType == ABANDON) {
                heapVisitPages(collectType, VISIT_TYPE_PAGE_MARK);
                this.blockDeque.clear();
                freeReservedSegment();
            }
            // Free all current thread's delayed blocks.
            // If during abandoning, after this, there are no more thread-delayed references into the pages.
            heapDelayedFreeAll();
            // Collect retired pages.
            // This will update `pageRetiredMin` & `pageRetiredMax`, which helps `heapCollectRetired()` efficiency.
            heapCollectRetired(isForceCollect(collectType));
            // Collect all pages owned by this thread.
            heapVisitPages(collectType, VISIT_TYPE_PAGE_COLLECT);
            // Collect abandoned segments.
            boolean isCollectAllAbandoned = collectType == FORCE;
            abandonedCollect(isCollectAllAbandoned);
        }

        private void freeReservedSegment() {
            if (this.reservedNormalSegment != null) {
                segmentOsFree(this.reservedNormalSegment);
            }
            this.reservedNormalSegment = null;
        }

        // Collect abandoned segments
        private void abandonedCollect(boolean isCollectAll) {
            Segment segment;
            long abandonedSegmentCount = this.allocator.abandonedSegmentCount.get();
            long maxTries = isCollectAll ? abandonedSegmentCount :
                    Math.min(1024, abandonedSegmentCount); // Limit latency
            while (maxTries-- > 0 && (segment = this.allocator.abandonedSegmentDeque.poll()) != null) {
                this.allocator.abandonedSegmentCount.decrementAndGet();
                segmentCheckFree(segment, 0, 0); // try to free up pages (due to concurrent frees)
                if (segment.usedPages == 0) {
                    // Free the segment (by forced reclaim) to make it available to other threads.
                    segmentReclaim(segment, 0, false);
                } else {
                    // Otherwise, push on the visited list.
                    segmentMarkAbandoned(segment);
                }
            }
        }

        // Free retired pages: we don't need to look at the entire queues,
        // since we only retire pages that are at the head position in a queue.
        private void heapCollectRetired(boolean isForce) {
            int min = PAGE_QUEUE_BIN_FULL_INDEX;
            int max = 0;
            for (int bin = this.pageRetiredMin; bin <= this.pageRetiredMax; bin++) {
                PageQueue pq = this.pageQueues[bin];
                Page page = pq.firstPage;
                if (page != null && page.retireExpire != 0) {
                    if (page.usedBlocks == 0) {
                        page.retireExpire--;
                        if (isForce || page.retireExpire == 0) {
                            pageFree(pq.firstPage, pq);
                        } else {
                            // keep retired, update min/max
                            if (bin < min) {
                                min = bin;
                            }
                            if (bin > max) {
                                max = bin;
                            }
                        }
                    } else {
                        // disable retirement
                        page.retireExpire = 0;
                    }
                }
            }
            this.pageRetiredMin = min;
            this.pageRetiredMax = max;
        }

        private void heapDelayedFreeAll() {
            while (!heapDelayedFreePartial()) {
                Thread.yield();
            }
        }

        // Returns true if all delayed frees were processed
        private boolean heapDelayedFreePartial() {
            // Take over the list (note: no atomic exchange since it is often NULL)
            Block block = this.threadDelayedFreeList.get();
            while (block != null && !this.threadDelayedFreeList.compareAndSet(block, null)) {
                block = this.threadDelayedFreeList.get();
            }
            boolean allFreed = true;
            // And free them all.
            while (block != null) {
                Block next = block.nextBlock;
                // Use internal free instead of regular one to keep stats correct.
                if (!freeDelayedBlock(block)) {
                    // We might already start delayed freeing while another thread has not yet
                    // reset the delayed_freeing flag, in that case,
                    // delay it further by reinserting the current block into the delayed free list.
                    allFreed = false;
                    Block current;
                    do {
                        current = this.threadDelayedFreeList.get();
                        block.nextBlock = current;
                    } while (!this.threadDelayedFreeList.compareAndSet(current, block));
                }
                block = next;
            }
            return allFreed;
        }

        /**
         * @return true if successful.
         */
        private boolean freeDelayedBlock(Block block) {
            Page page = block.page;
            // Clear the no-delayed flag so delayed freeing is used again for this page.
            // This must be done before collecting the free lists on this page -- otherwise
            // some blocks may end up in the page `thread_free` list with no blocks in the
            // heap `thread_delayed_free` list which may cause the page to be never freed!
            // (it would only be freed if we happen to scan it in `pageQueueFindFreeEx`)
            if (!pageTryUseDelayedFree(page, USE_DELAYED_FREE, false)) {
                return false;
            }
            // Collect all other non-local frees (move from `thread_free` to `free`) to ensure up-to-date `used` count.
            page.pageFreeCollect(false);
            // Free the block (possibly freeing the page as well since `usedBlocks` is updated)
            page.freeBlockLocal(block, true /* check for a full page */, this);
            return true;
        }

        // Visit all pages in a heap.
        private void heapVisitPages(CollectType collectType, byte visitType) {
            if (this.pageCount == 0) {
                return;
            }
            boolean isMarkPage = visitType == VISIT_TYPE_PAGE_MARK;
            // No need to collect the full page queue, except for the page mark or abandoning.
            int maxBinIndex = isMarkPage || collectType == ABANDON ?
                    PAGE_QUEUE_BIN_FULL_INDEX : PAGE_QUEUE_BIN_FULL_INDEX - 1;
            for (int i = 0; i <= maxBinIndex; i++) {
                PageQueue pq = this.pageQueues[i];
                Page page = pq.firstPage;
                while (page != null) {
                    Page next = page.nextPage; // Save next in case the page gets removed from the queue.
                    if (isMarkPage) {
                        pageUseDelayedFree(page, NEVER_DELAYED_FREE, false);
                    } else {
                        heapPageCollect(pq, page, collectType);
                    }
                    page = next;
                }
            }
        }

        // Allocate a page.
        private Page findPage(int size) {
            // Large or Huge allocation.
            if (size > MEDIUM_BLOCK_SIZE_MAX) {
                return largeOrHugePageAlloc(size);
            } else {
                // Otherwise, find a page with free blocks in our size segregated queues.
                return findFreePage(size);
            }
        }

        private Page createHugePage(int size) {
            // Allocate the segment.
            AbstractByteBuf buf = this.allocator.newChunk(size);
            if (buf == null) {
                return null; // Signal OOM
            }
            // huge segment only needs 1 slice.
            Segment segment = new Segment(this.allocator, size, 1, SEGMENT_HUGE, buf, this);
            segmentsTrackSize(segment.segmentSize);
            // Allocate a huge page which spans the entire segment.
            Page page = segmentHugeSpanAllocate(segment, size);
            // A fresh page was found, initialize it.
            page.reservedBlocks = 1;
            page.retireExpire = 0;
            page.freeList = new Block(page, page.blockSize, page.adjustment, null);
            page.capacityBlocks = 1;
            return page;
        }

        // Large and huge page allocation.
        // Huge pages contain just one block, and the segment contains just that page (as `SEGMENT_HUGE`).
        private Page largeOrHugePageAlloc(int size) {
            int blockSize = getGoodOsAllocSize(size);
            boolean isHuge = blockSize > LARGE_BLOCK_SIZE_MAX;
            PageQueue pq = isHuge ? null : pageQueue(blockSize);
            return pageFreshAlloc(pq, blockSize);
        }

        private PageQueue pageQueue(int size) {
            return this.pageQueues[pageQueueIndex(size)];
        }

        private Page findFreePage(int size) {
            PageQueue pq = pageQueue(size);
            if (pq.firstPage != null) {
                pq.firstPage.pageFreeCollect(false);
                if (pq.firstPage.immediateAvailable()) {
                    // disable retirement
                    pq.firstPage.retireExpire = 0;
                    return pq.firstPage; // fast path
                }
            }
            return pageQueueFindFreeEx(pq, true);
        }

        private Page pageQueueFindFreeEx(PageQueue pq, boolean firstTry) {
            int candidateCount = 0;
            Page pageCandidate = null;
            Page page = pq.firstPage;
            // Search through the pages in "next fit" order.
            while (page != null) {
                Page next = page.nextPage;
                candidateCount++;
                page.pageFreeCollect(false);
                if (PAGE_USE_BEST_FIT_SEARCH) {
                    // Search up to N pages for the best candidate
                    boolean immediateAvailable = page.immediateAvailable();
                    // If the page is completely full, move it to the `pages_full` queue,
                    // so we don't visit long-lived pages too often.
                    if (!immediateAvailable && !page.isPageExpandable()) {
                        pageToFull(page, pq);
                    } else {
                        // The page has free space, make it a candidate.
                        // We prefer non-expandable pages with high usage as candidates
                        // (to increase the chance of freeing up pages).
                        if (pageCandidate == null) {
                            pageCandidate = page;
                            candidateCount = 0;
                        } else if (page.usedBlocks >= pageCandidate.usedBlocks && !page.isMostlyUsed()
                                && !page.isPageExpandable()) {
                            // Prefer to reuse fuller pages (in the hope the less used page gets freed).
                            pageCandidate = page;
                        }
                        // If we find a non-expandable candidate, or searched for N pages,
                        // return with the best candidate.
                        if (immediateAvailable || candidateCount > MAX_PAGE_CANDIDATE_SEARCH) {
                            break;
                        }
                    }
                } else {
                    // First-fit algorithm
                    // If the page contains free blocks
                    if (page.immediateAvailable() || page.isPageExpandable()) {
                        break;  // Pick this one
                    }
                    // If the page is completely full, move it to the full
                    // queue so we don't visit long-lived pages too often.
                    assert !page.isInFull && !page.immediateAvailable();
                    pageToFull(page, pq);
                }
                page = next;
            }
            // Set the page to the best candidate.
            if (pageCandidate != null) {
                page = pageCandidate;
            }
            if (page != null) {
                if (!page.immediateAvailable()) {
                    if (!pageExtendFree(page)) {
                        page = null; // Failed to extend.
                    }
                }
            }
            if (page == null) {
                heapCollectRetired(false); // Perhaps make a page available.
                page = pageFresh(pq);
                if (page == null && firstTry) {
                    // out-of-memory or an abandoned page with free blocks was reclaimed, try once again.
                    page = pageQueueFindFreeEx(pq, false);
                }
            } else {
                // Move the page to the front of the queue.
                pageQueueMoveToFront(pq, page);
                // disable retirement
                page.retireExpire = 0;
            }
            return page;
        }

        // Extend the capacity (up to reserved) by initializing a free list.
        // We do at most `MAX_EXTEND` to avoid creating too many `Block` instances.
        private boolean pageExtendFree(Page page) {
            if (page.freeList != null) {
                return true;
            }
            if (page.capacityBlocks >= page.reservedBlocks) {
                return false;
            }
            // Calculate the extend count.
            int bSize = page.blockSize;
            int extend = page.reservedBlocks - page.capacityBlocks;
            int maxExtend = bSize >= PAGE_MAX_EXTEND_SIZE ? PAGE_MIN_EXTEND_BLOCKS : PAGE_MAX_EXTEND_SIZE / bSize;
            if (maxExtend < PAGE_MIN_EXTEND_BLOCKS) {
                maxExtend = PAGE_MIN_EXTEND_BLOCKS;
            }
            if (extend > maxExtend) {
                extend = maxExtend;
            }
            pageFreeListExtend(page, bSize, extend);
            page.capacityBlocks += extend;
            return true;
        }

        private Block getBlock(Page page, int blockBytes, int adjustment) {
            Block block;
            if (blockDeque.isEmpty()) {
                block = new Block(page, blockBytes, adjustment, null);
            } else {
                block = blockDeque.pollLast();
                assert block != null;
                block.page = page;
                block.blockBytes = blockBytes;
                block.blockAdjustment = adjustment;
                block.nextBlock = null;
            }
            return block;
        }

        private void pageFreeListExtend(Page page, int bSize, int extend) {
            assert extend > 0;
            Block start = getBlock(page, bSize, page.adjustment + page.capacityBlocks * bSize);
            // Initialize a sequential free list.
            Block last;
            int count = 1; // For assertion
            if (extend == 1) {
                last = start;
            } else {
                last = getBlock(page, bSize, page.adjustment + (page.capacityBlocks + extend - 1) * bSize);
                assert count++ > 0; // Increase `count` if assertion enabled
            }
            Block block = start;
            if (extend > 1) {
                while (block.blockAdjustment < last.blockAdjustment - bSize) {
                    Block next = getBlock(page, bSize, block.blockAdjustment + bSize);
                    block.nextBlock = next;
                    assert count++ > 0; // Increase `count` if assertion enabled
                    assert next.blockAdjustment > block.blockAdjustment;
                    block = next;
                }
                assert block != last;
                block.nextBlock = last;
            }
            assert count == extend;
            // Prepend to the free list (usually `null`).
            last.nextBlock = page.freeList;
            page.freeList = start;
        }

        // Get a fresh page to use.
        private Page pageFresh(PageQueue pq) {
            return pageFreshAlloc(pq, pq.blockSize);
        }

        // Allocate a fresh page from a segment
        private Page pageFreshAlloc(PageQueue pq, int blockSize) {
            Page page = segmentPageAlloc(blockSize);
            if (page == null) {
                // This may be out-of-memory, or an abandoned page was reclaimed (and in our queue).
                return null;
            }
            // A fresh page was found, initialize it.
            pageInit(page, blockSize);
            assert (pq == null && page.isHuge) || (pq != null && !page.isHuge);
            if (!page.isHuge) {
                pageQueuePush(pq, page);
            }
            return page;
        }

        // Initialize a fresh page.
        private void pageInit(Page page, int blockSize) {
            assert page.capacityBlocks == 0;
            assert page.freeList == null;
            assert page.localFreeList == null;
            assert page.usedBlocks == 0;
            assert page.threadFreeList.get() == null;
            assert page.nextPage == null;
            assert page.prevPage == null;
            // Set fields.
            if (page.isHuge) {
                page.reservedBlocks = 1;
                page.retireExpire = 0;
                page.freeList = getBlock(page, page.blockSize, page.adjustment);
                page.capacityBlocks = 1;
            } else {
                page.blockSize = blockSize;
                int pageSize = page.sliceCount * SEGMENT_SLICE_SIZE;
                page.reservedBlocks = pageSize / blockSize;
                page.retireExpire = DEFAULT_PAGE_RETIRE_EXPIRE_INIT;
                pageExtendFree(page);
            }
        }

        private Page segmentPageAlloc(int blockSize) {
            Page page;
            if (blockSize <= SMALL_BLOCK_SIZE_MAX) { // <= 8 KiB
                page = segmentsPageAlloc(blockSize, blockSize);
            } else if (blockSize <= MEDIUM_BLOCK_SIZE_MAX) { // <= 128 KiB
                page = segmentsPageAlloc(MEDIUM_PAGE_SIZE, blockSize);
            } else if (blockSize <= LARGE_BLOCK_SIZE_MAX) { // <= 2 MiB
                page = segmentsPageAlloc(blockSize, blockSize);
            } else {
                page = segmentsHugePageAlloc(blockSize);
            }
            return page;
        }

        private Page segmentsPageAlloc(int requiredSize, int blockSize) {
            int pageSize = alignUp(requiredSize,
                    requiredSize > MEDIUM_PAGE_SIZE ? MEDIUM_PAGE_SIZE : SEGMENT_SLICE_SIZE);
            int slicesNeeded = pageSize >> SEGMENT_SLICE_SHIFT;
            Page page = segmentsPageFindAndAllocate(slicesNeeded);
            if (page == null) {
                // No free page, allocate a new segment and try again.
                if (segmentReclaimOrAlloc(slicesNeeded, blockSize) == null) {
                    // OOM or reclaimed a good page in the heap.
                    return null;
                } else {
                    // Otherwise, try again.
                    return segmentsPageAlloc(requiredSize, blockSize);
                }
            }
            return page;
        }

        private Segment segmentReclaimOrAlloc(int neededSlices, int blockSize) {
            // 1. Try to reclaim an abandoned segment.
            Object segment = segmentTryReclaim(neededSlices, blockSize);
            if (segment == RECLAIMED_SEGMENT_FLAG) {
                // Reclaimed the right page right into the heap.
                // Pretend out-of-memory as the page will be in the page queue of the heap with available blocks.
                return null;
            } else if (segment != null) {
                // Reclaimed a segment with a large enough empty span in it.
                return (Segment) segment;
            }
            // 2. Otherwise, allocate a fresh segment.
            return segmentAllocNormal();
        }

        private Object segmentTryReclaim(int neededSlices, int blockSize) {
            int maxTries = segmentGetReclaimTries();
            if (maxTries <= 0) {
                return null;
            }
            Object result = null;
            for (Segment segment = this.allocator.abandonedSegmentDeque.poll();
                segment != null && maxTries > 0; maxTries--) {
                this.allocator.abandonedSegmentCount.decrementAndGet();
                segment.abandonedVisits++;
                // Try to free up pages (due to concurrent frees).
                boolean hasPage = segmentCheckFree(segment, neededSlices, blockSize);
                if (segment.usedPages == 0) {
                    // Free the segment (by forced reclaim) to make it available to other threads.
                    // Note: we prefer to free a segment as that might lead to reclaiming another
                    // segment that is still partially used.
                    segmentReclaim(segment, 0, false);
                } else if (hasPage) {
                    // Found a large enough free span, or a page of the right blockSize with free space;
                    // we return the result of reclaim (which is usually `segment`) as it might free
                    // the segment due to concurrent frees (in which case `null` is returned).
                    result = segmentReclaim(segment, blockSize, true);
                    break;
                } else if (segment.abandonedVisits > 3) {
                    // Always reclaim on the 3rd visit to limit the abandoned segment count.
                    segmentReclaim(segment, 0, false);
                } else {
                    // Otherwise, push on the visited list so it gets not looked at too quickly again.
                    maxTries++; // Don't count this as a try since it was not suitable.
                    segmentMarkAbandoned(segment);
                }
            }
            return result;
        }

        // Mark a specific segment as abandoned,
        // and clears the ownerThread.
        private void segmentMarkAbandoned(Segment segment) {
            segment.ownerThread = null;
            segment.ownerHeap = null;
            segment.parent.abandonedSegmentDeque.offer(segment);
            segment.parent.abandonedSegmentCount.incrementAndGet();
        }

        // Reclaim an abandoned segment; returns null if the segment was freed.
        // Return `RECLAIMED_SEGMENT_FLAG` if it reclaimed a page of the right block size that was not full.
        private Object segmentReclaim(Segment segment, int requestedBlockSize, boolean checkRightPageReclaimed) {
            segment.ownerThread = Thread.currentThread();
            segment.ownerHeap = this;
            segment.abandonedVisits = 0;
            segmentsTrackSize(segment.segmentSize);
            // For all slices
            Span slice = segment.slices[0];
            boolean reclaimed = false;
            while (slice.sliceIndex < segment.sliceEntries) {
                if (slice.blockSize > 0) {
                    assert slice.sliceOffset == 0;
                    assert slice.sliceCount > 0;
                    // In use: reclaim the page in our heap.
                    Page page = (Page) slice;
                    segment.abandonedPages--;
                    // Associate the heap with this page, and allow heap thread delayed free again.
                    pageUseDelayedFree(page, USE_DELAYED_FREE, true); // override never (after heap is set)
                    page.pageFreeCollect(false); // Ensure `usedBlocks` count is up to date.
                    if (page.usedBlocks == 0) {
                        // If everything free by now, free the page.
                        slice = segmentPageClear(page);   // Set slice again due to coalescing.
                    } else {
                        // Otherwise, reclaim it into the heap.
                        pageReclaim(page);
                        if (requestedBlockSize == page.blockSize && pageHasAnyAvailable(page)) {
                            if (checkRightPageReclaimed) {
                                reclaimed = true;
                            }
                        }
                    }
                } else {
                    // The span is free, add it to our span queues.
                    slice = segmentSpanFreeCoalesce(slice); // Set slice again due to coalescing.
                }
                assert slice.sliceOffset == 0;
                assert slice.sliceCount > 0;
                slice = segment.slices[slice.sliceIndex + slice.sliceCount];
            }
            if (segment.usedPages == 0) {  // due to `segmentPageClear()`
                segmentFree(segment);
                return null;
            } else if (reclaimed) {
                return RECLAIMED_SEGMENT_FLAG;
            } else {
                return segment;
            }
        }

        private void pageUseDelayedFree(Page page, DelayedFlag delayedFlag, boolean overrideNever) {
            while (!pageTryUseDelayedFree(page, delayedFlag, overrideNever)) {
                Thread.yield();
            }
        }

        private boolean pageTryUseDelayedFree(Page page, DelayedFlag delayedFlag, boolean overrideNever) {
            DelayedFlag oldDelay;
            int yieldCount = 0;
            do {
                oldDelay = page.threadDelayedFreeFlag.get();
                if (oldDelay == DELAYED_FREEING) {
                    if (yieldCount >= 4) {
                        return false;  // Give up after 4 tries
                    }
                    yieldCount++;
                    Thread.yield(); // Delay until outstanding DELAYED_FREEING are done.
                } else if (delayedFlag == oldDelay) {
                    break; // Avoid atomic operation if already equal.
                } else if (!overrideNever && oldDelay == NEVER_DELAYED_FREE) {
                    break; // Leave never-delayed flag set.
                }
            } while ((oldDelay == DELAYED_FREEING) || !page.threadDelayedFreeFlag.compareAndSet(oldDelay, delayedFlag));
            return true; // Success
        }

        private void segmentFree(Segment segment) {
            if (segment.kind != SEGMENT_HUGE) {
                // Remove the free spans.
                segmentClearFreeSpanFromQueue(segment);
                // Expensive assertion: the spanQueues should not contain this segment anymore.
                assert assertSegmentNotExistInSpanQueue(segment);
                LocalHeap heap = segment.ownerHeap;
                if (heap.reservedNormalSegment == null) {
                    heap.reservedNormalSegment = segment;
                    heap.reservedNormalSegmentNano = System.nanoTime();
                    return;
                }
            }
            // Free it.
            segmentOsFree(segment);
        }

        // Only used for assertion.
        private boolean assertSegmentNotExistInSpanQueue(Segment segment) {
            SpanQueue[] spanQueues = this.segmentTld.spanQueues;
            for (SpanQueue sq : spanQueues) {
                Span span = sq.firstSpan;
                while (span != null) {
                    if (span.segment == segment) {
                        return false;
                    }
                    span = span.nextSpan;
                }
            }
            return true;
        }

        private void segmentOsFree(Segment segment) {
            segmentsTrackSize(-segment.segmentSize);
            segment.deallocate();
        }

        // Called from segments when reclaiming abandoned pages.
        private void pageReclaim(Page page) {
            PageQueue pq = pageQueue(page.blockSize);
            pageQueuePush(pq, page);
        }

        // Possibly free pages and check if free space is available.
        private boolean segmentCheckFree(Segment segment, int slicesNeeded, int blockSize) {
            boolean hasPage = false;
            // For all slices
            Span slice = segment.slices[0];
            while (slice.sliceIndex < segment.sliceEntries) {
                if (slice.blockSize > 0) { // Used page
                    assert slice.sliceOffset == 0;
                    assert slice.sliceCount > 0;
                    // Ensure used count is up to date and collect potential concurrent frees.
                    Page page = (Page) slice;
                    page.pageFreeCollect(false);
                    if (page.usedBlocks == 0) {
                        // If this page is all free now, free it without adding to any queues (yet).
                        segment.abandonedPages--;
                        slice = segmentPageClear(page); // Re-assign slice due to coalesce.
                        if (slice.sliceCount >= slicesNeeded) {
                            hasPage = true;
                        }
                    } else if (page.blockSize == blockSize && pageHasAnyAvailable(page)) {
                        // A page has available free blocks of the right size.
                        hasPage = true;
                    }
                } else {
                    // Empty span.
                    if (slice.sliceCount >= slicesNeeded) {
                        hasPage = true;
                    }
                }
                slice = segment.slices[slice.sliceIndex + slice.sliceCount];
            }
            return hasPage;
        }

        // Are there any available blocks?
        private boolean pageHasAnyAvailable(Page page) {
            return page.usedBlocks < page.reservedBlocks || page.threadFreeList.get() != null;
        }

        // Note: can be called on abandoned pages
        private Span segmentPageClear(Page page) {
            assert page.usedBlocks == 0;
            assert page.threadFreeList.get() == null;
            Segment segment = page.segment;
            page.blockSize = 1;
            // Free it
            Span slice = segmentSpanFreeCoalesce((Span) page);
            segment.usedPages--;
            return slice;
        }

        // Note: can be called on abandoned segments, through `segmentCheckFree`->segmentPageClear`.
        private Span segmentSpanFreeCoalesce(Span slice) {
            Segment segment = slice.segment;
            // For huge pages, just mark as free but don't add to the queues.
            if (segment.kind == SEGMENT_HUGE) {
                // `segment.usedPages` can be 0 if the huge page block was freed while abandoned
                // (reclaim will get here in that case).
                slice.blockSize = 0;  // Mark as free anyway.
                // We should mark the last slice `blockSize = 0` now to maintain invariants,
                // but we skip it because the segment is about to be freed.
                return slice;
            }
            // Otherwise, coalesce the span and add to the free span queues.
            boolean isAbandoned = isSegmentAbandoned(segment);
            int sliceCount = slice.sliceCount;
            Span next = segment.slices[slice.sliceIndex + slice.sliceCount];
            if (next.sliceIndex < segment.sliceEntries && next.blockSize == 0) {
                // Free the next slice -- remove it from free and merge.
                assert next.sliceCount > 0 && next.sliceOffset == 0;
                sliceCount += next.sliceCount; // extend
                if (!isAbandoned) {
                    segmentSpanRemoveFromQueue(next);
                }
            }
            if (slice.sliceIndex > 0) {
                Span prevFirst = sliceFirst(segment.slices, segment.slices[slice.sliceIndex - 1]);
                if (prevFirst.blockSize == 0) {
                    // Free previous slice -- remove it from free and merge.
                    assert prevFirst.sliceCount > 0 && prevFirst.sliceOffset == 0;
                    sliceCount += prevFirst.sliceCount;
                    slice.sliceCount = 0;
                    // Set the slice offset.
                    slice.sliceOffset = slice.sliceIndex - prevFirst.sliceIndex;
                    if (!isAbandoned) {
                        segmentSpanRemoveFromQueue(prevFirst);
                    }
                    slice = prevFirst;
                }
            }
            // Add the new free span.
            segmentSpanFree(segment, slice.sliceIndex, sliceCount);
            return slice;
        }

        private Span sliceFirst(Span[] slices, Span slice) {
            Span start = slices[slice.sliceIndex - slice.sliceOffset];
            assert start.sliceOffset == 0;
            assert start.sliceIndex + start.sliceCount > slice.sliceIndex;
            return start;
        }

        private void segmentSpanRemoveFromQueue(Span slice) {
            assert slice.sliceCount > 0 && slice.sliceOffset == 0 && slice.blockSize == 0;
            assert slice.segment.kind != SEGMENT_HUGE;
            SpanQueue sq = getSpanQueue(slice.sliceCount);
            spanQueueDelete(sq, slice);
        }

        private int segmentGetReclaimTries() {
            // Limit the tries to 10% (default) of the abandoned segments with at least 8 and at most 1024 tries.
            int perc = 10;
            long totalCount = this.allocator.abandonedSegmentCount.get();
            if (totalCount == 0) {
                return 0;
            }
            // Avoid overflow.
            long relativeCount = totalCount > 10000 ? (totalCount / 100) * perc : (totalCount * perc) / 100;
            long maxTries = relativeCount <= 1 ? 1 : (relativeCount > 1024 ? 1024 : relativeCount);
            if (maxTries < 8 && totalCount > 8) {
                maxTries = 8;
            }
            return (int) maxTries;
        }

        // Allocate a segment.
        private Segment segmentAllocNormal() {
            Segment segment;
            if (this.reservedNormalSegment == null) {
                AbstractByteBuf chunk = this.allocator.newChunk(DEFAULT_SEGMENT_SIZE);
                if (chunk == null) {
                    return null; // Signal OOM
                }
                segment = new Segment(this.allocator, DEFAULT_SEGMENT_SIZE, DEFAULT_SLICE_COUNT, SEGMENT_NORMAL,
                        chunk, this);
                segmentsTrackSize(segment.segmentSize);
            } else {
                segment = this.reservedNormalSegment;
                this.reservedNormalSegment = null;
            }
            // Initialize the initial free spans.
            segmentSpanFree(segment, 0, segment.sliceEntries);
            return segment;
        }

        // Allocate a huge page, which also means allocate a huge segment.
        private Page segmentsHugePageAlloc(int required) {
            int segmentSize = alignUp(required, SEGMENT_SLICE_SIZE);
            // Allocate the segment.
            AbstractByteBuf buf = this.allocator.newChunk(segmentSize);
            if (buf == null) {
                return null; // Signal OOM
            }
            // huge segment only needs 1 slice.
            Segment segment = new Segment(this.allocator, segmentSize, 1, SEGMENT_HUGE, buf, this);
            segmentsTrackSize(segment.segmentSize);
            // Allocate a huge page which spans the entire segment.
            return segmentHugeSpanAllocate(segment, segmentSize);
        }

        private void segmentsTrackSize(long segmentSize) {
            segmentTld.segmentsCount += segmentSize >= 0 ? 1 : -1;
            segmentTld.normalSegmentsCount += segmentSize == DEFAULT_SEGMENT_SIZE ? 1 :
                    segmentSize == -DEFAULT_SEGMENT_SIZE ? -1 : 0;
            if (segmentTld.segmentsCount > segmentTld.segmentsPeakCount) {
                segmentTld.segmentsPeakCount = segmentTld.segmentsCount;
            }
            segmentTld.segmentsCurrentSize += segmentSize;
            if (segmentTld.segmentsCurrentSize > segmentTld.segmentsPeakSize) {
                segmentTld.segmentsPeakSize = segmentTld.segmentsCurrentSize;
            }
        }

        private Page segmentsPageFindAndAllocate(int sliceCount) {
            // Search from best fit up.
            int sqIndex = spanQueueIndex(sliceCount);
            for (int i = sqIndex; i < this.segmentTld.spanQueues.length; i++) {
                SpanQueue sq = this.segmentTld.spanQueues[i];
                for (Span slice = sq.firstSpan; slice != null; slice = slice.nextSpan) {
                    if (slice.sliceCount >= sliceCount) { // Found a suitable page span.
                        spanQueueDelete(sq, slice);
                        Segment segment = slice.segment;
                        if (slice.sliceCount > sliceCount) {
                            segmentSliceSplit(segment, slice, sliceCount);
                        }
                        return segmentNormalSpanAllocate(segment, slice.sliceIndex, slice.sliceCount);
                    }
                }
            }
            // Could not find a page.
            return null;
        }

        private Page segmentNormalSpanAllocate(Segment segment, int sliceIndex, int sliceCount) {
            assert segment.kind == SEGMENT_NORMAL;
            assert sliceCount > 0;
            Span slice = segment.slices[sliceIndex];
            assert slice.blockSize == 1;
            slice.sliceOffset = 0;
            slice.sliceCount = sliceCount;
            if (sliceCount > 1) {
                int offset = sliceCount - 1;
                Span sliceNext = segment.slices[slice.sliceIndex + offset];
                sliceNext.sliceOffset = offset;
                sliceNext.sliceCount = 0;
                sliceNext.blockSize = 1;
            }
            // And initialize the page.
            segment.usedPages++;
            slice.isHuge = false;
            // Convert to Page.
            return slice;
        }

        private Page segmentHugeSpanAllocate(Segment segment, int blockSize) {
            assert segment.kind == SEGMENT_HUGE;
            assert segment.slices.length == 1;
            Span slice = segment.slices[0];
            // Convert the slices to a page.
            slice.sliceOffset = 0;
            slice.sliceCount = 1;
            slice.blockSize = blockSize;
            // And initialize the page.
            segment.usedPages++;
            slice.isHuge = true;
            // Convert to Page.
            return slice;
        }

        private void segmentSliceSplit(Segment segment, Span slice, int sliceCount) {
            if (slice.sliceCount <= sliceCount) {
                return;
            }
            int nextIndex = slice.sliceIndex + sliceCount;
            int nextCount = slice.sliceCount - sliceCount;
            segmentSpanFree(segment, nextIndex, nextCount);
            slice.sliceCount = sliceCount;
        }

        // Note: can be called on abandoned segments, through `segmentCheckFree`->`segmentSpanFreeCoalesce`.
        private void segmentSpanFree(Segment segment, int sliceIndex, int sliceCount) {
            assert segment.kind != SEGMENT_HUGE;
            SpanQueue sq = isSegmentAbandoned(segment) ? null : getSpanQueue(sliceCount);
            // Set the first and last slice (the intermediates can be undetermined).
            Span slice = segment.slices[sliceIndex];
            slice.sliceCount = sliceCount;
            slice.sliceOffset = 0;
            if (sliceCount > 1) {
                int lastIndex = sliceIndex + sliceCount - 1;
                assert lastIndex < segment.sliceEntries;
                Span last = segment.slices[lastIndex];
                last.sliceCount = 0;
                last.sliceOffset = sliceCount - 1;
                last.blockSize = 0;
            }
            // And push it on the free span queue.
            if (sq != null) {
                spanQueuePush(sq, slice);
            } else {
                slice.blockSize = 0; // Mark the abandoned span as free anyway.
            }
        }

        private void spanQueuePush(SpanQueue sq, Span slice) {
            slice.prevSpan = null;
            slice.nextSpan = sq.firstSpan;
            sq.firstSpan = slice;
            if (slice.nextSpan != null) {
                slice.nextSpan.prevSpan = slice;
            } else {
                sq.lastSpan = slice;
            }
            slice.blockSize = 0; // free.
        }

        private boolean isSegmentAbandoned(Segment segment) {
            return segment.ownerThread == null;
        }

        private void spanQueueDelete(SpanQueue sq, Span span) {
            // Should work too if the queue does not contain span (which can happen during reclaim).
            if (span.prevSpan != null) {
                span.prevSpan.nextSpan = span.nextSpan;
            }
            if (span == sq.firstSpan) {
                sq.firstSpan = span.nextSpan;
            }
            if (span.nextSpan != null) {
                span.nextSpan.prevSpan = span.prevSpan;
            }
            if (span == sq.lastSpan) {
                sq.lastSpan = span.prevSpan;
            }
            span.prevSpan = null;
            span.nextSpan = null;
            span.blockSize = 1; // No more free.
        }

        private SpanQueue getSpanQueue(int sliceCount) {
            assert sliceCount <= DEFAULT_SLICE_COUNT;
            int bin = spanQueueIndex(sliceCount);
            return this.segmentTld.spanQueues[bin];
        }

        // Free a page with no more used blocks.
        private void pageFree(Page page, PageQueue pq) {
            // Remove from the page list
            // (no need to do `heapDelayedFree` first as all blocks are already free).
            pageQueueRemove(pq, page);
            // And free it.
            page.capacityBlocks = 0;
            recycleBlocks(page.freeList);
            page.freeList = null;
            recycleBlocks(page.localFreeList);
            page.localFreeList = null;
            segmentPageFree(page);
        }

        private void recycleBlocks(Block firstBlock) {
            if (firstBlock == null) {
                return;
            }
            Block currentBlock = firstBlock;
            Block recycledBlock;
            int blockQueueAvailableCapacity = MAX_BLOCK_QUEUE_SIZE - blockDeque.size();
            while (blockQueueAvailableCapacity-- > 0 && currentBlock != null) {
                recycledBlock = currentBlock;
                currentBlock = currentBlock.nextBlock;
                recycledBlock.page = null;
                recycledBlock.nextBlock = null;
                blockDeque.addLast(recycledBlock);
            }
        }

        private void segmentPageFree(Page page) {
            Segment segment = page.segment;
            // Mark it as free now.
            segmentPageClear(page);
            if (segment.usedPages == 0) {
                // No more used pages; remove it from the free list and free the segment.
                segmentFree(segment);
            } else if (segment.usedPages == segment.abandonedPages) {
                assert segment.kind != SEGMENT_HUGE;
                // Only abandoned pages, remove it from the free list and abandon.
                segmentAbandon(segment);
            }
        }

        /* -----------------------------------------------------------
            Abandonment

            When threads terminate, they can leave segments with
            live blocks (reachable through other threads). Such segments
            are "abandoned" and will be reclaimed by other threads to
            reuse their pages and/or free them eventually. The
            `ownerThread` of such segments is null.

            Moreover, if threads are looking for a fresh segment, they
            will first consider abandoned segments.
            ----------------------------------------------------------- */

            /* -----------------------------------------------------------
               Abandon segment/page
            ----------------------------------------------------------- */
        private void segmentAbandon(Segment segment) {
            // Remove the free spans from the free span queues.
            segmentClearFreeSpanFromQueue(segment);
            // All pages in the segment are abandoned; add it to the abandoned list.
            segmentsTrackSize(-segment.segmentSize);
            segment.abandonedVisits = 1;   // From 0 to 1 to signify it is abandoned.
            segmentMarkAbandoned(segment);
        }

        private void segmentClearFreeSpanFromQueue(Segment segment) {
            Span slice = segment.slices[0];
            Span end = segment.slices[segment.sliceEntries];
            while (slice.sliceIndex < end.sliceIndex) {
                if (slice.blockSize == 0) { // a free span
                    segmentSpanRemoveFromQueue(slice);
                    slice.blockSize = 0; // but keep it free
                }
                assert slice.sliceCount > 0;
                slice = segment.slices[slice.sliceIndex + slice.sliceCount];
            }
        }

        private void segmentPageAbandon(Page page) {
            Segment segment = page.segment;
            segment.abandonedPages++;
            if (segment.usedPages == segment.abandonedPages) {
                // All pages are abandoned, abandon the entire segment.
                segmentAbandon(segment);
            }
        }

        private void pageQueueRemove(PageQueue pq, Page page) {
            if (page.prevPage != null) {
                page.prevPage.nextPage = page.nextPage;
            }
            if (page.nextPage != null) {
                page.nextPage.prevPage = page.prevPage;
            }
            if (page == pq.lastPage) {
                pq.lastPage = page.prevPage;
            }
            if (page == pq.firstPage) {
                pq.firstPage = page.nextPage;
                // Update first.
                heapQueueFirstUpdate(pq);
            }
            this.pageCount--;
            page.nextPage = null;
            page.prevPage = null;
            page.isInFull = false;
        }

        private void pageQueuePush(PageQueue pq, Page page) {
            page.isInFull = pq.index == PAGE_QUEUE_BIN_FULL_INDEX;
            page.nextPage = pq.firstPage;
            page.prevPage = null;
            if (pq.firstPage != null) {
                pq.firstPage.prevPage = page;
                pq.firstPage = page;
            } else {
                pq.firstPage = pq.lastPage = page;
            }
            // Update direct.
            heapQueueFirstUpdate(pq);
            this.pageCount++;
        }

        private void pageQueueMoveToFront(PageQueue pq, Page page) {
            if (pq.firstPage == page) {
                return;
            }
            pageQueueRemove(pq, page);
            pageQueuePush(pq, page);
        }

        private void pageQueueEnqueueFromFull(PageQueue to, PageQueue from, Page page) {
            // Note: we could insert at the front to increase reuse?
            pageQueueEnqueueFromEx(to, from, true, page);
        }

        private void pageQueueEnqueueFromEx(PageQueue to, PageQueue from, boolean enqueueAtEnd, Page page) {
            // Delete from `from` queue.
            if (page.prevPage != null) {
                page.prevPage.nextPage = page.nextPage;
            }
            if (page.nextPage != null) {
                page.nextPage.prevPage = page.prevPage;
            }
            if (page == from.lastPage) {
                from.lastPage = page.prevPage;
            }
            if (page == from.firstPage) {
                from.firstPage = page.nextPage;
                // Update first.
                heapQueueFirstUpdate(from);
            }
            // Insert into `to` queue
            if (enqueueAtEnd) {
                // Enqueue at the end.
                page.prevPage = to.lastPage;
                page.nextPage = null;
                if (to.lastPage != null) {
                    to.lastPage.nextPage = page;
                    to.lastPage = page;
                } else {
                    to.firstPage = page;
                    to.lastPage = page;
                    heapQueueFirstUpdate(to);
                }
            } else {
                if (to.firstPage != null) {
                    // Enqueue at 2nd place.
                    Page next = to.firstPage.nextPage;
                    page.prevPage = to.firstPage;
                    page.nextPage = next;
                    to.firstPage.nextPage = page;
                    if (next != null) {
                        next.prevPage = page;
                    } else {
                        to.lastPage = page;
                    }
                } else {
                    // Enqueue at the head (singleton list).
                    page.prevPage = null;
                    page.nextPage = null;
                    to.firstPage = page;
                    to.lastPage = page;
                    heapQueueFirstUpdate(to);
                }
            }
            page.isInFull = to.index == PAGE_QUEUE_BIN_FULL_INDEX;
        }

        // The current small page array is for efficiency, and for each
        // small size it points directly to the page for that
        // size without having to compute the bin. This means when the
        // current free page queue is updated for a small bin, we need to update a
        // range of entries in `pagesFreeDirect`.
        private void heapQueueFirstUpdate(PageQueue pq) {
            int size = pq.blockSize; // size >= 8
            assert size >= 8;
            if (size > PAGES_FREE_DIRECT_SIZE_MAX) {
                return;
            }
            Page page = pq.firstPage;
            if (page == null) {
                page = EMPTY_PAGE;
            }
            // Find index in the right direct page array.
            int start;
            int idx = toWordSize(size);
            if (this.pagesFreeDirect[idx] == page) {
                return;  // already set
            }
            // Find start slot.
            if (idx <= 1) { // size == 8
                assert size == 8;
                start = 0;
            } else { // size > 8
                assert size > 8;
                assert pq.index > 1;
                PageQueue prev = this.pageQueues[pq.index - 1];
                start = 1 + toWordSize(prev.blockSize);
                if (start > idx) {
                    start = idx;
                }
            }
            // Set size range to the right page
            for (int sz = start; sz <= idx; sz++) {
                this.pagesFreeDirect[sz] = page;
            }
        }

        private PageQueue heapPageQueueOf(Page page) {
            int bin = pageBin(page);
            return this.pageQueues[bin];
        }

        private void pageToFull(Page page, PageQueue pq) {
            if (page.isInFull) {
                return;
            }
            pageQueueEnqueueFrom(this.pageQueues[PAGE_QUEUE_BIN_FULL_INDEX], pq, page);
            page.isInFull = true;
            page.pageFreeCollect(false);
        }

        private void pageQueueEnqueueFrom(PageQueue to, PageQueue from, Page page) {
            pageQueueEnqueueFromEx(to, from, true, page);
        }
    }

    static final class PageQueue {
        private Page firstPage;
        private Page lastPage;
        private final int blockWords;
        private final int blockSize;
        private final int index;

        PageQueue(int blockWords, int index) {
            this.blockWords = blockWords;
            this.blockSize = blockWords * WORD_SIZE;
            this.index = index;
        }
    }

    /**
     * <pre>
     * Used by {@link Page#threadDelayedFreeFlag}.
     * The flag is used to control how blocks are freed in a page.
     * The page can be in one of the following states:
     * - {@link #USE_DELAYED_FREE} - The page is using delayed free.
     * - {@link #DELAYED_FREEING} - The page is currently being freed in a delayed manner.
     * - {@link #NO_DELAYED_FREE} - The page is not using delayed free.
     * - {@link #NEVER_DELAYED_FREE} - The page is never using delayed free.
     *
     *
     *                                Initializing a page
     *                                         │
     *                                         ▼
     *                            ┌────────────────────────────┐
     *       ┌───────────────────►│      USE_DELAYED_FREE      │◄─────────────────────┐
     *       │                    │      (Delayed free)        │                      │
     *       │                    └────────────┬───────────────┘                      │
     *       │                                 │                                      │
     *       │                    First cross-thread free of the page                 │
     *       │                                 │                                      │
     *       │                                 ▼                                      │
     *       │                    ┌────────────────────────────┐                      │
     *       │                    │      DELAYED_FREEING       │         Before collecting the delayed list
     *       │                    │ (Currently processing free)│                      │
     *       │                    └────────────┬───────────────┘                      │
     * Before reclaim/free                     │                                      │
     * an abandoned segment         When freeing completes                            │
     *       │                                 │                                      │
     *       │                                 ▼                                      │
     *       │                    ┌────────────────────────────┐                      │
     *       │                    │      NO_DELAYED_FREE       │──────────────────────┘
     *       │                    │   (Delayed free disabled)  │
     *       │                    └────────────┬───────────────┘
     *       │                                 │
     *       │                          On heap abandon
     *       │                                 │
     *       │                                 ▼
     *       │                    ┌────────────────────────────┐
     *       │                    │    NEVER_DELAYED_FREE      │
     *       └────────────────────│ (Never use delayed free)   │
     *                            └────────────────────────────┘
     *
     *</pre>
     */
    enum DelayedFlag {
        USE_DELAYED_FREE,
        DELAYED_FREEING,
        NO_DELAYED_FREE,
        NEVER_DELAYED_FREE
    }

    enum SegmentKind {
        SEGMENT_NORMAL, // `SEGMENT_SIZE` size with pages inside.
        SEGMENT_HUGE   // Segment with just one huge page inside.
    }

    static class Page {
        Segment segment;
        int capacityBlocks; // number of blocks created.
        int reservedBlocks; // number of blocks reserved.
        boolean isInFull;
        // Expiration count for retired blocks.
        // retireExpire = 0 means disable retirement.
        byte retireExpire = DEFAULT_PAGE_RETIRE_EXPIRE_INIT;
        Block freeList;
        Block localFreeList;
        int usedBlocks; // number of blocks in use (including blocks in `thread-free list`)
        final AtomicReference<Block> threadFreeList = new AtomicReference<>();
        Page nextPage;
        Page prevPage;
        int adjustment;
        int sliceCount;
        /**
         * Meaning of `blockSize`:
         * <p>
         * blockSize > 1:
         *       (1).Actual block size of a page, the page may exist in page queue,
         *           or not: page has been abandoned or is a huge page.
         * <p>
         * blockSize = 0:
         *      (1).A slice whose segment has just been created.
         *      (2).A free span in span queue.
         *      (3).An abandoned free span.
         *      (4).A huge page which has been marked as free.
         *      (5).The first and last slice of continuous slices which represents a free span in span queue,
         *          the intermediate spans can be ignored, as we use `span.sliceOffset` to skip intermediate spans.
         * <p>
         * blockSize = 1:
         *      (1).The last slice of a page.
         *      (2).The span which has been removed from span queue.
         *      (3).A span which is about to be pushed into span queue or not (due to spans coalescence),
         *          or is about to be marked as free if it's a huge page.
         */
        int blockSize;
        final AtomicReference<DelayedFlag> threadDelayedFreeFlag = new AtomicReference<>(USE_DELAYED_FREE);
        boolean isHuge; // `true` if the page is in a huge segment (segment.kind == SEGMENT_HUGE)

        // Empty Page Constructor
        Page() { }

        // Abandon a page with used blocks at the end of a thread.
        // Note: only call if it is ensured that no references exist from
        // the `page->heap->threadDelayedFreeList` into this page.
        // Currently only called through `heapCollectEx` which ensures this.
        private void pageAbandon(PageQueue pq, LocalHeap heap) {
            // page is no longer associated with heap.
            // remove from page queues.
            heap.pageQueueRemove(pq, this);
            // abandon it.
            heap.segmentPageAbandon(this);
        }

        // Regular free of a local thread block.
        private void freeBlockLocal(Block block, boolean checkFull, LocalHeap heap) {
            // Actual free: push on the local free list.
            block.nextBlock = this.localFreeList;
            this.localFreeList = block;
            if (--this.usedBlocks == 0) {
                pageRetire(heap);
            } else if (checkFull && this.isInFull) {
                pageUnfull(heap);
            }
        }

        // Retire a page with no more used blocks.
        // Important to not retire too quickly though as new allocations might be coming soon.
        private void pageRetire(LocalHeap heap) {
            // Don't retire too often.
            // (or we end up retiring and re-allocating most of the time).
            // For now, we don't retire if it is the only page left of this size class.
            Page page = this;
            PageQueue pq = heap.heapPageQueueOf(page);
            int bSize = page.blockSize;
            if (pq.index < PAGE_QUEUE_BIN_LARGE_INDEX) {  // not full && not huge queue
                if (pq.lastPage == page && pq.firstPage == page) { // the only page in the queue
                    // Enable retirement
                    page.retireExpire = bSize <= SMALL_BLOCK_SIZE_MAX ?
                            DEFAULT_PAGE_RETIRE_CYCLES_HIGH : DEFAULT_PAGE_RETIRE_CYCLES_LOW;
                    int index = pq.index;
                    if (index < heap.pageRetiredMin) {
                        heap.pageRetiredMin = index;
                    }
                    if (index > heap.pageRetiredMax) {
                        heap.pageRetiredMax = index;
                    }
                    return; // don't free after all
                }
            }
            heap.pageFree(page, pq);
        }

        // Move a page from the full list back to a regular list.
        private void pageUnfull(LocalHeap heap) {
            Page page = this;
            if (!page.isInFull) {
                return;
            }
            PageQueue pqFull = heap.pageQueues[PAGE_QUEUE_BIN_FULL_INDEX];
            page.isInFull = false; // to get the right queue by following method `heapPageQueueOf(page)`.
            PageQueue pq = heap.heapPageQueueOf(page);
            page.isInFull = true;
            heap.pageQueueEnqueueFromFull(pq, pqFull, page);
        }

        private void pageFreeCollect(boolean force) {
            // Collect the thread-free list.
            if (force || this.threadFreeList.get() != null) {
                pageThreadFreeCollect();
            }
            if (this.localFreeList != null) {
                if (this.freeList == null) {
                    this.freeList = this.localFreeList;
                    this.localFreeList = null;
                } else if (force) {
                    // Append -- only on shutdown (force) as this is a linear operation.
                    Block tail = this.localFreeList;
                    Block next;
                    while ((next = tail.nextBlock) != null) {
                        tail = next;
                    }
                    tail.nextBlock = this.freeList;
                    this.freeList = this.localFreeList;
                    this.localFreeList = null;
                }
            }
        }

        // Collect the local `thread_free` list using an atomic exchange.
        // Note: The exchange must be done atomically as this is used right after moving to the full list,
        // and we need to ensure that there was no race where the page became unfull just before the move.
        private void pageThreadFreeCollect() {
            Block head;
            do {
                head = this.threadFreeList.get();
            } while (head != null && !this.threadFreeList.compareAndSet(head, null));
            // return if the list is empty
            if (head == null) {
                return;
            }
            // Find the tail -- also to get a proper count (without data races)
            int maxCount = this.capacityBlocks; // cannot collect more than capacity
            int count = 1;
            Block tail = head;
            Block next;
            while ((next = tail.nextBlock) != null && count <= maxCount) {
                count++;
                tail = next;
            }
            // If `count > maxCount` there was a memory corruption.
            // (possibly infinite list due to double multi-threaded free)
            if (count > maxCount) {
                // The thread-free items cannot be freed.
                PlatformDependent.throwException(new RuntimeException("the thread-free items cannot be freed"));
            }
            // And append the current local free list
            tail.nextBlock = this.localFreeList;
            this.localFreeList = head;
            // Update counts now
            this.usedBlocks -= count;
        }

        // Is the page not yet used up to its reserved space?
        private boolean isPageExpandable() {
            return this.capacityBlocks < this.reservedBlocks;
        }

        private boolean immediateAvailable() {
            return this.freeList != null;
        }

        private boolean isMostlyUsed() {
            int frac = this.reservedBlocks >> 3;
            return this.reservedBlocks - this.usedBlocks <= frac;
        }
    }

    static final class Block extends MiByteBuf {
        private Page page;
        private int blockBytes;
        private int blockAdjustment;
        private Block nextBlock;

        Block(Page page, int blockBytes, int blockAdjustment, Block nextBlock) {
            this.page = page;
            this.blockBytes = blockBytes;
            this.blockAdjustment = blockAdjustment;
            this.nextBlock = nextBlock;
        }
    }

    private AbstractByteBuf newChunk(int size) {
        try {
            AbstractByteBuf buf = chunkAllocator.allocate(size, size);
            this.usedMemory.addAndGet(size);
            return buf;
        } catch (OutOfMemoryError e) {
            return null; // OOM
        }
    }

    static final class SpanQueue {
        private Span firstSpan;
        private Span lastSpan;
        private final int sliceCount;
        private final int index;
        SpanQueue(int sliceCount, int index) {
            this.sliceCount = sliceCount;
            this.index = index;
        }
    }

    static final class Span extends Page {
        Span prevSpan;
        Span nextSpan;
        final int sliceIndex;
        int sliceOffset; // Distance from the actual page data slice (0 if a page).

        Span(Segment segment, int adjustment, int sliceCount, Span nextSpan, Span prevSpan, int sliceIndex) {
            this.segment = segment;
            this.adjustment = adjustment;
            this.sliceCount = sliceCount;
            this.nextSpan = nextSpan;
            this.prevSpan = prevSpan;
            this.sliceIndex = sliceIndex;
        }
    }

    static final class Segment {
        private final AbstractByteBuf delegate;
        private final MiMallocByteBufAllocator parent;
        private final int segmentSize;
        // For shared heap:
        //     The `ownerThread` is the thread who create this segment.
        // For thread-local heap:
        //     (1).The `ownerThread` is the thread who create/reclaim this segment.
        //     (2).If `ownerThread` is null, signals this segment is abandoned
        //         (the segment is in the abandoned queue, or it's a huge segment).
        private Thread ownerThread;
        private LocalHeap ownerHeap;
        private final Span[] slices;
        private final int sliceEntries; // Entries in the `slices` array, at most `DEFAULT_SLICE_COUNT`
        private int usedPages; // count of pages in use
        // Abandoned pages (i.e. the original owning thread stopped) (`abandoned <= used`)
        private int abandonedPages;
        // Count how often this segment is visited during abandoned reclamation (to force reclaim if it takes too long).
        private int abandonedVisits;
        private final SegmentKind kind;
        // Only used for huge segment freeing.
        private final AtomicReference<Thread> hugeSegmentOwnerThread;

        Segment(MiMallocByteBufAllocator parent, int segmentSize, int segmentSlices, SegmentKind kind,
                AbstractByteBuf delegate, LocalHeap heap) {
            this.parent = parent;
            this.delegate = delegate;
            if (kind == SEGMENT_HUGE) {
                this.slices = new Span[1];
                hugeSegmentOwnerThread = new AtomicReference<Thread>();
            } else { // normal segment
                this.ownerThread = Thread.currentThread();
                // one more slice for loop convenient.
                this.slices = new Span[segmentSlices + 1];
                this.hugeSegmentOwnerThread = null;
            }
            this.ownerHeap = heap;
            this.sliceEntries = segmentSlices;
            this.segmentSize = segmentSize;
            this.usedPages = 0;
            this.kind = kind;
            for (int i = 0; i < slices.length; i++) {
                slices[i] = new Span(this, i * SEGMENT_SLICE_SIZE, 1, null, null, i);
            }
        }

        void deallocate() {
            AbstractByteBuf delegate = this.delegate;
            assert delegate != null;
            delegate.release();
            this.parent.usedMemory.addAndGet(-this.segmentSize);
        }
    }

    long usedMemory() {
        return usedMemory.get();
    }

    long abandonedSegmentCount() {
        return abandonedSegmentCount.get();
    }

    // Free a block.
    void free(Block block) {
        Page page = block.page;
        assert page != null;
        Segment segment = page.segment;
        assert segment != null;
        LocalHeap ownerHeap = segment.ownerHeap;
        // If `ownerThread` is not null, then the `ownerHeap` must not be null.
        // If `ownerThread` is null, then the `ownerHeap` might be null (segment in abandoned queue),
        // or not null(huge segment).
        assert segment.ownerThread == null || ownerHeap != null;
        if (segment.ownerThread == Thread.currentThread() && ownerHeap.sharedLock == null) {
            // thread-local free.
            page.freeBlockLocal(block, page.isInFull, ownerHeap);
        } else {
            StampedLock sharedLock;
            long lockStamp;
            if (!page.isHuge && ownerHeap != null && (sharedLock = ownerHeap.sharedLock) != null &&
                    // Try to acquire the sharedLock once, if failed, then use the multi-threaded-free path.
                    (lockStamp = sharedLock.tryWriteLock()) != 0) {
                try {
                    // Successfully acquired the sharedLock, use thread-local free.
                    page.freeBlockLocal(block, page.isInFull, ownerHeap);
                } finally {
                    sharedLock.unlockWrite(lockStamp);
                }
            } else {
                // Use the generic multi-threaded-free path.
                freeBlockMt(page, segment, block);
            }
        }
    }

    // Multi-threaded-free, or free a huge block.
    private void freeBlockMt(Page page, Segment segment, Block block) {
        if (page.isHuge) {
            // Huge page segments are always abandoned and can be freed immediately.
            segmentHugePageFree(segment, page, block);
        } else {
            // Free the actual block by pushing it on the owning heap thread_delayed free list,
            // or thread_free list.
            freeBlockDelayedMt(page, block);
        }
    }

    // Push a block that is owned by another thread on its page-local thread_free
    // list, or it's heap delayed free list. Such blocks are later collected by
    // the owning thread in `freeDelayedBlock`.
    private void freeBlockDelayedMt(Page page, Block block) {
        // Try to put the block on either the page-local thread_free list,
        // or the heap delayed free list (if this is the first non-local free in that page).
        boolean useDelayed;
        do {
            useDelayed = page.threadDelayedFreeFlag.get() == USE_DELAYED_FREE;
        } while (useDelayed && !page.threadDelayedFreeFlag.compareAndSet(USE_DELAYED_FREE, DELAYED_FREEING));
        // If this was the first non-local free, we need to push it on the heap delayed free list.
        // `useDelayed` will only be true if `threadDelayedFreeFlag == USE_DELAYED_FREE`.
        if (useDelayed) {
            try {
                // Racy read on `heap`, but ok because `DELAYED_FREEING` is set.
                // (see `heapCollectAbandon`)
                LocalHeap heap = page.segment.ownerHeap;
                assert heap != null;
                // Add to the delayed free list of this heap.
                Block dfree;
                do {
                    dfree = heap.threadDelayedFreeList.get();
                    block.nextBlock = dfree;
                } while (!heap.threadDelayedFreeList.compareAndSet(dfree, block));
            } finally { // Make sure we always reset the `DELAYED_FREEING` to `NO_DELAYED_FREE`.
                if (!page.threadDelayedFreeFlag.compareAndSet(DELAYED_FREEING, NO_DELAYED_FREE)) {
                    // Should not happen.
                    PlatformDependent.throwException(new IllegalStateException("Failed to reset DELAYED_FREEING flag"));
                }
            }
        } else { // Common path
            Block current;
            do {
                current = page.threadFreeList.get();
                block.nextBlock = current;
            } while (!page.threadFreeList.compareAndSet(current, block));
        }
    }

    // Free huge block, either by another thread or current thread.
    private void segmentHugePageFree(Segment segment, Page page, Block block) {
        // Huge page segments are always abandoned(`ownerThread` and `hugeSegmentOwnerThread.get()` are `null`)
        // after creation, and can be freed immediately by any thread claim it and free.
        // If this is the last reference, the CAS should always succeed.
        assert segment.hugeSegmentOwnerThread != null;
        if (segment.hugeSegmentOwnerThread.compareAndSet(null, Thread.currentThread())) {
            block.nextBlock = page.freeList;
            page.freeList = block;
            page.usedBlocks--;
            assert page.usedBlocks == 0;
            // The `segment.ownerHeap` should never be `null`,
            // as only normal segment's `ownerHeap` is set to `null` when offered to the `abandonedSegmentDeque`.
            // But a huge segment should never have been offered to the `abandonedSegmentDeque`.
            assert segment.ownerHeap != null;
            // This is safe.
            // After the `hugeSegmentOwnerThread` CAS success,
            // this huge segment should be accessed only by current thread.
            segment.ownerHeap.segmentPageFree(page);
            assert segment.usedPages == 0;
            assert segment.delegate.refCnt() == 0;
        }
    }

    /**
     * Allocate into the given buffer. Used by {@link AdaptivePoolingAllocator.AdaptiveByteBuf#capacity(int)}.
     */
    void reallocate(int size, int maxCapacity, MiByteBuf into) {
        MiByteBuf result = allocate(size, maxCapacity, into, true);
        assert result == into: "Re-allocation created separate buffer instance";
    }

    ByteBuf allocate(int size, int maxCapacity) {
        return allocate(size, maxCapacity, null, false);
    }

    private MiByteBuf allocate(int size, int maxCapacity, MiByteBuf byteBuf, boolean isReAlloc) {
        int goodAllocSize = 0;
        if (FastThreadLocalThread.currentThreadWillCleanupFastThreadLocals()) {
            return allocate(size, maxCapacity, byteBuf, isReAlloc, THREAD_LOCAL_HEAP.get());
        } else if (size <= MEDIUM_BLOCK_SIZE_MAX ||
                (goodAllocSize = getGoodOsAllocSize(size)) <= LARGE_BLOCK_SIZE_MAX) { // If not huge.
            long threadId = Thread.currentThread().getId();
            int currentHeapsScanLength;
            do {
                currentHeapsScanLength = this.heapsScanLength.get();
                int mask = currentHeapsScanLength - 1;
                int index = (int) (threadId & mask);
                SharedHeapWrap sharedHeapWrap;
                StampedLock lock;
                long lockStamp;
                // Attempts range:[3, 10], to avoid spinning too long.
                int attempts = Math.max(3, Math.min(currentHeapsScanLength, 10));
                for (int i = 0; i < attempts; i++) {
                    sharedHeapWrap = this.sharedHeapWraps[index + i & mask];
                    lock = sharedHeapWrap.lock;
                    if ((lockStamp = lock.tryWriteLock()) != 0) {
                        // Was able to allocate.
                        try {
                            LocalHeap heap = sharedHeapWrap.heap;
                            if (heap == null) {
                                heap = sharedHeapWrap.heap = new LocalHeap(this, lock);
                            }
                            return allocate(size, maxCapacity, byteBuf, isReAlloc, heap);
                        } finally {
                            lock.unlockWrite(lockStamp);
                        }
                    }
                }
            } while (tryExpandHeapsScanLength(currentHeapsScanLength));
        }
        assert this.sharedHeapWraps[0] != null && this.sharedHeapWraps[0].heap != null;
        LocalHeap heap = this.sharedHeapWraps[0].heap;
        // If it is a huge size, or failed to acquire the shared heap lock.
        goodAllocSize = goodAllocSize > 0 ? goodAllocSize : getGoodOsAllocSize(size);
        return allocateFallback(goodAllocSize, maxCapacity, byteBuf, heap, isReAlloc);
    }

    private boolean tryExpandHeapsScanLength(int currentHeapsScanLength) {
        if (currentHeapsScanLength >= MAX_SHARED_HEAP_WRAPS_LENGTH) {
            return false;
        }
        // If the CAS failed, means another thread has already expanded the `heapsScanLength`.
        this.heapsScanLength.compareAndSet(currentHeapsScanLength, currentHeapsScanLength << 1);
        return true;
    }

    private MiByteBuf allocate(int size, int maxCapacity, MiByteBuf byteBuf, boolean isReAlloc, LocalHeap heap) {
        if (size <= PAGES_FREE_DIRECT_SIZE_MAX) {
            int wSize = toWordSize(size);
            Page page = heap.pagesFreeDirect[wSize];
            // Fast path
            Block block = page.freeList;
            if (block != null) {
                if (byteBuf == null) {
                    byteBuf = block;
                }
                page.freeList = block.nextBlock;
                byteBuf.init(block, size, maxCapacity, isReAlloc);
                page.usedBlocks++;
                return byteBuf;
            }
        }
        return allocateGeneric(size, maxCapacity, byteBuf, heap, isReAlloc);
    }

    private MiByteBuf allocateGeneric(int size, int maxCapacity, MiByteBuf byteBuf, LocalHeap heap, boolean isReAlloc) {
        // Do administrative tasks every N generic allocations.
        boolean heapCollected = false;
        if (++heap.genericCount >= 100) {
            heap.genericCollectCount += heap.genericCount;
            heap.genericCount = 0;
            // Call potential deferred free routines,
            // free delayed frees from other threads (but skip contended ones).
            heap.heapDelayedFreePartial();
            // Collect every once in a while.
            if (heap.genericCollectCount >= HEAP_OPTION_GENERIC_COLLECT) {
                heap.genericCollectCount = 0;
                heap.heapCollect(NORMAL);
                heapCollected = true;
            }
        }
        Page page = heap.findPage(size);
        if (page == null) { // First time out of memory, try to collect and retry the allocation once more.
            heap.heapCollect(FORCE);
            page = heap.findPage(size);
        }
        if (page == null) { // out of memory
            PlatformDependent.throwException(new OutOfMemoryError("Unable to allocate " + size + " bytes"));
        }
        Block block = page.freeList;
        if (byteBuf == null) {
            byteBuf = block;
        }
        page.freeList = block.nextBlock;
        byteBuf.init(block, size, maxCapacity, isReAlloc);
        page.usedBlocks++;
        // Move page to the full queue.
        if (!page.isHuge && page.reservedBlocks == page.usedBlocks) {
            heap.pageToFull(page, heap.heapPageQueueOf(page));
        }
        if (heapCollected && heap.reservedNormalSegment != null &&
                System.nanoTime() - heap.reservedNormalSegmentNano > DEFAULT_RESERVED_SEGMENT_RETIRE_NANO) {
            heap.segmentOsFree(heap.reservedNormalSegment);
            heap.reservedNormalSegment = null;
        }
        return byteBuf;
    }

    private MiByteBuf allocateFallback(int size, int maxCapacity, MiByteBuf buf, LocalHeap heap, boolean isReAlloc) {
        Page page = heap.createHugePage(size);
        if (page == null) { // out of memory
            PlatformDependent.throwException(new OutOfMemoryError("Unable to allocate " + size + " bytes"));
        }
        Block block = page.freeList;
        if (buf == null) {
            buf = block;
        }
        page.freeList = block.nextBlock;
        buf.init(block, size, maxCapacity, isReAlloc);
        page.usedBlocks++;
        return buf;
    }

    private static int pageBin(Page page) {
        return page.isInFull ? PAGE_QUEUE_BIN_FULL_INDEX : page.isHuge ?
                PAGE_QUEUE_BIN_LARGE_INDEX : pageQueueIndex(page.blockSize);
    }

    private static int toWordSize(int size) {
        return (size + WORD_SIZE_MASK) >> 3;
    }

    /**
     * Calculate the page queue index for a given size.
     * As the size increases, the index growth curve flattens out.
     */
    private static int pageQueueIndex(int size) {
        assert size >= 0;
        int wSize = toWordSize(size);
        if (wSize <= 9) {
            return (wSize == 0) ? 1 : wSize;
        }
        if (wSize > MEDIUM_BLOCK_WORD_SIZE_MAX) {
            return PAGE_QUEUE_BIN_LARGE_INDEX;
        }
        wSize--;
        // Find the highest bit position of wSize.
        int p = 31 - Integer.numberOfLeadingZeros(wSize);
        // Use the top 3 bits to determine the index.
        // Adjust with 3 because we already handled the first 8 wSizes, which each gets an exact index.
        return ((p << 2) | ((wSize >> (p - 2)) & 0x03)) - 3;
    }

    /**
     * Calculate the span queue index for a given sliceCount.
     * As the sliceCount increases, the index growth curve flattens out.
     */
    private static int spanQueueIndex(int sliceCount) {
        assert sliceCount > 0;
        if (sliceCount <= 8) {
            return sliceCount;
        }
        sliceCount--;
        // Find the highest bit position of sliceCount.
        int s = 31 - Integer.numberOfLeadingZeros(sliceCount);
        // Use the top 3 bits to determine the index.
        // Adjust with 4 because we already handled the first 7 sliceCounts, which each gets an exact index.
        return ((s << 2) | ((sliceCount >> (s - 2)) & 0x03)) - 4;
    }

    private static int alignUp(int size, int alignment) {
        assert size > 0;
        int mask = alignment - 1;
        int alignedSize;
        if ((alignment & mask) == 0) {  // If alignment is power of two.
            alignedSize = (size + mask) & ~mask;
        } else {
            alignedSize = ((size + mask) / alignment) * alignment;
        }
        // If alignedSize overflowed, return size.
        return alignedSize < 0 ? size : alignedSize;
    }

    // Round to a good OS allocation size (bounded by max 12.5% waste).
    static int getGoodOsAllocSize(int size) {
        int alignSize;
        if (size < 512 * KiB) {
            alignSize = DEFAULT_OS_PAGE_SIZE;
        } else if (size < 2 * MiB) {
            alignSize = 64 * KiB;
        } else if (size < 8 * MiB) {
            alignSize = 256 * KiB;
        } else if (size < 32 * MiB) {
            alignSize = MiB;
        } else {
            alignSize = 4 * MiB;
        }
        return alignUp(size, alignSize);
    }

    /**
     * The strategy for how {@link MiMallocByteBufAllocator} should allocate chunk buffers.
     */
    interface ChunkAllocator {
        /**
         * Allocate a buffer for a chunk. This can be any kind of {@link AbstractByteBuf} implementation.
         * @param initialCapacity The initial capacity of the returned {@link AbstractByteBuf}.
         * @param maxCapacity The maximum capacity of the returned {@link AbstractByteBuf}.
         * @return The buffer that represents the chunk memory.
         */
        AbstractByteBuf allocate(int initialCapacity, int maxCapacity);
    }

    static class MiByteBuf extends AbstractReferenceCountedByteBuf {
        private int length;
        private int maxFastCapacity;
        private AbstractByteBuf rootParent;
        private int adjustment;
        private ByteBuffer tmpNioBuf;
        private boolean hasArray;
        private boolean hasMemoryAddress;
        private Block block;

        MiByteBuf() {
            super(0);
        }

        void init(Block block, int length, int maxCapacity, boolean isReAlloc) {
            assert block != null;
            block.nextBlock = null;
            if (!isReAlloc) {
                resetRefCnt();
                discardMarks();
                setIndex0(0, 0);
            }
            this.block = block;
            this.length = length;
            this.maxFastCapacity = Math.min(block.blockBytes, maxCapacity);
            this.adjustment = block.blockAdjustment;
            maxCapacity(maxCapacity);
            this.rootParent = block.page.segment.delegate;
            this.tmpNioBuf = null;
            this.hasArray = rootParent.hasArray();
            this.hasMemoryAddress = rootParent.hasMemoryAddress();
        }

        @Override
        protected void deallocate() {
            assert this.block != null;
            this.length = -1;
            this.maxFastCapacity = -1;
            this.adjustment = -1;
            this.rootParent = null;
            this.tmpNioBuf = null;
            this.hasArray = false;
            this.hasMemoryAddress = false;
            MiMallocByteBufAllocator allocator = this.block.page.segment.parent;
            Block bk = this.block;
            this.block = null;
            allocator.free(bk);
        }

        public ByteBuf capacity(int newCapacity) {
            if (length <= newCapacity && newCapacity <= maxFastCapacity) {
                ensureAccessible();
                length = newCapacity;
                return this;
            }
            checkNewCapacity(newCapacity);
            if (newCapacity < capacity()) {
                length = newCapacity;
                trimIndicesToCapacity(newCapacity);
                return this;
            }
            // Reallocation required.
            /*
             * 1. The ByteBuf's block is a reusable node from the free list, representing a specific allocation range.
             * 2. The ByteBuf may share the same instance with the block itself to avoid extra object creation,
             *    therefore, we can't simply free the block.
             * 3. If (this == this.block), then we create a shallow copy block,
             *    pointing to the same allocation range, and release the copy instead.
             * 4. The reallocation will make this ByteBuf not share the same instance with its original block anymore.
             */
            MiMallocByteBufAllocator allocator = this.block.page.segment.parent;
            Block oldBlock = this.block;
            if (this == oldBlock) {
                oldBlock = new Block(oldBlock.page, oldBlock.blockBytes, oldBlock.blockAdjustment, null);
            }
            int baseOldRootIndex = adjustment;
            int oldCapacity = length;
            AbstractByteBuf oldRoot = rootParent();
            allocator.reallocate(newCapacity, maxCapacity(), this);
            oldRoot.getBytes(baseOldRootIndex, this, 0, oldCapacity);
            allocator.free(oldBlock);
            return this;
        }

        private AbstractByteBuf rootParent() {
            final AbstractByteBuf rootParent = this.rootParent;
            if (rootParent != null) {
                return rootParent;
            }
            throw new IllegalReferenceCountException();
        }

        @Override
        public int capacity() {
            return length;
        }

        @Override
        public int maxFastWritableBytes() {
            return Math.min(maxFastCapacity, maxCapacity()) - writerIndex;
        }

        @Override
        public ByteBufAllocator alloc() {
            return rootParent().alloc();
        }

        @Override
        public ByteOrder order() {
            return rootParent().order();
        }

        @Override
        public ByteBuf unwrap() {
            return null;
        }

        @Override
        public boolean isDirect() {
            return rootParent().isDirect();
        }

        @Override
        public int arrayOffset() {
            return idx(rootParent().arrayOffset());
        }

        @Override
        public boolean hasMemoryAddress() {
            return hasMemoryAddress;
        }

        @Override
        public long memoryAddress() {
            ensureAccessible();
            return _memoryAddress();
        }

        @Override
        long _memoryAddress() {
            AbstractByteBuf root = rootParent;
            return root != null ? root._memoryAddress() + adjustment : 0L;
        }

        @Override
        public ByteBuffer nioBuffer(int index, int length) {
            checkIndex(index, length);
            return rootParent().nioBuffer(idx(index), length);
        }

        @Override
        public ByteBuffer internalNioBuffer(int index, int length) {
            checkIndex(index, length);
            return (ByteBuffer) internalNioBuffer().position(index).limit(index + length);
        }

        private ByteBuffer internalNioBuffer() {
            if (tmpNioBuf == null) {
                tmpNioBuf = rootParent().nioBuffer(adjustment, maxFastCapacity);
            }
            return (ByteBuffer) tmpNioBuf.clear();
        }

        @Override
        public ByteBuffer[] nioBuffers(int index, int length) {
            checkIndex(index, length);
            return rootParent().nioBuffers(idx(index), length);
        }

        @Override
        public boolean hasArray() {
            return hasArray;
        }

        @Override
        public byte[] array() {
            ensureAccessible();
            return rootParent().array();
        }

        @Override
        public ByteBuf copy(int index, int length) {
            checkIndex(index, length);
            return rootParent().copy(idx(index), length);
        }

        @Override
        public int nioBufferCount() {
            return rootParent().nioBufferCount();
        }

        @Override
        protected byte _getByte(int index) {
            return rootParent()._getByte(idx(index));
        }

        @Override
        protected short _getShort(int index) {
            return rootParent()._getShort(idx(index));
        }

        @Override
        protected short _getShortLE(int index) {
            return rootParent()._getShortLE(idx(index));
        }

        @Override
        protected int _getUnsignedMedium(int index) {
            return rootParent()._getUnsignedMedium(idx(index));
        }

        @Override
        protected int _getUnsignedMediumLE(int index) {
            return rootParent()._getUnsignedMediumLE(idx(index));
        }

        @Override
        protected int _getInt(int index) {
            return rootParent()._getInt(idx(index));
        }

        @Override
        protected int _getIntLE(int index) {
            return rootParent()._getIntLE(idx(index));
        }

        @Override
        protected long _getLong(int index) {
            return rootParent()._getLong(idx(index));
        }

        @Override
        protected long _getLongLE(int index) {
            return rootParent()._getLongLE(idx(index));
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
            checkIndex(index, length);
            rootParent().getBytes(idx(index), dst, dstIndex, length);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
            checkIndex(index, length);
            rootParent().getBytes(idx(index), dst, dstIndex, length);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, ByteBuffer dst) {
            checkIndex(index, dst.remaining());
            rootParent().getBytes(idx(index), dst);
            return this;
        }

        @Override
        protected void _setByte(int index, int value) {
            rootParent()._setByte(idx(index), value);
        }

        @Override
        protected void _setShort(int index, int value) {
            rootParent()._setShort(idx(index), value);
        }

        @Override
        protected void _setShortLE(int index, int value) {
            rootParent()._setShortLE(idx(index), value);
        }

        @Override
        protected void _setMedium(int index, int value) {
            rootParent()._setMedium(idx(index), value);
        }

        @Override
        protected void _setMediumLE(int index, int value) {
            rootParent()._setMediumLE(idx(index), value);
        }

        @Override
        protected void _setInt(int index, int value) {
            rootParent()._setInt(idx(index), value);
        }

        @Override
        protected void _setIntLE(int index, int value) {
            rootParent()._setIntLE(idx(index), value);
        }

        @Override
        protected void _setLong(int index, long value) {
            rootParent()._setLong(idx(index), value);
        }

        @Override
        protected void _setLongLE(int index, long value) {
            rootParent().setLongLE(idx(index), value);
        }

        @Override
        public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
            checkIndex(index, length);
            if (tmpNioBuf == null && PlatformDependent.javaVersion() >= 13) {
                ByteBuffer dstBuffer = rootParent()._internalNioBuffer();
                PlatformDependent.absolutePut(dstBuffer, idx(index), src, srcIndex, length);
            } else {
                ByteBuffer tmp = (ByteBuffer) internalNioBuffer().clear().position(index);
                tmp.put(src, srcIndex, length);
            }
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
            checkIndex(index, length);
            if (src instanceof MiByteBuf && PlatformDependent.javaVersion() >= 16) {
                MiByteBuf srcBuf = (MiByteBuf) src;
                srcBuf.checkIndex(srcIndex, length);
                ByteBuffer dstBuffer = rootParent()._internalNioBuffer();
                ByteBuffer srcBuffer = srcBuf.rootParent()._internalNioBuffer();
                PlatformDependent.absolutePut(dstBuffer, idx(index), srcBuffer, srcBuf.idx(srcIndex), length);
            } else {
                ByteBuffer tmp = internalNioBuffer();
                tmp.position(index);
                tmp.put(src.nioBuffer(srcIndex, length));
            }
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuffer src) {
            int length = src.remaining();
            checkIndex(index, length);
            ByteBuffer tmp = internalNioBuffer();
            if (PlatformDependent.javaVersion() >= 16) {
                int offset = src.position();
                PlatformDependent.absolutePut(tmp, index, src, offset, length);
                src.position(offset + length);
            } else {
                tmp.position(index);
                tmp.put(src);
            }
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, OutputStream out, int length)
                throws IOException {
            checkIndex(index, length);
            if (length != 0) {
                ByteBuffer tmp = internalNioBuffer();
                ByteBufUtil.readBytes(alloc(), tmp.hasArray() ? tmp : tmp.duplicate(), index, length, out);
            }
            return this;
        }

        @Override
        public int getBytes(int index, GatheringByteChannel out, int length)
                throws IOException {
            ByteBuffer buf = internalNioBuffer().duplicate();
            buf.clear().position(index).limit(index + length);
            return out.write(buf);
        }

        @Override
        public int getBytes(int index, FileChannel out, long position, int length)
                throws IOException {
            ByteBuffer buf = internalNioBuffer().duplicate();
            buf.clear().position(index).limit(index + length);
            return out.write(buf, position);
        }

        @Override
        public int setBytes(int index, InputStream in, int length)
                throws IOException {
            checkIndex(index, length);
            final AbstractByteBuf rootParent = rootParent();
            if (rootParent.hasArray()) {
                return rootParent.setBytes(idx(index), in, length);
            }
            byte[] tmp = ByteBufUtil.threadLocalTempArray(length);
            int readBytes = in.read(tmp, 0, length);
            if (readBytes <= 0) {
                return readBytes;
            }
            setBytes(index, tmp, 0, readBytes);
            return readBytes;
        }

        @Override
        public int setBytes(int index, ScatteringByteChannel in, int length)
                throws IOException {
            try {
                return in.read(internalNioBuffer(index, length));
            } catch (ClosedChannelException ignored) {
                return -1;
            }
        }

        @Override
        public int setBytes(int index, FileChannel in, long position, int length)
                throws IOException {
            try {
                return in.read(internalNioBuffer(index, length), position);
            } catch (ClosedChannelException ignored) {
                return -1;
            }
        }

        @Override
        public int setCharSequence(int index, CharSequence sequence, Charset charset) {
            return setCharSequence0(index, sequence, charset, false);
        }

        private int setCharSequence0(int index, CharSequence sequence, Charset charset, boolean expand) {
            if (charset.equals(CharsetUtil.UTF_8)) {
                int length = ByteBufUtil.utf8MaxBytes(sequence);
                if (expand) {
                    ensureWritable0(length);
                    checkIndex0(index, length);
                } else {
                    checkIndex(index, length);
                }
                return ByteBufUtil.writeUtf8(this, index, length, sequence, sequence.length());
            }
            if (charset.equals(CharsetUtil.US_ASCII) || charset.equals(CharsetUtil.ISO_8859_1)) {
                int length = sequence.length();
                if (expand) {
                    ensureWritable0(length);
                    checkIndex0(index, length);
                } else {
                    checkIndex(index, length);
                }
                return ByteBufUtil.writeAscii(this, index, sequence, length);
            }
            byte[] bytes = sequence.toString().getBytes(charset);
            if (expand) {
                ensureWritable0(bytes.length);
                // setBytes(...) will take care of checking the indices.
            }
            setBytes(index, bytes);
            return bytes.length;
        }

        @Override
        public int writeCharSequence(CharSequence sequence, Charset charset) {
            int written = setCharSequence0(writerIndex, sequence, charset, true);
            writerIndex += written;
            return written;
        }

        @Override
        public int forEachByte(int index, int length, ByteProcessor processor) {
            checkIndex(index, length);
            int ret = rootParent().forEachByte(idx(index), length, processor);
            return forEachResult(ret);
        }

        @Override
        public int forEachByteDesc(int index, int length, ByteProcessor processor) {
            checkIndex(index, length);
            int ret = rootParent().forEachByteDesc(idx(index), length, processor);
            return forEachResult(ret);
        }

        @Override
        public ByteBuf setZero(int index, int length) {
            checkIndex(index, length);
            rootParent().setZero(idx(index), length);
            return this;
        }

        @Override
        public ByteBuf writeZero(int length) {
            ensureWritable(length);
            rootParent().setZero(idx(writerIndex), length);
            writerIndex += length;
            return this;
        }

        private int forEachResult(int ret) {
            if (ret < adjustment) {
                return -1;
            }
            return ret - adjustment;
        }

        @Override
        public boolean isContiguous() {
            return rootParent().isContiguous();
        }

        private int idx(int index) {
            return index + adjustment;
        }
    }
}
