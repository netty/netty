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

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Forked from <a href="https://github.com/JCTools/JCTools">JCTools</a>.
 */
abstract class BaseLinkedAtomicQueue<E> extends AbstractQueue<E> {
    private final AtomicReference<LinkedQueueAtomicNode<E>> producerNode;
    private final AtomicReference<LinkedQueueAtomicNode<E>> consumerNode;
    public BaseLinkedAtomicQueue() {
        producerNode = new AtomicReference<LinkedQueueAtomicNode<E>>();
        consumerNode = new AtomicReference<LinkedQueueAtomicNode<E>>();
    }
    protected final LinkedQueueAtomicNode<E> lvProducerNode() {
        return producerNode.get();
    }
    protected final LinkedQueueAtomicNode<E> lpProducerNode() {
        return producerNode.get();
    }
    protected final void spProducerNode(LinkedQueueAtomicNode<E> node) {
        producerNode.lazySet(node);
    }
    protected final LinkedQueueAtomicNode<E> xchgProducerNode(LinkedQueueAtomicNode<E> node) {
        return producerNode.getAndSet(node);
    }
    protected final LinkedQueueAtomicNode<E> lvConsumerNode() {
        return consumerNode.get();
    }

    protected final LinkedQueueAtomicNode<E> lpConsumerNode() {
        return consumerNode.get();
    }
    protected final void spConsumerNode(LinkedQueueAtomicNode<E> node) {
        consumerNode.lazySet(node);
    }
    @Override
    public final Iterator<E> iterator() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc} <br>
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * This is an O(n) operation as we run through all the nodes and count them.<br>
     *
     * @see java.util.Queue#size()
     */
    @Override
    public final int size() {
        LinkedQueueAtomicNode<E> chaserNode = lvConsumerNode();
        final LinkedQueueAtomicNode<E> producerNode = lvProducerNode();
        int size = 0;
        // must chase the nodes all the way to the producer node, but there's no need to chase a moving target.
        while (chaserNode != producerNode && size < Integer.MAX_VALUE) {
            LinkedQueueAtomicNode<E> next;
            while ((next = chaserNode.lvNext()) == null) {
                continue;
            }
            chaserNode = next;
            size++;
        }
        return size;
    }
    /**
     * {@inheritDoc} <br>
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * Queue is empty when producerNode is the same as consumerNode. An alternative implementation would be to observe
     * the producerNode.value is null, which also means an empty queue because only the consumerNode.value is allowed to
     * be null.
     *
     * @see MessagePassingQueue#isEmpty()
     */
    @Override
    public final boolean isEmpty() {
        return lvConsumerNode() == lvProducerNode();
    }
}
