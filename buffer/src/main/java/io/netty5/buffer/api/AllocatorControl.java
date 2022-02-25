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

import io.netty5.util.internal.UnstableApi;

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
     * Get the {@link BufferAllocator} instance that is the source of this allocator control.
     *
     * @return The {@link BufferAllocator} controlled by this {@link AllocatorControl}.
     */
    BufferAllocator getAllocator();
}
