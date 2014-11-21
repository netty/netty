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

import java.util.Collection;

/**
 * Interface for a primitive map that uses {@code int}s as keys.
 *
 * @param <V> the value type stored in the map.
 */
public interface IntObjectMap<V> {

    /**
     * An Entry in the map.
     *
     * @param <V> the value type stored in the map.
     */
    interface Entry<V> {
        /**
         * Gets the key for this entry.
         */
        int key();

        /**
         * Gets the value for this entry.
         */
        V value();

        /**
         * Sets the value for this entry.
         */
        void setValue(V value);
    }

    /**
     * Gets the value in the map with the specified key.
     *
     * @param key the key whose associated value is to be returned.
     * @return the value or {@code null} if the key was not found in the map.
     */
    V get(int key);

    /**
     * Puts the given entry into the map.
     *
     * @param key the key of the entry.
     * @param value the value of the entry.
     * @return the previous value for this key or {@code null} if there was no previous mapping.
     */
    V put(int key, V value);

    /**
     * Puts all of the entries from the given map into this map.
     */
    void putAll(IntObjectMap<V> sourceMap);

    /**
     * Removes the entry with the specified key.
     *
     * @param key the key for the entry to be removed from this map.
     * @return the previous value for the key, or {@code null} if there was no mapping.
     */
    V remove(int key);

    /**
     * Returns the number of entries contained in this map.
     */
    int size();

    /**
     * Indicates whether or not this map is empty (i.e {@link #size()} == {@code 0]).

     */
    boolean isEmpty();

    /**
     * Clears all entries from this map.
     */
    void clear();

    /**
     * Indicates whether or not this map contains a value for the specified key.
     */
    boolean containsKey(int key);

    /**
     * Indicates whether or not the map contains the specified value.
     */
    boolean containsValue(V value);

    /**
     * Gets an iterable collection of the entries contained in this map.
     */
    Iterable<Entry<V>> entries();

    /**
     * Gets the keys contained in this map.
     */
    int[] keys();

    /**
     * Gets the values contained in this map.
     */
    V[] values(Class<V> clazz);

    /**
     * Gets the values contatins in this map as a {@link Collection}.
     */
    Collection<V> values();
}
