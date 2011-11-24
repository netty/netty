/*
 * Copyright 2011 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.serialization;

import java.lang.ref.Reference;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

abstract class ReferenceMap<K, V> implements Map<K, V> {
    
    private final Map<K, Reference<V>> delegate;

    protected ReferenceMap(Map<K, Reference<V>> delegate) {
        this.delegate = delegate;
    }

    abstract Reference<V> fold(V value);

    private V unfold(Reference<V> ref) {
        if (ref == null) {
            return null;
        }

        return ref.get();
    }

    /*
     * (non-Javadoc)
     * @see java.util.Map#size()
     */
    public int size() {
        return delegate.size();
    }


    /*
     * (non-Javadoc)
     * @see java.util.Map#isEmpty()
     */
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    /*
     * (non-Javadoc)
     * @see java.util.Map#containsKey(java.lang.Object)
     */
    public boolean containsKey(Object key) {
        return delegate.containsKey(key);
    }

    /*
     * (non-Javadoc)
     * @see java.util.Map#containsValue(java.lang.Object)
     */
    public boolean containsValue(Object value) {
        throw new UnsupportedOperationException();
    }

    /*
     * (non-Javadoc)
     * @see java.util.Map#get(java.lang.Object)
     */
    public V get(Object key) {
        return unfold(delegate.get(key));
    }

    /*
     * (non-Javadoc)
     * @see java.util.Map#put(java.lang.Object, java.lang.Object)
     */
    public V put(K key, V value) {
        return unfold(delegate.put(key, fold(value)));
    }

    /*
     * (non-Javadoc)
     * @see java.util.Map#remove(java.lang.Object)
     */
    public V remove(Object key) {
        return unfold(delegate.remove(key));
    }

    /*
     * (non-Javadoc)
     * @see java.util.Map#putAll(java.util.Map)
     */
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Entry<? extends K, ? extends V> entry : m.entrySet()) {
            delegate.put(entry.getKey(), fold(entry.getValue()));
        }
    }

    /*
     * (non-Javadoc)
     * @see java.util.Map#clear()
     */
    public void clear() {
        delegate.clear();
    }

    /*
     * (non-Javadoc)
     * @see java.util.Map#keySet()
     */
    public Set<K> keySet() {
        return delegate.keySet();
    }

    /*
     * (non-Javadoc)
     * @see java.util.Map#values()
     */
    public Collection<V> values() {
        throw new UnsupportedOperationException();
    }

    /*
     * (non-Javadoc)
     * @see java.util.Map#entrySet()
     */
    public Set<Entry<K, V>> entrySet() {
        throw new UnsupportedOperationException();
    }
}
