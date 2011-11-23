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

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return delegate.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V get(Object key) {
        return unfold(delegate.get(key));
    }

    @Override
    public V put(K key, V value) {
        return unfold(delegate.put(key, fold(value)));
    }

    @Override
    public V remove(Object key) {
        return unfold(delegate.remove(key));
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Entry<? extends K, ? extends V> entry : m.entrySet()) {
            delegate.put(entry.getKey(), fold(entry.getValue()));
        }
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public Set<K> keySet() {
        return delegate.keySet();
    }

    @Override
    public Collection<V> values() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        throw new UnsupportedOperationException();
    }
}
