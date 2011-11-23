package org.jboss.netty.handler.codec.serialization;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.Map;

public class SoftReferenceMap<K, V> extends ReferenceMap<K, V> {

    public SoftReferenceMap(Map<K, Reference<V>> delegate) {
        super(delegate);
    }

    @Override
    Reference<V> fold(V value) {
        return new SoftReference<V>(value);
    }

}
