package io.netty.util;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class AttributeKey<T> implements Serializable, Comparable<AttributeKey<T>> {

    private static final long serialVersionUID = 2783354860083517323L;

    private static final ConcurrentMap<String, Boolean> names = new ConcurrentHashMap<String, Boolean>();

    private final String name;
    private final Class<T> valueType;

    public AttributeKey(String name, Class<T> valueType) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (valueType == null) {
            throw new NullPointerException("valueType");
        }

        if (names.putIfAbsent(name, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("key name already in use: " + name);
        }

        this.name = name;
        this.valueType = valueType;
    }

    public String name() {
        return name;
    }

    public Class<T> valueType() {
        return valueType;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int compareTo(AttributeKey<T> o) {
        return name().compareTo(o.name());
    }

    @Override
    public String toString() {
        return name();
    }
}
