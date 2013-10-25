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

/**
 * Key which can be used to access {@link Attribute} out of the {@link AttributeMap}. Be aware that it is not be
 * possible to have multiple keys with the same name.
 *
 *
 * @param <T>   the type of the {@link Attribute} which can be accessed via this {@link AttributeKey}.
 */
@SuppressWarnings({ "UnusedDeclaration", "deprecation" }) // 'T' is used only at compile time
public final class AttributeKey<T> extends UniqueName {

    private static final ConcurrentMap<String, Boolean> names = PlatformDependent.newConcurrentHashMap();

    /**
     * Creates a new {@link AttributeKey} with the specified {@code name}.
     */
    @SuppressWarnings("deprecation")
    public static <T> AttributeKey<T> valueOf(String name) {
        return new AttributeKey<T>(name);
    }

    /**
     * @deprecated Use {@link #valueOf(String)} instead.
     */
    @Deprecated
    public AttributeKey(String name) {
        super(names, name);
    }
}
