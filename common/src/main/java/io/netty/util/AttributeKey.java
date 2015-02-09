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

import io.netty.util.internal.PlatformDependent;

import java.util.concurrent.ConcurrentMap;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Key which can be used to access {@link Attribute} out of the {@link AttributeMap}. Be aware that it is not be
 * possible to have multiple keys with the same name.
 *
 *
 * @param <T>   the type of the {@link Attribute} which can be accessed via this {@link AttributeKey}.
 */
@SuppressWarnings({ "UnusedDeclaration", "deprecation" }) // 'T' is used only at compile time
public final class AttributeKey<T> extends UniqueName {

    @SuppressWarnings("rawtypes")
    private static final ConcurrentMap<String, AttributeKey> names = PlatformDependent.newConcurrentHashMap();

    /**
     * Creates a new {@link AttributeKey} with the specified {@param name} or return the already existing
     * {@link AttributeKey} for the given name.
     */
    @SuppressWarnings("unchecked")
    public static <T> AttributeKey<T> valueOf(String name) {
        checkNotNull(name, "name");
        AttributeKey<T> option = names.get(name);
        if (option == null) {
            option = new AttributeKey<T>(name);
            AttributeKey<T> old = names.putIfAbsent(name, option);
            if (old != null) {
                option = old;
            }
        }
        return option;
    }

    /**
     * Returns {@code true} if a {@link AttributeKey} exists for the given {@code name}.
     */
    public static boolean exists(String name) {
        checkNotNull(name, "name");
        return names.containsKey(name);
    }

    /**
     * Creates a new {@link AttributeKey} for the given {@param name} or fail with an
     * {@link IllegalArgumentException} if a {@link AttributeKey} for the given {@param name} exists.
     */
    @SuppressWarnings("unchecked")
    public static <T> AttributeKey<T> newInstance(String name) {
        checkNotNull(name, "name");
        AttributeKey<T> option = new AttributeKey<T>(name);
        AttributeKey<T> old = names.putIfAbsent(name, option);
        if (old != null) {
            throw new IllegalArgumentException(String.format("'%s' is already in use", name));
        }
        return option;
    }

    /**
     * @deprecated Use {@link #valueOf(String)} instead.
     */
    @Deprecated
    public AttributeKey(String name) {
        super(name);
    }
}
