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
import io.netty.channel.unix.Buffer;
import io.netty.channel.unix.IovArray;
import io.netty.util.internal.CleanableDirectBuffer;

/**
 * The iovec memory of an {@link IoUringIoHandler}, shared by all writes until the next submit.
 * <p>
 * Each write gets its own empty {@link IovArray} that starts after the entries of the previous writes, so the
 * {@code IOV_MAX} limit of an {@link IovArray} applies to each write and not to all of them together.
 */
final class IoUringSharedIovArray {

    private final CleanableDirectBuffer cleanable;
    private final ByteBuf internalBuffer;
    private final int numEntries;
    // Index of the first entry of the last write.
    private int current;
    // The IovArray of the last write since clear(), null if there is none.
    private IovArray last;

    @SuppressWarnings("deprecation")
    IoUringSharedIovArray(int numEntries) {
        this.numEntries = numEntries;
        this.cleanable = Buffer.allocateDirectBufferWithNativeOrder(Math.multiplyExact(numEntries, IovArray.IOV_SIZE));
        this.internalBuffer = Unpooled.wrappedBuffer(cleanable.buffer());
    }

    private int nextStart() {
        return last == null ? 0 : current + last.count();
    }

    /**
     * Returns {@code true} if the next write can not add any entry.
     */
    boolean isFull() {
        return nextStart() == numEntries;
    }

    /**
     * Returns the empty {@link IovArray} for the next write.
     */
    IovArray next() {
        current = nextStart();
        // IovArray expects an empty buffer, while a slice is full.
        last = new IovArray(internalBuffer.slice(
                current * IovArray.IOV_SIZE, (numEntries - current) * IovArray.IOV_SIZE).clear());
        return last;
    }

    /**
     * Must only be called once the kernel does not read the entries anymore, which is after the submit.
     */
    void clear() {
        current = 0;
        last = null;
    }

    void release() {
        cleanable.clean();
    }
}
