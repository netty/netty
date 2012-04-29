package io.netty.util;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultAttributeMap implements AttributeMap {

    // Initialize lazily to reduce memory consumption.
    private Map<AttributeKey<?>, Attribute<?>> map;

    @Override
    public synchronized <T> Attribute<T> attr(AttributeKey<T> key) {
        Map<AttributeKey<?>, Attribute<?>> map = this.map;
        if (map == null) {
            // Not using ConcurrentHashMap due to high memory consumption.
            map = this.map = new IdentityHashMap<AttributeKey<?>, Attribute<?>>(2);
        }

        Attribute<T> attr = (Attribute<T>) map.get(key);
        if (attr == null) {
            attr = new DefaultAttribute<T>();
            map.put(key, attr);
        }
        return attr;
    }

    private class DefaultAttribute<T> extends AtomicReference<T> implements Attribute<T> {

        private static final long serialVersionUID = -2661411462200283011L;

        @Override
        public T setIfAbsent(T value) {
            if (compareAndSet(null, value)) {
                return null;
            } else {
                return get();
            }
        }

        @Override
        public void remove() {
            set(null);
        }
    }
}
