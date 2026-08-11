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

final class WriteOperation {
    private static final ReferenceCounted[] EMPTY_REFERENCES = new ReferenceCounted[0];

    private byte opCode;
    private ReferenceCounted[] references = EMPTY_REFERENCES;
    private int count;
    private boolean active;
    private boolean retained;

    /**
     * Records the reference a submitted SQE handed to the kernel without retaining it: the outbound buffer already
     * owns the reference until its own completion path releases it. Instances live in the channel's slot array and
     * are reused, so this also clears the state left by the previous operation in the slot. The backing array is
     * kept across reuses of the slot, so recording a single reference only allocates on the first use of the id.
     */
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
        retained = false;
    }

    /**
     * Records the references a submitted multi-buffer SQE handed to the kernel, copying them into this operation's
     * own backing array. The caller is free to reuse or mutate {@code references} once this call returns; this
     * matters because zero-copy sends keep a slot alive across a primary CQE and its later
     * {@code IORING_CQE_F_NOTIF}, during which a reused collector array could otherwise be overwritten by the next
     * write. Instances live in the channel's slot array and are reused, so the backing array is kept across reuses
     * of the slot: recording only allocates the first time a slot needs to grow to fit a wider write.
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
        retained = false;
    }

    /**
     * Retains every reference this in-flight operation holds so a caller can release the outbound buffer's own
     * reference without a kernel completion racing it. Used both when a shutdown drops the outbound buffer early
     * and when a zero-copy send's primary completion arrives with {@code IORING_CQE_F_MORE} set, meaning the
     * kernel still owns the memory until the follow-up {@code IORING_CQE_F_NOTIF}. A no-op once already retained,
     * or when the slot is not currently active, so callers may call this repeatedly without double-retaining.
     * {@code retained} is set before the loop so that a reference whose {@code retain()} throws still leaves the
     * references retained so far eligible for release by {@link #finish()} instead of leaking them.
     */
    void retainReferences() {
        if (!isActive() || retained) {
            return;
        }
        retained = true;
        for (int i = 0; i < count; i++) {
            references[i].retain();
        }
    }

    /**
     * Whether this slot currently holds an operation that has not been released yet. This tracks occupancy of the
     * slot, not the number of references it holds: a submitted SQE that ended up with zero references (e.g. a
     * multi-buffer write whose buffers were all empty) still occupies the slot until its completion arrives.
     */
    boolean isActive() {
        return active;
    }

    /**
     * The opcode of the SQE that owns this operation. Write completions are keyed by the submitted
     * {@code user_data}, which is not allocated from a single namespace: the splice stages and the
     * domain-socket fd-passing sendmsg pick their own values. Matching the opcode as well keeps such a
     * completion from terminating an unrelated operation that happens to share the id.
     */
    byte opCode() {
        return opCode;
    }

    void complete(int cqeFlags) {
        if ((cqeFlags & Native.IORING_CQE_F_NOTIF) != 0 || (cqeFlags & Native.IORING_CQE_F_MORE) == 0) {
            finish();
        }
    }

    void rollback() {
        finish();
    }

    /**
     * Whether this slot no longer holds an operation, which is exactly the inverse of {@link #isActive()}: an
     * operation is done once its terminal completion or its rollback released the slot. A slot that was never
     * recorded into is reported as done as well, as there is nothing left to wait for in either case.
     */
    boolean isDone() {
        return !isActive();
    }

    private void finish() {
        if (!active) {
            return;
        }
        active = false;
        boolean release = retained;
        retained = false;
        int finishedCount = count;
        count = 0;
        for (int i = 0; i < finishedCount; i++) {
            if (release) {
                references[i].release();
            }
            references[i] = null;
        }
    }
}
