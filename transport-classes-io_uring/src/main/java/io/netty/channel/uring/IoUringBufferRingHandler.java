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

import io.netty.channel.Channel;

/**
 * Handles the selection of buffer rings for recv / read / readv {@link IoUringIoOps}.
 * <p>
 * Check
 * <a href="https://man7.org/linux/man-pages/man3/io_uring_setup_buf_ring.3.html"> man io_uring_setup_buf_ring</a>
 * an this <a href="https://lwn.net/Articles/815491/">LWN article</a> for more details.
 */
public interface IoUringBufferRingHandler {

    /**
     * Return the buffer group id to use when submitting recv / read / readv {@link IoUringIoOps}.
     * The buffer ring must have been configured via
     * {@link IoUringIoHandlerConfig#setBufferRingConfig(IoUringBufferRingHandler, IoUringBufferRingConfig...)}.
     *
     * @param guessedSize       the estimated size of the next read.
     * @return                  the group to use if non-negative. If negative no group will be used at all.
     */
    short selectBufferRing(Channel channel, int guessedSize);

    /**
     * Called when a buffer ring was exhausted and so all buffers in it are currently in use.
     *
     * @param id    the id of the buffer ring.
     */
    default void notifyAllBuffersUsed(short id) {
        // Do nothing by default.
    }

    /**
     * Called when a buffer ring has more buffers ready to use again (after it was exhausted)
     *
     * @param id    the id of the buffer ring.
     */
    default void notifyMoreBuffersReady(short id) {
        // Do nothing by default.
    }
}
