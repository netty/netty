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

import io.netty.util.internal.UnstableApi;

/**
 * Methods for accessing and controlling the internals of an allocator.
 * This interface is intended to be used by implementors of the {@link BufferAllocator}, {@link Buffer} and
 * {@link MemoryManager} interfaces.
 *
 * @apiNote This interface is public because it is a necessary integration point to separate allocators from concrete
 * buffer implementations. The API is {@linkplain UnstableApi unstable} because Netty's own allocators are the primary
 * customer of this API, and backwards compatibility at this level should not prevent us from evolving it.
 */
@UnstableApi
public interface AllocatorControl {
    /**
     * Allocates a buffer that is not tethered to any particular {@link Buffer} object,
     * and return the recoverable memory object from it.
     * <p>
     * This allows a buffer to implement {@link Buffer#ensureWritable(int)} by having new memory allocated to it,
     * without that memory being attached to some other lifetime.
     *
     * @param originator The buffer that originated the request for an untethered memory allocated.
     * @param size The size of the requested memory allocation, in bytes.
     * @return A {@link UntetheredMemory} object that is the requested allocation.
     */
    UntetheredMemory allocateUntethered(Buffer originator, int size);

    /**
     * Memory that isn't attached to any particular buffer.
     */
    interface UntetheredMemory {
        /**
         * Produces the recoverable memory object associated with this piece of untethered memory.
         * @implNote This method should only be called once, since it might be expensive.
         */
        <Memory> Memory memory();

        /**
         * Produces the drop instance associated with this piece of untethered memory.
         * @implNote This method should only be called once, since it might be expensive, or interact with Cleaners.
         */
        <BufferType extends Buffer> Drop<BufferType> drop();
    }
}
