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
import io.netty.util.ReferenceCounted;
import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.internal.MathUtil;

import java.util.Arrays;

import static io.netty.channel.uring.AbstractIoUringStreamChannel.IovBufferCollector.remainingIovs;

final class PendingZeroCopyWrites {
    private static final int MAX_POOLED_ID = Short.MAX_VALUE;

    private PendingWrite[] pooled = new PendingWrite[4];
    private short[] freeIds = new short[4];
    private int freeIdCount;
    private int issuedIds;
    private long nextOverflowId = MAX_POOLED_ID + 1L;
    private LongObjectHashMap<PendingWrite> overflow;

    long nextUserData() {
        if (freeIdCount > 0) {
            return freeIds[--freeIdCount];
        }
        return issuedIds < MAX_POOLED_ID ? ++issuedIds : nextOverflowId++;
    }

    PendingWrite register(long userData) {
        if (userData > MAX_POOLED_ID) {
            return registerOverflow(userData);
        }
        int id = (int) userData;
        if (id >= pooled.length) {
            pooled = Arrays.copyOf(pooled, MathUtil.safeFindNextPositivePowerOfTwo(id + 1));
        }
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

    // A request without MORE has no notification slot; only its ID needs to be returned.
    void recycle(long userData) {
        if (userData <= MAX_POOLED_ID) {
            if (freeIdCount == freeIds.length) {
                freeIds = Arrays.copyOf(freeIds, freeIdCount << 1);
            }
            freeIds[freeIdCount++] = (short) userData;
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
                references[i].release();
                references[i] = null;
            }
            count = 0;
        }
    }
}
