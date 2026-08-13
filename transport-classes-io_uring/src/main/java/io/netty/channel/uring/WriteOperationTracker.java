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

import io.netty.util.ReferenceCounted;
import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.Arrays;

/**
 * Owns every in-flight write operation a channel tracks, across the four namespaces a channel can have live at
 * once: a pooled slot array (ids this class hands out itself), an overflow map (a long-id fallback once the
 * pooled range is exhausted), a foreign slot array (ids an allocator this class does not own hands the caller,
 * such as a {@link MsgHdrMemoryArray} index), and a single slot for the one non-zero-copy write a stream channel
 * can have outstanding at a time. One instance per channel, created unconditionally in the channel constructor.
 */
final class WriteOperationTracker {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(WriteOperationTracker.class);

    // Ids index the pooled array, so they run from 1 (0 is reserved for "no id") to Short.MAX_VALUE, the same
    // bound scheduleWrite(...) already puts on the number of outstanding writes.
    private static final int MAX_POOLED_ID = Short.MAX_VALUE;

    // Ids issued by nextId()/nextZeroCopyId(). Dense, reused through freeIds, so allocating one never searches.
    private WriteOperation[] pooled;
    // Ids issued by an allocator this class does not own, such as the MsgHdrMemoryArray index the datagram
    // sendmsg path submits directly. A separate array lets that namespace overlap the pooled one above.
    private WriteOperation[] foreign;
    private short[] freeIds;
    private int freeIdCount;
    private int issuedIds;

    // Only ever holds values above MAX_POOLED_ID. Such a value does not survive a round-trip through short,
    // so IoUringIoHandler.canUseFastPath(...) rejects it and the slow path preserves the full user_data. The
    // counter only grows and never recycles, so a fallback id can never collide with a live one.
    private long nextOverflowId = ((long) MAX_POOLED_ID) + 1;
    // Never allocated before the short pool runs dry, so the common path never touches a map.
    private LongObjectHashMap<WriteOperation> overflow;

    // The one non-zero-copy write a stream channel can have in flight. Reused by direct field access -- no id,
    // no array, no opcode match -- because WRITE_SCHEDULED caps a stream channel to one outstanding write.
    // Exposed only through recordStream/completeStream/abandonStream/isStreamActive below; the field itself
    // never leaves this class.
    private final WriteOperation single = new WriteOperation();

    /**
     * Returns a write-operation id that no in-flight write owns, or {@code 0} when every id is held by a write
     * that has not seen its terminal CQE yet. Released ids come back through a free list, so this does not
     * search.
     */
    short nextId() {
        if (freeIdCount > 0) {
            return freeIds[--freeIdCount];
        }
        if (issuedIds == MAX_POOLED_ID) {
            return 0;
        }
        return (short) ++issuedIds;
    }

    /**
     * Returns a write-operation id for a zero-copy write, falling back to a long id outside the short range once
     * every short id is held by an in-flight write, so the write still gets submitted instead of being deferred.
     * Never returns 0.
     */
    long nextZeroCopyId() {
        short id = nextId();
        if (id != 0) {
            return id;
        }
        return nextOverflowId++;
    }

    /**
     * Registers a write whose id came from {@link #nextId()} or {@link #nextZeroCopyId()}. The id is recycled
     * once the terminal CQE arrives.
     */
    void record(long id, byte opCode, ReferenceCounted reference) {
        if (id > MAX_POOLED_ID) {
            recordOverflow(id, opCode, reference);
            return;
        }
        short slot = (short) id;
        WriteOperation[] grown = ensureCapacity(pooled, slot);
        if (grown != pooled) {
            pooled = grown;
        }
        slot(pooled, slot).record(opCode, reference);
    }

    // Split out of record(...) above so the overflow path -- taken only once every pooled id is in flight --
    // does not add to the bytecode size of the hot method and keep it eligible for inlining.
    private void recordOverflow(long id, byte opCode, ReferenceCounted reference) {
        overflowSlot(id).record(opCode, reference);
    }

    /**
     * Registers a write of multiple references whose id came from {@link #nextId()} or {@link #nextZeroCopyId()}.
     * Copies {@code references}, so the caller may reuse the array.
     */
    void record(long id, byte opCode, ReferenceCounted[] references, int count) {
        if (id > MAX_POOLED_ID) {
            recordOverflow(id, opCode, references, count);
            return;
        }
        short slot = (short) id;
        WriteOperation[] grown = ensureCapacity(pooled, slot);
        if (grown != pooled) {
            pooled = grown;
        }
        slot(pooled, slot).record(opCode, references, count);
    }

    // Split out for the same reason as the single-reference overflow above: keep it out of the hot method.
    private void recordOverflow(long id, byte opCode, ReferenceCounted[] references, int count) {
        overflowSlot(id).record(opCode, references, count);
    }

    /**
     * Registers a write whose id is owned by another allocator, such as the {@link MsgHdrMemoryArray} index used
     * by the datagram sendmsg path. That id lives in its own slot array and never enters the free list.
     */
    void recordForeign(short id, byte opCode, ReferenceCounted reference) {
        WriteOperation[] grown = ensureCapacity(foreign, id);
        if (grown != foreign) {
            foreign = grown;
        }
        slot(foreign, id).record(opCode, reference);
    }

    /**
     * Ends the slot identified by {@code id}/{@code opCode} without it seeing a completion CQE, at the caller's
     * choice: (1) the submission itself failed, so the kernel never saw the SQE, (2) deregistration discards a
     * slot whose completion this channel can no longer observe, and {@link #retainReferences(long, byte)} never
     * ran on it, making this call a plain discard, or (3) deregistration discards a slot that
     * {@link #retainReferences(long, byte)} did retain before a shutdown, in which case this call is what
     * actually releases those references.
     */
    void abandon(long id, byte opCode) {
        if (id > MAX_POOLED_ID) {
            abandonOverflow(id, opCode);
            return;
        }
        short slot = (short) id;
        WriteOperation op = matching(pooled, slot, opCode);
        if (op != null) {
            op.abandon();
            recycleId(slot);
            return;
        }
        op = matching(foreign, slot, opCode);
        if (op != null) {
            op.abandon();
        }
    }

    // Split out of abandon(...) above to keep the overflow path -- the rare case where every pooled id is
    // in flight -- out of the hot method's bytecode.
    private void abandonOverflow(long id, byte opCode) {
        WriteOperation op = matchingOverflow(id, opCode);
        if (op != null) {
            op.abandon();
            // Overflow ids are never recycled, so the entry has to go or the map grows without bound.
            overflow.remove(id);
        }
    }

    /**
     * Applies a completion CQE to the slot identified by {@code id}/{@code opCode}. A terminated pooled slot's id
     * is recycled back to the free list; a terminated overflow slot is removed from the map.
     */
    void complete(long id, byte opCode, int flags) {
        if (id > MAX_POOLED_ID) {
            completeOverflow(id, opCode, flags);
            return;
        }
        short slot = (short) id;
        WriteOperation op = matching(pooled, slot, opCode);
        if (op != null) {
            op.complete(flags);
            if (!op.isActive()) {
                recycleId(slot);
            }
            return;
        }
        op = matching(foreign, slot, opCode);
        if (op != null) {
            op.complete(flags);
        }
    }

    // Split out of complete(...) above for the same reason as abandonOverflow(...): keep the rare overflow
    // path out of the hot method's bytecode.
    private void completeOverflow(long id, byte opCode, int flags) {
        WriteOperation op = matchingOverflow(id, opCode);
        if (op != null) {
            op.complete(flags);
            if (!op.isActive()) {
                overflow.remove(id);
            }
        }
    }

    /**
     * Retains the references held by the active slot identified by {@code id}/{@code opCode}, if any. Used only
     * from the shutdown path.
     */
    void retainReferences(long id, byte opCode) {
        if (id > MAX_POOLED_ID) {
            retainOverflowReference(id, opCode);
            return;
        }
        short slot = (short) id;
        WriteOperation op = matching(pooled, slot, opCode);
        if (op != null) {
            op.retainReferences();
            return;
        }
        op = matching(foreign, slot, opCode);
        if (op != null) {
            op.retainReferences();
        }
    }

    // Split out of retainReferences(...) above for the same reason as abandonOverflow(...): keep the rare
    // overflow path out of the hot method's bytecode. Named distinctly from retainOverflow() below, which
    // retains every overflow slot instead of matching a single id/opCode pair.
    private void retainOverflowReference(long id, byte opCode) {
        WriteOperation op = matchingOverflow(id, opCode);
        if (op != null) {
            op.retainReferences();
        }
    }

    /**
     * The number of write operations parked in the overflow map. Only used to assert completions and abandons
     * drop their entry.
     */
    int overflowCount() {
        return overflow == null ? 0 : overflow.size();
    }

    /**
     * Records a single reference on the non-zero-copy stream write slot. No id, no array, no opcode match.
     */
    void recordStream(byte opCode, ReferenceCounted reference) {
        single.record(opCode, reference);
    }

    /**
     * Records multiple references (e.g. writev) on the non-zero-copy stream write slot. Copies {@code references}.
     */
    void recordStream(byte opCode, ReferenceCounted[] references, int count) {
        single.record(opCode, references, count);
    }

    /**
     * Ends the stream slot without it ever seeing a completion CQE. A no-op if the slot is inactive -- e.g.
     * deregistration at a point where there was no outstanding write to begin with. The same three situations
     * documented on {@link #abandon(long, byte)} apply here too, except there is no id, so there is no opcode
     * match either -- the slot simply finishes.
     */
    void abandonStream() {
        if (single.isActive()) {
            single.abandon();
        }
    }

    /**
     * Applies a completion CQE to the stream slot.
     */
    void completeStream(int flags) {
        single.complete(flags);
    }

    /**
     * Whether the stream slot is active. Test assertion only -- no production caller, same as
     * {@link #overflowCount()}.
     */
    boolean isStreamActive() {
        return single.isActive();
    }

    /**
     * Retains every active slot's references across all four members (pooled, foreign, overflow, single) right
     * before a shutdown, so a write completion that races the shutdown still finds a live reference to release
     * instead of one the outbound buffer already dropped.
     */
    void retainAll() {
        retainArray(pooled);
        retainArray(foreign);
        retainOverflow();
        single.retainReferences();
    }

    /**
     * Abandons every active slot across all four members and empties them. No further completion arrives once a
     * channel is deregistered, so references a shutdown retained on a slot would otherwise leak forever.
     */
    void releaseAll() {
        releaseArray(pooled);
        releaseArray(foreign);
        releaseOverflow();
        if (single.isActive()) {
            single.abandon();
        }
    }

    private static WriteOperation[] ensureCapacity(WriteOperation[] operations, short id) {
        if (operations == null) {
            return new WriteOperation[Math.max(id + 1, 4)];
        }
        if (id >= operations.length) {
            return Arrays.copyOf(operations, Math.max(id + 1, operations.length << 1));
        }
        return operations;
    }

    private static WriteOperation slot(WriteOperation[] operations, short id) {
        WriteOperation operation = operations[id];
        if (operation == null) {
            operation = operations[id] = new WriteOperation();
        }
        return operation;
    }

    private WriteOperation overflowSlot(long id) {
        if (overflow == null) {
            overflow = new LongObjectHashMap<WriteOperation>(2);
        }
        WriteOperation operation = overflow.get(id);
        if (operation == null) {
            operation = new WriteOperation();
            overflow.put(id, operation);
        }
        return operation;
    }

    private WriteOperation matchingOverflow(long id, byte opCode) {
        if (overflow == null) {
            return null;
        }
        WriteOperation operation = overflow.get(id);
        return operation != null && operation.isActive() && operation.opCode() == opCode ? operation : null;
    }

    private static WriteOperation matching(WriteOperation[] operations, short id, byte opCode) {
        if (operations == null || id < 0 || id >= operations.length) {
            return null;
        }
        WriteOperation operation = operations[id];
        // Write user_data is not allocated from a single namespace. The splice stages submit fixed values and the
        // domain-socket fd-passing sendmsg submits its MsgHdrMemory index, and neither is registered here, so both
        // can land on an occupied slot. Matching the opcode keeps such a completion from terminating what it finds.
        return operation != null && operation.isActive() && operation.opCode() == opCode ? operation : null;
    }

    private void recycleId(short id) {
        if (freeIds == null) {
            freeIds = new short[8];
        } else if (freeIdCount == freeIds.length) {
            freeIds = Arrays.copyOf(freeIds, freeIdCount << 1);
        }
        freeIds[freeIdCount++] = id;
    }

    private static void retainArray(WriteOperation[] operations) {
        if (operations == null) {
            return;
        }
        for (WriteOperation operation : operations) {
            if (operation == null) {
                continue;
            }
            // One slot failing to retain must not stop the remaining slots from being retained.
            try {
                operation.retainReferences();
            } catch (Throwable cause) {
                logger.warn("Failed to retain in-flight write operation before shutdown", cause);
            }
        }
    }

    private void retainOverflow() {
        if (overflow == null) {
            return;
        }
        for (WriteOperation operation : overflow.values()) {
            try {
                operation.retainReferences();
            } catch (Throwable cause) {
                logger.warn("Failed to retain in-flight write operation before shutdown", cause);
            }
        }
    }

    private static void releaseArray(WriteOperation[] operations) {
        if (operations == null) {
            return;
        }
        for (int i = 0; i < operations.length; i++) {
            WriteOperation operation = operations[i];
            if (operation == null) {
                continue;
            }
            if (operation.isActive()) {
                operation.abandon();
            }
            operations[i] = null;
        }
    }

    private void releaseOverflow() {
        if (overflow == null) {
            return;
        }
        for (WriteOperation operation : overflow.values()) {
            if (operation.isActive()) {
                operation.abandon();
            }
        }
        overflow.clear();
    }
}
