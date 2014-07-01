/*
 * Copyright 2014 The Netty Project
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

package io.netty.util.internal;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public abstract class MpscLinkedQueueNode<T> {

    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<MpscLinkedQueueNode, MpscLinkedQueueNode> nextUpdater;

    static {
        @SuppressWarnings("rawtypes")
        AtomicReferenceFieldUpdater<MpscLinkedQueueNode, MpscLinkedQueueNode> u;

        u = PlatformDependent.newAtomicReferenceFieldUpdater(MpscLinkedQueueNode.class, "next");
        if (u == null) {
            u = AtomicReferenceFieldUpdater.newUpdater(MpscLinkedQueueNode.class, MpscLinkedQueueNode.class, "next");
        }
        nextUpdater = u;
    }

    @SuppressWarnings("unused")
    private volatile MpscLinkedQueueNode<T> next;

    final MpscLinkedQueueNode<T> next() {
        return next;
    }

    final void setNext(final MpscLinkedQueueNode<T> newNext) {
        // Similar to 'next = newNext', but slightly faster (storestore vs loadstore)
        // See: http://robsjava.blogspot.com/2013/06/a-faster-volatile.html
        nextUpdater.lazySet(this, newNext);
    }

    public abstract T value();

    /**
     * Sets the element this node contains to {@code null} so that the node can be used as a tombstone.
     */
    protected T clearMaybe() {
        return value();
    }

    /**
     * Unlink to allow GC'ed
     */
    void unlink() {
        setNext(null);
    }
}
