/*
 * Copyright 2022 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.netty5.handler.codec.http.headers;

import io.netty5.util.internal.UnstableApi;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiPredicate;

import static io.netty5.handler.codec.http.headers.HeaderUtils.HASH_CODE_SEED;
import static io.netty5.util.internal.MathUtil.findNextPositivePowerOfTwo;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Collections.emptyIterator;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;

/**
 * A {@link Map} like implementation which supports multiple values for a single key.
 * <h1>Implementation Details (subject to change)</h1>
 * This class is designed to store "headers" which are commonly used in protocols to represent meta data.
 * Common protocols typically have the following properties:
 * <ul>
 * <li>Fast overall iteration - encoding typical requires iteration</li>
 * <li>Fast lookup for single value - a single value associative array is a common usage pattern</li>
 * <li>Multi value lookup - some use cases require multi value associative array like storage</li>
 * <li>Consistent iteration for a given key - the iteration order for a multi value lookup {@link #getValues(Object)}
 * should reflect the insertion order for each key</li>
 * <li>Avoid copy/resize operations - headers can be created frequently and cause GC pressure, so we should try to
 * minimize intermediate GC.</li>
 * </ul>
 * These requirements make using a Map&lt;K, List&lt;V&gt;&gt; structure prohibitive due to extra allocation and resize
 * operations.
 *
 * @param <K> The type of key.
 * @param <V> The type of value.
 */
@UnstableApi
@Internal
public abstract class MultiMap<K, V> {
    final BucketHead<K, V>[] entries;
    @Nullable
    BucketHead<K, V> lastBucketHead;
    private final byte hashMask;
    private int size;

    @SuppressWarnings("unchecked")
    MultiMap(final int arraySizeHint) {
        // Enforce a bound of [2, 128] because hashMask is a byte. The max possible value of hashMask is one less
        // than the length of this array, and we want the mask to be > 0.
        entries = (BucketHead<K, V>[]) new BucketHead[findNextPositivePowerOfTwo(max(2, min(arraySizeHint, 128)))];
        hashMask = (byte) (entries.length - 1);
    }

    /**
     * Generate a hash code for {@code key} used as an index in this {@link MultiMap}.
     *
     * @param key The key to create the hash code for.
     * @return a hash code for {@code key} used as an index in this {@link MultiMap}.
     */
    @Internal
    protected abstract int hashCode(K key);

    /**
     * Compare {@code key1} and {@code key2} for equality.
     *
     * @param key1 The first key.
     * @param key2 The second key.
     * @return {@code true} if {@code key1} and {@code key2} are equal.
     */
    @Internal
    protected abstract boolean equals(K key1, K key2);

    /**
     * Determine if the {@link #hashCode(Object)} and {@link #equals(Object, Object)} strategy are compatible with
     * {@code multiMap}.
     *
     * @param multiMap the {@link MultiMap} to compare.
     * @return {@code true} if the {@link #hashCode(Object)} and {@link #equals(Object, Object)} strategy are compatible
     * with {@code multiMap}.
     */
    @Internal
    protected abstract boolean isKeyEqualityCompatible(MultiMap<? extends K, ? extends V> multiMap);

    /**
     * Validate the key before inserting it into this {@link MultiMap}.
     *
     * @param key The key which will be inserted.
     */
    @Internal
    protected abstract K validateKey(K key);

    /**
     * Validate the value before inserting it into this {@link MultiMap}.
     *
     * @param value The value which will be inserted.
     */
    @Internal
    protected abstract V validateValue(V value);

    /**
     * Generate a hash code for {@code value} using for equality comparisons and {@link #hashCode(Object)}.
     *
     * @param value the value to generate a hash code for.
     * @return a hash code for {@code value} using during equality comparisons and {@link #hashCode(Object)}.
     */
    @Internal
    protected abstract int hashCodeForValue(V value);

    /**
     * Compare {@code value1} and {@code value2} for equality.
     *
     * @param value1 The first value.
     * @param value2 The second value.
     * @return {@code true} if {@code value1} and {@code value2} are equal.
     */
    @Internal
    protected abstract boolean equalsForValue(V value1, V value2);

    /**
     * Create a new {@link MultiMapEntry} to represent an entry in this {@link MultiMap}.
     *
     * @param key     The key for the {@link MultiMapEntry}.
     * @param value   The value for the {@link MultiMapEntry}.
     * @param keyHash The hash code for {@code key}.
     * @return a new {@link MultiMapEntry} to represent an entry in this {@link MultiMap}.
     */
    private MultiMapEntry<K, V> newEntry(K key, V value, int keyHash) {
        return new MultiMapEntry<>(key, value, keyHash);
    }

    final Set<K> getKeys() {
        if (isEmpty()) {
            return emptySet();
        }
        // Overall iteration order does not need to be preserved.
        final Set<K> names = new HashSet<>((int) (size() / .75), .75f);
        BucketHead<K, V> bucketHead = lastBucketHead;
        while (bucketHead != null) {
            MultiMapEntry<K, V> e = bucketHead.entry;
            assert e != null;
            do {
                names.add(e.getKey());
                e = e.bucketNext;
            } while (e != null);
            bucketHead = bucketHead.prevBucketHead;
        }
        return names;
    }

    @Internal
    public final int size() {
        return size;
    }

    @Internal
    public final boolean isEmpty() {
        return lastBucketHead == null;
    }

    @Nullable
    final V getValue(final K key) {
        final int nameHash = hashCode(key);
        final int i = index(nameHash);
        final BucketHead<K, V> bucketHead = entries[i];
        if (bucketHead != null) {
            MultiMapEntry<K, V> e = bucketHead.entry;
            assert e != null;
            do {
                if (e.keyHash == nameHash && equals(key, e.getKey())) {
                    return e.value;
                }
                e = e.bucketNext;
            } while (e != null);
        }
        return null;
    }

    final Iterator<V> getValues(final K key) {
        final int keyHash = hashCode(key);
        final BucketHead<K, V> bucketHead = entries[index(keyHash)];
        if (bucketHead == null) {
            return emptyIterator();
        }
        MultiMapEntry<K, V> e = bucketHead.entry;
        assert e != null;
        do {
            if (e.keyHash == keyHash && equals(key, e.getKey())) {
                return new ValuesByNameIterator(keyHash, key, e);
            }
            e = e.bucketNext;
        } while (e != null);
        return emptyIterator();
    }

    @Internal
    public final boolean contains(final K key, final V value) {
        return contains(key, value, this::equalsForValue);
    }

    final boolean contains(final K key, final V value, final BiPredicate<V, V> valueCompare) {
        final int keyHash = hashCode(key);
        final int bucketIndex = index(keyHash);
        final BucketHead<K, V> bucketHead = entries[bucketIndex];
        if (bucketHead != null) {
            MultiMapEntry<K, V> e = bucketHead.entry;
            assert e != null;
            do {
                if (e.keyHash == keyHash && equals(key, e.getKey()) && valueCompare.test(value, e.value)) {
                    return true;
                }
                e = e.bucketNext;
            } while (e != null);
        }
        return false;
    }

    final void put(final K key, final V value) {
        final int keyHash = hashCode(validateKey(key));
        final int bucketIndex = index(keyHash);
        putEntry(keyHash, bucketIndex, key, validateValue(value));
    }

    final void putAll(final K key, final Iterable<? extends V> values) {
        final int keyHash = hashCode(validateKey(key));
        final int bucketIndex = index(keyHash);
        BucketHead<K, V> bucketHead = entries[bucketIndex];
        if (bucketHead != null) {
            for (final V v : values) {
                putEntry(bucketHead, keyHash, bucketIndex, key, validateValue(v));
            }
        } else {
            final Iterator<? extends V> valueItr = values.iterator();
            if (valueItr.hasNext()) {
                bucketHead = putEntry(keyHash, bucketIndex, key, validateValue(valueItr.next()));
                while (valueItr.hasNext()) {
                    putEntry(bucketHead, keyHash, bucketIndex, key, validateValue(valueItr.next()));
                }
            }
        }
    }

    @SafeVarargs
    final void putAll(final K key, final V... values) {
        final int keyHash = hashCode(validateKey(key));
        final int bucketIndex = index(keyHash);
        BucketHead<K, V> bucketHead = entries[bucketIndex];
        if (bucketHead != null) {
            for (final V v : values) {
                putEntry(bucketHead, keyHash, bucketIndex, key, validateValue(v));
            }
        } else if (values.length != 0) {
            bucketHead = putEntry(keyHash, bucketIndex, key, validateValue(values[0]));
            for (int i = 1; i < values.length; ++i) {
                putEntry(bucketHead, keyHash, bucketIndex, key, validateValue(values[i]));
            }
        }
    }

    final void putAll(final MultiMap<? extends K, ? extends V> multiMap) {
        putAll0(multiMap);
    }

    final void putExclusive(final K key, final V value) {
        final int keyHash = hashCode(validateKey(key));
        final int bucketIndex = index(keyHash);
        removeAll(key, keyHash, bucketIndex);
        putEntry(keyHash, bucketIndex, key, validateValue(value));
    }

    final void putExclusive(final K key, final Iterable<? extends V> values) {
        final int keyHash = hashCode(validateKey(key));
        final int bucketIndex = index(keyHash);
        removeAll(key, keyHash, bucketIndex);
        final Iterator<? extends V> valueItr = values.iterator();
        if (valueItr.hasNext()) {
            BucketHead<K, V> bucketHead = entries[bucketIndex];
            if (bucketHead == null) {
                bucketHead = putEntry(keyHash, bucketIndex, key, validateValue(valueItr.next()));
                if (!valueItr.hasNext()) {
                    return;
                }
            }
            do {
                putEntry(bucketHead, keyHash, bucketIndex, key, validateValue(valueItr.next()));
            } while (valueItr.hasNext());
        }
    }

    @SafeVarargs
    final void putExclusive(final K key, final V... values) {
        final int keyHash = hashCode(validateKey(key));
        final int bucketIndex = index(keyHash);
        removeAll(key, keyHash, bucketIndex);
        if (values.length != 0) {
            BucketHead<K, V> bucketHead = entries[bucketIndex];
            int i = 0;
            if (bucketHead == null) {
                bucketHead = putEntry(keyHash, bucketIndex, key, validateValue(values[0]));
                i = 1;
            }
            for (; i < values.length; ++i) {
                putEntry(bucketHead, keyHash, bucketIndex, key, validateValue(values[i]));
            }
        }
    }

    final void clearAll() {
        Arrays.fill(entries, null);
        lastBucketHead = null;
        size = 0;
    }

    final boolean removeAll(final K key) {
        final int keyHash = hashCode(key);
        final int sizeBefore = size;
        removeAll(key, keyHash, index(keyHash));
        return sizeBefore != size;
    }

    private void removeAll(final K key, final int keyHash, final int bucketIndex) {
        final BucketHead<K, V> bucketHead = entries[bucketIndex];
        if (bucketHead == null) {
            return;
        }
        MultiMapEntry<K, V> e = bucketHead.entry;
        assert e != null;
        do {
            if (e.keyHash == keyHash && equals(key, e.getKey())) {
                final MultiMapEntry<K, V> tmpEntry = e;
                e = e.bucketNext;
                removeEntry(bucketHead, tmpEntry, bucketIndex);
            } else {
                e = e.bucketNext;
            }
        } while (e != null);
    }

    @Nullable
    final V removeAllAndGetFirst(final K key) {
        final int keyHash = hashCode(key);
        final int bucketIndex = index(keyHash);
        final BucketHead<K, V> bucketHead = entries[bucketIndex];
        if (bucketHead == null) {
            return null;
        }
        V value = null;
        MultiMapEntry<K, V> e = bucketHead.entry;
        assert e != null;
        do {
            if (e.keyHash == keyHash && equals(key, e.getKey())) {
                if (value == null) {
                    value = e.getValue();
                }
                final MultiMapEntry<K, V> tmpEntry = e;
                e = e.bucketNext;
                removeEntry(bucketHead, tmpEntry, bucketIndex);
            } else {
                e = e.bucketNext;
            }
        } while (e != null);
        return value;
    }

    final Iterator<Entry<K, V>> entryIterator() {
        return lastBucketHead == null ? emptyIterator() : new FullEntryIterator(lastBucketHead);
    }

    final Iterator<V> valueIterator() {
        return lastBucketHead == null ? emptyIterator() : new ValueEntryIterator(lastBucketHead);
    }

    final int index(final int hash) {
        return hash & hashMask;
    }

    final void removeEntry(@NotNull final BucketHead<K, V> bucketHead,
                           @NotNull final MultiMapEntry<K, V> entryToRemove,
                           final int bucketIndex) {
        // Check to see if the entry to remove is the bucketHead entry.
        if (bucketHead.entry == entryToRemove) {
            if (bucketHead.entry.bucketNext == null) {
                entries[bucketIndex] = null;
                if (lastBucketHead == bucketHead) {
                    // bucketHead is either the last bucket or the only bucket.
                    if (lastBucketHead.prevBucketHead != null) {
                        lastBucketHead.prevBucketHead.nextBucketHead = null;
                    }
                    lastBucketHead = lastBucketHead.prevBucketHead;
                } else {
                    // bucketHead is either a middle bucket or the first bucket.
                    assert lastBucketHead != null;
                    if (bucketHead.prevBucketHead != null) {
                        bucketHead.prevBucketHead.nextBucketHead = bucketHead.nextBucketHead;
                    }
                    assert bucketHead.nextBucketHead != null;
                    bucketHead.nextBucketHead.prevBucketHead = bucketHead.prevBucketHead;
                }
            } else {
                // The next entry will now be the bucket head. We need to point it's bucketLastOrPrevious to the last
                // entry, and remove its next links.
                bucketHead.entry.bucketNext.bucketLastOrPrevious = bucketHead.entry.bucketLastOrPrevious;
                bucketHead.entry = bucketHead.entry.bucketNext;
            }
        } else if (bucketHead.entry == null || entryToRemove.bucketLastOrPrevious == null) {
            throw new ConcurrentModificationException();
        } else {
            if (bucketHead.entry.bucketLastOrPrevious == entryToRemove) {
                bucketHead.entry.bucketLastOrPrevious = entryToRemove.bucketLastOrPrevious;
            }
            entryToRemove.bucketLastOrPrevious.bucketNext = entryToRemove.bucketNext;
            if (entryToRemove.bucketNext != null) {
                entryToRemove.bucketNext.bucketLastOrPrevious = entryToRemove.bucketLastOrPrevious;
            }
        }

        // Prevent GC nepotism.
        entryToRemove.bucketLastOrPrevious = entryToRemove.bucketNext = null;

        --size;
    }

    @Override
    public int hashCode() {
        if (isEmpty()) {
            return 0;
        }
        int result = HASH_CODE_SEED;
        for (final K key : getKeys()) {
            result = 31 * result + hashCode(key);
            final Iterator<? extends V> valueItr = getValues(key);
            while (valueItr.hasNext()) {
                result = 31 * result + hashCodeForValue(valueItr.next());
            }
        }
        return result;
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof MultiMap)) {
            return false;
        }

        @SuppressWarnings("unchecked")
        final MultiMap<K, V> h2 = (MultiMap<K, V>) o;
        if (h2.size() != size()) {
            return false;
        }

        if (this == h2) {
            return true;
        }

        // The regular iterator is not suitable for equality comparisons because the overall ordering is not
        // in any specific order relative to the content of this MultiMap.
        for (final K key : getKeys()) {
            final Iterator<? extends V> valueItr = getValues(key);
            final Iterator<? extends V> h2ValueItr = h2.getValues(key);
            while (valueItr.hasNext() && h2ValueItr.hasNext()) {
                if (!equalsForValue(valueItr.next(), h2ValueItr.next())) {
                    return false;
                }
            }
            if (valueItr.hasNext() != h2ValueItr.hasNext()) {
                return false;
            }
        }
        return true;
    }

    private BucketHead<K, V> putEntry(@Nullable BucketHead<K, V> bucketHead, final int keyHash, final int bucketIndex,
                                      final K key, final V value) {
        final MultiMapEntry<K, V> newEntry = newEntry(key, value, keyHash);
        if (bucketHead == null) {
            bucketHead = new BucketHead<>(lastBucketHead, newEntry);
            entries[bucketIndex] = bucketHead;
            lastBucketHead = bucketHead;
            newEntry.addAsBucketHead();
        } else {
            newEntry.addToBucketTail(bucketHead);
        }
        ++size;
        return bucketHead;
    }

    private BucketHead<K, V> putEntry(final int keyHash, final int bucketIndex, final K key, final V value) {
        return putEntry(entries[bucketIndex], keyHash, bucketIndex, key, value);
    }

    private void putAll0(final MultiMap<? extends K, ? extends V> rhs) {
        if (isKeyEqualityCompatible(rhs)) { // Fast path
            BucketHead<? extends K, ? extends V> rhsBucketHead = rhs.lastBucketHead;
            while (rhsBucketHead != null) {
                MultiMapEntry<? extends K, ? extends V> rhsEntry = rhsBucketHead.entry;
                assert rhsEntry != null;
                final int bucketIndex = index(rhsEntry.keyHash);
                BucketHead<K, V> bucketHead = entries[bucketIndex];
                if (bucketHead == null) {
                    bucketHead = putEntry(null, rhsEntry.keyHash, bucketIndex, rhsEntry.getKey(), rhsEntry.getValue());
                    rhsEntry = rhsEntry.bucketNext;
                    if (rhsEntry == null) {
                        rhsBucketHead = rhsBucketHead.prevBucketHead;
                        continue;
                    }
                }
                do {
                    putEntry(bucketHead, rhsEntry.keyHash, bucketIndex, rhsEntry.getKey(), rhsEntry.getValue());
                    rhsEntry = rhsEntry.bucketNext;
                } while (rhsEntry != null);
                rhsBucketHead = rhsBucketHead.prevBucketHead;
            }
        } else {
            BucketHead<? extends K, ? extends V> rhsBucketHead = rhs.lastBucketHead;
            while (rhsBucketHead != null) {
                MultiMapEntry<? extends K, ? extends V> rhsEntry = rhsBucketHead.entry;
                assert rhsEntry != null;
                do {
                    putEntry(rhsEntry.keyHash, index(rhsEntry.keyHash), rhsEntry.getKey(), rhsEntry.getValue());
                    rhsEntry = rhsEntry.bucketNext;
                } while (rhsEntry != null);
                rhsBucketHead = rhsBucketHead.prevBucketHead;
            }
        }
    }

    @Internal
    protected abstract class EntryIterator<T> implements Iterator<T> {
        @Nullable
        private MultiMapEntry<K, V> previous;
        @Nullable
        private BucketHead<K, V> currentBucketHead;
        @Nullable
        private MultiMapEntry<K, V> current;

        EntryIterator(final BucketHead<K, V> lastBucketHead) {
            currentBucketHead = lastBucketHead;
            current = lastBucketHead.entry;
        }

        @Override
        public boolean hasNext() {
            return current != null;
        }

        /**
         * @return The object that would be returned by a following {@link #next()} call, or {@code null}.
         */
        public T peekNext() {
            return current == null ? null : extractNextFromEntry(current);
        }

        @Override
        public T next() {
            if (current == null) {
                throw new NoSuchElementException();
            }
            previous = current;

            if (current.bucketNext == null) {
                assert currentBucketHead != null;
                currentBucketHead = currentBucketHead.prevBucketHead;
                current = currentBucketHead == null ? null : currentBucketHead.entry;
            } else {
                current = current.bucketNext;
            }

            return extractNextFromEntry(previous);
        }

        @Override
        public void remove() {
            if (previous == null) {
                throw new IllegalStateException();
            }
            final int i = index(previous.keyHash);
            removeEntry(entries[i], previous, i);
            previous = null;
        }

        abstract T extractNextFromEntry(MultiMapEntry<K, V> entry);
    }

    private final class FullEntryIterator extends EntryIterator<Entry<K, V>> {
        FullEntryIterator(final BucketHead<K, V> lastBucketHead) {
            super(lastBucketHead);
        }

        @Override
        Entry<K, V> extractNextFromEntry(final MultiMapEntry<K, V> entry) {
            return entry;
        }
    }

    private final class ValueEntryIterator extends EntryIterator<V> {
        ValueEntryIterator(final BucketHead<K, V> lastBucketHead) {
            super(lastBucketHead);
        }

        @Override
        V extractNextFromEntry(final MultiMapEntry<K, V> entry) {
            return entry.value;
        }
    }

    private final class ValuesByNameIterator implements Iterator<V> {
        final int keyHashCode;
        final K key;
        @Nullable
        private MultiMapEntry<K, V> current;
        @Nullable
        private MultiMapEntry<K, V> previous;

        ValuesByNameIterator(final int keyHashCode, final K key, final @Nullable MultiMapEntry<K, V> first) {
            this.keyHashCode = keyHashCode;
            this.key = key;
            current = first;
        }

        @Override
        public boolean hasNext() {
            return current != null;
        }

        @Override
        public V next() {
            if (current == null) {
                throw new NoSuchElementException();
            }
            previous = current;
            current = findNext(current.bucketNext);
            return previous.value;
        }

        @Override
        public void remove() {
            if (previous == null) {
                throw new IllegalStateException();
            }
            final int i = index(keyHashCode);
            removeEntry(entries[i], previous, i);
            previous = null;
        }

        @Nullable
        private MultiMapEntry<K, V> findNext(@Nullable MultiMapEntry<K, V> entry) {
            while (entry != null) {
                if (entry.keyHash == keyHashCode && MultiMap.this.equals(key, entry.getKey())) {
                    return entry;
                }
                entry = entry.bucketNext;
            }
            return null;
        }
    }

    static final class BucketHead<K, V> {
        @Nullable
        BucketHead<K, V> prevBucketHead;
        @Nullable
        BucketHead<K, V> nextBucketHead;
        MultiMapEntry<K, V> entry;

        BucketHead(@Nullable final BucketHead<K, V> prevBucketHead, final @Nullable MultiMapEntry<K, V> entry) {
            this.prevBucketHead = prevBucketHead;
            if (prevBucketHead != null) {
                prevBucketHead.nextBucketHead = this;
            }
            this.entry = entry;
        }
    }

    static final class MultiMapEntry<K, V> implements Entry<K, V> {
        final int keyHash;
        private final K key;
        V value;
        /**
         * In bucket linked list pointing to the next item in the bucket.
         */
        @Nullable
        MultiMapEntry<K, V> bucketNext;

        /**
         * In bucket linked list with a conditional purpose.
         * If this entry is the bucket head then this points to the last entry in the bucket.
         * If this entry is NOT the bucket head then it points to the previous entry in the bucket.
         * <p>
         * This exists so we can do constant time in order bucket insertions and removals.
         */
        @Nullable
        MultiMapEntry<K, V> bucketLastOrPrevious;

        MultiMapEntry(final K key, final V value, final int keyHash) {
            this.key = requireNonNull(key);
            if (value == null) {
                throw new IllegalArgumentException("Null value for key: " + key);
            }
            this.value = value;
            this.keyHash = keyHash;
        }

        void addToBucketTail(final BucketHead<K, V> bucketHead) {
            assert bucketHead.entry != null;
            assert bucketHead.entry.bucketLastOrPrevious != null;
            bucketLastOrPrevious = bucketHead.entry.bucketLastOrPrevious;
            bucketHead.entry.bucketLastOrPrevious.bucketNext = this;
            bucketHead.entry.bucketLastOrPrevious = this;
        }

        void addAsBucketHead() {
            bucketLastOrPrevious = this;
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(final V value) {
            requireNonNull(value);
            final V oldValue = this.value;
            this.value = value;
            return oldValue;
        }

        @Override
        public String toString() {
            return getKey() + "=" + value;
        }

        @Override
        public boolean equals(final Object o) {
            if (!(o instanceof Entry)) {
                return false;
            }
            final Entry<?, ?> other = (Entry<?, ?>) o;
            return getKey().equals(other.getKey()) && value.equals(other.getValue());
        }

        @Override
        public int hashCode() {
            return getKey().hashCode() ^ value.hashCode();
        }
    }
}
