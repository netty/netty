/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package org.jboss.netty.util;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;

/**
 * An unbounded {@linkplain BlockingQueue} based on linked nodes.
 * This queue orders elements FIFO (first-in-first-out) with respect
 * to any given producer.  The <em>head</em> of the queue is that
 * element that has been on the queue the longest time for some
 * producer.  The <em>tail</em> of the queue is that element that has
 * been on the queue the shortest time for some producer.
 *
 * <p>Beware that, unlike in most collections, the <tt>size</tt>
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
 * {@code LinkedTransferQueue} <i>happen-before</i> actions subsequent
 * to the access or removal of that element from the
 * {@code LinkedTransferQueue} in another thread.
 *
 * @author Doug Lea
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @param <E> the type of elements held in this collection
 *
 */
public class LinkedTransferQueue<E> extends AbstractQueue<E> implements BlockingQueue<E> {

    /*
     * This is still a work in progress...
     *
     * This class extends the approach used in FIFO-mode
     * SynchronousQueues. See the internal documentation, as well as
     * the PPoPP 2006 paper "Scalable Synchronous Queues" by Scherer,
     * Lea & Scott
     * (http://www.cs.rice.edu/~wns1/papers/2006-PPoPP-SQ.pdf)
     *
     * The main extension is to provide different Wait modes
     * for the main "xfer" method that puts or takes items.
     * These don't impact the basic dual-queue logic, but instead
     * control whether or how threads block upon insertion
     * of request or data nodes into the dual queue.
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
     * Node class for LinkedTransferQueue. Opportunistically subclasses from
     * AtomicReference to represent item. Uses Object, not E, to allow
     * setting item to "this" after use, to avoid garbage
     * retention. Similarly, setting the next field to this is used as
     * sentinel that node is off list.
     */
    private static final class QNode extends AtomicReference<Object> {
        private static final long serialVersionUID = 5925596372370723938L;

        volatile QNode next;
        transient volatile Thread waiter;       // to control park/unpark
        final boolean isData;

        QNode(Object item, boolean isData) {
            super(item);
            this.isData = isData;
        }

        private static final AtomicReferenceFieldUpdater<QNode, QNode>
            nextUpdater = AtomicReferenceFieldUpdater.newUpdater
            (QNode.class, QNode.class, "next");

        boolean casNext(QNode cmp, QNode val) {
            return nextUpdater.compareAndSet(this, cmp, val);
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
        Object p0, p1, p2, p3, p4, p5, p6, p7, p8, p9, pa, pb, pc, pd, pe;
        PaddedAtomicReference(T r) { super(r); }
    }


    /** head of the queue */
    private final PaddedAtomicReference<QNode> head;
    /** tail of the queue */
    private final PaddedAtomicReference<QNode> tail;

    /**
     * Reference to a cancelled node that might not yet have been
     * unlinked from queue because it was the last inserted node
     * when it cancelled.
     */
    private final PaddedAtomicReference<QNode> cleanMe;

    /**
     * Tries to cas nh as new head; if successful, unlink
     * old head's next node to avoid garbage retention.
     */
    private boolean advanceHead(QNode h, QNode nh) {
        if (h == head.get() && head.compareAndSet(h, nh)) {
            h.next = h; // forget old next
            return true;
        }
        return false;
    }

    /**
     * Puts or takes an item. Used for most queue operations (except
     * poll() and tryTransfer())
     * @param e the item or if null, signifies that this is a take
     * @param mode the wait mode: NOWAIT, TIMEOUT, WAIT
     * @param nanos timeout in nanosecs, used only if mode is TIMEOUT
     * @return an item, or null on failure
     */
    private Object xfer(Object e, int mode, long nanos) {
        boolean isData = e != null;
        QNode s = null;
        final AtomicReference<QNode> head = this.head;
        final AtomicReference<QNode> tail = this.tail;

        for (;;) {
            QNode t = tail.get();
            QNode h = head.get();

            if (t != null && (t == h || t.isData == isData)) {
                if (s == null) {
                    s = new QNode(e, isData);
                }
                QNode last = t.next;
                if (last != null) {
                    if (t == tail.get()) {
                        tail.compareAndSet(t, last);
                    }
                }
                else if (t.casNext(null, s)) {
                    tail.compareAndSet(t, s);
                    return awaitFulfill(t, s, e, mode, nanos);
                }
            }

            else if (h != null) {
                QNode first = h.next;
                if (t == tail.get() && first != null &&
                    advanceHead(h, first)) {
                    Object x = first.get();
                    if (x != first && first.compareAndSet(x, e)) {
                        LockSupport.unpark(first.waiter);
                        return isData? e : x;
                    }
                }
            }
        }
    }


    /**
     * Version of xfer for poll() and tryTransfer, which
     * simplifies control paths both here and in xfer
     */
    private Object fulfill(Object e) {
        boolean isData = e != null;
        final AtomicReference<QNode> head = this.head;
        final AtomicReference<QNode> tail = this.tail;

        for (;;) {
            QNode t = tail.get();
            QNode h = head.get();

            if (t != null && (t == h || t.isData == isData)) {
                QNode last = t.next;
                if (t == tail.get()) {
                    if (last != null) {
                        tail.compareAndSet(t, last);
                    } else {
                        return null;
                    }
                }
            }
            else if (h != null) {
                QNode first = h.next;
                if (t == tail.get() &&
                    first != null &&
                    advanceHead(h, first)) {
                    Object x = first.get();
                    if (x != first && first.compareAndSet(x, e)) {
                        LockSupport.unpark(first.waiter);
                        return isData? e : x;
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
     * @return matched item, or s if cancelled
     */
    private Object awaitFulfill(QNode pred, QNode s, Object e,
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
                if (x == s) {
                    return clean(pred, s);
                } else if (x != null) {
                    s.set(s);             // avoid garbage retention
                    return x;
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
                QNode h = head.get(); // only spin if at head
                spins = h != null && h.next == s ?
                         (mode == TIMEOUT?
                          maxTimedSpins : maxUntimedSpins) : 0;
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
     * Gets rid of cancelled node s with original predecessor pred.
     * @return null (to simplify use by callers)
     */
    Object clean(QNode pred, QNode s) {
        Thread w = s.waiter;
        if (w != null) {             // Wake up thread
            s.waiter = null;
            if (w != Thread.currentThread()) {
                LockSupport.unpark(w);
            }
        }

        for (;;) {
            if (pred.next != s) {
                return null;
            }
            QNode h = head.get();
            QNode hn = h.next;   // Absorb cancelled first node as head
            if (hn != null && hn.next == hn) {
                advanceHead(h, hn);
                continue;
            }
            QNode t = tail.get();      // Ensure consistent read for tail
            if (t == h) {
                return null;
            }
            QNode tn = t.next;
            if (t != tail.get()) {
                continue;
            }
            if (tn != null) {          // Help advance tail
                tail.compareAndSet(t, tn);
                continue;
            }
            if (s != t) {             // If not tail, try to unsplice
                QNode sn = s.next;
                if (sn == s || pred.casNext(s, sn)) {
                    return null;
                }
            }
            QNode dp = cleanMe.get();
            if (dp != null) {    // Try unlinking previous cancelled node
                QNode d = dp.next;
                QNode dn;
                if (d == null ||               // d is gone or
                    d == dp ||                 // d is off list or
                    d.get() != d ||            // d not cancelled or
                    d != t &&                 // d not tail and
                     (dn = d.next) != null &&  //   has successor
                     dn != d &&                //   that is on list
                     dp.casNext(d, dn)) {
                    cleanMe.compareAndSet(dp, null);
                }
                if (dp == pred) {
                    return null;      // s is already saved node
                }
            }
            else if (cleanMe.compareAndSet(null, pred)) {
                return null;          // Postpone cleaning s
            }
        }
    }

    /**
     * Creates an initially empty <tt>LinkedTransferQueue</tt>.
     */
    public LinkedTransferQueue() {
        QNode dummy = new QNode(null, false);
        head = new PaddedAtomicReference<QNode>(dummy);
        tail = new PaddedAtomicReference<QNode>(dummy);
        cleanMe = new PaddedAtomicReference<QNode>(null);
    }

    /**
     * Creates a <tt>LinkedTransferQueue</tt>
     * initially containing the elements of the given collection,
     * added in traversal order of the collection's iterator.
     * @param c the collection of elements to initially contain
     * @throws NullPointerException if the specified collection or any
     *         of its elements are null
     */
    public LinkedTransferQueue(Collection<? extends E> c) {
        this();
        addAll(c);
    }

    public void put(E e) throws InterruptedException {
        if (e == null) {
            throw new NullPointerException();
        }
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        xfer(e, NOWAIT, 0);
    }

    public boolean offer(E e, long timeout, TimeUnit unit)
        throws InterruptedException {
        if (e == null) {
            throw new NullPointerException();
        }
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        xfer(e, NOWAIT, 0);
        return true;
    }

    public boolean offer(E e) {
        if (e == null) {
            throw new NullPointerException();
        }
        xfer(e, NOWAIT, 0);
        return true;
    }

    public void transfer(E e) throws InterruptedException {
        if (e == null) {
            throw new NullPointerException();
        }
        if (xfer(e, WAIT, 0) == null) {
            Thread.interrupted();
            throw new InterruptedException();
        }
    }

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

    public boolean tryTransfer(E e) {
        if (e == null) {
            throw new NullPointerException();
        }
        return fulfill(e) != null;
    }

    public E take() throws InterruptedException {
        Object e = xfer(null, WAIT, 0);
        if (e != null) {
            return cast(e);
        }
        Thread.interrupted();
        throw new InterruptedException();
    }

    @SuppressWarnings("unchecked")
    E cast(Object e) {
        return (E) e;
    }

    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        Object e = xfer(null, TIMEOUT, unit.toNanos(timeout));
        if (e != null || !Thread.interrupted()) {
            return cast(e);
        }
        throw new InterruptedException();
    }

    public E poll() {
        return cast(fulfill(null));
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
     * Return head after performing any outstanding helping steps
     */
    QNode traversalHead() {
        for (;;) {
            QNode t = tail.get();
            QNode h = head.get();
            if (h != null && t != null) {
                QNode last = t.next;
                QNode first = h.next;
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
            }
        }
    }


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
    private class Itr implements Iterator<E> {
        private QNode nextNode;    // Next node to return next
        private QNode currentNode; // last returned node, for remove()
        private QNode prevNode;    // predecessor of last returned node
        private E nextItem;        // Cache of next item, once commited to in next

        Itr() {
            nextNode = traversalHead();
            advance();
        }

        private E advance() {
            prevNode = currentNode;
            currentNode = nextNode;
            E x = nextItem;

            QNode p = nextNode.next;
            for (;;) {
                if (p == null || !p.isData) {
                    nextNode = null;
                    nextItem = null;
                    return x;
                }
                Object item = p.get();
                if (item != p && item != null) {
                    nextNode = p;
                    nextItem = cast(item);
                    return x;
                }
                prevNode = p;
                p = p.next;
            }
        }

        public boolean hasNext() {
            return nextNode != null;
        }

        public E next() {
            if (nextNode == null) {
                throw new NoSuchElementException();
            }
            return advance();
        }

        public void remove() {
            QNode p = currentNode;
            QNode prev = prevNode;
            if (prev == null || p == null) {
                throw new IllegalStateException();
            }
            Object x = p.get();
            if (x != null && x != p && p.compareAndSet(x, p)) {
                clean(prev, p);
            }
        }
    }

    public E peek() {
        for (;;) {
            QNode h = traversalHead();
            QNode p = h.next;
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

    @Override
    public boolean isEmpty() {
        for (;;) {
            QNode h = traversalHead();
            QNode p = h.next;
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
            QNode h = traversalHead();
            QNode p = h.next;
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
     * contains more than <tt>Integer.MAX_VALUE</tt> elements, returns
     * <tt>Integer.MAX_VALUE</tt>.
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
        int count = 0;
        QNode h = traversalHead();
        for (QNode p = h.next; p != null && p.isData; p = p.next) {
            Object x = p.get();
            if (x != null && x != p) {
                if (++count == Integer.MAX_VALUE) {
                    break;
                }
            }
        }
        return count;
    }

    public int getWaitingConsumerCount() {
        int count = 0;
        QNode h = traversalHead();
        for (QNode p = h.next; p != null && !p.isData; p = p.next) {
            if (p.get() == null) {
                if (++count == Integer.MAX_VALUE) {
                    break;
                }
            }
        }
        return count;
    }

    public int remainingCapacity() {
        return Integer.MAX_VALUE;
    }
}