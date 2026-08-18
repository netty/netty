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

import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;

/**
 * A slot tracking the references a submitted SQE handed to the kernel. Recording does not retain: the outbound buffer
 * owns them until the write completes or {@link #retainReferences()} takes over. Slots are reused and the backing
 * array is kept across reuses, so a slot allocates only on its first use and when a write hands it more references
 * than any earlier use did. {@link #abandon()} is how a slot is ended outside of a completion CQE -- see its
 * javadoc for the three situations that call it.
 */
final class WriteOperation {
    private static final ReferenceCounted[] EMPTY_REFERENCES = new ReferenceCounted[0];

    private byte opCode;
    private ReferenceCounted[] references = EMPTY_REFERENCES;
    private int count;
    private boolean active;
    private int retainedCount;

    void record(byte opCode, ReferenceCounted reference) {
        if (isActive()) {
            throw new IllegalStateException("slot still owned by an operation that has not completed");
        }
        if (references.length < 1) {
            references = new ReferenceCounted[1];
        }
        references[0] = reference;
        count = 1;
        this.opCode = opCode;
        active = true;
        retainedCount = 0;
    }

    /**
     * Copies {@code references} so the caller may reuse the array: a zero-copy slot stays alive from its primary CQE
     * until the follow-up {@code IORING_CQE_F_NOTIF}, during which a reused collector array would be overwritten.
     */
    void record(byte opCode, ReferenceCounted[] references, int count) {
        if (isActive()) {
            throw new IllegalStateException("slot still owned by an operation that has not completed");
        }
        if (this.references.length < count) {
            this.references = new ReferenceCounted[count];
        }
        System.arraycopy(references, 0, this.references, 0, count);
        this.count = count;
        this.opCode = opCode;
        active = true;
        retainedCount = 0;
    }

    /**
     * NOOP once every reference is retained or while the slot is inactive. {@code retainedCount} only advances
     * past an index once its {@code retain()} call actually returns, so a {@code retain()} that throws partway
     * leaves {@link #finish()} to release exactly the references that were retained, instead of over-releasing
     * the ones that never were.
     */
    void retainReferences() {
        if (!isActive() || retainedCount == count) {
            return;
        }
        for (int i = retainedCount; i < count; i++) {
            references[i].retain();
            retainedCount = i + 1;
        }
    }

    /**
     * Whether this slot is occupied. A submitted SQE that ended up with zero references still occupies it.
     */
    boolean isActive() {
        return active;
    }

    byte opCode() {
        return opCode;
    }

    /**
     * Releases the slot on the terminal CQE: a notification, or any completion without {@code IORING_CQE_F_MORE}.
     */
    void complete(int cqeFlags) {
        if ((cqeFlags & Native.IORING_CQE_F_NOTIF) != 0 || (cqeFlags & Native.IORING_CQE_F_MORE) == 0) {
            finish();
        }
    }

    /**
     * Ends this slot without it ever seeing a completion CQE. Called in three situations: (1) submission itself
     * failed, so the kernel never saw the SQE and no CQE will ever arrive for it; (2) deregistration discards a
     * slot whose completion this channel can no longer observe, and {@link #retainReferences()} never ran on it,
     * making this call a plain discard; (3) deregistration discards a slot that {@link #retainReferences()} did
     * retain before a shutdown, in which case this call is what actually releases those references.
     */
    void abandon() {
        finish();
    }

    private void finish() {
        if (!active) {
            return;
        }
        active = false;
        int releaseCount = retainedCount;
        retainedCount = 0;
        int finishedCount = count;
        count = 0;
        for (int i = 0; i < finishedCount; i++) {
            if (i < releaseCount) {
                // A completion, a failed submission and a deregistration all end up in this loop, so a reference
                // that fails to release must not strand the ones after it or leave them reachable through the array.
                ReferenceCountUtil.safeRelease(references[i]);
            }
            references[i] = null;
        }
    }
}
