/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.ssl;

import io.netty.util.AbstractConstant;
import io.netty.util.ConstantPool;
import io.netty.util.internal.ObjectUtil;


/**
 * A {@link SslContextOption} allows to configure a {@link SslContext} in a type-safe
 * way. Which {@link SslContextOption} is supported depends on the actual implementation
 * of {@link SslContext} and may depend on the nature of the SSL implementation it belongs
 * to.
 *
 * @param <T>   the type of the value which is valid for the {@link SslContextOption}
 */
public class SslContextOption<T> extends AbstractConstant<SslContextOption<T>> {

    private static final ConstantPool<SslContextOption<Object>> pool = new ConstantPool<SslContextOption<Object>>() {
        @Override
        protected SslContextOption<Object> newConstant(int id, String name) {
            return new SslContextOption<Object>(id, name);
        }
    };

    /**
     * Returns the {@link SslContextOption} of the specified name.
     */
    @SuppressWarnings("unchecked")
    public static <T> SslContextOption<T> valueOf(String name) {
        return (SslContextOption<T>) pool.valueOf(name);
    }

    /**
     * Shortcut of {@link #valueOf(String) valueOf(firstNameComponent.getName() + "#" + secondNameComponent)}.
     */
    @SuppressWarnings("unchecked")
    public static <T> SslContextOption<T> valueOf(Class<?> firstNameComponent, String secondNameComponent) {
        return (SslContextOption<T>) pool.valueOf(firstNameComponent, secondNameComponent);
    }

    /**
     * Returns {@code true} if a {@link SslContextOption} exists for the given {@code name}.
     */
    public static boolean exists(String name) {
        return pool.exists(name);
    }

    /**
     * Creates a new {@link SslContextOption} with the specified unique {@code name}.
     */
    private SslContextOption(int id, String name) {
        super(id, name);
    }

    /**
     * Should be used by sub-classes.
     *
     * @param name the name of the option
     */
    protected SslContextOption(String name) {
        this(pool.nextId(), name);
    }

    /**
     * Validate the value which is set for the {@link SslContextOption}. Sub-classes
     * may override this for special checks.
     */
    public void validate(T value) {
        ObjectUtil.checkNotNull(value, "value");
    }
}
