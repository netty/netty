/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.sockjs.util;

import java.util.Collection;

/**
 * This class consists exclusively of a static method useful for checking arguments
 * passed to methods.
 */
public final class Arguments {

    private Arguments() {
    }

    /**
     * Checks if the specified {@code ref} is {@code null}, and if so throws a {@link NullPointerException}
     * containing the {@code name} of the field.
     *
     * @param ref the reference to check for {@code null}
     * @param name the name of the argument. This will be shown in the {@link NullPointerException}'s message
     * @param <T> The type of ref
     */
    public static <T> void checkNotNull(final T ref, final String name) {
        if (ref == null) {
            throw new NullPointerException('[' + name + "] must not be null");
        }
    }

    /**
     * Checks that the specified {@link String} is not {@code null} and not empty.
     * <p>
     * If the {@code String} is {@code null} this method will throw a {@link NullPointerException} containing
     * the {@code name} of the field.
     *
     * If the {@code String} is empty this method will throw a {@link IllegalAccessException} containing
     * the {@code name} of the field.
     *
     * @param str the {@code String} to check
     * @param name the name of the argument. This will be shown in the {@link NullPointerException}'s message or
     *             the {@link IllegalAccessException} depending on whether {@code String} was null or empty
     */
    public static void checkNotNullAndNotEmpty(final String str, final String name) {
        checkNotNull(str, name);
        if (str.isEmpty()) {
            throw new IllegalArgumentException('[' + name + "] must not be empty");
        }
    }

    /**
     * Checks that the specified {@link Collection} is not {@code null} and not empty.
     * <pr>
     * If the {@link Collection} is {@code null} this method will throw a {@link NullPointerException} containing
     * the {@code name} of the field.
     *
     * If the {@link Collection} is empty this method will throw a {@link IllegalAccessException} containing
     * the {@code name} of the field.
     *
     * @param collection the {@link Collection} to check
     * @param name the name of the argument. This will be shown in the {@link NullPointerException}'s message or
     *             the {@link IllegalAccessException} depending on whether {@link Collection} was null or empty
     */
    public static void checkNotNullAndNotEmpty(final Collection<?> collection, final String name) {
        checkNotNull(collection, name);
        if (collection.isEmpty()) {
            throw new IllegalArgumentException("Collection[" + name + "] must not be empty");
        }
    }

    /**
     * Checks that the specified {@link Collection} is not empty.
     * <pr>
     *
     * If the {@link Collection} is empty this method will throw a {@link IllegalAccessException} containing
     * the {@code name} of the field.
     *
     * @param collection the {@link Collection} to check
     * @param name the name of the argument. This will be shown in the {@link IllegalAccessException} if the collection
     *             is empty
     */
    public static void checkNotEmpty(final Collection<?> collection, final String name) {
        if (collection.isEmpty()) {
            throw new IllegalArgumentException("Collection[" + name + "] must not be empty");
        }
    }

    /**
     * Checks that the specified Array is not empty.
     * <pr>
     * If the {@code array} is empty this method will throw a {@link IllegalAccessException} containing
     * the {@code name} of the field.
     *
     * @param array the Array to check
     * @param name the name of the argument. This will be shown in the {@link IllegalAccessException} if the array
     *             is empty
     */
    public static <T> void checkNotEmpty(final T[] array, final String name) {
        if (array.length == 0) {
            throw new IllegalArgumentException("Collection[" + name + "] must not be empty");
        }
    }

    /**
     * Checks that the specified Array is not {@code null} and not empty.
     * <pr>
     *
     * If the {@code array} is {@code null} this method will throw a {@link NullPointerException} containing
     * the {@code name} of the field.
     *
     * If the {@code array} is empty this method will throw a {@link IllegalAccessException} containing
     * the {@code name} of the field.
     *
     * @param array the Array to check
     * @param name the name of the argument. This will be shown in the {@link IllegalAccessException} if the array
     *             is empty
     */
    public static <T> void checkNotNullAndNotEmpty(final T[] array, final String name) {
        checkNotNull(array, name);
        checkNotEmpty(array, name);
    }

}
