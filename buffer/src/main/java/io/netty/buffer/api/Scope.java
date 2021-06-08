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
package io.netty.buffer.api;

import io.netty.buffer.api.internal.ArrayListScope;

import java.util.Collections;

/**
 * A scope is a convenient mechanism for capturing the life cycles of multiple resource objects. Once the scope
 * is closed, all the added objects will also be closed in reverse insert order. That is, the most recently added
 * object will be closed first.
 * <p>
 * Scopes can be reused. After a scope has been closed, new objects can be added to it, and they will be closed when the
 * scope is closed again.
 * <p>
 * Objects will not be closed multiple times if the scope is closed multiple times, unless said objects are also added
 * multiple times.
 * <p>
 * Note that scopes are not thread-safe. They are intended to be used from a single thread.
 */
public interface Scope extends AutoCloseable {
    /**
     * Create a new, empty scope.
     *
     * @return A new, empty scope.
     */
    static Scope empty() {
        return new ArrayListScope();
    }

    /**
     * Create a new scope with the given resources.
     * <p>
     * More resources can be added later, and they will all be closed in reverse order of when they were added.
     *
     * @param resources The initial resources to add, in order.
     * @return A new scope with the given resources.
     */
    static Scope of(Resource<?>... resources) {
        ArrayListScope scope = new ArrayListScope();
        Collections.addAll(scope, resources);
        return scope;
    }

    /**
     * Add the given reference counted object to this scope, so that it will be {@linkplain Resource#close() closed}
     * when this scope is {@linkplain #close() closed}.
     *
     * @param obj The reference counted object to add to this scope.
     * @param <T> The type of the reference counted object.
     * @return The same exact object that was added; further operations can be chained on the object after this method
     * call.
     */
    <T extends Resource<T>> T add(T obj);

    /**
     * Close this scope and all the reference counted object it contains.
     */
    @Override
    void close();
}
