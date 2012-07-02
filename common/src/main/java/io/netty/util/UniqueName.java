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

/**
 * Defines a name that must be unique in the map that is provided during construction.
 */
public class UniqueName implements Comparable<UniqueName> {

    /**
     * The {@link AtomicInteger} that indicates the next ID
     */
    private static final AtomicInteger nextId = new AtomicInteger();

    /**
     * This {@link UniqueName}'s ID
     */
    private final int id;

    /**
     * This {@link UniqueName}'s name
     */
    private final String name;

    /**
     * Constructs a new {@link UniqueName}
     *
     * @param map the map of names to compare with
     * @param name the name of this {@link UniqueName}
     * @param args the arguments to process
     */
    public UniqueName(ConcurrentMap<String, Boolean> map, String name, Object... args) {
        if (map == null) {
            throw new NullPointerException("Supplied map cannot be null");
        }
        if (name == null) {
            throw new NullPointerException("Supplied name cannot be null");
        }
        if (args != null && args.length > 0) {
            validateArgs(args);
        }

        if (map.putIfAbsent(name, Boolean.TRUE) != null) {
            throw new IllegalArgumentException(String.format("'%s' is already in use", name));
        }

        id = nextId.incrementAndGet();
        this.name = name;
    }

    /**
     * Validates the given arguments.
     * This does not do anything on its own, but must be overridden by subclasses
     *
     * @param arguments the arguments to validate
     */
    protected void validateArgs(Object... arguments) {
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
     * Returns this {@link UniqueName}'s hash code
     *
     * @return the hash code
     */
    @Override
    public final int hashCode() {
        return super.hashCode();
    }

    /**
     * Checks to see if this {@link UniqueName} is equal to another object
     *
     * @param o the other object to compare with
     * @return true if equal, otherwise false
     */
    @Override
    public final boolean equals(Object o) {
        return super.equals(o);
    }

    /**
     * Compares this {@link UniqueName} with another
     *
     * @param other the other {@link UniqueName} to compare with
     * @return -1 if this {@link UniqueName} is less than, 0 if equal to, or 1
     *         if it is greater than the other {@link UniqueName}
     */
    @Override
    public int compareTo(UniqueName other) {
        if (this == other) {
            return 0;
        }

        int returnCode = name.compareTo(other.name);
        if (returnCode != 0) {
            return returnCode;
        }

        if (id < other.id) {
            return -1;
        } else if (id > other.id) {
            return 1;
        } else {
            return 0;
        }
    }

    /**
     * Gets a representation of this class as a {@link String}.
     * In this case, it is simply the name.
     *
     * @return the name
     */
    @Override
    public String toString() {
        return name();
    }
}
