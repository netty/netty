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
    private byte opCode;
    private ReferenceCounted[] references;
    private boolean done;

    /**
     * Takes ownership of the references a submitted SQE handed to the kernel. Instances live in the channel's slot
     * array and are reused, so this also clears the state left by the previous operation in the slot.
     */
    void retain(byte opCode, ReferenceCounted... references) {
        if (this.references != null) {
            throw new IllegalStateException("slot still owned by an operation that has not completed");
        }
        for (ReferenceCounted reference : references) {
            reference.retain();
        }
        this.opCode = opCode;
        this.references = references;
        this.done = false;
    }

    /**
     * Whether this slot currently holds an operation that has not been released yet.
     */
    boolean isActive() {
        return references != null;
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
            release();
        }
    }

    void rollback() {
        release();
    }

    boolean isDone() {
        return done;
    }

    private void release() {
        if (done) {
            return;
        }
        done = true;
        ReferenceCounted[] references = this.references;
        this.references = null;
        if (references != null) {
            for (ReferenceCounted reference : references) {
                reference.release();
            }
        }
    }
}
