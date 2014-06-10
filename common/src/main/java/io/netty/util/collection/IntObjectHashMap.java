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
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A hash map implementation of {@link IntObjectMap} that uses open addressing for keys. To minimize
 * the memory footprint, this class uses open addressing rather than chaining. Collisions are
 * resolved using double hashing.
 *
 * @param <V> The value type stored in the map.
 */
public class IntObjectHashMap<V> implements IntObjectMap<V>, Iterable<IntObjectMap.Entry<V>> {

    /** State indicating that a slot is available.*/
    private static final byte AVAILABLE = 0;

    /** State indicating that a slot is occupied. */
    private static final byte OCCUPIED = 1;

    /** State indicating that a slot was removed. */
    private static final byte REMOVED = 2;

    /** Default initial capacity. Used if not specified in the constructor */
    private static final int DEFAULT_CAPACITY = 11;

    /** Default load factor. Used if not specified in the constructor */
    private static final float DEFAULT_LOAD_FACTOR = 0.5f;

    /** The maximum number of elements allowed without allocating more space. */
    private int maxSize;

    /** The load factor for the map. Used to calculate {@link maxSize}. */
    private final float loadFactor;

    private byte[] states;
    private int[] keys;
    private V[] values;
    private int size;
    private int available;

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
        if (loadFactor <= 0.0f) {
            throw new IllegalArgumentException("loadFactor must be > 0");
        }

        this.loadFactor = loadFactor;

        // Allocate the arrays.
        states = new byte[initialCapacity];
        keys = new int[initialCapacity];
        @SuppressWarnings("unchecked")
        V[] temp = (V[]) new Object[initialCapacity];
        values = temp;

        // Initialize the maximum size value.
        maxSize = calcMaxSize(initialCapacity);

        // Initialize the available element count
        available = initialCapacity - size;
    }

    @Override
    public V get(int key) {
        int index = indexOf(key);
        return index < 0 ? null : values[index];
    }

    @Override
    public V put(int key, V value) {
        int hash = hash(key);
        int capacity = capacity();
        int index = hash % capacity;
        int increment = 1 + (hash % (capacity - 2));
        final int startIndex = index;
        int firstRemovedIndex = -1;
        do {
            switch (states[index]) {
                case AVAILABLE:
                    // We only stop probing at a AVAILABLE node, since the value may still exist
                    // beyond
                    // a REMOVED node.
                    if (firstRemovedIndex != -1) {
                        // We encountered a REMOVED node prior. Store the entry there so that
                        // retrieval
                        // will be faster.
                        insertAt(firstRemovedIndex, key, value);
                        return null;
                    }

                    // No REMOVED node, just store the entry here.
                    insertAt(index, key, value);
                    return null;
                case OCCUPIED:
                    if (keys[index] == key) {
                        V previousValue = values[index];
                        insertAt(index, key, value);
                        return previousValue;
                    }
                    break;
                case REMOVED:
                    // Check for first removed index.
                    if (firstRemovedIndex == -1) {
                        firstRemovedIndex = index;
                    }
                    break;
                default:
                    throw new AssertionError("Invalid state: " + states[index]);
            }

            // REMOVED or OCCUPIED but wrong key, keep probing ...
            index += increment;
            if (index >= capacity) {
                // Handle wrap-around by decrement rather than mod.
                index -= capacity;
            }
        } while (index != startIndex);

        if (firstRemovedIndex == -1) {
            // Should never happen.
            throw new AssertionError("Unable to insert");
        }

        // Never found a AVAILABLE slot, just use the first REMOVED.
        insertAt(firstRemovedIndex, key, value);
        return null;
    }

    @Override
    public void putAll(IntObjectMap<V> sourceMap) {
        if (sourceMap instanceof IntObjectHashMap) {
            // Optimization - iterate through the arrays.
            IntObjectHashMap<V> source = (IntObjectHashMap<V>) sourceMap;
            int i = -1;
            while ((i = source.nextEntryIndex(i + 1)) >= 0) {
                put(source.keys[i], source.values[i]);
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
        if (index < 0) {
            return null;
        }

        V prev = values[index];
        removeAt(index);
        return prev;
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
        Arrays.fill(states, AVAILABLE);
        Arrays.fill(values, null);
        size = 0;
        available = capacity();
    }

    @Override
    public boolean containsKey(int key) {
        return indexOf(key) >= 0;
    }

    @Override
    public boolean containsValue(V value) {
        int i = -1;
        while ((i = nextEntryIndex(i + 1)) >= 0) {
            V next = values[i];
            if (value == next || (value != null && value.equals(next))) {
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
        copyEntries(keys, outKeys);
        return outKeys;
    }

    @Override
    public V[] values(Class<V> clazz) {
        @SuppressWarnings("unchecked")
        V[] outValues = (V[]) Array.newInstance(clazz, size());
        copyEntries(values, outValues);
        return outValues;
    }

    /**
     * Copies the occupied entries from the source to the target array.
     */
    private void copyEntries(Object sourceArray, Object targetArray) {
        int sourceIx = -1;
        int targetIx = 0;
        while ((sourceIx = nextEntryIndex(sourceIx + 1)) >= 0) {
            Object obj = Array.get(sourceArray, sourceIx);
            Array.set(targetArray, targetIx++, obj);
        }
    }

    /**
     * Locates the index for the given key. This method probes using double hashing.
     *
     * @param key the key for an entry in the map.
     * @return the index where the key was found, or {@code -1} if no entry is found for that key.
     */
    private int indexOf(int key) {
        int hash = hash(key);
        int capacity = capacity();
        int increment = 1 + (hash % (capacity - 2));
        int index = hash % capacity;
        int startIndex = index;
        do {
            switch(states[index]) {
                case AVAILABLE:
                    // It's available, so no chance that this value exists anywhere in the map.
                    return -1;
                case OCCUPIED:
                    if (key == keys[index]) {
                        // Found it!
                        return index;
                    }
                    break;
                default:
                    break;
            }

            // REMOVED or OCCUPIED but wrong key, keep probing ...
            index += increment;
            if (index >= capacity) {
                // Handle wrap-around by decrement rather than mod.
                index -= capacity;
            }
        } while (index != startIndex);

        // Got back to the beginning. Not found.
        return -1;
    }

    /**
     * Determines the current capacity (i.e. size of the arrays).
     */
    private int capacity() {
        return keys.length;
    }

    /**
     * Creates a hash value for the given key.
     */
    private int hash(int key) {
        // Just make sure the integer is positive.
        return key & Integer.MAX_VALUE;
    }

    /**
     * Performs an insert of the key/value at the given index position. If necessary, performs a
     * rehash of the map.
     *
     * @param index the index at which to insert the key/value
     * @param key the entry key
     * @param value the entry value
     */
    private void insertAt(int index, int key, V value) {
        byte state = states[index];
        if (state != OCCUPIED) {
            // Added a new mapping, increment the size.
            size++;

            if (state == AVAILABLE) {
                // Consumed a OCCUPIED slot, decrement the number of available slots.
                available--;
            }
        }

        keys[index] = key;
        values[index] = value;
        states[index] = OCCUPIED;

        if (size > maxSize) {
            // Need to grow the arrays.
            // TODO: consider using the next prime greater than capacity * 2.
            rehash(capacity() * 2);
        } else if (available == 0) {
            // Open addressing requires that we have at least 1 slot available. Need to refresh
            // the arrays to clear any removed elements.
            rehash(capacity());
        }
    }

    /**
     * Marks the entry at the given index position as {@link REMOVED} and sets the value to
     * {@code null}.
     * <p>
     * TODO: consider performing re-compaction.
     *
     * @param index the index position of the element to remove.
     */
    private void removeAt(int index) {
        if (states[index] == OCCUPIED) {
            size--;
        }
        states[index] = REMOVED;
        values[index] = null;
    }

    /**
     * Calculates the maximum size allowed before rehashing.
     */
    private int calcMaxSize(int capacity) {
        // Clip the upper bound so that there will always be at least one
        // available slot.
        int upperBound = capacity - 1;
        return Math.min(upperBound, (int) (capacity * loadFactor));
    }

    /**
     * Rehashes the map for the given capacity.
     *
     * @param newCapacity the new capacity for the map.
     */
    private void rehash(int newCapacity) {
        int oldCapacity = capacity();
        int[] oldKeys = keys;
        V[] oldVals = values;
        byte[] oldStates = states;

        // New states array is automatically initialized to AVAILABLE (i.e. 0 == AVAILABLE).
        states = new byte[newCapacity];
        keys = new int[newCapacity];
        @SuppressWarnings("unchecked")
        V[] temp = (V[]) new Object[newCapacity];
        values = temp;

        size = 0;
        available = newCapacity;
        maxSize = calcMaxSize(newCapacity);

        // Insert the new states.
        for (int i = 0; i < oldCapacity; ++i) {
            if (oldStates[i] == OCCUPIED) {
                put(oldKeys[i], oldVals[i]);
            }
        }
    }

    /**
     * Returns the next index of the next entry in the map.
     *
     * @param index the index at which to begin the search.
     * @return the index of the next entry, or {@code -1} if not found.
     */
    private int nextEntryIndex(int index) {
        int capacity = capacity();
        for (; index < capacity; ++index) {
            if (states[index] == OCCUPIED) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Iterator for traversing the entries in this map.
     */
    private final class IteratorImpl implements Iterator<Entry<V>> {
        int prevIndex;
        int nextIndex;

        IteratorImpl() {
            prevIndex = -1;
            nextIndex = nextEntryIndex(0);
        }

        @Override
        public boolean hasNext() {
            return nextIndex >= 0;
        }

        @Override
        public Entry<V> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            prevIndex = nextIndex;
            nextIndex = nextEntryIndex(nextIndex + 1);
            return new EntryImpl(prevIndex);
        }

        @Override
        public void remove() {
            if (prevIndex < 0) {
                throw new IllegalStateException("Next must be called before removing.");
            }
            removeAt(prevIndex);
            prevIndex = -1;
        }
    }

    /**
     * {@link Entry} implementation that just references the key/value at the given index position.
     */
    private final class EntryImpl implements Entry<V> {
        final int index;

        EntryImpl(int index) {
            this.index = index;
        }

        @Override
        public int key() {
            return keys[index];
        }

        @Override
        public V value() {
            return values[index];
        }

        @Override
        public void setValue(V value) {
            values[index] = value;
        }
    }
}
