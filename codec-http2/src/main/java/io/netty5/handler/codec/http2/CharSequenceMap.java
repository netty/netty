/*
 * Copyright 2015 The Netty Project
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
package io.netty5.handler.codec.http2;

import io.netty5.handler.codec.http.headers.MultiMap;
import io.netty5.util.HashingStrategy;
import io.netty5.util.internal.UnstableApi;

import static io.netty5.util.AsciiString.CASE_INSENSITIVE_HASHER;
import static io.netty5.util.AsciiString.CASE_SENSITIVE_HASHER;
import static java.util.Objects.requireNonNull;

/**
 * Internal use only!
 */
@UnstableApi
public final class CharSequenceMap<V> extends MultiMap<CharSequence, V> {
    private final HashingStrategy<CharSequence> hashingStrategy;

    public CharSequenceMap() {
        this(true);
    }

    public CharSequenceMap(boolean caseSensitive) {
        this(caseSensitive, 32);
    }

    public CharSequenceMap(boolean caseSensitive, int arraySizeHint) {
        super(arraySizeHint);
        hashingStrategy = caseSensitive ? CASE_SENSITIVE_HASHER : CASE_INSENSITIVE_HASHER;
    }

    @Override
    protected int hashCode(CharSequence key) {
        return hashingStrategy.hashCode(key);
    }

    @Override
    protected boolean equals(CharSequence key1, CharSequence key2) {
        return hashingStrategy.equals(key1, key2);
    }

    @Override
    protected boolean isKeyEqualityCompatible(MultiMap<? extends CharSequence, ? extends V> multiMap) {
        return multiMap instanceof CharSequenceMap &&
                ((CharSequenceMap<?>) multiMap).hashingStrategy == hashingStrategy;
    }

    @Override
    protected CharSequence validateKey(CharSequence key, boolean forAdd) {
        return requireNonNull(key, "key");
    }

    @Override
    protected V validateValue(CharSequence key, V value) {
        return requireNonNull(value, "value");
    }

    @Override
    protected int hashCodeForValue(V value) {
        return value.hashCode();
    }

    @Override
    protected boolean equalsForValue(V value1, V value2) {
        return value1.equals(value2);
    }

    /**
     * Get the value mapped to the given key name, or {@code null} if there is no such mapping.
     *
     * @param name The key name to look up.
     * @return The value mapped to the given key, or {@code null} if there is no such mapping.
     */
    public V get(CharSequence name) {
        return getValue(name);
    }

    /**
     * Set the given mapping of the given key name to the given value, overwriting any existing mappings.
     *
     * @param name The key name of the mapping to assign.
     * @param value The value to be assigned to the mapping given by the key name.
     */
    public void set(CharSequence name, V value) {
        putExclusive(name, value);
    }

    /**
     * Add a value to the mapping by the given key name.
     * Any existing values will be left intact.
     * The new value will be added to the end of the sequence of any existing values.
     * If there is no existing mapping for the given key name, then one will be created.
     *
     * @param name The key name of the mapping to add to.
     * @param value The value to add.
     */
    public void add(CharSequence name, V value) {
        put(name, value);
    }

    /**
     * Query if the map contain a mapping by the given key name.
     *
     * @param name The key name to look up.
     * @return {@code true} if a mapping exists for the given key name, with at least one value,
     * otherwise {@code false}.
     */
    public boolean contains(CharSequence name) {
        return getValue(name) != null;
    }

    /**
     * Add all the mappings from the given {@link CharSequenceMap}, to this map.
     * This is effectively the same as iterating the given map, and calling {@link #add(CharSequence, Object)} on this
     * map, for each key/value pair in the given map.
     * However, this method will likely be more efficient than that.
     *
     * @param charSequenceMap The mappings to add to this map.
     */
    public void add(CharSequenceMap<V> charSequenceMap) {
        putAll(charSequenceMap);
    }
}
