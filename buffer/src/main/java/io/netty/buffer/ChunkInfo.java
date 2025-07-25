/*
 * Copyright 2025 The Netty Project
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
package io.netty.buffer;

import io.netty.util.internal.UnstableApi;

/**
 * Information about an allocator chunk.
 */
@UnstableApi
interface ChunkInfo {
    /**
     * The capacity of the chunk, in bytes.
     */
    int capacity();

    /**
     * {@code true} if the chunk contain native memory, otherwise {@code false}.
     */
    boolean isDirect();

    /**
     * The native memory address of the chunk, if any, otherwise zero.
     */
    long memoryAddress();
}
