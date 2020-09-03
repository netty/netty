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

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Default {@link AttributeMap} implementation which not exibit any blocking behaviour on attribute lookup while using a
 * copy-on-write approach on the modify path.<br> Attributes lookup and remove exibit {@code O(logn)} time worst-case
 * complexity, hence {@code attribute::set(null)} is to be preferred to {@code remove}.
 */
public class DefaultAttributeMap implements AttributeMap {

    private static final AtomicReferenceFieldUpdater<DefaultAttributeMap, DefaultAttribute[]> ATTRIBUTES_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(DefaultAttributeMap.class, DefaultAttribute[].class, "attributes");
    private static final DefaultAttribute[] EMPTY_ATTRIBUTES = new DefaultAttribute[0];

    /**
     * Similarly to {@code Arrays::binarySearch} it perform a binary search optimized for this use case, in order to
     * save polymorphic calls (on comparator side) and unnecessary class checks.
     */
    private static int searchAttributeByKey(DefaultAttribute[] sortedAttributes, AttributeKey<?> key) {
        int low = 0;
        int high = sortedAttributes.length - 1;

        while (low <= high) {
            int mid = low + high >>> 1;
            DefaultAttribute midVal = sortedAttributes[mid];
            AttributeKey midValKey = midVal.key;
            if (midValKey == key) {
                return mid;
            }
            int midValKeyId = midValKey.id();
            int keyId = key.id();
            assert midValKeyId != keyId;
            boolean searchRight = midValKeyId < keyId;
            if (searchRight) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        return -(low + 1);
    }

    private static void orderedCopyOnInsert(DefaultAttribute[] sortedSrc, int srcLength, DefaultAttribute[] copy,
                                            DefaultAttribute toInsert) {
        // let's walk backward, because as a rule of thumb, toInsert.key.id() tends to be higher for new keys
        final int id = toInsert.key.id();
        int i;
        for (i = srcLength - 1; i >= 0; i--) {
            DefaultAttribute attribute = sortedSrc[i];
            assert attribute.key.id() != id;
            if (attribute.key.id() < id) {
                break;
            }
            copy[i + 1] = sortedSrc[i];
        }
        copy[i + 1] = toInsert;
        final int toCopy = i + 1;
        if (toCopy > 0) {
            System.arraycopy(sortedSrc, 0, copy, 0, toCopy);
        }
    }

    private volatile DefaultAttribute[] attributes = EMPTY_ATTRIBUTES;

    @SuppressWarnings("unchecked")
    @Override
    public <T> Attribute<T> attr(AttributeKey<T> key) {
        ObjectUtil.checkNotNull(key, "key");
        DefaultAttribute newAttribute = null;
        for (;;) {
            final DefaultAttribute[] attributes = this.attributes;
            final int index = searchAttributeByKey(attributes, key);
            final DefaultAttribute[] newAttributes;
            if (index >= 0) {
                final DefaultAttribute attribute = attributes[index];
                assert attribute.key() == key;
                if (!attribute.isRemoved()) {
                    return attribute;
                }
                // let's try replace the removed attribute with a new one
                if (newAttribute == null) {
                    newAttribute = new DefaultAttribute<T>(this, key);
                }
                final int count = attributes.length;
                newAttributes = Arrays.copyOf(attributes, count);
                newAttributes[index] = newAttribute;
            } else {
                if (newAttribute == null) {
                    newAttribute = new DefaultAttribute<T>(this, key);
                }
                final int count = attributes.length;
                newAttributes = new DefaultAttribute[count + 1];
                orderedCopyOnInsert(attributes, count, newAttributes, newAttribute);
            }
            if (ATTRIBUTES_UPDATER.compareAndSet(this, attributes, newAttributes)) {
                return newAttribute;
            }
        }
    }

    @Override
    public <T> boolean hasAttr(AttributeKey<T> key) {
        ObjectUtil.checkNotNull(key, "key");
        return searchAttributeByKey(attributes, key) >= 0;
    }

    private <T> void removeAttributeIfMatch(AttributeKey<T> key, DefaultAttribute<T> value) {
        for (;;) {
            final DefaultAttribute[] attributes = this.attributes;
            final int index = searchAttributeByKey(attributes, key);
            if (index < 0) {
                return;
            }
            final DefaultAttribute attribute = attributes[index];
            assert attribute.key() == key;
            if (attribute != value) {
                return;
            }
            final int count = attributes.length;
            final int newCount = count - 1;
            final DefaultAttribute[] newAttributes =
                    newCount == 0? EMPTY_ATTRIBUTES : new DefaultAttribute[newCount];
            // perform 2 bulk copies
            System.arraycopy(attributes, 0, newAttributes, 0, index);
            final int remaining = count - index - 1;
            if (remaining > 0) {
                System.arraycopy(attributes, index + 1, newAttributes, index, remaining);
            }
            if (ATTRIBUTES_UPDATER.compareAndSet(this, attributes, newAttributes)) {
                return;
            }
        }
    }

    @SuppressWarnings("serial")
    private static final class DefaultAttribute<T> extends AtomicReference<T> implements Attribute<T> {

        private static final AtomicReferenceFieldUpdater<DefaultAttribute, DefaultAttributeMap> MAP_UPDATER =
                AtomicReferenceFieldUpdater.newUpdater(DefaultAttribute.class,
                                                       DefaultAttributeMap.class, "attributeMap");
        private static final long serialVersionUID = -2661411462200283011L;

        private volatile DefaultAttributeMap attributeMap;
        private final AttributeKey<T> key;

        DefaultAttribute(DefaultAttributeMap attributeMap, AttributeKey<T> key) {
            this.attributeMap = attributeMap;
            this.key = key;
        }

        @Override
        public AttributeKey<T> key() {
            return key;
        }

        private boolean isRemoved() {
            return attributeMap == null;
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
            final DefaultAttributeMap attributeMap = this.attributeMap;
            final boolean removed = attributeMap != null && MAP_UPDATER.compareAndSet(this, attributeMap, null);
            T oldValue = getAndSet(null);
            if (removed) {
                attributeMap.removeAttributeIfMatch(key, this);
            }
            return oldValue;
        }

        @Override
        public void remove() {
            final DefaultAttributeMap attributeMap = this.attributeMap;
            final boolean removed = attributeMap != null && MAP_UPDATER.compareAndSet(this, attributeMap, null);
            set(null);
            if (removed) {
                attributeMap.removeAttributeIfMatch(key, this);
            }
        }
    }
}
