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
package io.netty5.buffer.api.internal;

import io.netty5.buffer.api.MemoryManager;

import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.stream.Stream;

public final class MemoryManagerLoader {
    /**
     * Cache the service loader to reduce cost of repeated calls.
     * However, also place the cached loader field in a dedicated class, so the service loading is performed lazily,
     * on class initialisation, when (and if) needed.
     */
    private static final ServiceLoader<MemoryManager> LOADER = ServiceLoader.load(MemoryManager.class);

    private MemoryManagerLoader() {
    }

    /**
     * @see MemoryManager#availableManagers()
     */
    public static Stream<Provider<MemoryManager>> stream() {
        return LOADER.stream();
    }
}
