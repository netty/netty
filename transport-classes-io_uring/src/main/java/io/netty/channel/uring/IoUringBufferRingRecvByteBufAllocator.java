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

import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.util.UncheckedBooleanSupplier;

import java.util.Objects;

/**
 * {@link RecvByteBufAllocator} that should be used if we want to use provided buffers.
 * It will return the buffer group id to use when submitting recv / read / readv {@link IoUringIoOps}.
 * <p>
 * Check
 * <a href="https://man7.org/linux/man-pages/man3/io_uring_setup_buf_ring.3.html"> man io_uring_setup_buf_ring</a>
 * and this <a href="https://lwn.net/Articles/815491/">LWN article</a> for more details.
 */
public final class IoUringBufferRingRecvByteBufAllocator implements RecvByteBufAllocator {
    private final RecvByteBufAllocator allocator;
    private final IoUringBufferGroupIdHandler ringHandler;

    /**
     * Interface that will return the buffer group id.
     */
    public interface IoUringBufferGroupIdHandler {
        /**
         * Return the group id that should be used for the given size. This method must return a valid group id
         * that was configured previously via {
         * @link IoUringIoHandlerConfig#addBufferRingConfig(IoUringBufferRingConfig)}
         *
         * @param sizeGuess the guess of bytes that we think we might be able to read.
         * @return          the group id.
         */
        short choose(int sizeGuess);

        /**
         * Will be called if a read failed for a {@link Channel} because the configured buffer ring
         * has no buffers left to use.
         *
         * @param channel   the {@link Channel} for which the read failed.
         * @param bgid      the group id.
         */
        default void exhausted(Channel channel, short bgid) {
            // Noop.
        }
    }

    /**
     * Create a new instance.
     *
     * @param allocator     the {@link RecvByteBufAllocator} that should be used internally.
     * @param ringHandler   the {@link IoUringBufferGroupIdHandler} that is used.
     */
    public IoUringBufferRingRecvByteBufAllocator(RecvByteBufAllocator allocator,
                                                 IoUringBufferGroupIdHandler ringHandler) {
        this.allocator = Objects.requireNonNull(allocator, "allocator");
        this.ringHandler = Objects.requireNonNull(ringHandler, "ringHandler");
    }

    /**
     * Create a new instance.
     *
     * @param ringHandler the {@link IoUringBufferGroupIdHandler} that is used.
     */
    public IoUringBufferRingRecvByteBufAllocator(IoUringBufferGroupIdHandler ringHandler) {
        this(new AdaptiveRecvByteBufAllocator(), ringHandler);
    }

    /**
     * Create a new instance.
     *
     * @param allocator the {@link RecvByteBufAllocator} that should be used internally.
     * @param groupId   the buffer group id that is used.
     */
    public IoUringBufferRingRecvByteBufAllocator(RecvByteBufAllocator allocator, short groupId) {
        this(allocator, size -> groupId);
    }

    /**
     * Create a new instance.
     *
     * @param groupId   the buffer group id that is used.
     */
    public IoUringBufferRingRecvByteBufAllocator(short groupId) {
        this(new AdaptiveRecvByteBufAllocator(), size -> groupId);
    }

    @Override
    public Handle newHandle() {
        return new IoBufferRingExtendedHandle(allocator.newHandle(), ringHandler);
    }

    static final class IoBufferRingExtendedHandle extends DelegatingHandle implements ExtendedHandle {
        private final IoUringBufferGroupIdHandler ringHandler;

        IoBufferRingExtendedHandle(Handle delegate, IoUringBufferGroupIdHandler ringHandler) {
            super(delegate);
            this.ringHandler = ringHandler;
        }

        @Override
        public boolean continueReading(UncheckedBooleanSupplier maybeMoreDataSupplier) {
            return ((ExtendedHandle) delegate()).continueReading(maybeMoreDataSupplier);
        }

        short getBufferGroupId() {
            return ringHandler.choose(delegate().guess());
        }

        void exhaustedBufferRing(Channel channel, short bgid) {
            ringHandler.exhausted(channel, bgid);
        }
    }
}
