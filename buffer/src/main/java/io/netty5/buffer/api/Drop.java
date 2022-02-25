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
package io.netty5.buffer.api;

/**
 * An interface used by {@link Resource} instances to implement their resource disposal mechanics.
 * The {@link #drop(Object)} method will be called by the resource when they are closed.
 *
 * @param <T> The type of resource that can be dropped.
 */
public interface Drop<T> {
    /**
     * Dispose of the resources in the given {@link Resource} instance.
     *
     * @param obj The {@link Resource} instance being dropped.
     */
    void drop(T obj);

    /**
     * Branch the responsibility of dropping a resource.
     * This drop instance will remain associated with its current resource, while the returned drop instance
     * must be {@linkplain #attach(Object) attached} to its new resource.
     *
     * @return A drop instance, similar to this one, but for the purpose of being associated with a different but
     * related resource.
     */
    Drop<T> fork();

    /**
     * Called when the resource changes owner.
     *
     * @param obj The new {@link Resource} instance with the new owner.
     */
    void attach(T obj);
}
