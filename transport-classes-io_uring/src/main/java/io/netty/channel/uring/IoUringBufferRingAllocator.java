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
package io.netty.channel.uring;

import io.netty.buffer.ByteBuf;

/**
 * Allocator that is responsible to allocate buffers for a buffer ring.
 */
public interface IoUringBufferRingAllocator {
    /**
     * Creates a new receive buffer to use by the buffer ring. The returned {@link ByteBuf} must be direct.
     */
    ByteBuf allocate();

    /**
     * Set the bytes that have been read for the last read operation that was full-filled out of the buffer ring.
     *
     * @param attempted  the attempted bytes to read.
     * @param actual     the number of bytes that could be read.
     */
    void lastBytesRead(int attempted, int actual);
}
