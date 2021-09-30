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

import io.netty.buffer.api.internal.LeakDetection;
import io.netty.buffer.api.internal.MemoryManagerLoader;
import io.netty.buffer.api.internal.MemoryManagerOverride;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader.Provider;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The choice of {@code MemoryManager} implementation also determines the choice of {@link Buffer} implementation.
 * It is the MemoryManager that implement memory allocation, and how to wrap the allocated memory in a {@link Buffer}
 * interface.
 *
 * @apiNote This is a low-level, {@linkplain UnstableApi unstable}, API that is used for
 * {@link BufferAllocator BufferAllocator} implementations to build upon.
 * The methods in this interface are unsafe, because they can be used to violate the safety guarantees of the
 * {@link Buffer} API, and potentially also the safety guarantees of the JVM.
 */
@UnstableApi
public interface MemoryManager {
    /**
     * Get the default, or currently configured, memory managers instance.
     * @return A MemoryManagers instance.
     */
    static MemoryManager instance() {
        return MemoryManagerOverride.configuredOrDefaultManager();
    }

    /**
     * Register a callback that will be called whenever a {@link Buffer} instance is leaked.
     * <p>
     * Be mindful that the callback must be fast, and not take any locks or call any blocking methods,
     * as this might interfere with the garbage collectors reference processing and cleaning.
     *
     * @param callback The callback that will be called when a buffer leak is detected.
     * @return An {@link AutoCloseable} instance that, when closed, removes the given callback again.
     */
    @UnstableApi
    static AutoCloseable onLeakDetected(Consumer<LeakInfo> callback) {
        return LeakDetection.onLeakDetected(callback);
    }

    /**
     * Temporarily override the default configured memory managers instance.
     * <p>
     * Calls to {@link #instance()} from within the given supplier will get the given managers instance.
     *
     * @param managers Override the default configured managers instance with this instance.
     * @param supplier The supplier function to be called while the override is in place.
     * @param <T> The result type from the supplier.
     * @return The result from the supplier.
     */
    static <T> T using(MemoryManager managers, Supplier<T> supplier) {
        return MemoryManagerOverride.using(managers, supplier);
    }

    /**
     * Get a lazy-loading stream of all available memory managers.
     *
     * @return A stream of providers of memory managers instances.
     */
    static Stream<Provider<MemoryManager>> availableManagers() {
        return MemoryManagerLoader.stream();
    }

    /**
     * Find a {@link MemoryManager} implementation by its {@linkplain #implementationName() implementation name}.
     *
     * @param implementationName The named implementation to look for.
     * @return A {@link MemoryManager} implementation, if any was found.
     */
    static Optional<MemoryManager> lookupImplementation(String implementationName) {
        return availableManagers()
                .flatMap(provider -> {
                    try {
                        return Stream.ofNullable(provider.get());
                    } catch (ServiceConfigurationError | Exception e) {
                        InternalLogger logger = InternalLoggerFactory.getInstance(MemoryManager.class);
                        if (logger.isTraceEnabled()) {
                            logger.debug("Failed to load a MemoryManager implementation.", e);
                        } else {
                            logger.debug("Failed to load a MemoryManager implementation: " + e.getMessage());
                        }
                        return Stream.empty();
                    }
                })
                .filter(impl -> implementationName.equals(impl.implementationName()))
                .findFirst();
    }

    /**
     * Allocates a shared buffer. "Shared" is the normal type of buffer, and means the buffer permit concurrent access
     * from multiple threads, within the limited thread-safety guarantees of the {@link Buffer} interface.
     *
     * @param allocatorControl Call-back interface for controlling the {@linkplain BufferAllocator allocator} that
     *                        requested the allocation of this buffer.
     * @param size The size of the buffer to allocate. This size is assumed to be valid for the implementation.
     * @param drop The {@link Drop} instance to use when the buffer is {@linkplain Resource#close() closed}.
     * @param allocationType The type of allocation to perform.
     *                      Typically, one of the {@linkplain StandardAllocationTypes}.
     * @return A {@link Buffer} instance with the given configuration.
     * @throws IllegalArgumentException For unknown {@link AllocationType}s.
     */
    Buffer allocateShared(AllocatorControl allocatorControl, long size, Drop<Buffer> drop,
                          AllocationType allocationType);

    /**
     * Allocates a constant buffer based on the given parent. A "constant" buffer is conceptually similar to a read-only
     * buffer, but the implementation may share the underlying memory across multiple buffer instance - something that
     * is normally not allowed by the API. This allows efficient implementation of the
     * {@link BufferAllocator#constBufferSupplier(byte[])} method.
     * <p>
     * <strong>Note:</strong> the const-parent buffer must be allocated by this memory manager.
     *
     * @param readOnlyConstParent The read-only parent buffer for which a const buffer should be created. The parent
     *                            buffer is allocated in the usual way, with
     *                            {@link #allocateShared(AllocatorControl, long, Drop, AllocationType)},
     *                            initialised with contents, and then made {@linkplain Buffer#makeReadOnly() read-only}.
     * @return A const buffer with the same size, contents, and read-only state of the given parent buffer.
     */
    Buffer allocateConstChild(Buffer readOnlyConstParent);

    /**
     * The buffer implementation-specific {@link Drop} implementation that will release the underlying memory.
     *
     * @return A new drop instance.
     */
    Drop<Buffer> drop();

    /**
     * Create an object that represents the internal memory of the given buffer.
     *
     * @param buf The buffer to unwrap.
     * @return The internal memory of the given buffer, as an opaque object.
     */
    Object unwrapRecoverableMemory(Buffer buf);

    /**
     * Recover the memory from a prior {@link #unwrapRecoverableMemory(Buffer)} call, and wrap it in a {@link Buffer}
     * instance.
     *
     * @param allocatorControl The allocator control to attach to the buffer.
     * @param recoverableMemory The opaque memory to use for the buffer.
     * @param drop The {@link Drop} instance to use when the buffer is {@linkplain Resource#close() closed}.
     * @return A {@link Buffer} instance backed by the given recovered memory.
     */
    Buffer recoverMemory(AllocatorControl allocatorControl, Object recoverableMemory, Drop<Buffer> drop);

    /**
     * Produces a slice of the given internal memory representation object.
     *
     * @param memory The opaque memory to slice.
     * @param offset The offset into the memory to slice from.
     * @param length The length of the slice.
     * @return A new opaque memory instance that represents the given slice of the original.
     */
    Object sliceMemory(Object memory, int offset, int length);

    /**
     * Get the name for this implementation, which can be used for finding this particular implementation via the
     * {@link #lookupImplementation(String)} method.
     *
     * @return The name of this memory managers implementation.
     */
    String implementationName();
}
