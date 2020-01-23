/*
 * Copyright 2020 The Netty Project
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LRU like lock-free cache implementation.
 *
 * It rotates 3 maps: primary, secondary, and empty.
 */
final class SimpleSizeLimitedMap<K, V> {
    private final AtomicInteger counter;
    private final int mask;
    private final int shift;
    private final ConcurrentMap<K, V>[] ring;

    /**
     * Use power of two as cache capacity. The argument is just a hint.
     *
     * @param capacity approximate cache capacity.
     */
    @SuppressWarnings("unchecked")
    SimpleSizeLimitedMap(final int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be a positive integer: " + capacity);
        }
        final int power = power(capacity);
        this.mask = power - 1;
        this.counter = new AtomicInteger(mask);
        this.shift = Integer.numberOfTrailingZeros(power);
        final int slightlyBigger = power + 3;
        final int mapCapacity = slightlyBigger < 0 ? Integer.MAX_VALUE : slightlyBigger;
        final ConcurrentMap<K, V> map = new ConcurrentHashMap<K, V>(mapCapacity);
        this.ring = new ConcurrentMap[] {
                map,
                new ConcurrentHashMap<K, V>(mapCapacity),
                new ConcurrentHashMap<K, V>(mapCapacity),
                new ConcurrentHashMap<K, V>(mapCapacity),
                map
        };
    }

    /**
     * Get the value associated with {@code key} from this cache.
     * @param key the key to lookup.
     * @return the value associated with {@code key} from this cache.
     */
    public V get(final K key) {
        final int index = indexFor(counter.get());
        final Map<K, V> primary = ring[index];
        final V v1 = primary.get(key);
        if (v1 != null) {
            return v1;
        }
        final V v2 = ring[index + 1].get(key);
        if (v2 != null) {
            if (primary.put(key, v2) == null) {
                maintainSize();
            }
        }
        return v2;
    }

    /**
     * Create a new association between {@code key} and {@code value} in this cache.
     * @param key the key.
     * @param value the value.
     */
    public void put(final K key, final V value) {
        if (ring[indexFor(counter.get())].put(key, value) == null) {
            maintainSize();
        }
    }

    /**
     * Create a new association between {@code key} and {@code value} in this cache.
     * @param key the key.
     * @param value the value.
     * @return value If the specified key is not already associated with a value
     * (or is mapped to {@code null}) associates it with the given value and returns
     * {@code null}, else returns the current value.
     */
    public V putIfAbsent(final K key, final V value) {
        final V current = ring[indexFor(counter.get())].putIfAbsent(key, value);
        if (current == null) {
            maintainSize();
        }
        return current;
    }

    /**
     * Clear the contents of this cache.
     */
    public void clear() {
        final Map<K, V>[] maps = ring;
        maps[0].clear();
        maps[1].clear();
        maps[2].clear();
        maps[3].clear();
        counter.set(mask);
    }

    private static int power(final int capacity) {
        final int power = Integer.highestOneBit(capacity);
        return power == capacity ? power : power << 1;
    }

    private void maintainSize() {
        final int n = counter.getAndDecrement();
        if ((n & mask) == 0) {
            // Detected map switch since the counter was 0 before the decrement.
            // indexFor(n) is the current secondary map so (+ 1) is the new empty one.
            ring[indexFor(n) + 1].clear();
        }
    }

    private int indexFor(final int n) {
        // Use two bits to point to the current primary map (00, 01, 10, 11).
        // It is always safe to refer to ring[indexFor(n) + 1] since the map array size is 5 while 0 <= indexFor(n) < 4
        return (n >>> shift) & 3;
    }

    /**
     * @param key key
     */
    public void remove(final K key) {
        final Map<K, V>[] maps = ring;
        maps[0].remove(key);
        maps[1].remove(key);
        maps[2].remove(key);
        maps[3].remove(key);
    }
}
