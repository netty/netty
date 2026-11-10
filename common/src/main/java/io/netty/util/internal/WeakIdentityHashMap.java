/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.internal;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * A {@link Map} with the properties of both {@link IdentityHashMap} and {@link WeakHashMap}.
 * Implementation is based on open-addressing. Not intended for more than a few thousand entries.
 * <p>
 * {@code null} keys and values are not supported by this implementation.
 * <p>
 * This class is not thread-safe.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class WeakIdentityHashMap<K, V> extends AbstractMap<K, V> {
    private static final int INITIAL_SIZE = 32;
    // Same general implementation as LongLongHashMap
    private final ReferenceQueue<K> queue;
    private IdentityEntry[] array;
    private int mask;
    private int maxProbe;
    private int size;

    public WeakIdentityHashMap() {
        queue = new ReferenceQueue<>();
        array = new IdentityEntry[INITIAL_SIZE];
        computeMaskAndProbe();
    }

    @Override
    public @NotNull Set<Entry<K, V>> entrySet() {
        return new EntrySet<>(this);
    }

    @Override
    public @NotNull Set<K> keySet() {
        return new KeySet<>(new EntrySet<>(this));
    }

    @Override
    public int size() {
        cleanStaleEntries();
        return size;
    }

    @Override
    public V put(K key, V value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        cleanStaleEntries();
        int hash = System.identityHashCode(key);
        for (;;) {
            int index = index(hash);
            for (int i = 0; i < maxProbe; i++) {
                IdentityEntry<K, V> existing = array[index];
                if (existing == null || existing.get() == key) {
                    V prev;
                    if (existing == null) {
                        prev = null;
                        array[index] = new IdentityEntry<>(key, value, hash, queue);
                    } else {
                        prev = existing.getValue();
                        existing.setValue(value);
                    }
                    for (; i < maxProbe; i++) { // Nerf any existing misplaced entries.
                        index = index + 1 & mask;
                        IdentityEntry<K, V> misplaced = array[index];
                        if (misplaced != null && misplaced.get() == key) {
                            misplaced.clear();
                            array[index] = null;
                            prev = misplaced.getValue();
                            break;
                        }
                    }
                    if (prev == null) {
                        size++;
                    }
                    return prev;
                }
                index = index + 1 & mask;
            }
            expand(); // Grow array and re-hash.
        }
    }

    @Override
    public V get(Object key) {
        Objects.requireNonNull(key, "key");
        cleanStaleEntries();
        int hash = System.identityHashCode(key);
        int index = index(hash);
        for (int i = 0; i < maxProbe; i++) {
            IdentityEntry<K, V> existing = array[index];
            if (existing != null && existing.get() == key) {
                return existing.getValue();
            }
            index = index + 1 & mask;
        }
        return null;
    }

    @Override
    public boolean containsKey(Object key) {
        Objects.requireNonNull(key, "key");
        cleanStaleEntries();
        int hash = System.identityHashCode(key);
        int index = index(hash);
        for (int i = 0; i < maxProbe; i++) {
            IdentityEntry<K, V> existing = array[index];
            if (existing != null && existing.get() == key) {
                return true;
            }
            index = index + 1 & mask;
        }
        return false;
    }

    @Override
    public V remove(Object key) {
        Objects.requireNonNull(key, "key");
        cleanStaleEntries();
        int hash = System.identityHashCode(key);
        int index = index(hash);
        for (int i = 0; i < maxProbe; i++) {
            IdentityEntry<K, V> existing = array[index];
            if (existing != null && existing.get() == key) {
                existing.clear();
                array[index] = null;
                size--;
                return existing.getValue();
            }
            index = index + 1 & mask;
        }
        return null;
    }

    void removeEntry(IdentityEntry<K, V> entry) {
        Objects.requireNonNull(entry, "entry");
        cleanStaleEntries();
        int hash = entry.hash;
        int index = index(hash);
        for (int i = 0; i < maxProbe; i++) {
            IdentityEntry<K, V> existing = array[index];
            if (existing == entry) {
                existing.clear();
                size--;
                array[index] = null;
                break;
            }
            index = index + 1 & mask;
        }
    }

    @Override
    public void clear() {
        cleanStaleEntries();
        IdentityEntry[] old = this.array;
        for (IdentityEntry entry : old) {
            if (entry != null) {
                entry.clear();
                entry.value = null; // Help GC
            }
        }
        array = new IdentityEntry[INITIAL_SIZE];
        computeMaskAndProbe();
        size = 0;
    }

    private void cleanStaleEntries() {
        IdentityEntry<K, V> entry;
        while ((entry = (IdentityEntry<K, V>) queue.poll()) != null) {
            int index = index(entry.hash);
            for (int i = 0; i < maxProbe; i++) {
                if (array[index] == entry) {
                    size--;
                    array[index] = null;
                    entry.value = null; // Help GC
                    break;
                }
                index = index + 1 & mask;
            }
        }
    }

    private int index(int key) {
        // 32-bit finalization mix of murmur3.
        key ^= key >>> 16;
        key *= 0x85ebca6b;
        key ^= key >>> 13;
        key *= 0xc2b2ae35;
        key ^= key >>> 16;
        return key & mask;
    }

    private void expand() {
        IdentityEntry[] prev = array;
        array = new IdentityEntry[prev.length * 2];
        computeMaskAndProbe();
        size = 0;
        for (IdentityEntry<K, V> entry : prev) {
            K key; // Must capture strong ref to key and check it's not null before calling 'put'
            if (entry != null && (key = entry.get()) != null) {
                put(key, entry.getValue());
            }
        }
    }

    private void computeMaskAndProbe() {
        int length = array.length;
        mask = length - 1;
        maxProbe = 2 * (int) Math.log(length);
    }

    private static final class IdentityEntry<K, V> extends WeakReference<K> implements Map.Entry<K, V> {
        private final int hash;
        private V value;

        private IdentityEntry(K key, V value, int hash, ReferenceQueue<K> queue) {
            super(Objects.requireNonNull(key, "key"), queue);
            this.hash = hash;
            this.value = value;
        }

        @Override
        public int hashCode() {
            return hash ^ value.hashCode();
        }

        @Override
        public K getKey() {
            return get();
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(V value) {
            Objects.requireNonNull(value, "value");
            V prev = this.value;
            this.value = value;
            return prev;
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean equals(Object obj) {
            if (obj == null || obj.getClass() != IdentityEntry.class) {
                return false;
            }
            IdentityEntry<K, V> other = (IdentityEntry<K, V>) obj;
            return get() == other.get() &&
                Objects.equals(value, other.value);
        }
    }

    private static final class EntrySet<K, V> extends AbstractSet<Map.Entry<K, V>> {
        private final WeakIdentityHashMap map;

        EntrySet(WeakIdentityHashMap map) {
            this.map = map;
        }

        @Override
        public @NotNull Iterator<Entry<K, V>> iterator() {
            map.cleanStaleEntries();
            return new Iterator<Entry<K, V>>() {
                private final IdentityEntry[] array = map.array;
                private int index;
                private IdentityEntry<K, V> next = findNext();
                @SuppressWarnings("FieldCanBeLocal")
                private K nextKey; // Keep key for 'next' alive until 'next()' is called
                private IdentityEntry<K, V> prev; // For supporting 'remove()'

                @Override
                public boolean hasNext() {
                    return next != null;
                }

                @Override
                public Entry<K, V> next() {
                    IdentityEntry<K, V> result = this.next;
                    if (result == null) {
                        throw new NoSuchElementException();
                    }
                    prev = next;
                    next = findNext();
                    return result;
                }

                @Override
                public void remove() {
                    IdentityEntry<K, V> prev = this.prev;
                    if (prev == null) {
                        throw new IllegalStateException();
                    }
                    this.prev = null;
                    map.removeEntry(prev);
                }

                private IdentityEntry<K, V> findNext() {
                    for (; index < array.length; index++) {
                        IdentityEntry<K, V> next = array[index];
                        if (next != null && (nextKey = next.get()) != null) {
                            index++;
                            return next;
                        }
                    }
                    nextKey = null;
                    return null;
                }
            };
        }

        @Override
        public boolean remove(Object o) {
            if (!(o instanceof Map.Entry)) {
                return false;
            }
            Map.Entry entry = (Entry) o;
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (key != null && value != null) {
                return map.remove(key, value);
            }
            return false;
        }

        @Override
        public int size() {
            return map.size();
        }
    }

    private static final class KeySet<K, V> extends AbstractSet<K> {
        @NotNull
        private final EntrySet<K, V> entries;

        KeySet(@NotNull EntrySet<K, V> entries) {
            this.entries = entries;
        }

        @Override
        public @NotNull Iterator<K> iterator() {
            return new Iterator<K>() {
                final Iterator<Entry<K, V>> itr = entries.iterator();
                @Override
                public boolean hasNext() {
                    return itr.hasNext();
                }

                @Override
                public K next() {
                    return itr.next().getKey();
                }

                @Override
                public void remove() {
                    itr.remove();
                }
            };
        }

        @Override
        public boolean remove(Object o) {
            return entries.map.remove(o) != null;
        }

        @Override
        public int size() {
            return entries.size();
        }
    }
}
