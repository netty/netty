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

import java.util.ArrayDeque;

/**
 * A scope is a convenient mechanism for capturing the life cycles of multiple reference counted objects. Once the scope
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
public final class Scope implements AutoCloseable {
    private final ArrayDeque<Resource<?>> deque = new ArrayDeque<>();

    /**
     * Add the given reference counted object to this scope, so that it will be {@linkplain Resource#close() closed}
     * when this scope is {@linkplain #close() closed}.
     *
     * @param obj The reference counted object to add to this scope.
     * @param <T> The type of the reference counted object.
     * @return The same exact object that was added; further operations can be chained on the object after this method
     * call.
     */
    public <T extends Resource<T>> T add(T obj) {
        deque.addLast(obj);
        return obj;
    }

    /**
     * Close this scope and all the reference counted object it contains.
     */
    @Override
    public void close() {
        Resource<?> obj;
        while ((obj = deque.pollLast()) != null) {
            obj.close();
        }
    }
}
