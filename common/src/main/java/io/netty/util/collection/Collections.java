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

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Utility methods for collections.
 */
public final class Collections {
    private static final IntObjectMap<Object> EMPTY_INT_OBJECT_MAP = new EmptyIntObjectMap();

    private Collections() {
    }

    /**
     * Returns an unmodifiable empty {@link IntObjectMap}.
     */
    @SuppressWarnings("unchecked")
    public static <V> IntObjectMap<V> emptyIntObjectMap() {
        return (IntObjectMap<V>) EMPTY_INT_OBJECT_MAP;
    }

    /**
     * Creates an unmodifiable wrapper around the given map.
     */
    public static <V> IntObjectMap<V> unmodifiable(final IntObjectMap<V> map) {
        return new UnmodifiableIntObjectMap<V>(map);
    }

    /**
     * An empty map. All operations that attempt to modify the map are unsupported.
     *
     * @param <V> the value type for the map.
     */
    private static final class EmptyIntObjectMap implements IntObjectMap<Object> {

        @Override
        public Object get(int key) {
            return null;
        }

        @Override
        public Object put(int key, Object value) {
            throw new UnsupportedOperationException("put");
        }

        @Override
        public void putAll(IntObjectMap<Object> sourceMap) {
            throw new UnsupportedOperationException("putAll");
        }

        @Override
        public Object remove(int key) {
            throw new UnsupportedOperationException("remove");
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public void clear() {
            // Do nothing.
        }

        @Override
        public boolean containsKey(int key) {
            return false;
        }

        @Override
        public boolean containsValue(Object value) {
            return false;
        }

        @Override
        public Iterable<Entry<Object>> entries() {
            return java.util.Collections.emptySet();
        }

        @Override
        public int[] keys() {
            return new int[0];
        }

        @Override
        public Object[] values(Class<Object> clazz) {
            return new Object[0];
        }
    }

    /**
     * An unmodifiable wrapper around a {@link IntObjectMap}.
     *
     * @param <V> the value type stored in the map.
     */
    private static final class UnmodifiableIntObjectMap<V> implements IntObjectMap<V>,
            Iterable<IntObjectMap.Entry<V>> {
        final IntObjectMap<V> map;

        UnmodifiableIntObjectMap(IntObjectMap<V> map) {
            this.map = map;
        }

        @Override
        public V get(int key) {
            return map.get(key);
        }

        @Override
        public V put(int key, V value) {
            throw new UnsupportedOperationException("put");
        }

        @Override
        public void putAll(IntObjectMap<V> sourceMap) {
            throw new UnsupportedOperationException("putAll");
        }

        @Override
        public V remove(int key) {
            throw new UnsupportedOperationException("remove");
        }

        @Override
        public int size() {
            return map.size();
        }

        @Override
        public boolean isEmpty() {
            return map.isEmpty();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("clear");
        }

        @Override
        public boolean containsKey(int key) {
            return map.containsKey(key);
        }

        @Override
        public boolean containsValue(V value) {
            return map.containsValue(value);
        }

        @Override
        public Iterable<Entry<V>> entries() {
            return this;
        }

        @Override
        public Iterator<Entry<V>> iterator() {
            return new IteratorImpl(map.entries().iterator());
        }

        @Override
        public int[] keys() {
            return map.keys();
        }

        @Override
        public V[] values(Class<V> clazz) {
            return map.values(clazz);
        }

        /**
         * Unmodifiable wrapper for an iterator.
         */
        private class IteratorImpl implements Iterator<Entry<V>> {
            final Iterator<Entry<V>> iter;

            IteratorImpl(Iterator<Entry<V>> iter) {
                this.iter = iter;
            }

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public Entry<V> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return new EntryImpl(iter.next());
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("remove");
            }
        }

        /**
         * Unmodifiable wrapper for an entry.
         */
        private class EntryImpl implements Entry<V> {
            final Entry<V> entry;

            EntryImpl(Entry<V> entry) {
                this.entry = entry;
            }

            @Override
            public int key() {
                return entry.key();
            }

            @Override
            public V value() {
                return entry.value();
            }

            @Override
            public void setValue(V value) {
                throw new UnsupportedOperationException("setValue");
            }
        }
    };
}
