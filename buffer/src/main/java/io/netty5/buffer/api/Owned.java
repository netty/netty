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
 * This interface encapsulates the ownership of a {@link Resource}, and exposes a method that may be used to transfer
 * this ownership to the specified recipient thread.
 *
 * @param <T> The concrete type of {@link Resource} that is owned.
 */
@SuppressWarnings("InterfaceMayBeAnnotatedFunctional")
public interface Owned<T> {
    /**
     * Transfer the ownership of the resource, to the calling thread. The resource instance is invalidated but without
     * disposing of its internal state. Then a new resource instance with the given owner is produced in its stead.
     * <p>
     * This method is called by {@link Send} implementations. These implementations will ensure that the transfer of
     * ownership (the calling of this method) happens-before the new owner begins accessing the new object. This ensures
     * that the new resource instance is safely published to the new owners.
     *
     * @param drop The drop object that knows how to dispose of the state represented by this {@link Resource}.
     * @return A new resource instance that is exactly the same as this resource.
     */
    T transferOwnership(Drop<T> drop);
}
