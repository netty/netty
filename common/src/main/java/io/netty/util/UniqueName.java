/*
 * Copyright 2012 The Netty Project
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

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * @deprecated Known to have problems with class loaders.
 *
 * Defines a name that must be unique in the map that is provided during construction.
 */
@Deprecated
public class UniqueName implements Comparable<UniqueName> {

    private static final AtomicInteger nextId = new AtomicInteger();

    private final int id;
    private final String name;

    /**
     * Constructs a new {@link UniqueName}
     *
     * @param map the map of names to compare with
     * @param name the name of this {@link UniqueName}
     * @param args the arguments to process
     */
    public UniqueName(ConcurrentMap<String, Boolean> map, String name, Object... args) {
        checkNotNull(map, "map");

        if (args != null && args.length > 0) {
            validateArgs(args);
        }

        if (map.putIfAbsent(name, Boolean.TRUE) != null) {
            throw new IllegalArgumentException(String.format("'%s' is already in use", name));
        }
        this.name = checkNotNull(name, "name");
        id = nextId.incrementAndGet();
    }

    protected UniqueName(String name) {
        this.name = checkNotNull(name, "name");
        id = nextId.incrementAndGet();
    }

    /**
     * Validates the given arguments.  This method does not do anything on its own, but must be
     * overridden by its subclasses.
     *
     * @param args arguments to validate
     */
    @SuppressWarnings("unused")
    protected void validateArgs(Object... args) {
        // Subclasses will override.
    }

    /**
     * Returns this {@link UniqueName}'s name
     *
     * @return the name
     */
    public final String name() {
        return name;
    }

    /**
     * Returns this {@link UniqueName}'s ID
     *
     * @return the id
     */
    public final int id() {
        return id;
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
    public int compareTo(UniqueName other) {
        if (this == other) {
            return 0;
        }

        int returnCode = name.compareTo(other.name);
        if (returnCode != 0) {
            return returnCode;
        }

        return ((Integer) id).compareTo(other.id);
    }

    @Override
    public String toString() {
        return name();
    }
}
