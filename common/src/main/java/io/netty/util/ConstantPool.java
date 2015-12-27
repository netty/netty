/*
 * Copyright 2013 The Netty Project
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

import io.netty.util.internal.ObjectUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * A pool of {@link Constant}s.
 *
 * @param <T> the type of the constant
 */
public abstract class ConstantPool<T extends Constant<T>> {

    private final Map<String, T> constants = new HashMap<String, T>();

    private int nextId = 1;

    /**
     * Shortcut of {@link #valueOf(String) valueOf(firstNameComponent.getName() + "#" + secondNameComponent)}.
     */
    public T valueOf(Class<?> firstNameComponent, String secondNameComponent) {
        if (firstNameComponent == null) {
            throw new NullPointerException("firstNameComponent");
        }
        if (secondNameComponent == null) {
            throw new NullPointerException("secondNameComponent");
        }

        return valueOf(firstNameComponent.getName() + '#' + secondNameComponent);
    }

    /**
     * Returns the {@link Constant} which is assigned to the specified {@code name}.
     * If there's no such {@link Constant}, a new one will be created and returned.
     * Once created, the subsequent calls with the same {@code name} will always return the previously created one
     * (i.e. singleton.)
     *
     * @param name the name of the {@link Constant}
     */
    public T valueOf(String name) {
        T c;

        synchronized (constants) {
            if (exists(name)) {
                c = constants.get(name);
            } else {
                c = newInstance0(name);
            }
        }

        return c;
    }

    /**
     * Returns {@code true} if a {@link AttributeKey} exists for the given {@code name}.
     */
    public boolean exists(String name) {
        checkNotNullAndNotEmpty(name);
        synchronized (constants) {
            return constants.containsKey(name);
        }
    }

    /**
     * Creates a new {@link Constant} for the given {@code name} or fail with an
     * {@link IllegalArgumentException} if a {@link Constant} for the given {@code name} exists.
     */
    @SuppressWarnings("unchecked")
    public T newInstance(String name) {
        if (exists(name)) {
            throw new IllegalArgumentException(String.format("'%s' is already in use", name));
        }

        T c = newInstance0(name);

        return c;
    }

    // Be careful that this dose not check whether the argument is null or empty.
    private T newInstance0(String name) {
        synchronized (constants) {
            T c = newConstant(nextId, name);
            constants.put(name, c);
            nextId++;
            return c;
        }
    }

    private String checkNotNullAndNotEmpty(String name) {
        ObjectUtil.checkNotNull(name, "name");

        if (name.isEmpty()) {
            throw new IllegalArgumentException("empty name");
        }

        return name;
    }

    protected abstract T newConstant(int id, String name);

    @Deprecated
    public final int nextId() {
        synchronized (constants) {
            int id = nextId;
            nextId++;
            return id;
        }
    }
}
