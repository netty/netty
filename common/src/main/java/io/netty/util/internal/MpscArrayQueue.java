/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.netty.util.internal;

/**
 * Forked from <a href="https://github.com/JCTools/JCTools">JCTools</a>.
 *
 * A Multi-Producer-Single-Consumer queue based on a {@link ConcurrentCircularArrayQueue}. This implies that
 * any thread may call the offer method, but only a single thread may call poll/peek for correctness to
 * maintained. <br>
 * This implementation follows patterns documented on the package level for False Sharing protection.<br>
 * This implementation is using the <a href="http://sourceforge.net/projects/mc-fastflow/">Fast Flow</a>
 * method for polling from the queue (with minor change to correctly publish the index) and an extension of
 * the Leslie Lamport concurrent queue algorithm (originated by Martin Thompson) on the producer side.<br>
 *
 * @param <E>
 */
final class MpscArrayQueue<E> extends MpscArrayQueueConsumerField<E> {
    long p40, p41, p42, p43, p44, p45, p46;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpscArrayQueue(final int capacity) {
        super(capacity);
    }

    /**
     * {@inheritDoc} <br>
     *
     * IMPLEMENTATION NOTES:<br>
     * Lock free offer using a single CAS. As class name suggests access is permitted to many threads
     * concurrently.
     *
     * @see java.util.Queue#offer(java.lang.Object)
     */
    @Override
    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }

        // use a cached view on consumer index (potentially updated in loop)
        final long mask = this.mask;
        final long capacity = mask + 1;
        long consumerIndexCache = lvConsumerIndexCache(); // LoadLoad
        long currentProducerIndex;
        do {
            currentProducerIndex = lvProducerIndex(); // LoadLoad
            final long wrapPoint = currentProducerIndex - capacity;
            if (consumerIndexCache <= wrapPoint) {
                final long currHead = lvConsumerIndex(); // LoadLoad
                if (currHead <= wrapPoint) {
                    return false; // FULL :(
                } else {
                    // update shared cached value of the consumerIndex
                    svConsumerIndexCache(currHead); // StoreLoad
                    // update on stack copy, we might need this value again if we lose the CAS.
                    consumerIndexCache = currHead;
                }
            }
        } while (!casProducerIndex(currentProducerIndex, currentProducerIndex + 1));
        /*
         * NOTE: the new producer index value is made visible BEFORE the element in the array. If we relied on
         * the index visibility to poll() we would need to handle the case where the element is not visible.
         */

        // Won CAS, move on to storing
        final long offset = calcElementOffset(currentProducerIndex, mask);
        soElement(offset, e); // StoreStore
        return true; // AWESOME :)
    }

    /**
     * A wait free alternative to offer which fails on CAS failure.
     *
     * @param e new element, not null
     * @return 1 if next element cannot be filled, -1 if CAS failed, 0 if successful
     */
    public int weakOffer(final E e) {
        if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }
        final long mask = this.mask;
        final long capacity = mask + 1;
        final long currentTail = lvProducerIndex(); // LoadLoad
        final long consumerIndexCache = lvConsumerIndexCache(); // LoadLoad
        final long wrapPoint = currentTail - capacity;
        if (consumerIndexCache <= wrapPoint) {
            long currHead = lvConsumerIndex(); // LoadLoad
            if (currHead <= wrapPoint) {
                return 1; // FULL :(
            } else {
                svConsumerIndexCache(currHead); // StoreLoad
            }
        }

        // look Ma, no loop!
        if (!casProducerIndex(currentTail, currentTail + 1)) {
            return -1; // CAS FAIL :(
        }

        // Won CAS, move on to storing
        final long offset = calcElementOffset(currentTail, mask);
        soElement(offset, e);
        return 0; // AWESOME :)
    }

    /**
     * {@inheritDoc}
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * Lock free poll using ordered loads/stores. As class name suggests access is limited to a single thread.
     *
     * @see java.util.Queue#poll()
     */
    @Override
    public E poll() {
        final long consumerIndex = lvConsumerIndex(); // LoadLoad
        final long offset = calcElementOffset(consumerIndex);
        // Copy field to avoid re-reading after volatile load
        final E[] buffer = this.buffer;

        // If we can't see the next available element we can't poll
        E e = lvElement(buffer, offset); // LoadLoad
        if (null == e) {
            /*
             * NOTE: Queue may not actually be empty in the case of a producer (P1) being interrupted after
             * winning the CAS on offer but before storing the element in the queue. Other producers may go on
             * to fill up the queue after this element.
             */
            if (consumerIndex != lvProducerIndex()) {
                do {
                    e = lvElement(buffer, offset);
                } while (e == null);
            } else {
                return null;
            }
        }

        spElement(buffer, offset, null);
        soConsumerIndex(consumerIndex + 1); // StoreStore
        return e;
    }

    /**
     * {@inheritDoc}
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * Lock free peek using ordered loads. As class name suggests access is limited to a single thread.
     *
     * @see java.util.Queue#poll()
     */
    @Override
    public E peek() {
        // Copy field to avoid re-reading after volatile load
        final E[] buffer = this.buffer;

        final long consumerIndex = lvConsumerIndex(); // LoadLoad
        final long offset = calcElementOffset(consumerIndex);
        E e = lvElement(buffer, offset);
        if (null == e) {
            /*
             * NOTE: Queue may not actually be empty in the case of a producer (P1) being interrupted after
             * winning the CAS on offer but before storing the element in the queue. Other producers may go on
             * to fill up the queue after this element.
             */
            if (consumerIndex != lvProducerIndex()) {
                do {
                    e = lvElement(buffer, offset);
                } while (e == null);
            } else {
                return null;
            }
        }
        return e;
    }

    /**
     * {@inheritDoc}
     * <p>
     *
     */
    @Override
    public int size() {
        /*
         * It is possible for a thread to be interrupted or reschedule between the read of the producer and
         * consumer indices, therefore protection is required to ensure size is within valid range. In the
         * event of concurrent polls/offers to this method the size is OVER estimated as we read consumer
         * index BEFORE the producer index.
         */
        long after = lvConsumerIndex();
        while (true) {
            final long before = after;
            final long currentProducerIndex = lvProducerIndex();
            after = lvConsumerIndex();
            if (before == after) {
                return (int) (currentProducerIndex - after);
            }
        }
    }

    @Override
    public boolean isEmpty() {
        // Order matters!
        // Loading consumer before producer allows for producer increments after consumer index is read.
        // This ensures the correctness of this method at least for the consumer thread. Other threads POV is
        // not really
        // something we can fix here.
        return lvConsumerIndex() == lvProducerIndex();
    }
}

abstract class MpscArrayQueueL1Pad<E> extends ConcurrentCircularArrayQueue<E> {
    long p10, p11, p12, p13, p14, p15, p16;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpscArrayQueueL1Pad(int capacity) {
        super(capacity);
    }
}

abstract class MpscArrayQueueTailField<E> extends MpscArrayQueueL1Pad<E> {
    private static final long P_INDEX_OFFSET;

    static {
        try {
            P_INDEX_OFFSET = PlatformDependent0.UNSAFE.objectFieldOffset(MpscArrayQueueTailField.class
                                                                                 .getDeclaredField("producerIndex"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    private volatile long producerIndex;

    public MpscArrayQueueTailField(int capacity) {
        super(capacity);
    }

    protected final long lvProducerIndex() {
        return producerIndex;
    }

    protected final boolean casProducerIndex(long expect, long newValue) {
        return PlatformDependent0.UNSAFE.compareAndSwapLong(this, P_INDEX_OFFSET, expect, newValue);
    }
}

abstract class MpscArrayQueueMidPad<E> extends MpscArrayQueueTailField<E> {
    long p20, p21, p22, p23, p24, p25, p26;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpscArrayQueueMidPad(int capacity) {
        super(capacity);
    }
}

abstract class MpscArrayQueueHeadCacheField<E> extends MpscArrayQueueMidPad<E> {
    private volatile long headCache;

    public MpscArrayQueueHeadCacheField(int capacity) {
        super(capacity);
    }

    protected final long lvConsumerIndexCache() {
        return headCache;
    }

    protected final void svConsumerIndexCache(long v) {
        headCache = v;
    }
}

abstract class MpscArrayQueueL2Pad<E> extends MpscArrayQueueHeadCacheField<E> {
    long p20, p21, p22, p23, p24, p25, p26;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpscArrayQueueL2Pad(int capacity) {
        super(capacity);
    }
}

abstract class MpscArrayQueueConsumerField<E> extends MpscArrayQueueL2Pad<E> {
    private static final long C_INDEX_OFFSET;
    static {
        try {
            C_INDEX_OFFSET = PlatformDependent0.UNSAFE.objectFieldOffset(MpscArrayQueueConsumerField.class
                                                                                 .getDeclaredField("consumerIndex"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    private volatile long consumerIndex;

    public MpscArrayQueueConsumerField(int capacity) {
        super(capacity);
    }

    protected final long lvConsumerIndex() {
        return consumerIndex;
    }

    protected void soConsumerIndex(long l) {
        PlatformDependent0.UNSAFE.putOrderedLong(this, C_INDEX_OFFSET, l);
    }
}

