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
package io.netty5.buffer;

import io.netty5.buffer.ComponentIterator.Next;
import io.netty5.util.SafeCloseable;

/**
 * A facade for iterating the readable or writable components of a {@link Buffer}.
 * <p>
 * <strong>Note:</strong> the {@linkplain ComponentIterator iterator object} must be
 * {@linkplain ComponentIterator#close() closed} after use.
 * The easiest way to ensure this, is to use a try-with-resources clause.
 * <p>
 * The typical code pattern for using this API looks like the following:
 * <pre>{@code
 *      try (var iteration = buffer.forEachComponent()) {
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
 * @param <T> A type that implements {@link Next} and {@link BufferComponent}.
 * @see Buffer#forEachComponent()
 */
public interface ComponentIterator<T extends Next & BufferComponent> extends SafeCloseable {
    /**
     * Get the first component of the iteration, or {@code null} if there are no components.
     * <p>
     * The object returned will implement both of the {@link Next} and {@link BufferComponent} interfaces.
     *
     * @return The first component of the iteration, or {@code null} if there are no components.
     */
    T first();

    /**
     * Get the first component of the iteration that has any bytes to read, or {@code null} if there are no components
     * with any readable bytes.
     * <p>
     * The object returned will implement both of the {@link Next} and {@link BufferComponent} interfaces.
     *
     * @return The first readable component of the iteration, or {@code null} if there are no readable components.
     */
    default T firstReadable() {
        return nextReadable(first());
    }

    /**
     * Get the first component of the iteration that has any space for writing bytes,
     * or {@code null} if there are no components with any spare capacity.
     * <p>
     * The object returned will implement both of the {@link Next} and {@link BufferComponent} interfaces.
     *
     * @return The first writable component of the iteration, or {@code null} if there are no writable components.
     */
    default T firstWritable() {
        return nextWritable(first());
    }

    private static <T extends Next & BufferComponent> T nextReadable(T component) {
        while (component != null && component.readableBytes() == 0) {
            component = component.next();
        }
        return component;
    }

    private static <T extends Next & BufferComponent> T nextWritable(T component) {
        while (component != null && component.writableBytes() == 0) {
            component = component.next();
        }
        return component;
    }

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
         * @param <N> A type that implements the {@link Next} and {@link BufferComponent} interfaces.
         */
        <N extends Next & BufferComponent> N next();

        /**
         * Get the next component of the iteration that has any bytes to read, or {@code null} if there are no
         * components with any readable bytes.
         * <p>
         * The object returned will implement both of the {@link Next} and {@link BufferComponent} interfaces.
         *
         * @return The next readable component of the iteration, or {@code null} if there are no readable components.
         */
        default <N extends Next & BufferComponent> N nextReadable() {
            return ComponentIterator.nextReadable(next());
        }

        /**
         * Get the next component of the iteration that has any space for writing bytes,
         * or {@code null} if there are no components with any spare capacity.
         * <p>
         * The object returned will implement both of the {@link Next} and {@link BufferComponent} interfaces.
         *
         * @return The next writable component of the iteration, or {@code null} if there are no writable components.
         */
        default <N extends Next & BufferComponent> N nextWritable() {
            return ComponentIterator.nextWritable(next());
        }
    }
}
