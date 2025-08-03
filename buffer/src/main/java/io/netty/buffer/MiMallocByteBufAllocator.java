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
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.PlatformDependent;
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
import java.util.Deque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import static io.netty.buffer.MiMallocByteBufAllocator.COLLECT_TYPE.ABANDON;
import static io.netty.buffer.MiMallocByteBufAllocator.COLLECT_TYPE.FORCE;
import static io.netty.buffer.MiMallocByteBufAllocator.COLLECT_TYPE.NORMAL;
import static io.netty.buffer.MiMallocByteBufAllocator.DELAYED_FLAG.DELAYED_FREEING;
import static io.netty.buffer.MiMallocByteBufAllocator.DELAYED_FLAG.NEVER_DELAYED_FREE;
import static io.netty.buffer.MiMallocByteBufAllocator.DELAYED_FLAG.NO_DELAYED_FREE;
import static io.netty.buffer.MiMallocByteBufAllocator.DELAYED_FLAG.USE_DELAYED_FREE;
import static io.netty.buffer.MiMallocByteBufAllocator.SEGMENT_KIND.SEGMENT_HUGE;
import static io.netty.buffer.MiMallocByteBufAllocator.SEGMENT_KIND.SEGMENT_NORMAL;

@UnstableApi
final class MiMallocByteBufAllocator {

    // 8 bytes
    private static final int WORD_SIZE = 8;
    private static final int WORD_SIZE_MASK = WORD_SIZE - 1;

    // 64 KiB
    private static final int SEGMENT_SLICE_SHIFT = 16;
    // 4 Mib
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

    private static final int MAX_PAGE_CANDIDATE_SEARCH = 4;

    private static final Page EMPTY_PAGE = new Page();
    // 4 KiB
    private static final int PAGE_MAX_EXTEND_SIZE = 4 * 1024;

    private static final int PAGE_MIN_EXTEND_BLOCKS = 4;

    private static final int PAGE_QUEUE_BIN_LARGE_INDEX = 53;

    private static final int PAGE_QUEUE_BIN_FULL_INDEX = PAGE_QUEUE_BIN_LARGE_INDEX + 1;

    private final FastThreadLocal<LocalHeap> THREAD_LOCAL_HEAP;

    private final Deque<Segment> abandonedSegmentQueue;
    // Count of abandoned segments for this allocator.
    private final AtomicLong abandonedSegmentCount = new AtomicLong();

    private final ChunkAllocator chunkAllocator;

    private static final Object RECLAIMED_SEGMENT_FLAG = new Object();

    private static final int KiB = 1024;
    private static final int MiB = KiB * KiB;

    private static final int PAGE_RETIRE_CYCLES = 16;

    // Collect heaps every N(default 10000) generic allocation calls.
    private static final int HEAP_OPTION_GENERIC_COLLECT = 10000;

    private static final int DEFAULT_PAGE_RETIRE_EXPIRE = 7;

    private static final int  BLOCK_ALIGNMENT_MAX = DEFAULT_SEGMENT_SIZE >> 1;
    // Maximum slice count(31)
    private static final int  MAX_SLICE_OFFSET_COUNT = (BLOCK_ALIGNMENT_MAX / SEGMENT_SLICE_SIZE) - 1;

    private final AtomicLong usedMemory = new AtomicLong();

    MiMallocByteBufAllocator(ChunkAllocator chunkAllocator) {
        this.chunkAllocator = chunkAllocator;
        this.abandonedSegmentQueue = PlatformDependent.newConcurrentDeque();
        this.THREAD_LOCAL_HEAP = new FastThreadLocal<LocalHeap>() {
            @Override
            protected LocalHeap initialValue() {
                return new LocalHeap(MiMallocByteBufAllocator.this);
            }

            @Override
            protected void onRemoval(LocalHeap heap) {
                // Cleanup if needed
                heap.threadHeapDone();
            }
        };
    }

    static final class SegmentTld {
        int segmentsCount;        // current number of segments;
        int segmentsPeakCount;   // peak number of segments
        long segmentsCurrentSize; // current size of all segments
        long segmentsPeakSize;    // peak size of all segments
        int reclaimCount; // number of reclaimed (abandoned) segments
        private final SpanQueue[] spanQueues = new SpanQueue[] {
            new SpanQueue(1, 0),
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

    enum COLLECT_TYPE {
        NORMAL,
        FORCE,
        ABANDON
    }

    static final class LocalHeap {
        final SegmentTld segmentTld;
        final Thread ownerThread;
        int pageCount; // total number of pages in the `pages` queues.
        int pageRetiredMin; // smallest retired index (retired pages are fully free, but still in the page queues)
        int pageRetiredMax; // largest retired index into the `pages` array.
        int genericCount; // how often is `allocateGeneric` called
        int genericCollectCount; // how often is `allocateGeneric` called without collecting.
        final Page[] pagesFreeDirect;
        // 8 bytes to 2 MiB.
        final PageQueue[] pageQueues;
        final MiMallocByteBufAllocator allocator;
        final AtomicReference<Block> threadDelayedFreeList = new AtomicReference<>();
        private static final byte VISIT_WORK_TYPE_MARK_PAGE_NEVER_DELAYED_FREE = 0;
        private static final byte VISIT_WORK_TYPE_PAGE_COLLECT = 1;

        private static final int BLOCK_QUEUE_SIZE = PAGE_MAX_EXTEND_SIZE;
        private final ArrayDeque<Block> blockDeque = new ArrayDeque<Block>(BLOCK_QUEUE_SIZE);

        LocalHeap(MiMallocByteBufAllocator allocator) {
            this.ownerThread = Thread.currentThread();
            segmentTld = new SegmentTld();
            pagesFreeDirect = new Page[PAGES_FREE_DIRECT_ARRAY_SIZE + 1];
            Arrays.fill(pagesFreeDirect, EMPTY_PAGE);
            this.allocator = allocator;
            pageQueues = new PageQueue[] {
                    new PageQueue(1, 0),
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

        private void threadHeapDone() {
            heapCollectAbandon();
        }

        private void heapCollectAbandon() {
            heapCollectEx(ABANDON, false);
        }

        private void heapCollect(boolean force, boolean isMainExit) {
            heapCollectEx(force ? FORCE : NORMAL, isMainExit);
        }

        private boolean heapPageNeverDelayedFree(Page page) {
            pageUseDelayedFree(page, NEVER_DELAYED_FREE, false);
            return true;
        }

        private void heapPageCollect(PageQueue pq, Page page, COLLECT_TYPE collect) {
            boolean isForce = isForceCollect(collect);
            page.pageFreeCollect(isForce);
            if (page.usedBlocks == 0) {
                // No more used blocks, free the page.
                // Note: this will free retired pages.
                this.pageFree(page, pq, isForceCollect(collect));
            } else if (collect == ABANDON) {
                // There are still used blocks, but the thread is done, abandon the page.
                page.pageAbandon(pq, this);
            }
        }

        private boolean isForceCollect(COLLECT_TYPE collect) {
            return collect == FORCE || collect == ABANDON;
        }

        private void heapCollectEx(COLLECT_TYPE collect, boolean isMainExit) {
            boolean force = isForceCollect(collect);
            //TODO: handle this in finalize()?
            if (isMainExit && force) {
                // The main thread is abandoned (end-of-program), try to reclaim all abandoned segments.
                // If all memory is freed by now, all segments should be freed.
                abandonedReclaimAll();
            }
            // If during abandoning, mark all pages to no longer add to the delayed-free list
            if (collect == ABANDON) {
                heapVisitPages(collect, VISIT_WORK_TYPE_MARK_PAGE_NEVER_DELAYED_FREE);
            }
            // Free all current thread's delayed blocks.
            // (If during abandoning, after this, there are no more thread-delayed references into the pages.)
            heapDelayedFreeAll();
            // Collect retired pages.
            heapCollectRetired(force);
            // Collect all pages owned by this thread.
            heapVisitPages(collect, VISIT_WORK_TYPE_PAGE_COLLECT);
            // Collect abandoned segments.
            abandonedCollect(collect == FORCE);
        }

        // Collect abandoned segments
        private void abandonedCollect(boolean force) {
            Segment segment;
            long abandonedSegmentCount = this.allocator.abandonedSegmentCount.get();
            long max_tries = force ? abandonedSegmentCount : Math.min(1024, abandonedSegmentCount);  // Limit latency
            while (max_tries-- > 0 && (segment = this.allocator.abandonedSegmentQueue.poll()) != null) {
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
        private void heapCollectRetired(boolean force) {
            int min = PAGE_QUEUE_BIN_FULL_INDEX;
            int max = 0;
            for (int bin = this.pageRetiredMin; bin <= this.pageRetiredMax; bin++) {
                PageQueue pq = this.pageQueues[bin];
                Page page = pq.firstPage;
                if (page != null && page.retireExpire != 0) {
                    if (page.usedBlocks == 0) {
                        page.retireExpire--;
                        if (force || page.retireExpire == 0) {
                            pageFree(pq.firstPage, pq, force);
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
            boolean all_freed = true;
            // And free them all.
            while (block != null) {
                Block next = block.nextBlock;
                // Use internal free instead of regular one to keep stats correct.
                if (!freeDelayedBlock(block)) {
                    // We might already start delayed freeing while another thread has not yet
                    // reset the delayed_freeing flag, in that case,
                    // delay it further by reinserting the current block into the delayed free list.
                    all_freed = false;
                    Block current;
                    do {
                        current = this.threadDelayedFreeList.get();
                        block.nextBlock = current;
                    } while (!this.threadDelayedFreeList.compareAndSet(current, block));
                }
                block = next;
            }
            return all_freed;
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
        private void heapVisitPages(COLLECT_TYPE collect, byte workType) {
            if (this.pageCount == 0) {
                return;
            }
            for (int i = 0; i <= PAGE_QUEUE_BIN_FULL_INDEX; i++) {
                PageQueue pq = this.pageQueues[i];
                Page page = pq.firstPage;
                while (page != null) {
                    Page next = page.nextPage; // Save next in case the page gets removed from the queue.
                    if (workType == VISIT_WORK_TYPE_MARK_PAGE_NEVER_DELAYED_FREE) {
                        this.heapPageNeverDelayedFree(page);
                    } else {
                        this.heapPageCollect(pq, page, collect);
                    }
                    page = next;
                }
            }
        }

        private void abandonedReclaimAll() {
            Segment segment;
            while ((segment = this.allocator.abandonedSegmentQueue.poll()) != null) {
                this.allocator.abandonedSegmentCount.decrementAndGet();
                segmentReclaim(segment, 0, false);
            }
        }

        // Allocate a page.
        private Page findPage(int size) {
            // Huge allocation.
            if (size > MEDIUM_BLOCK_SIZE_MAX) {
                return largeOrHugePageAlloc(size);
            } else {
                // Otherwise, find a page with free blocks in our size segregated queues.
                return findFreePage(size);
            }
        }

        // Large and huge page allocation.
        // Huge pages contain just one block, and the segment contains just that page (as `SEGMENT_HUGE`).
        private Page largeOrHugePageAlloc(int size) {
            int block_size = getGoodOsAllocSize(size);
            boolean is_huge = block_size > LARGE_BLOCK_SIZE_MAX;
            //TODO: handle huge page stats?
            PageQueue pq = is_huge ? null : pageQueue(block_size);
            Page page = pageFreshAlloc(pq, block_size);
            if (page != null && is_huge) {
                page.heapx.set(null);
            }
            return page;
        }

        private PageQueue pageQueue(int size) {
            return this.pageQueues[pageQueueIndex(size)];
        }

        private Page findFreePage(int size) {
            PageQueue pq = pageQueue(size);
            if (pq.firstPage != null) {
                pq.firstPage.pageFreeCollect(false);
                if (pq.firstPage.immediateAvailable()) {
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
                    // If we find a non-expandable candidate, or searched for N pages, return with the best candidate.
                    if (immediateAvailable || candidateCount > MAX_PAGE_CANDIDATE_SEARCH) {
                        break;
                    }
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
            int max_extend = bSize >= PAGE_MAX_EXTEND_SIZE ? PAGE_MIN_EXTEND_BLOCKS : PAGE_MAX_EXTEND_SIZE / bSize;
            if (max_extend < PAGE_MIN_EXTEND_BLOCKS) {
                max_extend = PAGE_MIN_EXTEND_BLOCKS;
            }
            if (extend > max_extend) {
                extend = max_extend;
            }
            pageFreeListExtend(page, bSize, extend);
            page.capacityBlocks += extend;
            return true;
        }

        private Block getBlock(Page page, int blockBytes, int adjustment) {
            Block block = blockDeque.poll();
            if (block == null) {
                block = new Block();
            }
            block.page = page;
            block.blockBytes = blockBytes;
            block.blockAdjustment = adjustment;
            block.nextBlock = null;
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
                count++;
            }
            Block block = start;
            if (extend > 1) {
                while (block.blockAdjustment < last.blockAdjustment - bSize) {
                    Block next = getBlock(page, bSize, block.blockAdjustment + bSize);
                    block.nextBlock = next;
                    count++;
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
            int fullBlockSize = (pq == null || page.isHuge) ? page.blockSize : blockSize;
            assert fullBlockSize > 0;
            pageInit(page, fullBlockSize);
            if (pq != null) {
                pageQueuePush(pq, page);
            }
            return page;
        }

        // Initialize a fresh page.
        private void pageInit(Page page, int block_size) {
            // Set fields.
            page.heapx.set(this);
            page.blockSize = block_size;
            int page_size = page.sliceCount * SEGMENT_SLICE_SIZE;
            page.reservedBlocks = page_size / block_size;
            assert page.capacityBlocks == 0;
            assert page.freeList == null;
            assert page.localFreeList == null;
            assert page.usedBlocks == 0;
            assert page.threadFreeList.get() == null;
            assert page.nextPage == null;
            assert page.prevPage == null;
            pageExtendFree(page);
        }

        private Page segmentPageAlloc(int block_size) {
            Page page;
            if (block_size <= SMALL_BLOCK_SIZE_MAX) { // <= 8 KiB
                page = segmentsPageAlloc(block_size, block_size);
            } else if (block_size <= MEDIUM_BLOCK_SIZE_MAX) { // <= 128 KiB
                page = segmentsPageAlloc(MEDIUM_PAGE_SIZE, block_size);
            } else if (block_size <= LARGE_BLOCK_SIZE_MAX) { // <= 2 MiB
                page = segmentsPageAlloc(block_size, block_size);
            } else {
                page = segmentAllocHuge(block_size);
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

        private Segment segmentReclaimOrAlloc(int needed_slices, int block_size) {
            // 1. Try to reclaim an abandoned segment.
            Object segment = segmentTryReclaim(needed_slices, block_size);
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

        private Object segmentTryReclaim(int needed_slices, int block_size) {
            int max_tries = segmentGetReclaimTries();
            if (max_tries <= 0) {
                return null;
            }
            Object result = null;
            for (Segment segment = this.allocator.abandonedSegmentQueue.poll();
                segment != null && max_tries > 0; max_tries--) {
                this.allocator.abandonedSegmentCount.decrementAndGet();
                segment.abandonedVisits++;
                // Try to free up pages (due to concurrent frees).
                boolean has_page = segmentCheckFree(segment, needed_slices, block_size);
                if (segment.usedPages == 0) {
                    // Free the segment (by forced reclaim) to make it available to other threads.
                    // Note: we prefer to free a segment as that might lead to reclaiming another
                    // segment that is still partially used.
                    segmentReclaim(segment, 0, false);
                } else if (has_page) {
                    // Found a large enough free span, or a page of the right block_size with free space;
                    // we return the result of reclaim (which is usually `segment`) as it might free
                    // the segment due to concurrent frees (in which case `null` is returned).
                    result = segmentReclaim(segment, block_size, true);
                    break;
                } else if (segment.abandonedVisits > 3) {
                    // Always reclaim on the 3rd visit to limit the abandoned segment count.
                    segmentReclaim(segment, 0, false);
                } else {
                    // Otherwise, push on the visited list so it gets not looked at too quickly again.
                    max_tries++; // Don't count this as a try since it was not suitable.
                    segmentMarkAbandoned(segment);
                }
            }
            return result;
        }

        // Mark a specific segment as abandoned,
        // and clears the ownerThread.
        private void segmentMarkAbandoned(Segment segment) {
            if (segment.parent.abandonedSegmentQueue.offer(segment)) {
                segment.parent.abandonedSegmentCount.incrementAndGet();
                segment.ownerThread.set(null);
            }
        }

        // Reclaim an abandoned segment; returns null if the segment was freed.
        // Return `RECLAIMED_SEGMENT_FLAG` if it reclaimed a page of the right block size that was not full.
        private Object segmentReclaim(Segment segment, int requested_block_size, boolean check_right_page_reclaimed) {
            segment.ownerThread.set(Thread.currentThread());
            segment.abandonedVisits = 0;
            segment.wasReclaimed = true;
            this.segmentTld.reclaimCount++;
            segmentsTrackSize(segment.segmentSize);
            // For all slices
            Span slice = segment.slices[0];
            boolean reclaimed = false;
            while (slice.sliceIndex < segment.sliceEntries) {
                if (slice.blockSize > 0) {
                    // In use: reclaim the page in our heap.
                    Page page = (Page) slice;
                    segment.abandonedPages--;
                    // Associate the heap with this page, and allow heap thread delayed free again.
                    page.heapx.set(this);
                    pageUseDelayedFree(page, USE_DELAYED_FREE, true); // override never (after heap is set)
                    page.pageFreeCollect(false); // Ensure `usedBlocks` count is up to date.
                    if (page.usedBlocks == 0) {
                        // If everything free by now, free the page.
                        slice = segmentPageClear(page);   // Set slice again due to coalescing.
                    } else {
                        // Otherwise, reclaim it into the heap.
                        pageReclaim(page);
                        if (requested_block_size == page.blockSize && pageHasAnyAvailable(page)) {
                            if (check_right_page_reclaimed) {
                                reclaimed = true;
                            }
                        }
                    }
                } else {
                    // The span is free, add it to our page queues.
                    slice = segmentSpanFreeCoalesce(slice); // Set slice again due to coalescing.
                }
                slice = segment.slices[slice.sliceIndex + slice.sliceCount];
            }
            if (segment.usedPages == 0) {  // due to `segmentPageClear()`
                segmentFree(segment, true);
                return null;
            } else if (reclaimed) {
                return RECLAIMED_SEGMENT_FLAG;
            } else {
                return segment;
            }
        }

        private static void pageUseDelayedFree(Page page, DELAYED_FLAG delay, boolean override_never) {
            while (!pageTryUseDelayedFree(page, delay, override_never)) {
                Thread.yield();
            }
        }

        private static boolean pageTryUseDelayedFree(Page page, DELAYED_FLAG delayedFlag, boolean override_never) {
            DELAYED_FLAG oldDeley;
            int yield_count = 0;
            do {
                oldDeley = page.threadDelayedFreeFlag.get();
                if (oldDeley == DELAYED_FREEING) {
                    if (yield_count >= 4) {
                        return false;  // Give up after 4 tries
                    }
                    yield_count++;
                    Thread.yield(); // Delay until outstanding DELAYED_FREEING are done.
                } else if (delayedFlag == oldDeley) {
                    break; // Avoid atomic operation if already equal.
                } else if (!override_never && oldDeley == NEVER_DELAYED_FREE) {
                    break; // Leave never-delayed flag set.
                }
            } while ((oldDeley == DELAYED_FREEING) || !page.threadDelayedFreeFlag.compareAndSet(oldDeley, delayedFlag));
            return true; // Success
        }

        private void segmentFree(Segment segment, boolean force) {
            // If it's a huge segment, then `force` must be true.
            assert segment.kind != SEGMENT_HUGE || force;
            // Don't free the segment if: `isForce` == false && `segmentsCount` < 2.
            if (!force && this.segmentTld.segmentsCount < 2) {
                return;
            }
            if (segment.kind != SEGMENT_HUGE) {
                // Remove the free pages
                Span slice = segment.slices[0];
                Span end = segment.slices[segment.sliceEntries];
                while (slice.sliceIndex < end.sliceIndex) {
                    if (slice.blockSize == 0) {
                        segmentSpanRemoveFromQueue(slice);
                    }
                    slice = segment.slices[slice.sliceIndex + slice.sliceCount];
                }
            }
            // Free it.
            segmentOsFree(segment);
        }

        private void segmentOsFree(Segment segment) {
            segmentsTrackSize(-segment.segmentSize);
            if (segment.wasReclaimed) {
                segmentTld.reclaimCount--;
                segment.wasReclaimed = false;
            }
            segment.deallocate();
        }

        // Called from segments when reclaiming abandoned pages.
        private void pageReclaim(Page page) {
            // TODO: push on full queue immediately if it is full?
            PageQueue pq = pageQueue(page.blockSize);
            pageQueuePush(pq, page);
        }

        // Possibly free pages and check if free space is available.
        private boolean segmentCheckFree(Segment segment, int slices_needed, int block_size) {
            boolean has_page = false;
            // For all slices
            Span slice = segment.slices[0];
            while (slice.sliceIndex < segment.sliceEntries) {
                if (slice.blockSize > 0) { // Used page
                    // Ensure used count is up to date and collect potential concurrent frees.
                    Page page = (Page) slice;
                    page.pageFreeCollect(false);
                    if (page.usedBlocks == 0) {
                        // If this page is all free now, free it without adding to any queues (yet).
                        segment.abandonedPages--;
                        slice = segmentPageClear(page); // Re-assign slice due to coalesce.
                        if (slice.sliceCount >= slices_needed) {
                            has_page = true;
                        }
                    } else if (page.blockSize == block_size && pageHasAnyAvailable(page)) {
                        // A page has available free blocks of the right size.
                        has_page = true;
                    }
                } else {
                    // Empty span.
                    if (slice.sliceCount >= slices_needed) {
                        has_page = true;
                    }
                }
                slice = segment.slices[slice.sliceIndex + slice.sliceCount];
            }
            return has_page;
        }

        // Are there any available blocks?
        private boolean pageHasAnyAvailable(Page page) {
            return page.usedBlocks < page.reservedBlocks || page.threadFreeList.get() != null;
        }

        // Note: can be called on abandoned pages
        private Span segmentPageClear(Page page) {
            Segment segment = page.segment;
            page.blockSize = 1;
            // Free it
            Span slice = segmentSpanFreeCoalesce((Span) page);
            segment.usedPages--;
            return slice;
        }

        // Note: can be called on abandoned segments.
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
            boolean is_abandoned = isSegmentAbandoned(segment);
            int slice_count = slice.sliceCount;
            Span next = segment.slices[slice.sliceIndex + slice.sliceCount];
            if (next.sliceIndex < segment.sliceEntries && next.blockSize == 0) {
                // Free the next block -- remove it from free and merge.
                assert next.sliceCount > 0 && next.sliceOffset == 0;
                slice_count += next.sliceCount; // extend
                if (!is_abandoned) {
                    segmentSpanRemoveFromQueue(next);
                }
            }
            if (slice.sliceIndex > 0) {
                Span prevFirst = sliceFirst(segment.slices, segment.slices[slice.sliceIndex - 1]);
                if (prevFirst.blockSize == 0) {
                    // Free previous slice -- remove it from free and merge.
                    assert prevFirst.sliceCount > 0 && prevFirst.sliceOffset == 0;
                    slice_count += prevFirst.sliceCount;
                    slice.sliceCount = 0;
                    // Set the slice offset.
                    slice.sliceOffset = slice.sliceIndex - prevFirst.sliceIndex;
                    if (!is_abandoned) {
                        segmentSpanRemoveFromQueue(prevFirst);
                    }
                    slice = prevFirst;
                }
            }
            // Add the new free page.
            segmentSpanFree(segment, slice.sliceIndex, slice_count);
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
            long total_count = this.allocator.abandonedSegmentCount.get();
            if (total_count == 0) {
                return 0;
            }
            // Avoid overflow.
            long relative_count = total_count > 10000 ? (total_count / 100) * perc : (total_count * perc) / 100;
            long max_tries = relative_count <= 1 ? 1 : (relative_count > 1024 ? 1024 : relative_count);
            if (max_tries < 8 && total_count > 8) {
                max_tries = 8;
            }
            return (int) max_tries;
        }

        // Allocate a segment.
        private Segment segmentAllocNormal() {
            int segment_slices = segmentCalculateSlices(0);
            int segment_size = segment_slices * SEGMENT_SLICE_SIZE;
            // Allocate the segment.
            Segment segment = new Segment(this.allocator, segment_size, segment_slices, SEGMENT_NORMAL);
            segmentsTrackSize(segment.segmentSize);
            // Initialize the initial free pages.
            segmentSpanFree(segment, 0, segment.sliceEntries);
            return segment;
        }

        private Page segmentAllocHuge(int required) {
            int segment_slices = segmentCalculateSlices(required);
            int segment_size = segment_slices * SEGMENT_SLICE_SIZE;
            // Allocate the segment.
            Segment segment = new Segment(this.allocator, segment_size, segment_slices, SEGMENT_HUGE);
            segmentsTrackSize(segment.segmentSize);
            // Initialize the initial free pages.
            return segmentSpanAllocate(segment, 0, segment_slices);
        }

        private int segmentCalculateSlices(int required) {
            int segment_size = required == 0 ? DEFAULT_SEGMENT_SIZE : alignUp(required, SEGMENT_SLICE_SIZE);
            return segment_size / SEGMENT_SLICE_SIZE;
        }

        private void segmentsTrackSize(long segment_size) {
            segmentTld.segmentsCount += segment_size >= 0 ? 1 : -1;
            if (segmentTld.segmentsCount > segmentTld.segmentsPeakCount) {
                segmentTld.segmentsPeakCount = segmentTld.segmentsCount;
            }
            segmentTld.segmentsCurrentSize += segment_size;
            if (segmentTld.segmentsCurrentSize > segmentTld.segmentsPeakSize) {
                segmentTld.segmentsPeakSize = segmentTld.segmentsCurrentSize;
            }
        }

        private Page segmentsPageFindAndAllocate(int sliceCount) {
            // Search from best fit up.
            int sqIndex = spanQueueIndex(sliceCount);
            if (sliceCount == 0) {
                sliceCount = 1;
            }
            for (int i = sqIndex; i < this.segmentTld.spanQueues.length; i++) {
                SpanQueue sq = this.segmentTld.spanQueues[i];
                for (Span slice = sq.firstSpan; slice != null; slice = slice.nextSpan) {
                    if (slice.sliceCount >= sliceCount) { // Found a suitable page span.
                        spanQueueDelete(sq, slice);
                        Segment segment = slice.segment;
                        if (slice.sliceCount > sliceCount) {
                            segmentSliceSplit(segment, slice, sliceCount);
                        }
                        return segmentSpanAllocate(segment, slice.sliceIndex, slice.sliceCount);
                    }
                }
            }
            // Could not find a page.
            return null;
        }

        private Page segmentSpanAllocate(Segment segment, int slice_index, int slice_count) {
            Span slice = segment.slices[slice_index];
            // Convert the slices to a page.
            slice.sliceOffset = 0;
            slice.sliceCount = slice_count;
            slice.blockSize = slice_count * SEGMENT_SLICE_SIZE;
            int extra = slice_count - 1;
            if (extra > MAX_SLICE_OFFSET_COUNT) {
                extra = MAX_SLICE_OFFSET_COUNT;
            }
            if (slice_index + extra >= segment.sliceEntries) {
                // Huge objects may have more slices than available entries in the segment.slices[].
                extra = segment.sliceEntries - slice_index - 1;
            }
            Span slice_next = segment.slices[slice.sliceIndex + 1];
            for (int i = 1; i <= extra; i++) {
                slice_next.sliceOffset = i;
                slice_next.sliceCount = 0;
                slice_next.blockSize = 1;
                slice_next = segment.slices[slice_next.sliceIndex + 1];
            }
            // And also for the last one (if not set already).
            int lastIndex = slice.sliceIndex + slice_count - 1;
            int endIndex = segment.sliceEntries;
            if (lastIndex > endIndex) {
                lastIndex = endIndex;
            }
            if (lastIndex > slice.sliceIndex) {
                Span last = segment.slices[lastIndex];
                last.sliceOffset = last.sliceIndex - slice.sliceIndex;
                last.sliceCount = 0;
                last.blockSize = 1;
            }
            // And initialize the page.
            segment.usedPages++;
            // Convert to Page.
            Page page = slice;
            page.isHuge = segment.kind == SEGMENT_HUGE;
            return page;
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

        // Note: can be called on abandoned segments.
        private void segmentSpanFree(Segment segment, int sliceIndex, int sliceCount) {
            SpanQueue sq = segment.kind == SEGMENT_HUGE || isSegmentAbandoned(segment) ?
                    null : getSpanQueue(sliceCount);
            if (sliceCount == 0) {
                sliceCount = 1;
            }
            // Set first and last slice (the intermediates can be undetermined).
            Span slice = segment.slices[sliceIndex];
            slice.sliceCount = sliceCount;
            slice.sliceOffset = 0;
            if (sliceCount > 1) {
                int lastIndex = sliceIndex + sliceCount - 1;
                int endIndex = segment.sliceEntries;
                if (lastIndex > endIndex) {
                    lastIndex = endIndex;
                }
                Span last = segment.slices[lastIndex];
                last.sliceCount = 0;
                last.sliceOffset = sliceCount - 1;
                last.blockSize = 0;
            }
            // And push it on the free page queue (if it was not a huge page).
            if (sq != null) {
                spanQueuePush(sq, slice);
            } else {
                slice.blockSize = 0; // Mark the huge page as free anyway.
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
            return segment.ownerThread.get() == null;
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

        // Free a page with no more free blocks.
        private void pageFree(Page page, PageQueue pq, boolean force) {
            // Remove from the page list
            // (no need to do `heapDelayedFree` first as all blocks are already free).
            pageQueueRemove(pq, page);
            // And free it.
            page.heapx.set(null);
            page.capacityBlocks = 0;
            Block currentBlock = page.freeList;
            Block block = currentBlock;
            while (block != null) {
                currentBlock = block;
                blockDeque.offer(block);
                block = block.nextBlock;
                currentBlock.nextBlock = null;
            }
            page.freeList = null;
            currentBlock = page.localFreeList;
            block = currentBlock;
            while (block != null) {
                currentBlock = block;
                blockDeque.offer(block);
                block = block.nextBlock;
                currentBlock.nextBlock = null;
            }
            page.localFreeList = null;
            segmentPageFree(page, force);
        }

        private void segmentPageFree(Page page, boolean force) {
            Segment segment = page.segment;
            // Mark it as free now.
            segmentPageClear(page);
            if (segment.usedPages == 0) {
                // No more used pages; remove it from the free list and free the segment.
                segmentFree(segment, force);
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
            // Remove the free pages from the free page queues.
            Span slice = segment.slices[0];
            Span end = segment.slices[segment.sliceEntries];
            while (slice.sliceIndex < end.sliceIndex) {
                if (slice.blockSize == 0) { // a free page
                    segmentSpanRemoveFromQueue(slice);
                    slice.blockSize = 0; // but keep it free
                }
                slice = segment.slices[slice.sliceIndex + slice.sliceCount];
            }
            // All pages in the segment are abandoned; add it to the abandoned list.
            segmentsTrackSize(-segment.segmentSize);
            segment.abandonedVisits = 1;   // From 0 to 1 to signify it is abandoned.
            if (segment.wasReclaimed) {
                this.segmentTld.reclaimCount--;
                segment.wasReclaimed = false;
            }
            segmentMarkAbandoned(segment);
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
                this.heapQueueFirstUpdate(pq);
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
            this.heapQueueFirstUpdate(pq);
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

        private void pageQueueEnqueueFromEx(PageQueue to, PageQueue from, boolean enqueue_at_end, Page page) {
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
            if (enqueue_at_end) {
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
            int size = pq.blockSize;
            if (size > PAGES_FREE_DIRECT_SIZE_MAX) {
                return;
            }
            Page page = pq.firstPage;
            if (pq.firstPage == null) {
                page = EMPTY_PAGE;
            }
            // Find index in the right direct page array.
            int start;
            int idx = toWordSize(size);
            if (this.pagesFreeDirect[idx] == page) {
                return;  // already set
            }
            // Find start slot.
            if (idx <= 1) {
                start = 0;
            } else {
                // Find previous size; due to minimal alignment upto 3 previous bins may need to be skipped.
                int bin = pageQueueIndex(size);
                PageQueue prev = this.pageQueues[pq.index - 1];
                while (bin == pageQueueIndex(prev.blockSize) && prev.index > 0) {
                    prev = this.pageQueues[prev.index - 1];
                }
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
        Page firstPage;
        Page lastPage;
        final int blockWords;
        final int blockSize;
        final int index;

        PageQueue(int blockWords, int index) {
            this.blockWords = blockWords;
            this.blockSize = blockWords * WORD_SIZE;
            this.index = index;
        }
    }

    enum DELAYED_FLAG {
        USE_DELAYED_FREE,
        DELAYED_FREEING,
        NO_DELAYED_FREE,
        NEVER_DELAYED_FREE
    }

    enum SEGMENT_KIND {
        SEGMENT_NORMAL, // `SEGMENT_SIZE` size with pages inside.
        SEGMENT_HUGE,   // Segment with just one huge page inside.
    }

    static class Page {
        Segment segment;
        int capacityBlocks; // number of blocks created.
        int reservedBlocks; // number of blocks reserved.
        boolean isInFull;
        byte retireExpire = DEFAULT_PAGE_RETIRE_EXPIRE; // Expiration count for retired blocks.
        Block freeList;
        Block localFreeList;
        int usedBlocks; // number of blocks in use (including blocks in `thread-free list`)
        final AtomicReference<Block> threadFreeList = new AtomicReference<>();
        Page nextPage;
        Page prevPage;
        int adjustment;
        int sliceCount;
        int blockSize;
        final AtomicReference<DELAYED_FLAG> threadDelayedFreeFlag = new AtomicReference<>(USE_DELAYED_FREE);
        boolean isHuge; // `true` if the page is in a huge segment (segment.kind == SEGMENT_HUGE)
        final AtomicReference<LocalHeap> heapx = new AtomicReference<LocalHeap>();

        // Empty Page Constructor
        Page() { }

        // Abandon a page with used blocks at the end of a thread.
        // Note: only call if it is ensured that no references exist from
        // the `page->heap->threadDelayedFreeList` into this page.
        // Currently only called through `heapCollectEx` which ensures this.
        private void pageAbandon(PageQueue pq, LocalHeap heap) {
            // page is no longer associated with heap.
            this.heapx.set(null);
            // remove from page queues.
            heap.pageQueueRemove(pq, this);
            // abandon it.
            heap.segmentPageAbandon(this);
        }

        // Regular free of a local thread block.
        private void freeBlockLocal(Block block, boolean check_full, LocalHeap heap) {
            // Actual free: push on the local free list.
            block.nextBlock = this.localFreeList;
            this.localFreeList = block;
            if (--this.usedBlocks == 0) {
                this.pageRetire(heap);
            } else if (check_full && this.isInFull) {
                this.pageUnfull(heap);
            }
        }

        // Retire a page with no more used blocks.
        // Important to not retire too quickly though as new allocations might coming.
        private void pageRetire(LocalHeap heap) {
            // Don't retire too often.
            // (or we end up retiring and re-allocating most of the time).
            // For now, we don't retire if it is the only page left of this size class.
            Page page = this;
            PageQueue pq = heap.heapPageQueueOf(page);
            if (PAGE_RETIRE_CYCLES > 0) {
                int bSize = page.blockSize;
                if (pq.index < PAGE_QUEUE_BIN_LARGE_INDEX) {  // not full && not huge queue
                    if (pq.lastPage == page && pq.firstPage == page) { // the only page in the queue
                        page.retireExpire =
                                (byte) (bSize <= SMALL_BLOCK_SIZE_MAX ? PAGE_RETIRE_CYCLES : PAGE_RETIRE_CYCLES / 4);
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
            }
            heap.pageFree(page, pq, false);
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
                this.pageThreadFreeCollect();
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
            int max_count = this.capacityBlocks; // cannot collect more than capacity
            int count = 1;
            Block tail = head;
            Block next;
            while ((next = tail.nextBlock) != null && count <= max_count) {
                count++;
                tail = next;
            }
            // If `count > max_count` there was a memory corruption.
            // (possibly infinite list due to double multi-threaded free)
            if (count > max_count) {
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

        Block() { }

        static Block copyBlock(Block block) {
            Block copy = new Block();
            copy.page = block.page;
            copy.blockBytes = block.blockBytes;
            copy.blockAdjustment = block.blockAdjustment;
            copy.nextBlock = block.nextBlock;
            return copy;
        }
    }

    private AbstractByteBuf newChunk(int size) {
        return chunkAllocator.allocate(size, size);
    }

    static final class SpanQueue {
        Span firstSpan;
        Span lastSpan;
        final int sliceCount;
        final int index;
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
        private final AtomicReference<Thread> ownerThread = new AtomicReference<>();
        // One extra final entry for huge blocks.
        final Span[] slices = new Span[DEFAULT_SLICE_COUNT + 1];
        int sliceEntries; // Entries in the `slices` array, at most `DEFAULT_SLICE_COUNT`
        int segmentSlices; // For huge segments, this may be different from `DEFAULT_SLICE_COUNT`
        int usedPages; // count of pages in use
        // Abandoned pages (i.e. the original owning thread stopped) (`abandoned <= used`)
        int abandonedPages;
        // Count how often this segment is visited during abandoned reclamation (to force reclaim if it takes too long).
        int abandonedVisits;
        boolean wasReclaimed; // True if it was reclaimed (used to limit on-free reclamation).
        final SEGMENT_KIND kind;

        Segment(MiMallocByteBufAllocator parent, int segmentSize, int segmentSlices, SEGMENT_KIND kind) {
            this.parent = parent;
            this.delegate = parent.newChunk(segmentSize);
            ownerThread.set(kind == SEGMENT_HUGE ? null : Thread.currentThread());
            this.segmentSize = segmentSize;
            this.parent.usedMemory.addAndGet(segmentSize);
            this.segmentSlices = segmentSlices;
            this.sliceEntries = Math.min(segmentSlices, DEFAULT_SLICE_COUNT);
            this.usedPages = 0;
            this.kind = kind;
            for (int i = 0; i < slices.length; i++) {
                slices[i] = new Span(this, i * SEGMENT_SLICE_SIZE, 1,
                        null, null, i);
            }
        }

        void deallocate() {
            if (this.delegate != null) {
                this.delegate.release();
                this.parent.usedMemory.addAndGet(-this.segmentSize);
            }
            this.ownerThread.set(null);
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
        Segment segment = block.page.segment;
        if (segment == null) {
            return;
        }
        boolean is_local = segment.ownerThread.get() == Thread.currentThread();
        Page page = block.page;
        if (is_local) { // thread-local free.
            LocalHeap heap = THREAD_LOCAL_HEAP.get();
            if (!page.isInFull) { // It is not in a full page (full pages need to move from the full bin).
                page.freeBlockLocal(block, false, heap);
            } else {
                // Page is full, use the generic free path.
                freeGenericLocal(page, segment, block, heap);
            }
        } else {
            // Not thread-local, use the generic multi-threaded-free path.
            freeGenericMt(page, segment, block);
        }
    }

    // Multi-threaded-free, or free a huge block.
    private void freeBlockMt(Page page, Segment segment, Block block) {
        if (segment.kind == SEGMENT_HUGE) {
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
        boolean use_delayed;
        do {
            use_delayed = page.threadDelayedFreeFlag.get() == USE_DELAYED_FREE;
        } while (use_delayed && !page.threadDelayedFreeFlag.compareAndSet(USE_DELAYED_FREE, DELAYED_FREEING));
        // If this was the first non-local free, we need to push it on the heap delayed free list.
        // `use_delayed` will only be true if `threadDelayedFreeFlag == USE_DELAYED_FREE`.
        if (use_delayed) {
            // Racy read on `heap`, but ok because `DELAYED_FREEING` is set.
            // (see `heapCollectAbandon`)
            LocalHeap heap = page.heapx.get();
            if (heap != null) {
                // Add to the delayed free list of this heap.
                Block dfree;
                do {
                    dfree = heap.threadDelayedFreeList.get();
                    block.nextBlock = dfree;
                } while (!heap.threadDelayedFreeList.compareAndSet(dfree, block));
            }
            // Reset the `DELAYED_FREEING` flag.
            boolean isReset;
            do {
                isReset = page.threadDelayedFreeFlag.compareAndSet(DELAYED_FREEING, NO_DELAYED_FREE);
            } while (!isReset);
        } else { // Common path
            Block current;
            do {
                current = page.threadFreeList.get();
                block.nextBlock = current;
            } while (!page.threadFreeList.compareAndSet(current, block));
        }
    }

    // Free huge block from another thread
    private void segmentHugePageFree(Segment segment, Page page, Block block) {
        // Huge page segments are always abandoned and can be freed immediately by any thread claim it and free.
        LocalHeap heap = THREAD_LOCAL_HEAP.get();
        // If this is the last reference, the CAS should always succeed.
        if (segment.ownerThread.compareAndSet(null, Thread.currentThread())) {
            block.nextBlock = page.freeList;
            page.freeList = block;
            page.usedBlocks--;
            heap.segmentPageFree(page, true);
        }
    }

    private void freeGenericMt(Page page, Segment segment, Block block) {
        freeBlockMt(page, segment, block);
    }

    private void freeGenericLocal(Page page, Segment segment, Block block, LocalHeap heap) {
        page.freeBlockLocal(block, true, heap);
    }

    /**
     * Allocate into the given buffer. Used by {@link AdaptivePoolingAllocator.AdaptiveByteBuf#capacity(int)}.
     */
    void reallocate(int size, int maxCapacity, MiByteBuf into) {
        MiByteBuf result = allocate(size, maxCapacity, into);
        assert result == into: "Re-allocation created separate buffer instance";
    }

    ByteBuf allocate(int size, int maxCapacity) {
        return allocate(size, maxCapacity, null);
    }

    private MiByteBuf allocate(int size, int maxCapacity, MiByteBuf byteBuf) {
        LocalHeap localHeap = THREAD_LOCAL_HEAP.get();
        if (size <= PAGES_FREE_DIRECT_SIZE_MAX) {
            int wSize = toWordSize(size);
            Page page = localHeap.pagesFreeDirect[wSize];
            // Fast path
            Block block = page.freeList;
            if (block != null) {
                if (byteBuf == null) {
                    byteBuf = block;
                }
                page.freeList = block.nextBlock;
                byteBuf.init(block, size, maxCapacity);
                page.usedBlocks++;
                return byteBuf;
            }
        }
        return allocateGeneric(size, maxCapacity, byteBuf, localHeap);
    }

    private MiByteBuf allocateGeneric(int size, int maxCapacity, MiByteBuf byteBuf, LocalHeap heap) {
        // Do administrative tasks every N generic allocations.
        if (++heap.genericCount >= 100) {
            heap.genericCollectCount += heap.genericCount;
            heap.genericCount = 0;
            // Call potential deferred free routines,
            // free delayed frees from other threads (but skip contended ones).
            heap.heapDelayedFreePartial();
            // Collect every once in a while.
            if (heap.genericCollectCount >= HEAP_OPTION_GENERIC_COLLECT) {
                heap.genericCollectCount = 0;
                heap.heapCollect(false, false);
            }
        }
        Page page = heap.findPage(size);
        if (page == null) { // First time out of memory, try to collect and retry the allocation once more.
            heap.heapCollect(true, false);
            page = heap.findPage(size);
        }
        if (page == null) { // out of memory
            PlatformDependent.throwException(new RuntimeException("Unable to allocate " + size + " bytes"));
        }
        Block block = page.freeList;
        if (byteBuf == null) {
            byteBuf = block;
        }
        page.freeList = block.nextBlock;
        byteBuf.init(block, size, maxCapacity);
        page.usedBlocks++;
        // Move page to the full queue.
        if (page.reservedBlocks == page.usedBlocks) {
            heap.pageToFull(page, heap.heapPageQueueOf(page));
        }
        return byteBuf;
    }

    private static int pageBin(Page page) {
        return page.isInFull ? PAGE_QUEUE_BIN_FULL_INDEX : page.isHuge ?
                PAGE_QUEUE_BIN_LARGE_INDEX : pageQueueIndex(page.blockSize);
    }

    private static int toWordSize(int size) {
        return (size + WORD_SIZE_MASK) >> 3;
    }

    private static int pageQueueIndex(int size) {
        assert size >= 0;
        int wSize = toWordSize(size);
        if (wSize <= 8) {
            return (wSize == 0) ? 1 : wSize;
        }
        if (wSize > MEDIUM_BLOCK_WORD_SIZE_MAX) {
            return PAGE_QUEUE_BIN_LARGE_INDEX;
        }
        wSize--;
        int p = 31 - Integer.numberOfLeadingZeros(wSize);
        return ((p << 2) | ((wSize >> (p - 2)) & 0x03)) - 3;
    }

    private static int spanQueueIndex(int sliceCount) {
        assert sliceCount > 0;
        if (sliceCount <= 8) {
            return sliceCount;
        }
        sliceCount--;
        int s = 31 - Integer.numberOfLeadingZeros(sliceCount);
        return ((s << 2) | ((sliceCount >> (s - 2)) & 0x03)) - 4;
    }

    private static int alignUp(int sz, int alignment) {
        int mask = alignment - 1;
        if ((alignment & mask) == 0) {  // If alignment is power of two.
            return (sz + mask) & ~mask;
        } else {
            return ((sz + mask) / alignment) * alignment;
        }
    }

    // Round to a good OS allocation size (bounded by max 12.5% waste).
    static int getGoodOsAllocSize(int size) {
        int align_size;
        if (size < 512 * KiB) {
            align_size = DEFAULT_OS_PAGE_SIZE;
        } else if (size < 2 * MiB) {
            align_size = 64 * KiB;
        } else if (size < 8 * MiB) {
            align_size = 256 * KiB;
        } else if (size < 32 * MiB) {
            align_size = 1 * MiB;
        } else {
            align_size = 4 * MiB;
        }
        return alignUp(size, align_size);
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

        void init(Block block, int length, int maxCapacity) {
            assert block != null;
            block.nextBlock = null;
            this.resetRefCnt();
            this.discardMarks();
            this.block = block;
            this.length = length;
            this.maxFastCapacity = block.blockBytes;
            this.adjustment = block.blockAdjustment;
            maxCapacity(maxCapacity);
            setIndex0(0, 0);
            this.rootParent = block.page.segment.delegate;
            this.tmpNioBuf = null;
            this.hasArray = block.page.segment.delegate.hasArray();
            this.hasMemoryAddress = block.page.segment.delegate.hasMemoryAddress();
        }

        @Override
        protected void deallocate() {
            assert this.block != null;
            this.length = 0;
            this.maxFastCapacity = 0;
            this.adjustment = 0;
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
            Block oldBlock = Block.copyBlock(this.block);
            MiMallocByteBufAllocator allocator = oldBlock.page.segment.parent;
            int readerIndex = this.readerIndex;
            int writerIndex = this.writerIndex;
            int baseOldRootIndex = adjustment;
            int oldCapacity = length;

            AbstractByteBuf oldRoot = rootParent();
            allocator.reallocate(newCapacity, maxCapacity(), this);
            oldRoot.getBytes(baseOldRootIndex, this, 0, oldCapacity);
            allocator.free(oldBlock);
            this.readerIndex = readerIndex;
            this.writerIndex = writerIndex;
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
            ByteBuffer tmp = (ByteBuffer) internalNioBuffer().clear().position(index);
            tmp.put(src, srcIndex, length);
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
            checkIndex(index, length);
            ByteBuffer tmp = (ByteBuffer) internalNioBuffer().clear().position(index);
            tmp.put(src.nioBuffer(srcIndex, length));
            return this;
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuffer src) {
            checkIndex(index, src.remaining());
            ByteBuffer tmp = (ByteBuffer) internalNioBuffer().clear().position(index);
            tmp.put(src);
            return this;
        }

        @Override
        public ByteBuf getBytes(int index, OutputStream out, int length)
                throws IOException {
            checkIndex(index, length);
            if (length != 0) {
                ByteBufUtil.readBytes(alloc(), internalNioBuffer().duplicate(), index, length, out);
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
                return in.read(internalNioBuffer(index, length).duplicate());
            } catch (ClosedChannelException ignored) {
                return -1;
            }
        }

        @Override
        public int setBytes(int index, FileChannel in, long position, int length)
                throws IOException {
            try {
                return in.read(internalNioBuffer(index, length).duplicate(), position);
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
                // Directly pass in the rootParent() with the adjusted index
                return ByteBufUtil.writeUtf8(rootParent(), idx(index), length, sequence, sequence.length());
            }
            if (charset.equals(CharsetUtil.US_ASCII) || charset.equals(CharsetUtil.ISO_8859_1)) {
                int length = sequence.length();
                if (expand) {
                    ensureWritable0(length);
                    checkIndex0(index, length);
                } else {
                    checkIndex(index, length);
                }
                // Directly pass in the rootParent() with the adjusted index
                return ByteBufUtil.writeAscii(rootParent(), idx(index), sequence, length);
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
