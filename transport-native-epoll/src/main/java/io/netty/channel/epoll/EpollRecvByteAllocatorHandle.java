/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.epoll;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelConfig;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.util.UncheckedBooleanSupplier;
import io.netty.util.internal.ObjectUtil;

class EpollRecvByteAllocatorHandle implements RecvByteBufAllocator.ExtendedHandle {
    private final RecvByteBufAllocator.ExtendedHandle delegate;
    private final UncheckedBooleanSupplier defaultMaybeMoreDataSupplier = new UncheckedBooleanSupplier() {
        @Override
        public boolean get() {
            return maybeMoreDataToRead();
        }
    };
    private boolean isEdgeTriggered;
    private boolean receivedRdHup;

    EpollRecvByteAllocatorHandle(RecvByteBufAllocator.ExtendedHandle handle) {
        this.delegate = ObjectUtil.checkNotNull(handle, "handle");
    }

    final void receivedRdHup() {
        receivedRdHup = true;
    }

    final boolean isReceivedRdHup() {
        return receivedRdHup;
    }

    boolean maybeMoreDataToRead() {
        /**
         * EPOLL ET requires that we read until we get an EAGAIN
         * (see Q9 in <a href="http://man7.org/linux/man-pages/man7/epoll.7.html">epoll man</a>). However in order to
         * respect auto read we supporting reading to stop if auto read is off. It is expected that the
         * {@link #EpollSocketChannel} implementations will track if we are in edgeTriggered mode and all data was not
         * read, and will force a EPOLLIN ready event.
         */
        return (isEdgeTriggered && lastBytesRead() > 0) ||
               (!isEdgeTriggered && lastBytesRead() == attemptedBytesRead()) ||
                receivedRdHup;
    }

    final void edgeTriggered(boolean edgeTriggered) {
        isEdgeTriggered = edgeTriggered;
    }

    final boolean isEdgeTriggered() {
        return isEdgeTriggered;
    }

    @Override
    public final ByteBuf allocate(ByteBufAllocator alloc) {
        return delegate.allocate(alloc);
    }

    @Override
    public final int guess() {
        return delegate.guess();
    }

    @Override
    public final void reset(ChannelConfig config) {
        delegate.reset(config);
    }

    @Override
    public final void incMessagesRead(int numMessages) {
        delegate.incMessagesRead(numMessages);
    }

    @Override
    public final void lastBytesRead(int bytes) {
        delegate.lastBytesRead(bytes);
    }

    @Override
    public final int lastBytesRead() {
        return delegate.lastBytesRead();
    }

    @Override
    public final int attemptedBytesRead() {
        return delegate.attemptedBytesRead();
    }

    @Override
    public final void attemptedBytesRead(int bytes) {
        delegate.attemptedBytesRead(bytes);
    }

    @Override
    public final void readComplete() {
        delegate.readComplete();
    }

    @Override
    public final boolean continueReading(UncheckedBooleanSupplier maybeMoreDataSupplier) {
        return delegate.continueReading(maybeMoreDataSupplier);
    }

    @Override
    public final boolean continueReading() {
        // We must override the supplier which determines if there maybe more data to read.
        return delegate.continueReading(defaultMaybeMoreDataSupplier);
    }
}
