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
import io.netty.channel.unix.IovArray;
import io.netty.util.ReferenceCounted;

import java.util.Arrays;

/**
 * Fills the {@link IoUringIoHandler}'s {@link IovArray} from flushed outbound messages and records the
 * {@link ByteBuf} behind each entry it added, so the caller can copy those references into a {@link WriteOperation}
 * slot once the SQE is built.
 *
 * <p>One instance per {@link IoUringIoHandler}, matching the {@link IovArray} it wraps: the handler hands out the
 * same {@link IovArray} instance to every channel it services, so a per-channel collector would be scoped smaller
 * than the array it fills. This collector is only ever valid between a {@link #reset()} and the {@link WriteOperation}
 * record call that copies its references out -- the caller then resets it from a {@code finally} that also covers
 * the submit, so every exit from the write path, including the ones that throw, leaves it empty. Without that reset
 * this instance, being permanently owned by the event loop rather than any one channel, would keep the previous
 * write's buffers reachable for as long as this event loop went without servicing another write.
 */
final class IovArrayReferenceCollector implements ChannelOutboundBuffer.MessageProcessor {
    private final IovArray iovArray;
    private ReferenceCounted[] references = new ReferenceCounted[4];
    private int count;

    IovArrayReferenceCollector(IovArray iovArray) {
        this.iovArray = iovArray;
    }

    /**
     * Drops the previous references, keeping the array for reuse. Nulls out the dropped entries too: otherwise a
     * smaller write reusing the collector after a larger one would leave stale {@code ByteBuf} references reachable
     * through the backing array until the next reset, which risks promoting them into an old generation.
     */
    void reset() {
        Arrays.fill(references, 0, count, null);
        count = 0;
    }

    @Override
    public boolean processMessage(Object msg) throws Exception {
        int previousCount = iovArray.count();
        boolean processed = iovArray.processMessage(msg);
        recordIfAdded(msg, previousCount);
        return processed;
    }

    /**
     * Records the buffer behind {@code msg} once {@link IovArray} actually gained an entry for it. Split out of
     * {@link #processMessage(Object)} so that method stays under HotSpot's default inline size threshold (35
     * bytes), which it exceeded with the {@code if} check below inlined.
     */
    private void recordIfAdded(Object msg, int previousCount) {
        // A 0-byte readable buffer makes IovArray.add(...) return true without adding an entry (see
        // IovArray.add(ByteBuf, int, int)), so it never needs a slot here either -- comparing count before and
        // after is what tells such a buffer apart from one that was actually added.
        if (iovArray.count() != previousCount) {
            add((ByteBuf) msg);
        }
    }

    ReferenceCounted[] referencesArray() {
        return references;
    }

    int referencesCount() {
        return count;
    }

    private void add(ByteBuf buffer) {
        if (count == references.length) {
            grow();
        }
        references[count++] = buffer;
    }

    /**
     * Doubles the backing array once it fills up. Split out of {@link #add(ByteBuf)} so that method stays under
     * HotSpot's default inline size threshold (35 bytes); growing is genuinely the rare branch here, since
     * {@link #reset()} clears the array in place for reuse instead of shrinking it back down.
     */
    private void grow() {
        references = Arrays.copyOf(references, count << 1);
    }
}
