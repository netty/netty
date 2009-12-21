/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package org.jboss.netty.util.internal;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;

/**
 * An unbounded <tt>TransferQueue</tt> based on linked nodes.
 * This queue orders elements FIFO (first-in-first-out) with respect
 * to any given producer.  The <em>head</em> of the queue is that
 * element that has been on the queue the longest time for some
 * producer.  The <em>tail</em> of the queue is that element that has
 * been on the queue the shortest time for some producer.
 *
 * <p>Beware that, unlike in most collections, the {@code size}
 * method is <em>NOT</em> a constant-time operation. Because of the
 * asynchronous nature of these queues, determining the current number
 * of elements requires a traversal of the elements.
 *
 * <p>This class and its iterator implement all of the
 * <em>optional</em> methods of the {@link Collection} and {@link
 * Iterator} interfaces.
 *
 * <p>Memory consistency effects: As with other concurrent
 * collections, actions in a thread prior to placing an object into a
 * {@code LinkedTransferQueue}
 * <a href="package-summary.html#MemoryVisibility"><i>happen-before</i></a>
 * actions subsequent to the access or removal of that element from
 * the {@code LinkedTransferQueue} in another thread.
 *
 * @author Doug Lea
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (trustin@gmail.com)
 *
 * @param <E> the type of elements held in this collection
 *
 */
public class LinkedTransferQueue<E> extends AbstractQueue<E> implements BlockingQueue<E> {

    /*
     * This class extends the approach used in FIFO-mode
     * SynchronousQueues. See the internal documentation, as well as
     * the PPoPP 2006 paper "Scalable Synchronous Queues" by Scherer,
     * Lea & Scott
     * (http://www.cs.rice.edu/~wns1/papers/2006-PPoPP-SQ.pdf)
     *
     * The main extension is to provide different Wait modes for the
     * main "xfer" method that puts or takes items.  These don't
     * impact the basic dual-queue logic, but instead control whether
     * or how threads block upon insertion of request or data nodes
     * into the dual queue. It also uses slightly different
     * conventions for tracking whether nodes are off-list or
     * cancelled.
     */

    // Wait modes for xfer method
    private static final int NOWAIT  = 0;
    private static final int TIMEOUT = 1;
    private static final int WAIT    = 2;

    /** The number of CPUs, for spin control */
    private static final int NCPUS = Runtime.getRuntime().availableProcessors();

    /**
     * The number of times to spin before blocking in timed waits.
     * The value is empirically derived -- it works well across a
     * variety of processors and OSes. Empirically, the best value
     * seems not to vary with number of CPUs (beyond 2) so is just
     * a constant.
     */
    private static final int maxTimedSpins = NCPUS < 2? 0 : 32;

    /**
     * The number of times to spin before blocking in untimed waits.
     * This is greater than timed value because untimed waits spin
     * faster since they don't need to check times on each spin.
     */
    private static final int maxUntimedSpins = maxTimedSpins * 16;

    /**
     * The number of nanoseconds for which it is faster to spin
     * rather than to use timed park. A rough estimate suffices.
     */
    private static final long spinForTimeoutThreshold = 1000L;

    /**
     * Node class for LinkedTransferQueue. Opportunistically
     * subclasses from AtomicReference to represent item. Uses Object,
     * not E, to allow setting item to "this" after use, to avoid
     * garbage retention. Similarly, setting the next field to this is
     * used as sentinel that node is off list.
     */
    private static final class Node<E> extends AtomicReference<Object> {
        private static final long serialVersionUID = 5925596372370723938L;

        transient volatile Node<E> next;
        transient volatile Thread waiter;       // to control park/unpark
        final boolean isData;
        Node(E item, boolean isData) {
            super(item);
            this.isData = isData;
        }

        @SuppressWarnings("unchecked")
        private static final AtomicReferenceFieldUpdater<Node, Node> nextUpdater;
        static {
            @SuppressWarnings("unchecked")
            AtomicReferenceFieldUpdater<Node, Node> tmp = null;
            try {
                tmp = AtomicReferenceFieldUpdater.newUpdater(
                        Node.class, Node.class, "next");

                // Test if AtomicReferenceFieldUpdater is really working.
                @SuppressWarnings("unchecked")
                Node testNode = new Node(null, false);
                tmp.set(testNode, testNode);
                if (testNode.next != testNode) {
                    // Not set as expected - fall back to the safe mode.
                    throw new Exception();
                }
            } catch (Throwable t) {
                // Running in a restricted environment with a security manager.
                tmp = null;
            }
            nextUpdater = tmp;
        }

        final boolean casNext(Node<E> cmp, Node<E> val) {
            if (nextUpdater != null) {
                return nextUpdater.compareAndSet(this, cmp, val);
            } else {
                return alternativeCasNext(cmp, val);
            }
        }

        private synchronized final boolean alternativeCasNext(Node<E> cmp, Node<E> val) {
            if (next == cmp) {
                next = val;
                return true;
            } else {
                return false;
            }
        }

        final void clearNext() {
            // nextUpdater.lazySet(this, this);
            next = this; // allows to run on java5
        }
    }

    /**
     * Padded version of AtomicReference used for head, tail and
     * cleanMe, to alleviate contention across threads CASing one vs
     * the other.
     */
    private static final class PaddedAtomicReference<T> extends AtomicReference<T> {
        private static final long serialVersionUID = 4684288940772921317L;

        // enough padding for 64bytes with 4byte refs
        @SuppressWarnings("unused")
        Object p0, p1, p2, p3, p4, p5, p6, p7, p8, p9, pa, pb, pc, pd, pe;
        PaddedAtomicReference(T r) { super(r); }
    }


    /** head of the queue */
    private final PaddedAtomicReference<Node<E>> head;
    /** tail of the queue */
    private final PaddedAtomicReference<Node<E>> tail;

    /**
     * Reference to a cancelled node that might not yet have been
     * unlinked from queue because it was the last inserted node
     * when it cancelled.
     */
    private final PaddedAtomicReference<Node<E>> cleanMe;

    /**
     * Tries to cas nh as new head; if successful, unlink
     * old head's next node to avoid garbage retention.
     */
    private boolean advanceHead(Node<E> h, Node<E> nh) {
        if (h == head.get() && head.compareAndSet(h, nh)) {
            h.clearNext(); // forget old next
            return true;
        }
        return false;
    }

    /**
     * Puts or takes an item. Used for most queue operations (except
     * poll() and tryTransfer()). See the similar code in
     * SynchronousQueue for detailed explanation.
     *
     * @param e the item or if null, signifies that this is a take
     * @param mode the wait mode: NOWAIT, TIMEOUT, WAIT
     * @param nanos timeout in nanosecs, used only if mode is TIMEOUT
     * @return an item, or null on failure
     */
    private E xfer(E e, int mode, long nanos) {
        boolean isData = e != null;
        Node<E> s = null;
        final PaddedAtomicReference<Node<E>> head = this.head;
        final PaddedAtomicReference<Node<E>> tail = this.tail;

        for (;;) {
            Node<E> t = tail.get();
            Node<E> h = head.get();

            if (t == h || t.isData == isData) {
                if (s == null) {
                    s = new Node<E>(e, isData);
                }
                Node<E> last = t.next;
                if (last != null) {
                    if (t == tail.get()) {
                        tail.compareAndSet(t, last);
                    }
                }
                else if (t.casNext(null, s)) {
                    tail.compareAndSet(t, s);
                    return awaitFulfill(t, s, e, mode, nanos);
                }
            } else {
                Node<E> first = h.next;
                if (t == tail.get() && first != null &&
                    advanceHead(h, first)) {
                    Object x = first.get();
                    if (x != first && first.compareAndSet(x, e)) {
                        LockSupport.unpark(first.waiter);
                        return isData? e : cast(x);
                    }
                }
            }
        }
    }


    /**
     * Version of xfer for poll() and tryTransfer, which
     * simplifies control paths both here and in xfer.
     */
    private E fulfill(E e) {
        boolean isData = e != null;
        final PaddedAtomicReference<Node<E>> head = this.head;
        final PaddedAtomicReference<Node<E>> tail = this.tail;

        for (;;) {
            Node<E> t = tail.get();
            Node<E> h = head.get();

            if (t == h || t.isData == isData) {
                Node<E> last = t.next;
                if (t == tail.get()) {
                    if (last != null) {
                        tail.compareAndSet(t, last);
                    } else {
                        return null;
                    }
                }
            } else {
                Node<E> first = h.next;
                if (t == tail.get() &&
                    first != null &&
                    advanceHead(h, first)) {
                    Object x = first.get();
                    if (x != first && first.compareAndSet(x, e)) {
                        LockSupport.unpark(first.waiter);
                        return isData? e : cast(x);
                    }
                }
            }
        }
    }

    /**
     * Spins/blocks until node s is fulfilled or caller gives up,
     * depending on wait mode.
     *
     * @param pred the predecessor of waiting node
     * @param s the waiting node
     * @param e the comparison value for checking match
     * @param mode mode
     * @param nanos timeout value
     * @return matched item, or null if cancelled
     */
    private E awaitFulfill(Node<E> pred, Node<E> s, E e,
                                int mode, long nanos) {
        if (mode == NOWAIT) {
            return null;
        }

        long lastTime = mode == TIMEOUT? System.nanoTime() : 0;
        Thread w = Thread.currentThread();
        int spins = -1; // set to desired spin count below
        for (;;) {
            if (w.isInterrupted()) {
                s.compareAndSet(e, s);
            }
            Object x = s.get();
            if (x != e) {                 // Node was matched or cancelled
                advanceHead(pred, s);     // unlink if head
                if (x == s) {              // was cancelled
                    clean(pred, s);
                    return null;
                }
                else if (x != null) {
                    s.set(s);             // avoid garbage retention
                    return cast(x);
                } else {
                    return e;
                }
            }
            if (mode == TIMEOUT) {
                long now = System.nanoTime();
                nanos -= now - lastTime;
                lastTime = now;
                if (nanos <= 0) {
                    s.compareAndSet(e, s); // try to cancel
                    continue;
                }
            }
            if (spins < 0) {
                Node<E> h = head.get(); // only spin if at head
                spins = h.next != s ? 0 :
                    mode == TIMEOUT ? maxTimedSpins :
                        maxUntimedSpins;
            }
            if (spins > 0) {
                --spins;
            } else if (s.waiter == null) {
                s.waiter = w;
            } else if (mode != TIMEOUT) {
                //                LockSupport.park(this);
                LockSupport.park(); // allows run on java5
                s.waiter = null;
                spins = -1;
            }
            else if (nanos > spinForTimeoutThreshold) {
                //                LockSupport.parkNanos(this, nanos);
                LockSupport.parkNanos(nanos);
                s.waiter = null;
                spins = -1;
            }
        }
    }

    /**
     * Returns validated tail for use in cleaning methods.
     */
    private Node<E> getValidatedTail() {
        for (;;) {
            Node<E> h = head.get();
            Node<E> first = h.next;
            if (first != null && first.get() == first) { // help advance
                advanceHead(h, first);
                continue;
            }
            Node<E> t = tail.get();
            Node<E> last = t.next;
            if (t == tail.get()) {
                if (last != null) {
                    tail.compareAndSet(t, last); // help advance
                } else {
                    return t;
                }
            }
        }
    }

    /**
     * Gets rid of cancelled node s with original predecessor pred.
     *
     * @param pred predecessor of cancelled node
     * @param s the cancelled node
     */
    void clean(Node<E> pred, Node<E> s) {
        Thread w = s.waiter;
        if (w != null) {             // Wake up thread
            s.waiter = null;
            if (w != Thread.currentThread()) {
                LockSupport.unpark(w);
            }
        }

        if (pred == null) {
            return;
        }

        /*
         * At any given time, exactly one node on list cannot be
         * deleted -- the last inserted node. To accommodate this, if
         * we cannot delete s, we save its predecessor as "cleanMe",
         * processing the previously saved version first. At least one
         * of node s or the node previously saved can always be
         * processed, so this always terminates.
         */
        while (pred.next == s) {
            Node<E> oldpred = reclean();  // First, help get rid of cleanMe
            Node<E> t = getValidatedTail();
            if (s != t) {               // If not tail, try to unsplice
                Node<E> sn = s.next;      // s.next == s means s already off list
                if (sn == s || pred.casNext(s, sn)) {
                    break;
                }
            }
            else if (oldpred == pred || // Already saved
                     oldpred == null && cleanMe.compareAndSet(null, pred)) {
                break;                  // Postpone cleaning
            }
        }
    }

    /**
     * Tries to unsplice the cancelled node held in cleanMe that was
     * previously uncleanable because it was at tail.
     *
     * @return current cleanMe node (or null)
     */
    private Node<E> reclean() {
        /*
         * cleanMe is, or at one time was, predecessor of cancelled
         * node s that was the tail so could not be unspliced.  If s
         * is no longer the tail, try to unsplice if necessary and
         * make cleanMe slot available.  This differs from similar
         * code in clean() because we must check that pred still
         * points to a cancelled node that must be unspliced -- if
         * not, we can (must) clear cleanMe without unsplicing.
         * This can loop only due to contention on casNext or
         * clearing cleanMe.
         */
        Node<E> pred;
        while ((pred = cleanMe.get()) != null) {
            Node<E> t = getValidatedTail();
            Node<E> s = pred.next;
            if (s != t) {
                Node<E> sn;
                if (s == null || s == pred || s.get() != s ||
                    (sn = s.next) == s || pred.casNext(s, sn)) {
                    cleanMe.compareAndSet(pred, null);
                }
            } else {
                break;
            }
        }
        return pred;
    }

    @SuppressWarnings("unchecked")
    E cast(Object e) {
        return (E)e;
    }

    /**
     * Creates an initially empty {@code LinkedTransferQueue}.
     */
    public LinkedTransferQueue() {
        Node<E> dummy = new Node<E>(null, false);
        head = new PaddedAtomicReference<Node<E>>(dummy);
        tail = new PaddedAtomicReference<Node<E>>(dummy);
        cleanMe = new PaddedAtomicReference<Node<E>>(null);
    }

    /**
     * Creates a {@code LinkedTransferQueue}
     * initially containing the elements of the given collection,
     * added in traversal order of the collection's iterator.
     *
     * @param c the collection of elements to initially contain
     * @throws NullPointerException if the specified collection or any
     *         of its elements are null
     */
    public LinkedTransferQueue(Collection<? extends E> c) {
        this();
        addAll(c);
    }

    /**
     * Inserts the specified element at the tail of this queue.
     * As the queue is unbounded, this method will never block.
     *
     * @throws NullPointerException if the specified element is null
     */
    public void put(E e) throws InterruptedException {
        offer(e);
    }

    /**
     * Inserts the specified element at the tail of this queue
     * As the queue is unbounded, this method will never block or
     * return {@code false}.
     *
     * @return {@code true} (as specified by {@link BlockingQueue#offer(Object,long,TimeUnit) BlockingQueue.offer})
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e, long timeout, TimeUnit unit)
        throws InterruptedException {
        return offer(e);
    }

    /**
     * Inserts the specified element at the tail of this queue.
     * As the queue is unbounded, this method will never return {@code false}.
     *
     * @return {@code true} (as specified by {@link BlockingQueue#offer(Object) BlockingQueue.offer})
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e) {
        if (e == null) {
            throw new NullPointerException();
        }
        xfer(e, NOWAIT, 0);
        return true;
    }

    /**
     * Inserts the specified element at the tail of this queue.
     * As the queue is unbounded, this method will never throw
     * {@link IllegalStateException} or return {@code false}.
     *
     * @return {@code true} (as specified by {@link Collection#add})
     * @throws NullPointerException if the specified element is null
     */
    @Override
    public boolean add(E e) {
        return offer(e);
    }
    /**
     * Transfers the element to a waiting consumer immediately, if possible.
     *
     * <p>More precisely, transfers the specified element immediately
     * if there exists a consumer already waiting to receive it (in
     * {@link #take} or timed {@link #poll(long,TimeUnit) poll}),
     * otherwise returning {@code false} without enqueuing the element.
     *
     * @throws NullPointerException if the specified element is null
     */
    public boolean tryTransfer(E e) {
        if (e == null) {
            throw new NullPointerException();
        }
        return fulfill(e) != null;
    }

    /**
     * Transfers the element to a consumer, waiting if necessary to do so.
     *
     * <p>More precisely, transfers the specified element immediately
     * if there exists a consumer already waiting to receive it (in
     * {@link #take} or timed {@link #poll(long,TimeUnit) poll}),
     * else inserts the specified element at the tail of this queue
     * and waits until the element is received by a consumer.
     *
     * @throws NullPointerException if the specified element is null
     */
    public void transfer(E e) throws InterruptedException {
        if (e == null) {
            throw new NullPointerException();
        }
        if (xfer(e, WAIT, 0) == null) {
            Thread.interrupted();
            throw new InterruptedException();
        }
    }

    /**
     * Transfers the element to a consumer if it is possible to do so
     * before the timeout elapses.
     *
     * <p>More precisely, transfers the specified element immediately
     * if there exists a consumer already waiting to receive it (in
     * {@link #take} or timed {@link #poll(long,TimeUnit) poll}),
     * else inserts the specified element at the tail of this queue
     * and waits until the element is received by a consumer,
     * returning {@code false} if the specified wait time elapses
     * before the element can be transferred.
     *
     * @throws NullPointerException if the specified element is null
     */
    public boolean tryTransfer(E e, long timeout, TimeUnit unit)
        throws InterruptedException {
        if (e == null) {
            throw new NullPointerException();
        }
        if (xfer(e, TIMEOUT, unit.toNanos(timeout)) != null) {
            return true;
        }
        if (!Thread.interrupted()) {
            return false;
        }
        throw new InterruptedException();
    }

    public E take() throws InterruptedException {
        E e = xfer(null, WAIT, 0);
        if (e != null) {
            return e;
        }
        Thread.interrupted();
        throw new InterruptedException();
    }

    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E e = xfer(null, TIMEOUT, unit.toNanos(timeout));
        if (e != null || !Thread.interrupted()) {
            return e;
        }
        throw new InterruptedException();
    }

    public E poll() {
        return fulfill(null);
    }

    public int drainTo(Collection<? super E> c) {
        if (c == null) {
            throw new NullPointerException();
        }
        if (c == this) {
            throw new IllegalArgumentException();
        }
        int n = 0;
        E e;
        while ( (e = poll()) != null) {
            c.add(e);
            ++n;
        }
        return n;
    }

    public int drainTo(Collection<? super E> c, int maxElements) {
        if (c == null) {
            throw new NullPointerException();
        }
        if (c == this) {
            throw new IllegalArgumentException();
        }
        int n = 0;
        E e;
        while (n < maxElements && (e = poll()) != null) {
            c.add(e);
            ++n;
        }
        return n;
    }

    // Traversal-based methods

    /**
     * Returns head after performing any outstanding helping steps.
     */
    Node<E> traversalHead() {
        for (;;) {
            Node<E> t = tail.get();
            Node<E> h = head.get();
            Node<E> last = t.next;
            Node<E> first = h.next;
            if (t == tail.get()) {
                if (last != null) {
                    tail.compareAndSet(t, last);
                } else if (first != null) {
                    Object x = first.get();
                    if (x == first) {
                        advanceHead(h, first);
                    } else {
                        return h;
                    }
                } else {
                    return h;
                }
            }
            reclean();
        }
    }

    /**
     * Returns an iterator over the elements in this queue in proper
     * sequence, from head to tail.
     *
     * <p>The returned iterator is a "weakly consistent" iterator that
     * will never throw
     * {@link ConcurrentModificationException ConcurrentModificationException},
     * and guarantees to traverse elements as they existed upon
     * construction of the iterator, and may (but is not guaranteed
     * to) reflect any modifications subsequent to construction.
     *
     * @return an iterator over the elements in this queue in proper sequence
     */
    @Override
    public Iterator<E> iterator() {
        return new Itr();
    }

    /**
     * Iterators. Basic strategy is to traverse list, treating
     * non-data (i.e., request) nodes as terminating list.
     * Once a valid data node is found, the item is cached
     * so that the next call to next() will return it even
     * if subsequently removed.
     */
    class Itr implements Iterator<E> {
        Node<E> next;        // node to return next
        Node<E> pnext;       // predecessor of next
        Node<E> curr;        // last returned node, for remove()
        Node<E> pcurr;       // predecessor of curr, for remove()
        E nextItem;        // Cache of next item, once committed to in next

        Itr() {
            advance();
        }

        /**
         * Moves to next valid node and returns item to return for
         * next(), or null if no such.
         */
        private E advance() {
            pcurr = pnext;
            curr = next;
            E item = nextItem;
            for (;;) {
                pnext = next == null ? traversalHead() : next;
                next = pnext.next;
                if (next == pnext) {
                    next = null;
                    continue; // restart
                }
                if (next == null) {
                    break;
                }
                Object x = next.get();
                if (x != null && x != next) {
                    nextItem = cast(x);
                    break;
                }
            }
            return item;
        }

        public boolean hasNext() {
            return next != null;
        }

        public E next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            return advance();
        }

        public void remove() {
            Node<E> p = curr;
            if (p == null) {
                throw new IllegalStateException();
            }
            Object x = p.get();
            if (x != null && x != p && p.compareAndSet(x, p)) {
                clean(pcurr, p);
            }
        }
    }

    public E peek() {
        for (;;) {
            Node<E> h = traversalHead();
            Node<E> p = h.next;
            if (p == null) {
                return null;
            }
            Object x = p.get();
            if (p != x) {
                if (!p.isData) {
                    return null;
                }
                if (x != null) {
                    return cast(x);
                }
            }
        }
    }

    /**
     * Returns {@code true} if this queue contains no elements.
     *
     * @return {@code true} if this queue contains no elements
     */
    @Override
    public boolean isEmpty() {
        for (;;) {
            Node<E> h = traversalHead();
            Node<E> p = h.next;
            if (p == null) {
                return true;
            }
            Object x = p.get();
            if (p != x) {
                if (!p.isData) {
                    return true;
                }
                if (x != null) {
                    return false;
                }
            }
        }
    }

    public boolean hasWaitingConsumer() {
        for (;;) {
            Node<E> h = traversalHead();
            Node<E> p = h.next;
            if (p == null) {
                return false;
            }
            Object x = p.get();
            if (p != x) {
                return !p.isData;
            }
        }
    }

    /**
     * Returns the number of elements in this queue.  If this queue
     * contains more than {@code Integer.MAX_VALUE} elements, returns
     * {@code Integer.MAX_VALUE}.
     *
     * <p>Beware that, unlike in most collections, this method is
     * <em>NOT</em> a constant-time operation. Because of the
     * asynchronous nature of these queues, determining the current
     * number of elements requires an O(n) traversal.
     *
     * @return the number of elements in this queue
     */
    @Override
    public int size() {
        for (;;) {
            int count = 0;
            Node<E> pred = traversalHead();
            for (;;) {
                Node<E> q = pred.next;
                if (q == pred) { // restart
                    break;
                }
                if (q == null || !q.isData) {
                    return count;
                }
                Object x = q.get();
                if (x != null && x != q) {
                    if (++count == Integer.MAX_VALUE) { // saturated
                        return count;
                    }
                }
                pred = q;
            }
        }
    }

    public int getWaitingConsumerCount() {
        // converse of size -- count valid non-data nodes
        for (;;) {
            int count = 0;
            Node<E> pred = traversalHead();
            for (;;) {
                Node<E> q = pred.next;
                if (q == pred) { // restart
                    break;
                }
                if (q == null || q.isData) {
                    return count;
                }
                Object x = q.get();
                if (x == null) {
                    if (++count == Integer.MAX_VALUE) { // saturated
                        return count;
                    }
                }
                pred = q;
            }
        }
    }

    /**
     * Removes a single instance of the specified element from this queue,
     * if it is present.  More formally, removes an element {@code e} such
     * that {@code o.equals(e)}, if this queue contains one or more such
     * elements.
     * Returns {@code true} if this queue contained the specified element
     * (or equivalently, if this queue changed as a result of the call).
     *
     * @param o element to be removed from this queue, if present
     * @return {@code true} if this queue changed as a result of the call
     */
    @Override
    public boolean remove(Object o) {
        if (o == null) {
            return false;
        }
        for (;;) {
            Node<E> pred = traversalHead();
            for (;;) {
                Node<E> q = pred.next;
                if (q == pred) {// restart
                    break;
                }
                if (q == null || !q.isData) {
                    return false;
                }
                Object x = q.get();
                if (x != null && x != q && o.equals(x) &&
                        q.compareAndSet(x, q)) {
                    clean(pred, q);
                    return true;
                }
                pred = q;
            }
        }
    }

    /**
     * Always returns {@code Integer.MAX_VALUE} because a
     * {@code LinkedTransferQueue} is not capacity constrained.
     *
     * @return {@code Integer.MAX_VALUE} (as specified by
     *         {@link BlockingQueue#remainingCapacity()})
     */
    public int remainingCapacity() {
        return Integer.MAX_VALUE;
    }
}
