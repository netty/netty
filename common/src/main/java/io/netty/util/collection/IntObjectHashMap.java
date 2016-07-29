/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.util.collection;

import java.lang.reflect.Array;
import java.util.AbstractCollection;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A hash map implementation of {@link IntObjectMap} that uses open addressing for keys.
 * To minimize the memory footprint, this class uses open addressing rather than chaining.
 * Collisions are resolved using linear probing. Deletions implement compaction, so cost of
 * remove can approach O(N) for full maps, which makes a small loadFactor recommended.
 *
 * @param <V> The value type stored in the map.
 */
public class IntObjectHashMap<V> implements IntObjectMap<V>, Iterable<IntObjectMap.Entry<V>> {

    /** Default initial capacity. Used if not specified in the constructor */
    private static final int DEFAULT_CAPACITY = 11;

    /** Default load factor. Used if not specified in the constructor */
    private static final float DEFAULT_LOAD_FACTOR = 0.5f;

    /**
     * Placeholder for null values, so we can use the actual null to mean available.
     * (Better than using a placeholder for available: less references for GC processing.)
     */
    private static final Object NULL_VALUE = new Object();

    /** The maximum number of elements allowed without allocating more space. */
    private int maxSize;

    /** The load factor for the map. Used to calculate {@link #maxSize}. */
    private final float loadFactor;

    private int[] keys;
    private V[] values;
    private Collection<V> valueCollection;
    private int size;

    public IntObjectHashMap() {
        this(DEFAULT_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    public IntObjectHashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    public IntObjectHashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 1) {
            throw new IllegalArgumentException("initialCapacity must be >= 1");
        }

        if (loadFactor <= 0.0f || loadFactor > 1.0f) {
            // Cannot exceed 1 because we can never store more than capacity elements;
            // using a bigger loadFactor would trigger rehashing before the desired load is reached.
            throw new IllegalArgumentException("loadFactor must be > 0 and <= 1");
        }

        this.loadFactor = loadFactor;

        // Adjust the initial capacity if necessary.
        int capacity = adjustCapacity(initialCapacity);

        // Allocate the arrays.
        keys = new int[capacity];
        @SuppressWarnings({ "unchecked", "SuspiciousArrayCast" })
        V[] temp = (V[]) new Object[capacity];
        values = temp;

        // Initialize the maximum size value.
        maxSize = calcMaxSize(capacity);
    }

    private static <T> T toExternal(T value) {
        return value == NULL_VALUE ? null : value;
    }

    @SuppressWarnings("unchecked")
    private static <T> T toInternal(T value) {
        return value == null ? (T) NULL_VALUE : value;
    }

    @Override
    public V get(int key) {
        int index = indexOf(key);
        return index == -1 ? null : toExternal(values[index]);
    }

    @Override
    public V put(int key, V value) {
        int startIndex = hashIndex(key);
        int index = startIndex;

        for (;;) {
            if (values[index] == null) {
                // Found empty slot, use it.
                keys[index] = key;
                values[index] = toInternal(value);
                growSize();
                return null;
            }
            if (keys[index] == key) {
                // Found existing entry with this key, just replace the value.
                V previousValue = values[index];
                values[index] = toInternal(value);
                return toExternal(previousValue);
            }

            // Conflict, keep probing ...
            if ((index = probeNext(index)) == startIndex) {
                // Can only happen if the map was full at MAX_ARRAY_SIZE and couldn't grow.
                throw new IllegalStateException("Unable to insert");
            }
        }
    }

    private int probeNext(int index) {
        return index == values.length - 1 ? 0 : index + 1;
    }

    @Override
    public void putAll(IntObjectMap<V> sourceMap) {
        if (sourceMap instanceof IntObjectHashMap) {
            // Optimization - iterate through the arrays.
            IntObjectHashMap<V> source = (IntObjectHashMap<V>) sourceMap;
            for (int i = 0; i < source.values.length; ++i) {
                V sourceValue = source.values[i];
                if (sourceValue != null) {
                    put(source.keys[i], sourceValue);
                }
            }
            return;
        }

        // Otherwise, just add each entry.
        for (Entry<V> entry : sourceMap.entries()) {
            put(entry.key(), entry.value());
        }
    }

    @Override
    public V remove(int key) {
        int index = indexOf(key);
        if (index == -1) {
            return null;
        }

        V prev = values[index];
        removeAt(index);
        return toExternal(prev);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public void clear() {
        Arrays.fill(keys, 0);
        Arrays.fill(values, null);
        size = 0;
    }

    @Override
    public boolean containsKey(int key) {
        return indexOf(key) >= 0;
    }

    @Override
    public boolean containsValue(V value) {
        V v1 = toInternal(value);
        for (V v2 : values) {
            // The map supports null values; this will be matched as NULL_VALUE.equals(NULL_VALUE).
            if (v2 != null && v2.equals(v1)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Iterable<Entry<V>> entries() {
        return this;
    }

    @Override
    public Iterator<Entry<V>> iterator() {
        return new IteratorImpl();
    }

    @Override
    public int[] keys() {
        int[] outKeys = new int[size()];
        int targetIx = 0;
        for (int i = 0; i < values.length; ++i) {
            if (values[i] != null) {
                outKeys[targetIx++] = keys[i];
            }
        }
        return outKeys;
    }

    @Override
    public V[] values(Class<V> clazz) {
        @SuppressWarnings("unchecked")
        V[] outValues = (V[]) Array.newInstance(clazz, size());
        int targetIx = 0;
        for (V value : values) {
            if (value != null) {
                outValues[targetIx++] = value;
            }
        }
        return outValues;
    }

    @Override
    public Collection<V> values() {
        Collection<V> valueCollection = this.valueCollection;
        if (valueCollection == null) {
            this.valueCollection = valueCollection = new AbstractCollection<V>() {
                @Override
                public Iterator<V> iterator() {
                    return new Iterator<V>() {
                        final Iterator<Entry<V>> iter = IntObjectHashMap.this.iterator();
                        @Override
                        public boolean hasNext() {
                            return iter.hasNext();
                        }

                        @Override
                        public V next() {
                            return iter.next().value();
                        }

                        @Override
                        public void remove() {
                            throw new UnsupportedOperationException();
                        }
                    };
                }

                @Override
                public int size() {
                    return size;
                }
            };
        }

        return valueCollection;
    }

    @Override
    public int hashCode() {
        // Hashcode is based on all non-zero, valid keys. We have to scan the whole keys
        // array, which may have different lengths for two maps of same size(), so the
        // capacity cannot be used as input for hashing but the size can.
        int hash = size;
        for (int key : keys) {
            // 0 can be a valid key or unused slot, but won't impact the hashcode in either case.
            // This way we can use a cheap loop without conditionals, or hard-to-unroll operations,
            // or the devastatingly bad memory locality of visiting value objects.
            // Also, it's important to use a hash function that does not depend on the ordering
            // of terms, only their values; since the map is an unordered collection and
            // entries can end up in different positions in different maps that have the same
            // elements, but with different history of puts/removes, due to conflicts.
            hash ^= key;
        }
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof IntObjectMap)) {
            return false;
        }
        @SuppressWarnings("rawtypes")
        IntObjectMap other = (IntObjectMap) obj;
        if (size != other.size()) {
            return false;
        }
        for (int i = 0; i < values.length; ++i) {
            V value = values[i];
            if (value != null) {
                int key = keys[i];
                Object otherValue = other.get(key);
                if (value == NULL_VALUE) {
                    if (otherValue != null) {
                        return false;
                    }
                } else if (!value.equals(otherValue)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Locates the index for the given key. This method probes using double hashing.
     *
     * @param key the key for an entry in the map.
     * @return the index where the key was found, or {@code -1} if no entry is found for that key.
     */
    private int indexOf(int key) {
        int startIndex = hashIndex(key);
        int index = startIndex;

        for (;;) {
            if (values[index] == null) {
                // It's available, so no chance that this value exists anywhere in the map.
                return -1;
            }
            if (key == keys[index]) {
                return index;
            }

            // Conflict, keep probing ...
            if ((index = probeNext(index)) == startIndex) {
                return -1;
            }
        }
    }

    /**
     * Returns the hashed index for the given key.
     */
    private int hashIndex(int key) {
        // Allowing for negative keys by adding the length after the first mod operation.
        return (key % keys.length + keys.length) % keys.length;
    }

    /**
     * Grows the map size after an insertion. If necessary, performs a rehash of the map.
     */
    private void growSize() {
        size++;

        if (size > maxSize) {
            // Need to grow the arrays. We take care to detect integer overflow,
            // also limit array size to ArrayList.MAX_ARRAY_SIZE.
            rehash(adjustCapacity((int) Math.min(keys.length * 2.0, Integer.MAX_VALUE - 8)));
        } else if (size == keys.length) {
            // Open addressing requires that we have at least 1 slot available. Need to refresh
            // the arrays to clear any removed elements.
            rehash(keys.length);
        }
    }

    /**
     * Adjusts the given capacity value to ensure that it's odd. Even capacities can break probing.
     */
    private static int adjustCapacity(int capacity) {
        return capacity | 1;
    }

    /**
     * Removes entry at the given index position. Also performs opportunistic, incremental rehashing
     * if necessary to not break conflict chains.
     *
     * @param index the index position of the element to remove.
     */
    private void removeAt(int index) {
        --size;
        // Clearing the key is not strictly necessary (for GC like in a regular collection),
        // but recommended for security. The memory location is still fresh in the cache anyway.
        keys[index] = 0;
        values[index] = null;

        // In the interval from index to the next available entry, the arrays may have entries
        // that are displaced from their base position due to prior conflicts. Iterate these
        // entries and move them back if possible, optimizing future lookups.
        // Knuth Section 6.4 Algorithm R, also used by the JDK's IdentityHashMap.

        int nextFree = index;
        for (int i = probeNext(index); values[i] != null; i = probeNext(i)) {
            int bucket = hashIndex(keys[i]);
            if (i < bucket && (bucket <= nextFree || nextFree <= i) ||
                bucket <= nextFree && nextFree <= i) {
                // Move the displaced entry "back" to the first available position.
                keys[nextFree] = keys[i];
                values[nextFree] = values[i];
                // Put the first entry after the displaced entry
                keys[i] = 0;
                values[i] = null;
                nextFree = i;
            }
        }
    }

    /**
     * Calculates the maximum size allowed before rehashing.
     */
    private int calcMaxSize(int capacity) {
        // Clip the upper bound so that there will always be at least one available slot.
        int upperBound = capacity - 1;
        return Math.min(upperBound, (int) (capacity * loadFactor));
    }

    /**
     * Rehashes the map for the given capacity.
     *
     * @param newCapacity the new capacity for the map.
     */
    private void rehash(int newCapacity) {
        int[] oldKeys = keys;
        V[] oldVals = values;

        keys = new int[newCapacity];
        @SuppressWarnings({ "unchecked", "SuspiciousArrayCast" })
        V[] temp = (V[]) new Object[newCapacity];
        values = temp;

        maxSize = calcMaxSize(newCapacity);

        // Insert to the new arrays.
        for (int i = 0; i < oldVals.length; ++i) {
            V oldVal = oldVals[i];
            if (oldVal != null) {
                // Inlined put(), but much simpler: we don't need to worry about
                // duplicated keys, growing/rehashing, or failing to insert.
                int oldKey = oldKeys[i];
                int index = hashIndex(oldKey);

                for (;;) {
                    if (values[index] == null) {
                        keys[index] = oldKey;
                        values[index] = toInternal(oldVal);
                        break;
                    }

                    // Conflict, keep probing. Can wrap around, but never reaches startIndex again.
                    index = probeNext(index);
                }
            }
        }
    }

    /**
     * Iterator for traversing the entries in this map.
     */
    private final class IteratorImpl implements Iterator<Entry<V>>, Entry<V> {
        private int prevIndex = -1;
        private int nextIndex = -1;
        private int entryIndex = -1;

        private void scanNext() {
            for (;;) {
                if (++nextIndex == values.length || values[nextIndex] != null) {
                    break;
                }
            }
        }

        @Override
        public boolean hasNext() {
            if (nextIndex == -1) {
                scanNext();
            }
            return nextIndex < keys.length;
        }

        @Override
        public Entry<V> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            prevIndex = nextIndex;
            scanNext();

            // Always return the same Entry object, just change its index each time.
            entryIndex = prevIndex;
            return this;
        }

        @Override
        public void remove() {
            if (prevIndex < 0) {
                throw new IllegalStateException("next must be called before each remove.");
            }
            removeAt(prevIndex);
            prevIndex = -1;
        }

        // Entry implementation. Since this implementation uses a single Entry, we coalesce that
        // into the Iterator object (potentially making loop optimization much easier).

        @Override
        public int key() {
            return keys[entryIndex];
        }

        @Override
        public V value() {
            return toExternal(values[entryIndex]);
        }

        @Override
        public void setValue(V value) {
            values[entryIndex] = toInternal(value);
        }
    }

    @Override
    public String toString() {
        if (size == 0) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder(4 * size);
        for (int i = 0; i < values.length; ++i) {
            V value = values[i];
            if (value != null) {
                sb.append(sb.length() == 0 ? "{" : ", ");
                sb.append(keyToString(keys[i])).append('=').append(value == this ? "(this Map)" : value);
            }
        }
        return sb.append('}').toString();
    }

    /**
     * Helper method called by {@link #toString()} in order to convert a single map key into a string.
     */
    protected String keyToString(int key) {
        return Integer.toString(key);
    }
}
