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
package io.netty.channel.epoll;

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.concurrent.locks.LockSupport;

import org.jctools.queues.CircularArrayOffsetCalculator;
import org.jctools.queues.IndexedQueueSizeUtil.IndexedQueue;
import org.jctools.queues.QueueProgressIndicators;
import org.jctools.util.Pow2;
import org.jctools.util.RangeUtil;
import org.jctools.util.UnsafeRefArrayAccess;

//import static org.jctools.queues.LinkedArrayQueueUtil.modifiedCalcElementOffset;
import static org.jctools.util.UnsafeAccess.UNSAFE;
//import static org.jctools.util.UnsafeAccess.fieldOffset;
import static org.jctools.util.UnsafeRefArrayAccess.lvElement;
import static org.jctools.util.UnsafeRefArrayAccess.soElement;

abstract class MpscBlockingConsumerArrayQueuePad1<E> extends AbstractQueue<E> implements IndexedQueue
{
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    // copied in
    static long fieldOffset(Class<?> clz, String fieldName) throws RuntimeException
    {
        try
        {
            return UNSAFE.objectFieldOffset(clz.getDeclaredField(fieldName));
        }
        catch (NoSuchFieldException e)
        {
            throw new RuntimeException(e);
        }
    }
}
// $gen:ordered-fields
abstract class MpscBlockingConsumerArrayQueueColdProducerFields<E> extends MpscBlockingConsumerArrayQueuePad1<E>
{
    private final static long P_LIMIT_OFFSET = fieldOffset(MpscBlockingConsumerArrayQueueColdProducerFields.class,"producerLimit");

    private volatile long producerLimit;
    protected final long producerMask;
    protected final E[] producerBuffer;

    MpscBlockingConsumerArrayQueueColdProducerFields(long producerMask, E[] producerBuffer)
    {
        this.producerMask = producerMask;
        this.producerBuffer = producerBuffer;
    }

    final long lvProducerLimit()
    {
        return producerLimit;
    }

    final boolean casProducerLimit(long expect, long newValue)
    {
        return UNSAFE.compareAndSwapLong(this, P_LIMIT_OFFSET, expect, newValue);
    }

    final void soProducerLimit(long newValue)
    {
        UNSAFE.putOrderedLong(this, P_LIMIT_OFFSET, newValue);
    }
}

abstract class MpscBlockingConsumerArrayQueuePad2<E> extends MpscBlockingConsumerArrayQueueColdProducerFields<E>
{
    long p0, p1, p2, p3, p4, p5, p6;

    MpscBlockingConsumerArrayQueuePad2(long mask, E[] buffer)
    {
        super(mask, buffer);
    }
}

// $gen:ordered-fields
abstract class MpscBlockingConsumerArrayQueueProducerFields<E> extends MpscBlockingConsumerArrayQueuePad2<E>
{
    private final static long P_INDEX_OFFSET = fieldOffset(MpscBlockingConsumerArrayQueueProducerFields.class, "producerIndex");

    private volatile long producerIndex;

    MpscBlockingConsumerArrayQueueProducerFields(long mask, E[] buffer)
    {
        super(mask, buffer);
    }

    @Override
    public final long lvProducerIndex()
    {
        return producerIndex;
    }

    final void soProducerIndex(long newValue)
    {
        UNSAFE.putOrderedLong(this, P_INDEX_OFFSET, newValue);
    }

    final boolean casProducerIndex(long expect, long newValue)
    {
        return UNSAFE.compareAndSwapLong(this, P_INDEX_OFFSET, expect, newValue);
    }
}

abstract class MpscBlockingConsumerArrayQueuePad3<E> extends MpscBlockingConsumerArrayQueueProducerFields<E>
{
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    MpscBlockingConsumerArrayQueuePad3(long mask, E[] buffer)
    {
        super(mask, buffer);
    }
}

// $gen:ordered-fields
abstract class MpscBlockingConsumerArrayQueueConsumerFields<E> extends MpscBlockingConsumerArrayQueuePad3<E>
{
    private final static long C_INDEX_OFFSET = fieldOffset(MpscBlockingConsumerArrayQueueConsumerFields.class,"consumerIndex");

    private volatile long consumerIndex;
    protected final long consumerMask;
    protected final E[] consumerBuffer;

    MpscBlockingConsumerArrayQueueConsumerFields(long mask, E[] buffer)
    {
        super(mask, buffer);
        consumerMask = mask;
        consumerBuffer = buffer;
    }

    @Override
    public final long lvConsumerIndex()
    {
        return consumerIndex;
    }
    
    final long lpConsumerIndex()
    {
        return UNSAFE.getLong(this, C_INDEX_OFFSET);
    }

    final void soConsumerIndex(long newValue)
    {
        UNSAFE.putOrderedLong(this, C_INDEX_OFFSET, newValue);
    }
}



/**
 * This is a special-purpose queue impl derived from JCTools' MpscBlockingConsumerArrayQueue:
 * https://github.com/JCTools/JCTools/blob/master/jctools-core/src/main/java/org/jctools/queues/MpscBlockingConsumerArrayQueue.java
 *
 *
 * @param <E>
 */
class TagTeamConsumerArrayQueue<E> extends MpscBlockingConsumerArrayQueueConsumerFields<E>
    implements QueueProgressIndicators
{
    long p0, p1, p2, p3, p4, p5, p6, p7;
    long p10, p11, p12, p13, p14, p15, p16, p17;
    
    private Thread consumerThread;

    // Possible states, stored as smallest 2 bits of producerIndex
    public static final int ST_PRIMARY_ACTIVE = 0;
    public static final int ST_SECONDARY_ACTIVE = 2;
    public static final int ST_BOTH_WAITING = 1;

    static int state(long producerIndex) {
        return (int) (producerIndex & 3L);
    }

    public TagTeamConsumerArrayQueue(final int capacity)
    {
        // leave lower 2 bits of mask clear
        super((long) ((Pow2.roundToPowerOfTwo(capacity) - 1) << 2), 
                CircularArrayOffsetCalculator.<E>allocate(Pow2.roundToPowerOfTwo(capacity)));
        
        RangeUtil.checkGreaterThanOrEqual(capacity, 1, "capacity");
        soProducerLimit((long) ((Pow2.roundToPowerOfTwo(capacity) - 1) << 2)); // we know it's all empty to start with
    }

    public void setConsumerThread(Thread thread) {
        this.consumerThread = thread;
    }

    @Override
    public final Iterator<E> iterator()
    {
        throw new UnsupportedOperationException();
    }
    
    // ----------- methods safe to call from any thread --------

    @Override
    public final int size()
    {
        // NOTE: because indices are on even numbers we cannot use the size util.

        /*
         * It is possible for a thread to be interrupted or reschedule between the read of the producer and
         * consumer indices, therefore protection is required to ensure size is within valid range. In the
         * event of concurrent polls/offers to this method the size is OVER estimated as we read consumer
         * index BEFORE the producer index.
         */
        long after = lvConsumerIndex();
        long size;
        while (true)
        {
            final long before = after;
            final long currentProducerIndex = lvProducerIndex();
            after = lvConsumerIndex();
            if (before == after)
            {
                size = ((currentProducerIndex - after) >> 2);
                break;
            }
        }
        // Long overflow is impossible, so size is always positive. Integer overflow is possible for the unbounded
        // indexed queues.
        if (size > Integer.MAX_VALUE)
        {
            return Integer.MAX_VALUE;
        }
        else
        {
            return (int) size;
        }
    }

    @Override
    public final boolean isEmpty()
    {
        // Order matters!
        // Loading consumer before producer allows for producer increments after consumer index is read.
        // This ensures this method is conservative in it's estimate. Note that as this is an MPMC there is
        // nothing we can do to make this an exact method.
        return ((this.lvConsumerIndex()/4) == (this.lvProducerIndex()/4));
    }

    @Override
    public String toString()
    {
        return this.getClass().getName();
    }

    @Override
    public long currentProducerIndex()
    {
        return lvProducerIndex() / 4;
    }

    @Override
    public long currentConsumerIndex()
    {
        return lvConsumerIndex() / 4;
    }

    //@Override
    public int capacity()
    {
        return (int) ((consumerMask >> 2) + 1); //TODO verify this
    }

    public int currentState() {
        return state(lvProducerIndex());
    }

    /**
     * State transition:
     * ST_BOTH_WAITING -> ST_PRIMARY_ACTIVE
     * otherwise state unchanged
     */
    @Override
    public boolean offer(final E e)
    {
        if (null == e)
        {
            throw new NullPointerException();
        }

        final long mask = this.producerMask;
        final E[] buffer = this.producerBuffer;
        long pIndex;
        while (true)
        {
            pIndex = lvProducerIndex();
            // lower bit is indicative of blocked consumer
            if (state(pIndex) == ST_BOTH_WAITING)
            {
                if (offerAndWakeup(buffer, mask, pIndex, e))
                    return true;
                continue;
            }
            // pIndex is even (lower bit is 0) -> actual index is (pIndex >> 2), consumer is awake
            final long producerLimit = lvProducerLimit();

            // Use producer limit to save a read of the more rapidly mutated consumer index.
            // Assumption: queue is usually empty or near empty
            if (producerLimit <= pIndex)
            {
                if (!recalculateProducerLimit(mask, pIndex, producerLimit))
                {
                    return false;
                }
            }

            // Claim the index
            if (casProducerIndex(pIndex, pIndex + 4))
            {
                break;
            }
        }
        final long offset = modifiedCalcElementOffset(pIndex, mask);
        // INDEX visible before ELEMENT
        soElement(buffer, offset, e); // release element e
        return true;
    }

    // ----------- methods only safe to call from primary consumer --------

    /**
     * ST_PRIMARY_ACTIVE   -> ST_PRIMARY_ACTIVE (returns 1)
     * ST_SECONDARY_ACTIVE -> ST_SECONDARY_ACTIVE (returns 0)
     * ST_BOTH_WAITING     -> ST_PRIMARY_ACTIVE (returns 1)
     * 
     * @return 0 submitted, 1 grabbed control (not submitted), -1 queue full
     */
    public int primaryGrabControlOrOffer(final E e)
    {
        if (null == e)
        {
            throw new NullPointerException();
        }

        final long mask = this.producerMask;
        final E[] buffer = this.producerBuffer;
        //final int ourCtx = primaryConsumer ? ST_PRIMARY_ACTIVE : ST_SECONDARY_ACTIVE;
        long pIndex;
        while (true)
        {
            pIndex = lvProducerIndex();
            
            int state = state(pIndex);
            if (state == ST_PRIMARY_ACTIVE)
            {
                return 1; // already have control
            }
            // lower bit is indicative of both consumers waiting
            if (state == ST_BOTH_WAITING)
            {
                if(casProducerIndex(pIndex, pIndex - 1))
                {
                    return 1; // control grabbed
                }
                continue;
            }
            // pIndex is even (lower bit is 0) -> actual index is (pIndex >> 2), consumer is awake
            final long producerLimit = lvProducerLimit();

            // Use producer limit to save a read of the more rapidly mutated consumer index.
            // Assumption: queue is usually empty or near empty
            if (producerLimit <= pIndex)
            {
                if (!recalculateProducerLimit(mask, pIndex, producerLimit))
                {
                    return -1; // full
                }
            }

            // Claim the index
            if (casProducerIndex(pIndex, pIndex + 4))
            {
                break;
            }
        }
        final long offset = modifiedCalcElementOffset(pIndex, mask);
        // INDEX visible before ELEMENT
        soElement(buffer, offset, e); // release element e
        return 0; // success, no wakeup needed
    }

    /**
     * Must only be called in {@link #ST_PRIMARY_ACTIVE} state,
     * will only return or throw in {@link #ST_PRIMARY_ACTIVE} state.
     */
    public E take() throws InterruptedException
    {
        assert Thread.currentThread() == consumerThread;
        //assert state(lvProducerIndex()) == ST_PRIMARY_ACTIVE;

        final E[] buffer = consumerBuffer;
        final long mask = consumerMask;

        long cIndex = lpConsumerIndex();
        long offset = modifiedCalcElementOffset(cIndex, mask);
        E e = lvElement(buffer, offset);// LoadLoad
        if (e == null) {
            final long pIndex = lvProducerIndex();
            if (pIndex == cIndex && casProducerIndex(pIndex, pIndex + 1)) {
                do {
                    LockSupport.park(this);
                    handleParkInterrupted();
                }
                while (state(lvProducerIndex()) != ST_PRIMARY_ACTIVE);
                // need to re-read this here, may have changed
                cIndex = lpConsumerIndex();
                offset = modifiedCalcElementOffset(cIndex, mask);
            }
            e = spinWaitForElement(buffer, offset);
        }
        soElement(buffer, offset, null); // release element null
        soConsumerIndex(cIndex + 4); // release cIndex
        return e;
    }

    /**
     * Block-poll until {@code deadlineNanos} (relative to {@link System#nanoTime()}).
     * <p>
     * Must only be called in {@link #ST_PRIMARY_ACTIVE} state,
     * will only return or throw in {@link #ST_PRIMARY_ACTIVE} state. This means that the
     * timeout could be delayed if/while the secondary thread is in control of the queue.
     *
     * @return a queued element or null if deadline is reached
     */
    public E pollUntil(long deadlineNanos) throws InterruptedException
    {
        assert Thread.currentThread() == consumerThread;
        //assert state(lvProducerIndex()) == ST_PRIMARY_ACTIVE;

        final E[] buffer = consumerBuffer;
        final long mask = consumerMask;

        long cIndex = lpConsumerIndex();
        long offset = modifiedCalcElementOffset(cIndex, mask);
        E e = lvElement(buffer, offset);// LoadLoad
        if (e == null) {
            final long pIndex = lvProducerIndex();
            if (pIndex == cIndex && casProducerIndex(pIndex, pIndex + 1)) {
                do {
                    long remaining = deadlineNanos - System.nanoTime();
                    if (remaining <= 0L) { // timeout
                        waitAfterInterruptOrTimeout();
                        return null;
                    }
                    LockSupport.parkNanos(this, remaining);
                    handleParkInterrupted();
                }
                while (state(lvProducerIndex()) != ST_PRIMARY_ACTIVE);
                // need to re-read this here, may have changed
                cIndex = lpConsumerIndex();
                offset = modifiedCalcElementOffset(cIndex, mask);
            }
            e = spinWaitForElement(buffer, offset);
        }
        soElement(buffer, offset, null); // release element null
        soConsumerIndex(cIndex + 4); // release cIndex
        return e;
    }

    private void handleParkInterrupted() throws InterruptedException {
        if (Thread.interrupted()) {
            if (waitAfterInterruptOrTimeout()) {
                // Clear again incase we were re-interrupted
                Thread.interrupted();
            }
            throw new InterruptedException();
        }
    }

    private volatile boolean primaryNeedsWakeup;

    private boolean waitAfterInterruptOrTimeout() {
        boolean flagSet = false;
        for (;;) {
            long pIndex = lvProducerIndex();
            int state = state(pIndex);
            if (state == ST_SECONDARY_ACTIVE) {
                if (!flagSet) {
                    primaryNeedsWakeup = true;
                    flagSet = true;
                    continue;
                }
                LockSupport.park(this);
            } else if (state == ST_PRIMARY_ACTIVE
                    || casProducerIndex(pIndex, pIndex - 1L)) {
                break;
            }
        }
        // assert state(lvProducerIndex()) == ST_PRIMARY_ACTIVE;
        if (flagSet) {
            primaryNeedsWakeup = false;
            return true;
        }
        return false;
    }

    // ------------ methods only safe to call from secondary consumer -------------
    
    /**
     * Expected to be in ST_SECONDARY_ACTIVE when called.
     *    queue empty     -> ST_BOTH_WAITING (returns true)
     *    queue non-empty -> ST_SECONDARY_ACTIVE (returns false)
     * 
     * @return true to enter wait or false if there are more tasks
     */
    public boolean secondaryEnterWait() {
        long pIndex = lvProducerIndex();
        if (state(pIndex) != ST_SECONDARY_ACTIVE) {
            return true;
        }
        if (pIndex / 4 != lpConsumerIndex() / 4) {
            return false;
        }
        boolean needWakeup = primaryNeedsWakeup;
        if (!casProducerIndex(pIndex, pIndex - 1)) {
            return false;
        }
        if (needWakeup) {
            LockSupport.unpark(consumerThread);
        }
        return true;
    }

    /**
     * ST_PRIMARY_ACTIVE   -> ST_PRIMARY_ACTIVE (returns 0)
     * ST_SECONDARY_ACTIVE -> ST_SECONDARY_ACTIVE (returns 1)
     * ST_BOTH_WAITING     -> ST_SECONDARY_ACTIVE (returns 1)
     * 
     * @return 0 submitted, 1 submitted *and* got control, -1 queue full
     */
    public int secondaryOfferAndTryToGrabControl(final E e)
    {
        if (null == e)
        {
            throw new NullPointerException();
        }

        final long mask = this.producerMask;
        final E[] buffer = this.producerBuffer;
        long pIndex;
        int rc;
        while (true)
        {
            pIndex = lvProducerIndex();

            // pIndex is even (lower bit is 0) -> actual index is (pIndex >> 2), consumer is awake
            final long producerLimit = lvProducerLimit();

            // Use producer limit to save a read of the more rapidly mutated consumer index.
            // Assumption: queue is usually empty or near empty
            if (producerLimit <= pIndex)
            {
                if (!recalculateProducerLimit(mask, pIndex, producerLimit))
                {
                    return -1; // full
                }
            }

            int state = state(pIndex);
            long newPIndex = state == ST_BOTH_WAITING ? pIndex + 5 : pIndex + 4;
            if (casProducerIndex(pIndex, newPIndex)) {
                rc = state == ST_PRIMARY_ACTIVE ? 0 : 1;
                break;
            }
        }
        final long offset = modifiedCalcElementOffset(pIndex, mask);
        // INDEX visible before ELEMENT
        soElement(buffer, offset, e); // release element e
        return rc;
    }

    // ----------- methods only safe to call from *currently active* consumer --------

    public long plainCurrentConsumerIndex()
    {
        return lpConsumerIndex() / 4;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @Override
    public E poll()
    {
        final E[] buffer = consumerBuffer;
        final long mask = consumerMask;

        final long index = lpConsumerIndex();
        final long offset = modifiedCalcElementOffset(index, mask);
        E e = lvElement(buffer, offset);// LoadLoad
        if (e == null)
        {
            // consumer can't see the odd producer index
            if (index/4 != lvProducerIndex()/4)
            {
                // poll() == null iff queue is empty, null element is not strong enough indicator, so we must
                // check the producer index. If the queue is indeed not empty we spin until element is
                // visible.
                e = spinWaitForElement(buffer, offset);
            }
            else
            {
                return null;
            }
        }

        soElement(buffer, offset, null); // release element null
        soConsumerIndex(index + 4); // release cIndex
        return e;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @Override
    public E peek()
    {
        final E[] buffer = consumerBuffer;
        final long mask = consumerMask;

        final long index = lpConsumerIndex();
        final long offset = modifiedCalcElementOffset(index, mask);
        E e = lvElement(buffer, offset);// LoadLoad
        if (e == null && index/4 != lvProducerIndex()/4)
        {
            // peek() == null iff queue is empty, null element is not strong enough indicator, so we must
            // check the producer index. If the queue is indeed not empty we spin until element is visible.
            e = spinWaitForElement(buffer, offset);
        }
        
        return e;
    }

    // --------------- private methods -----------

    private boolean offerAndWakeup(E[] buffer, long mask, long pIndex, E e)
    {
        // Claim the slot and the responsibility of unparking
        if(!casProducerIndex(pIndex, pIndex + 3))
        {
            return false;
        }

        final long offset = modifiedCalcElementOffset(pIndex, mask);
        soElement(buffer, offset, e);
        LockSupport.unpark(consumerThread);
        return true;
    }

    private boolean recalculateProducerLimit(long mask, long pIndex, long producerLimit)
    {
        final long cIndex = lvConsumerIndex();
        final long bufferCapacity = mask + 4;

        if (cIndex + bufferCapacity > pIndex)
        {
            casProducerLimit(producerLimit, cIndex + bufferCapacity); 
        }
        // full and cannot grow
        else if (pIndex - cIndex == bufferCapacity)
        {
            // offer should return false;
            return false;
        }
        else 
            throw new IllegalStateException();
        return true;
    }

    private E spinWaitForElement(E[] buffer, long offset)
    {
        E e;
        do
        {
            e = lvElement(buffer, offset);
        }
        while (e == null);
        return e;
    }

    // Copied in

    /**
     * This method assumes index is actually (index << 2) because lower 2 bits are
     * used to encode state. This is compensated for by reducing the element shift.
     * The computation is constant folded, so there's no cost.
     */
    static long modifiedCalcElementOffset(long index, long mask)
    {
        return UnsafeRefArrayAccess.REF_ARRAY_BASE +
                ((index & mask) << (UnsafeRefArrayAccess.REF_ELEMENT_SHIFT - 2));
    }
}