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
package io.netty.util.concurrent;

import io.netty.util.internal.MathUtil;
import io.netty.util.internal.ObjectUtil;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public interface MpscIntQueue {
    static MpscIntQueue create(int size, int emptyValue) {
        return new MpscAtomicIntegerArrayQueue(size, emptyValue);
    }

    boolean offer(int value);

    int poll();

    int drain(int limit, IntConsumer consumer);

    int fill(int limit, IntSupplier supplier);

    boolean isEmpty();

    int size();

    /**
     * This implementation is based on MpscAtomicUnpaddedArrayQueue from JCTools.
     */
    final class MpscAtomicIntegerArrayQueue extends AtomicIntegerArray implements MpscIntQueue {
        private static final long serialVersionUID = 8740338425124821455L;
        private static final AtomicLongFieldUpdater<MpscAtomicIntegerArrayQueue> PRODUCER_INDEX =
                AtomicLongFieldUpdater.newUpdater(MpscAtomicIntegerArrayQueue.class, "producerIndex");
        private static final AtomicLongFieldUpdater<MpscAtomicIntegerArrayQueue> PRODUCER_LIMIT =
                AtomicLongFieldUpdater.newUpdater(MpscAtomicIntegerArrayQueue.class, "producerLimit");
        private static final AtomicLongFieldUpdater<MpscAtomicIntegerArrayQueue> CONSUMER_INDEX =
                AtomicLongFieldUpdater.newUpdater(MpscAtomicIntegerArrayQueue.class, "consumerIndex");
        private final int mask;
        private final int emptyValue;
        private volatile long producerIndex;
        private volatile long producerLimit;
        private volatile long consumerIndex;

        public MpscAtomicIntegerArrayQueue(int capacity, int emptyValue) {
            super(MathUtil.safeFindNextPositivePowerOfTwo(capacity));
            if (emptyValue != 0) {
                this.emptyValue = emptyValue;
                int end = capacity - 1;
                for (int i = 0; i < end; i++) {
                    lazySet(i, emptyValue);
                }
                getAndSet(end, emptyValue); // 'getAndSet' acts as a full barrier, giving us initialization safety.
            } else {
                this.emptyValue = 0;
            }
            mask = length() - 1;
        }

        @Override
        public boolean offer(int value) {
            if (value == emptyValue) {
                throw new IllegalArgumentException("Cannot offer the \"empty\" value: " + emptyValue);
            }
            // use a cached view on consumer index (potentially updated in loop)
            final int mask = this.mask;
            long producerLimit = this.producerLimit;
            long pIndex;
            do {
                pIndex = producerIndex;
                if (pIndex >= producerLimit) {
                    final long cIndex = consumerIndex;
                    producerLimit = cIndex + mask + 1;
                    if (pIndex >= producerLimit) {
                        // FULL :(
                        return false;
                    } else {
                        // update producer limit to the next index that we must recheck the consumer index
                        // this is racy, but the race is benign
                        PRODUCER_LIMIT.lazySet(this, producerLimit);
                    }
                }
            } while (!PRODUCER_INDEX.compareAndSet(this, pIndex, pIndex + 1));
            /*
             * NOTE: the new producer index value is made visible BEFORE the element in the array. If we relied on
             * the index visibility to poll() we would need to handle the case where the element is not visible.
             */
            // Won CAS, move on to storing
            final int offset = (int) (pIndex & mask);
            lazySet(offset, value);
            // AWESOME :)
            return true;
        }

        @Override
        public int poll() {
            final long cIndex = consumerIndex;
            final int offset = (int) (cIndex & mask);
            // If we can't see the next available element we can't poll
            int value = get(offset);
            if (emptyValue == value) {
                /*
                 * NOTE: Queue may not actually be empty in the case of a producer (P1) being interrupted after
                 * winning the CAS on offer but before storing the element in the queue. Other producers may go on
                 * to fill up the queue after this element.
                 */
                if (cIndex != producerIndex) {
                    do {
                        value = get(offset);
                    } while (emptyValue == value);
                } else {
                    return emptyValue;
                }
            }
            lazySet(offset, emptyValue);
            CONSUMER_INDEX.lazySet(this, cIndex + 1);
            return value;
        }

        @Override
        public int drain(int limit, IntConsumer consumer) {
            Objects.requireNonNull(consumer, "consumer");
            ObjectUtil.checkPositiveOrZero(limit, "limit");
            if (limit == 0) {
                return 0;
            }
            final int mask = this.mask;
            final long cIndex = consumerIndex; // Note: could be weakened to plain-load.
            for (int i = 0; i < limit; i++) {
                final long index = cIndex + i;
                final int offset = (int) (index & mask);
                final int value = get(offset);
                if (emptyValue == value) {
                    return i;
                }
                lazySet(offset, emptyValue); // Note: could be weakened to plain-store.
                // ordered store -> atomic and ordered for size()
                CONSUMER_INDEX.lazySet(this, index + 1);
                consumer.accept(value);
            }
            return limit;
        }

        @Override
        public int fill(int limit, IntSupplier supplier) {
            Objects.requireNonNull(supplier, "supplier");
            ObjectUtil.checkPositiveOrZero(limit, "limit");
            if (limit == 0) {
                return 0;
            }
            final int mask = this.mask;
            final long capacity = mask + 1;
            long producerLimit = this.producerLimit;
            long pIndex;
            int actualLimit;
            do {
                pIndex = producerIndex;
                long available = producerLimit - pIndex;
                if (available <= 0) {
                    final long cIndex = consumerIndex;
                    producerLimit = cIndex + capacity;
                    available = producerLimit - pIndex;
                    if (available <= 0) {
                        // FULL :(
                        return 0;
                    } else {
                        // update producer limit to the next index that we must recheck the consumer index
                        PRODUCER_LIMIT.lazySet(this, producerLimit);
                    }
                }
                actualLimit = Math.min((int) available, limit);
            } while (!PRODUCER_INDEX.compareAndSet(this, pIndex, pIndex + actualLimit));
            // right, now we claimed a few slots and can fill them with goodness
            for (int i = 0; i < actualLimit; i++) {
                // Won CAS, move on to storing
                final int offset = (int) (pIndex + i & mask);
                lazySet(offset, supplier.getAsInt());
            }
            return actualLimit;
        }

        @Override
        public boolean isEmpty() {
            // Load consumer index before producer index, so our check is conservative.
            long cIndex = consumerIndex;
            long pIndex = producerIndex;
            return cIndex >= pIndex;
        }

        @Override
        public int size() {
            // Loop until we get a consistent read of both the consumer and producer indices.
            long after = consumerIndex;
            long size;
            for (;;) {
                long before = after;
                long pIndex = producerIndex;
                after = consumerIndex;
                if (before == after) {
                    size = pIndex - after;
                    break;
                }
            }
            return size < 0 ? 0 : size > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) size;
        }
    }
}
