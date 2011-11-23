package org.jboss.netty.handler.codec.serialization;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Map;

public class WeakReferenceMap<K, V> extends ReferenceMap<K, V> {

    public WeakReferenceMap(Map<K, Reference<V>> delegate) {
        super(delegate);
    }

    @Override
    Reference<V> fold(V value) {
        return new WeakReference<V>(value);
    }

}
