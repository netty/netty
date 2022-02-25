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
import io.netty5.buffer.api.bytebuffer.ByteBufferMemoryManager;
import io.netty5.buffer.api.unsafe.UnsafeMemoryManager;
import io.netty5.util.internal.PlatformDependent;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class MemoryManagerOverride {
    private static final MemoryManager DEFAULT = createDefaultMemoryManagerInstance();
    private static final AtomicInteger OVERRIDES_AVAILABLE = new AtomicInteger();
    private static final Map<Thread, MemoryManager> OVERRIDES = Collections.synchronizedMap(new IdentityHashMap<>());

    private MemoryManagerOverride() {
    }

    private static MemoryManager createDefaultMemoryManagerInstance() {
        String systemProperty = "io.netty5.buffer.api.MemoryManager";
        String configured = System.getProperty(systemProperty);
        InternalLogger logger = InternalLoggerFactory.getInstance(MemoryManagerOverride.class);
        if (configured != null) {
            Optional<MemoryManager> candidateManager = MemoryManager.lookupImplementation(configured);
            if (candidateManager.isPresent()) {
                logger.debug("{} configured: {}", systemProperty, configured);
                return candidateManager.get();
            } else {
                MemoryManager fallback = new ByteBufferMemoryManager();
                logger.debug("{} requested implementation is unavailable: {} (using default {} implementation instead)",
                             systemProperty, configured, fallback.implementationName());
                return fallback;
            }
        }
        if (PlatformDependent.hasUnsafe() && PlatformDependent.hasDirectBufferNoCleanerConstructor()) {
            try {
                return new UnsafeMemoryManager();
            } catch (Exception exception) {
                // We will just fall back to ByteBuffer based memory management if Unsafe fails.
                logger.warn("Both sun.misc.Unsafe and DirectByteBuffer-without-Cleaner constructor are available, " +
                            "yet an UnsafeMemoryManager could not be created.", exception);
            }
        }
        return new ByteBufferMemoryManager();
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
