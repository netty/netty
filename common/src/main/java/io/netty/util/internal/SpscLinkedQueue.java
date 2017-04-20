/*
 * Copyright 2016 The Netty Project
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
 * This is a weakened version of the MPSC algorithm as presented
 * <a href="http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue"> on
 * 1024 Cores</a> by D. Vyukov. The original has been adapted to Java and it's quirks with regards to memory
 * model and layout:
 * <ol>
 * <li>Use inheritance to ensure no false sharing occurs between producer/consumer node reference fields.
 * <li>As this is an SPSC we have no need for XCHG, an ordered store is enough.
 * </ol>
 * The queue is initialized with a stub node which is set to both the producer and consumer node references.
 * From this point follow the notes on offer/poll.
 *
 * @param <E>
 */
public class SpscLinkedQueue<E> extends BaseLinkedQueue<E> {

    public SpscLinkedQueue() {
        spProducerNode(new LinkedQueueNode<E>());
        spConsumerNode(producerNode);
        consumerNode.soNext(null); // this ensures correct construction: StoreStore
    }

    /**
     * {@inheritDoc} <br>
     *
     * IMPLEMENTATION NOTES:<br>
     * Offer is allowed from a SINGLE thread.<br>
     * Offer allocates a new node (holding the offered value) and:
     * <ol>
     * <li>Sets that node as the producerNode.next
     * <li>Sets the new node as the producerNode
     * </ol>
     * From this follows that producerNode.next is always null and for all other nodes node.next is not null.
     *
     * @see MessagePassingQueue#offer(Object)
     * @see java.util.Queue#offer(java.lang.Object)
     */
    @Override
    public boolean offer(final E nextValue) {
        if (nextValue == null) {
            throw new IllegalArgumentException("null elements not allowed");
        }
        final LinkedQueueNode<E> nextNode = new LinkedQueueNode<E>(nextValue);
        producerNode.soNext(nextNode);
        producerNode = nextNode;
        return true;
    }

    /**
     * {@inheritDoc} <br>
     *
     * IMPLEMENTATION NOTES:<br>
     * Poll is allowed from a SINGLE thread.<br>
     * Poll reads the next node from the consumerNode and:
     * <ol>
     * <li>If it is null, the queue is empty.
     * <li>If it is not null set it as the consumer node and return it's now evacuated value.
     * </ol>
     * This means the consumerNode.value is always null, which is also the starting point for the queue.
     * Because null values are not allowed to be offered this is the only node with it's value set to null at
     * any one time.
     *
     */
    @Override
    public E poll() {
        final LinkedQueueNode<E> nextNode = consumerNode.lvNext();
        if (nextNode != null) {
            // we have to null out the value because we are going to hang on to the node
            final E nextValue = nextNode.getAndNullValue();
            consumerNode = nextNode;
            return nextValue;
        }
        return null;
    }

    @Override
    public E peek() {
        final LinkedQueueNode<E> nextNode = consumerNode.lvNext();
        if (nextNode != null) {
            return nextNode.lpValue();
        } else {
            return null;
        }
    }

    @Override
    public boolean relaxedOffer(E e) {
        return offer(e);
    }

    @Override
    public E relaxedPoll() {
        return poll();
    }

    @Override
    public E relaxedPeek() {
        return peek();
    }

    @Override
    public int drain(Consumer<E> c) {
        long result = 0; // use long to force safepoint into loop below
        int drained;
        do {
            drained = drain(c, 4096);
            result += drained;
        } while (drained == 4096 && result <= Integer.MAX_VALUE - 4096);
        return (int) result;
    }

    @Override
    public int fill(Supplier<E> s) {
        long result = 0; // result is a long because we want to have a safepoint check at regular intervals
        do {
            fill(s, 4096);
            result += 4096;
        } while (result <= Integer.MAX_VALUE - 4096);
        return (int) result;
    }

    @Override
    public int drain(Consumer<E> c, int limit) {
        LinkedQueueNode<E> chaserNode = this.consumerNode;
        for (int i = 0; i < limit; i++) {
            chaserNode = chaserNode.lvNext();
            if (chaserNode == null) {
                return i;
            }
            // we have to null out the value because we are going to hang on to the node
            final E nextValue = chaserNode.getAndNullValue();
            this.consumerNode = chaserNode;
            c.accept(nextValue);
        }
        return limit;
    }

    @Override
    public int fill(Supplier<E> s, int limit) {
        LinkedQueueNode<E> chaserNode = producerNode;
        for (int i = 0; i < limit; i++) {
            final LinkedQueueNode<E> nextNode = new LinkedQueueNode<E>(s.get());
            chaserNode.soNext(nextNode);
            chaserNode = nextNode;
            this.producerNode = chaserNode;
        }
        return limit;
    }

    @Override
    public void drain(Consumer<E> c, WaitStrategy wait, ExitCondition exit) {
        LinkedQueueNode<E> chaserNode = this.consumerNode;
        int idleCounter = 0;
        while (exit.keepRunning()) {
            for (int i = 0; i < 4096; i++) {
                final LinkedQueueNode<E> next = chaserNode.lvNext();
                if (next == null) {
                    idleCounter = wait.idle(idleCounter);
                    continue;
                }
                chaserNode = next;
                idleCounter = 0;
                // we have to null out the value because we are going to hang on to the node
                final E nextValue = chaserNode.getAndNullValue();
                this.consumerNode = chaserNode;
                c.accept(nextValue);
            }
        }
    }

    @Override
    public void fill(Supplier<E> s, WaitStrategy wait, ExitCondition exit) {
        LinkedQueueNode<E> chaserNode = producerNode;
        while (exit.keepRunning()) {
            for (int i = 0; i < 4096; i++) {
                final LinkedQueueNode<E> nextNode = new LinkedQueueNode<E>(s.get());
                chaserNode.soNext(nextNode);
                chaserNode = nextNode;
                this.producerNode = chaserNode;
            }
        }
    }
}
