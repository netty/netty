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

import io.netty.util.internal.MathUtil;
import io.netty.util.internal.StringUtil;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * {@link io.netty.util.DomainNameMapping} implementation which will always match exact matches first
 * and may cache wildcard mappings depending on if caching is enabled or not.
 *
 * @param <V> concrete type of value objects
 */
public final class CachingDomainNameMapping<V> extends DomainNameMapping<V> {
    private final Map<String, V> exactMatchMap;
    private final Map.Entry<String, V>[] wildcardArray;
    private final RelaxedCache<V> wildcardCache;
    private volatile Map<String, V> combinedMap;

    @SuppressWarnings("deprecation")
    private CachingDomainNameMapping(final V defaultValue, final Map<String, V> exactMatchMap,
                                     final Map.Entry<String, V>[] wildcardArray, final int cacheCapacity) {
        super(defaultValue);
        this.exactMatchMap = exactMatchMap;
        this.wildcardArray = wildcardArray;
        this.wildcardCache = cacheCapacity > 0 ? new RelaxedCache<V>(cacheCapacity) : null;
    }

    @Override
    public V map(final String hostname) {
        if (hostname != null) {
            final String normalized = normalizeHostname(hostname);

            // look up in non-wildcard map
            V value = exactMatchMap.get(normalized);
            if (value != null) {
                return value;
            }
            // look up in cache
            if (wildcardCache != null) {
                value = wildcardCache.get(normalized);
                if (value != null) {
                    return value;
                }
            }

            for (int i = 0; i < wildcardArray.length; i++) {
                Map.Entry<String, V> entry = wildcardArray[i];
                if (matches(entry.getKey(), normalized)) {
                    V v = entry.getValue();
                    cacheIfPossible(normalized, v);
                    return v;
                }
            }

            cacheIfPossible(normalized, defaultValue);
        }
        return defaultValue;
    }

    private void cacheIfPossible(String normalized, V value) {
        if (wildcardCache != null) {
            wildcardCache.put(normalized, value);
        }
    }

    @Override
    @Deprecated
    public DomainNameMapping<V> add(String hostname, V output) {
        throw new UnsupportedOperationException(
                "CachingDomainNameMapping does not support modification after initial creation");
    }

    @Override
    public Map<String, V> asMap() {
        Map<String, V> combinedMap = this.combinedMap;
        if (combinedMap == null) {
            final Map<String, V> workingMap = new LinkedHashMap<String, V>(exactMatchMap.size() + wildcardArray.length);
            workingMap.putAll(exactMatchMap);
            for (int i = 0; i < wildcardArray.length; i++) {
                Map.Entry<String, V> entry = wildcardArray[i];
                workingMap.put(entry.getKey(), entry.getValue());
            }
            this.combinedMap = combinedMap = Collections.unmodifiableMap(workingMap);
        }
        return combinedMap;
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "(default: " + defaultValue + ", map: " + asMap() + ')';
    }

    public static final class Builder<V> {
        private final V defaultValue;
        private final Map<String, V> exactMatchMap;
        private final Map<String, V> wildcardMap;
        private int cacheCapacity;

        /**
         * Constructor with default initial capacity of the map holding the mappings
         *
         * @param defaultValue the default value for {@link DomainNameMapping#map(String)} to return
         *                     when nothing matches the input
         */
        public Builder(final V defaultValue) {
            this(4, defaultValue);
        }

        /**
         * Constructor with initial capacity of the map holding the mappings
         *
         * @param initialCapacity initial capacity for the internal map
         * @param defaultValue    the default value for {@link DomainNameMapping#map(String)} to return
         *                        when nothing matches the input
         */
        public Builder(final int initialCapacity, final V defaultValue) {
            this.defaultValue = checkNotNull(defaultValue, "defaultValue");
            this.exactMatchMap = new LinkedHashMap<String, V>(initialCapacity);
            this.wildcardMap = new LinkedHashMap<String, V>(initialCapacity);
        }

        /**
         * Set the initial capacity of internal caching for wildcard hostname match
         * @param cacheCapacity initial capacity for the internal caching
         * @return this builder
         */
        public Builder<V> withCacheCapacity(final int cacheCapacity) {
            this.cacheCapacity = checkPositiveOrZero(cacheCapacity, "cacheCapacity");
            return this;
        }

        /**
         * Adds a mapping that maps the specified host name to the specified output value.
         * Null values are forbidden for both hostnames and values.
         *
         * @param hostname the host name (optionally wildcard)
         * @param output   the output value that will be returned by {@link CachingDomainNameMapping#map(String)}
         *                 when the specified host name matches the specified input host name
         */
        public Builder<V> add(final String hostname, final V output) {
            checkNotNull(hostname, "hostname");
            checkNotNull(output, "output");
            if (!hostname.contains("*")) {
                exactMatchMap.put(hostname, output);
            } else {
                wildcardMap.put(hostname, output);
            }
            return this;
        }

        /**
         * Creates a new instance of immutable {@link CachingDomainNameMapping}
         * Attempts to add new mappings to the result object will cause {@link UnsupportedOperationException}
         * to be thrown
         *
         * @return new {@link DomainNameMapping} instance
         */
        @SuppressWarnings("unchecked")
        public CachingDomainNameMapping<V> build() {
            return new CachingDomainNameMapping<V>(defaultValue, new LinkedHashMap<String, V>(exactMatchMap),
                    wildcardMap.entrySet().toArray(new Map.Entry[0]), cacheCapacity);
        }
    }

    private static final class RelaxedCache<V> {

        private static final class CacheEntry<V> {
            final String key;
            final V value;

            CacheEntry(String key, V value) {
                this.key = key;
                this.value = value;
            }
        }

        private final CacheEntry<V>[] entries;
        private final int shift;

        @SuppressWarnings("unchecked")
        RelaxedCache(final int capacity) {
            entries = (CacheEntry<V>[]) new Object[MathUtil.findNextPositivePowerOfTwo(capacity)];
            //log2 of entries.length
            shift = 31 - Integer.numberOfLeadingZeros(entries.length);
        }

        // fast % operation with power of 2 entries.length
        private int firstIndex(int hashCode) {
            return hashCode & (entries.length - 1);
        }

        private int secondIndex(int hashCode) {
            return (hashCode >> shift) & (entries.length - 1);
        }

        private V value(int idx, String key) {
            final CacheEntry<V> entry = entries[idx];
            if (entry != null && entry.key.equals(key)) {
                return entry.value;
            }
            return null;
        }

        V get(final String key) {
            final int hashCode = key.hashCode();
            final int firstIndex = firstIndex(hashCode);
            final V firstValue = value(firstIndex, key);
            return firstValue != null ? firstValue :
                    value(secondIndex(hashCode), key);
        }

        void put(String key, V value) {
            CacheEntry<V> entry = new CacheEntry<V>(key, value);
            final int hashCode = key.hashCode();
            final int firstIndex = firstIndex(hashCode);
            final V firstValue = value(firstIndex, key);
            if (firstValue == null) {
                entries[firstIndex] = entry;
            } else {
                entries[secondIndex(hashCode)] = entry;
            }
        }
    }
}
