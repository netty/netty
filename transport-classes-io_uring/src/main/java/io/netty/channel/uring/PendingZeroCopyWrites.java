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
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import io.netty.util.collection.LongObjectHashMap;

import java.util.Arrays;

import static io.netty.channel.uring.AbstractIoUringStreamChannel.IovBufferCollector.remainingIovs;

/**
 * Retains buffers until their zero-copy notifications arrive.
 *
 * <p>{@code freeIdStack} is a LIFO stack of available IDs indexing {@code pooled}. Submission pops an ID;
 * a failed submission, a primary CQE without MORE, or the NOTIF CQE returns it. This allows out-of-order
 * notifications to recycle IDs independently.
 *
 * <p>The pool grows up to {@link Short#MAX_VALUE} IDs. If all are in use, for example because notifications
 * lag behind new writes, further requests receive increasing long IDs above that range. Their retained
 * buffers are stored in {@code overflow} until NOTIF, and these IDs are never reused. Returned pooled IDs
 * are preferred even while overflow requests remain pending.
 */
final class PendingZeroCopyWrites {
    private static final int MAX_POOLED_ID = Short.MAX_VALUE;

    private PendingWrite[] pooled;
    private short[] freeIdStack;
    // Next insertion index in the free ID stack.
    private int freeStackTop;
    // Overflow IDs are never reused; exhausting the positive long range is not feasible in practice.
    private long nextOverflowId = MAX_POOLED_ID + 1L;
    private LongObjectHashMap<PendingWrite> overflow;

    PendingZeroCopyWrites() {
        pooled = new PendingWrite[4];
        freeIdStack = new short[pooled.length];
        // Slot 0 is unused.
        for (int id = pooled.length - 1; id > 0; id--) {
            freeIdStack[freeStackTop++] = (short) id;
        }
    }

    long nextUserData() {
        if (freeStackTop > 0) {
            return freeIdStack[--freeStackTop];
        }
        return nextUserDataSlow();
    }

    private long nextUserDataSlow() {
        int oldCapacity = pooled.length;
        if (oldCapacity == MAX_POOLED_ID + 1) {
            return nextOverflowId++;
        }
        int newCapacity = oldCapacity << 1;
        pooled = Arrays.copyOf(pooled, newCapacity);
        freeIdStack = new short[newCapacity];
        for (int id = newCapacity - 1; id >= oldCapacity; id--) {
            freeIdStack[freeStackTop++] = (short) id;
        }
        return nextUserData();
    }

    PendingWrite register(long userData) {
        if (userData > MAX_POOLED_ID) {
            return registerOverflow(userData);
        }
        int id = (int) userData;
        PendingWrite write = pooled[id];
        if (write == null) {
            pooled[id] = write = new PendingWrite();
        }
        return write;
    }

    // Only used when all pooled IDs are in flight; registration preserves these IDs on its slow path.
    private PendingWrite registerOverflow(long userData) {
        if (overflow == null) {
            overflow = new LongObjectHashMap<>(2);
        }
        PendingWrite write = new PendingWrite();
        overflow.put(userData, write);
        return write;
    }

    void release(long userData) {
        PendingWrite write = userData <= MAX_POOLED_ID ?
                pooled[(int) userData] : overflow.remove(userData);
        write.release();
        recycle(userData);
    }

    // Also used for requests without MORE, which retain no buffers.
    void recycle(long userData) {
        if (userData <= MAX_POOLED_ID) {
            freeIdStack[freeStackTop++] = (short) userData;
        }
    }

    static final class PendingWrite implements ChannelOutboundBuffer.MessageProcessor {
        private ReferenceCounted[] references = new ReferenceCounted[1];
        private int count;
        private int remaining;

        void retain(ChannelOutboundBuffer buffer, int iovCount) {
            if (iovCount == 0) {
                return;
            }
            ByteBuf first = (ByteBuf) buffer.current();
            if (buffer.size() == 1 || iovCount == 1 && first.isReadable()) {
                add(first.retain());
                return;
            }
            remaining = iovCount;
            try {
                buffer.forEachFlushedMessage(this);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public boolean processMessage(Object msg) {
            ByteBuf buf = (ByteBuf) msg;
            if (buf.isReadable()) {
                add(buf.retain());
                remaining = remainingIovs(buf, remaining);
            }
            return remaining != 0;
        }

        void add(ReferenceCounted reference) {
            if (count == references.length) {
                references = Arrays.copyOf(references, count << 1);
            }
            references[count++] = reference;
        }

        private void release() {
            for (int i = 0; i < count; i++) {
                ReferenceCountUtil.safeRelease(references[i]);
                references[i] = null;
            }
            count = 0;
        }
    }
}
