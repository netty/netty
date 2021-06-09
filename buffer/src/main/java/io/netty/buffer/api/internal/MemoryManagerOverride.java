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
package io.netty.buffer.api.internal;

import io.netty.buffer.api.MemoryManager;
import io.netty.buffer.api.bytebuffer.ByteBufferMemoryManager;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class MemoryManagerOverride {
    private static final MemoryManager DEFAULT = new ByteBufferMemoryManager();
    private static final AtomicInteger OVERRIDES_AVAILABLE = new AtomicInteger();
    private static final Map<Thread, MemoryManager> OVERRIDES = Collections.synchronizedMap(new IdentityHashMap<>());

    private MemoryManagerOverride() {
    }

    public static MemoryManager configuredOrDefaultManager() {
        if (OVERRIDES_AVAILABLE.get() > 0) {
            return OVERRIDES.getOrDefault(Thread.currentThread(), DEFAULT);
        }
        return DEFAULT;
    }

    public static <T> T using(MemoryManager managers, Supplier<T> supplier) {
        Thread thread = Thread.currentThread();
        OVERRIDES.put(thread, managers);
        OVERRIDES_AVAILABLE.incrementAndGet();
        try {
            return supplier.get();
        } finally {
            OVERRIDES_AVAILABLE.decrementAndGet();
            OVERRIDES.remove(thread);
        }
    }
}
