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

import io.netty.util.internal.ObjectUtil;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Default {@link AttributeMap} implementation which use simple synchronization per bucket to keep the memory overhead
 * as low as possible.
 */
public class DefaultAttributeMap implements AttributeMap {

    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<DefaultAttributeMap, AtomicReferenceArray> updater =
            AtomicReferenceFieldUpdater.newUpdater(DefaultAttributeMap.class, AtomicReferenceArray.class, "attributes");

    private static final int BUCKET_SIZE = 4;
    private static final int MASK = BUCKET_SIZE  - 1;

    // Initialize lazily to reduce memory consumption; updated by AtomicReferenceFieldUpdater above.
    @SuppressWarnings("UnusedDeclaration")
    private volatile AtomicReferenceArray<DefaultAttribute<?>> attributes;

    @SuppressWarnings("unchecked")
    @Override
    public <T> Attribute<T> attr(AttributeKey<T> key) {
        ObjectUtil.checkNotNull(key, "key");
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
            // use compare and set. At worst we need to fallback to synchronization and waste two allocations.
            head = new DefaultAttribute();
            DefaultAttribute<T> attr = new DefaultAttribute<T>(head, key);
            head.next = attr;
            attr.prev = head;
            if (attributes.compareAndSet(i, null, head)) {
                // we were able to add it so return the attr right away
                return attr;
            } else {
                head = attributes.get(i);
            }
        }

        synchronized (head) {
            DefaultAttribute<?> curr = head;
            for (;;) {
                DefaultAttribute<?> next = curr.next;
                if (next == null) {
                    DefaultAttribute<T> attr = new DefaultAttribute<T>(head, key);
                    curr.next = attr;
                    attr.prev = curr;
                    return attr;
                }

                if (next.key == key && !next.removed) {
                    return (Attribute<T>) next;
                }
                curr = next;
            }
        }
    }

    @Override
    public <T> boolean hasAttr(AttributeKey<T> key) {
        ObjectUtil.checkNotNull(key, "key");
        AtomicReferenceArray<DefaultAttribute<?>> attributes = this.attributes;
        if (attributes == null) {
            // no attribute exists
            return false;
        }

        int i = index(key);
        DefaultAttribute<?> head = attributes.get(i);
        if (head == null) {
            // No attribute exists which point to the bucket in which the head should be located
            return false;
        }

        // We need to synchronize on the head.
        synchronized (head) {
            // Start with head.next as the head itself does not store an attribute.
            DefaultAttribute<?> curr = head.next;
            while (curr != null) {
                if (curr.key == key && !curr.removed) {
                    return true;
                }
                curr = curr.next;
            }
            return false;
        }
    }

    private static int index(AttributeKey<?> key) {
        return key.id() & MASK;
    }

    @SuppressWarnings("serial")
    private static final class DefaultAttribute<T> extends AtomicReference<T> implements Attribute<T> {

        private static final long serialVersionUID = -2661411462200283011L;

        // The head of the linked-list this attribute belongs to
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

        // Special constructor for the head of the linked-list.
        DefaultAttribute() {
            head = this;
            key = null;
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
                if (prev == null) {
                    // Removed before.
                    return;
                }

                prev.next = next;

                if (next != null) {
                    next.prev = prev;
                }

                // Null out prev and next - this will guard against multiple remove0() calls which may corrupt
                // the linked list for the bucket.
                prev = null;
                next = null;
            }
        }
    }
}
