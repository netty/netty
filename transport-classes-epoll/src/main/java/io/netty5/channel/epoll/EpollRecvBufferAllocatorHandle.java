/*
 * Copyright 2015 The Netty Project
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
package io.netty5.channel.epoll;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.DefaultBufferAllocators;
import io.netty5.buffer.api.StandardAllocationTypes;
import io.netty5.channel.RecvBufferAllocator.DelegatingHandle;
import io.netty5.channel.RecvBufferAllocator.Handle;
import io.netty5.channel.unix.PreferredDirectByteBufAllocator;
import io.netty5.util.UncheckedBooleanSupplier;

class EpollRecvBufferAllocatorHandle extends DelegatingHandle {
    private final PreferredDirectByteBufAllocator preferredDirectByteBufAllocator =
            new PreferredDirectByteBufAllocator();
    private final UncheckedBooleanSupplier defaultMaybeMoreDataSupplier = this::maybeMoreDataToRead;
    private boolean receivedRdHup;

    EpollRecvBufferAllocatorHandle(Handle handle) {
        super(handle);
    }

    final void receivedRdHup() {
        receivedRdHup = true;
    }

    final boolean isReceivedRdHup() {
        return receivedRdHup;
    }

    /**
     * EPOLL ET requires that we read until we get an EAGAIN
     * (see Q9 in <a href="https://man7.org/linux/man-pages/man7/epoll.7.html">epoll man</a>). However, in order to
     * respect auto read, we support reading to stop if auto read is off. It is expected that the
     * {@link EpollSocketChannel} implementations will track if we are in edgeTriggered mode and all data was not
     * read, and will force a EPOLLIN ready event.
     *
     * It is assumed RDHUP is handled externally by checking {@link #isReceivedRdHup()}.
     */
    boolean maybeMoreDataToRead() {
        return lastBytesRead() > 0;
    }

    @Override
    public final ByteBuf allocate(ByteBufAllocator alloc) {
        // We need to ensure we always allocate a direct ByteBuf as we can only use a direct buffer to read via JNI.
        preferredDirectByteBufAllocator.updateAllocator(alloc);
        return delegate().allocate(preferredDirectByteBufAllocator);
    }

    @Override
    public Buffer allocate(BufferAllocator alloc) {
        // We need to ensure we always allocate a direct ByteBuf as we can only use a direct buffer to read via JNI.
        if (alloc.getAllocationType() != StandardAllocationTypes.OFF_HEAP) {
            return super.allocate(DefaultBufferAllocators.offHeapAllocator());
        }
        return super.allocate(alloc);
    }

    @Override
    public final boolean continueReading() {
        // We must override the supplier which determines if there maybe more data to read.
        return continueReading(defaultMaybeMoreDataSupplier);
    }
}
