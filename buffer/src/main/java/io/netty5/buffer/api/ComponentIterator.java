/*
 * Copyright 2022 The Netty Project
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
package io.netty5.buffer.api;

import io.netty5.buffer.api.ComponentIterator.Next;
import io.netty5.util.SafeCloseable;

/**
 * A facade for iterating the readable or writable components of a {@link Buffer}.
 *
 * <strong>Note:</strong> the {@linkplain ComponentIterator iterator object} must be
 * {@linkplain ComponentIterator#close() closed} after use.
 * The easiest way to ensure this, is to use a try-with-resources clause.
 * <p>
 * The typical code pattern for using this API looks like the following:
 * <pre>{@code
 *      try (var iteration = buffer.forEachReadable()) {
 *          for (var c = iteration.first(); c != null; c = c.next()) {
 *              ByteBuffer componentBuffer = c.readableBuffer();
 *              // ...
 *          }
 *      }
 * }</pre>
 * Note the use of the {@code var} keyword for local variables, which are required for correctly expressing the
 * generic types used in the iteration.
 * Following this code pattern will ensure that the components, and their parent buffer, will be correctly
 * life-cycled.
 *
 * @param <T> A type that implements {@link Next}, and either {@link ReadableComponent} or {@link WritableComponent}.
 * @see Buffer#forEachReadable()
 * @see Buffer#forEachWritable()
 */
public interface ComponentIterator<T extends Next> extends SafeCloseable {
    /**
     * Get the first component of the iteration, or {@code null} if there are no components.
     * <p>
     * The object returned will implement both {@link Next}, and one of the {@link ReadableComponent} or
     * {@link WritableComponent} interfaces.
     *
     * @return The first component of the iteration, or {@code null} if there are no components.
     */
    T first();

    /**
     * This interface exposes external iteration on components.
     */
    interface Next {
        /**
         * Get the next component of the iteration, or {@code null} if there are no more components.
         * <p>
         * The returned object, if any, will be of the same type as this object, and would typically be assigned to the
         * same variable.
         * <p>
         * Iteration is only valid while the {@link ComponentIterator} is open.
         * Calling this method after the associated component iterator has been
         * {@linkplain ComponentIterator#close() closed} may result in undefined behaviour.
         *
         * @return The next component in the iteration, or {@code null} if there are no more components.
         * @param <N> A type that implements {@link Next}, and one of the {@link ReadableComponent} or
         * {@link WritableComponent} interfaces.
         */
        <N extends Next> N next();
    }
}
