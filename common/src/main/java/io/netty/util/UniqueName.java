package io.netty.util;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class UniqueName implements Comparable<UniqueName> {

    private static final AtomicInteger nextId = new AtomicInteger();

    private final int id;
    private final String name;

    protected UniqueName(ConcurrentMap<String, Boolean> map, String name, Object... args) {
        if (map == null) {
            throw new NullPointerException("map");
        }
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (args != null && args.length > 0) {
            validateArgs(args);
        }

        if (map.putIfAbsent(name, Boolean.TRUE) != null) {
            throw new IllegalArgumentException(String.format("'%s' already in use", name));
        }

        id = nextId.incrementAndGet();
        this.name = name;
    }

    protected void validateArgs(Object... args) {
        // Subclasses will override.
    }

    public final String name() {
        return name;
    }

    @Override
    public final int hashCode() {
        return super.hashCode();
    }

    @Override
    public final boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int compareTo(UniqueName o) {
        if (this == o) {
            return 0;
        }

        int ret = name.compareTo(o.name);
        if (ret != 0) {
            return ret;
        }

        if (id < o.id) {
            return -1;
        } else if (id > o.id) {
            return 1;
        } else {
            return 0;
        }
    }

    @Override
    public String toString() {
        return name();
    }
}
