/*
 * Copyright 2012 The Netty Project
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
package io.netty.util;

import io.netty.util.internal.PlatformDependent;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Default {@link AttributeMap} implementation which use simple synchronization per bucket to keep the memory overhead
 * as low as possible.
 */
public class DefaultAttributeMap implements AttributeMap {

    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<DefaultAttributeMap, AtomicReferenceArray> updater;

    static {
        @SuppressWarnings("rawtypes")
        AtomicReferenceFieldUpdater<DefaultAttributeMap, AtomicReferenceArray> referenceFieldUpdater =
                PlatformDependent.newAtomicReferenceFieldUpdater(DefaultAttributeMap.class, "attributes");
        if (referenceFieldUpdater == null) {
            referenceFieldUpdater = AtomicReferenceFieldUpdater
                            .newUpdater(DefaultAttributeMap.class, AtomicReferenceArray.class, "attributes");
        }
        updater = referenceFieldUpdater;
    }

    private static final int BUCKET_SIZE = 4;
    private static final int MASK = BUCKET_SIZE  - 1;

    // Initialize lazily to reduce memory consumption; updated by AtomicReferenceFieldUpdater above.
    @SuppressWarnings("UnusedDeclaration")
    private volatile AtomicReferenceArray<DefaultAttribute<?>> attributes;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public <T> Attribute<T> attr(AttributeKey<T> key) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        AtomicReferenceArray<DefaultAttribute<?>> attributes = this.attributes;
        if (attributes == null) {
            // Not using ConcurrentHashMap due to high memory consumption.
            attributes = new AtomicReferenceArray<DefaultAttribute<?>>(BUCKET_SIZE);

            if (!updater.compareAndSet(this, null, attributes)) {
                attributes = this.attributes;
            }
        }

        int i = index(key);
        DefaultAttribute<?> head = attributes.get(i);
        if (head == null) {
            // No head exists yet which means we may be able to add the attribute without synchronization and just
            // use compare and set. At worst we need to fallback to synchronization
            head = new DefaultAttribute(key);
            if (attributes.compareAndSet(i, null, head)) {
                // we were able to add it so return the head right away
                return (Attribute<T>) head;
            } else {
                head = attributes.get(i);
            }
        }

        synchronized (head) {
            DefaultAttribute<?> curr = head;
            for (;;) {
                if (!curr.removed && curr.key == key) {
                    return (Attribute<T>) curr;
                }

                DefaultAttribute<?> next = curr.next;
                if (next == null) {
                    DefaultAttribute<T> attr = new DefaultAttribute<T>(head, key);
                    curr.next =  attr;
                    attr.prev = curr;
                    return attr;
                } else {
                    curr = next;
                }
            }
        }
    }

    private static int index(AttributeKey<?> key) {
        return key.id() & MASK;
    }

    @SuppressWarnings("serial")
    private static final class DefaultAttribute<T> extends AtomicReference<T> implements Attribute<T> {

        private static final long serialVersionUID = -2661411462200283011L;

        // The head of the linked-list this attribute belongs to, which may be itself
        private final DefaultAttribute<?> head;
        private final AttributeKey<T> key;

        // Double-linked list to prev and next node to allow fast removal
        private DefaultAttribute<?> prev;
        private DefaultAttribute<?> next;

        // Will be set to true one the attribute is removed via getAndRemove() or remove()
        private volatile boolean removed;

        DefaultAttribute(DefaultAttribute<?> head, AttributeKey<T> key) {
            this.head = head;
            this.key = key;
        }

        DefaultAttribute(AttributeKey<T> key) {
            head = this;
            this.key = key;
        }

        @Override
        public AttributeKey<T> key() {
            return key;
        }

        @Override
        public T setIfAbsent(T value) {
            while (!compareAndSet(null, value)) {
                T old = get();
                if (old != null) {
                    return old;
                }
            }
            return null;
        }

        @Override
        public T getAndRemove() {
            removed = true;
            T oldValue = getAndSet(null);
            remove0();
            return oldValue;
        }

        @Override
        public void remove() {
            removed = true;
            set(null);
            remove0();
        }

        private void remove0() {
            synchronized (head) {
                // We only update the linked-list structure if prev != null because if it is null this
                // DefaultAttribute acts also as head. The head must never be removed completely and just be
                // marked as removed as all synchronization is done on the head itself for each bucket.
                // The head itself will be GC'ed once the DefaultAttributeMap is GC'ed. So at most 5 heads will
                // be removed lazy as the array size is 5.
                if (prev != null) {
                    prev.next = next;

                    if (next != null) {
                        next.prev = prev;
                    }
                }
            }
        }
    }
}
