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

import io.netty.channel.ChannelConfig;
import io.netty.channel.RecvByteBufAllocator;

class EpollRecvByteAllocatorHandle extends RecvByteBufAllocator.DelegatingHandle {
    private boolean isEdgeTriggered;
    private final ChannelConfig config;
    private boolean receivedRdHup;

    EpollRecvByteAllocatorHandle(RecvByteBufAllocator.Handle handle, ChannelConfig config) {
        super(handle);
        this.config = config;
    }

    final void receivedRdHup() {
        receivedRdHup = true;
    }

    boolean maybeMoreDataToRead() {
        return isEdgeTriggered && lastBytesRead() > 0;
    }

    final void edgeTriggered(boolean edgeTriggered) {
        isEdgeTriggered = edgeTriggered;
    }

    final boolean isEdgeTriggered() {
        return isEdgeTriggered;
    }

    @Override
    public final boolean continueReading() {
        /**
         * EPOLL ET requires that we read until we get an EAGAIN
         * (see Q9 in <a href="http://man7.org/linux/man-pages/man7/epoll.7.html">epoll man</a>). However in order to
         * respect auto read we supporting reading to stop if auto read is off. If auto read is on we force reading to
         * continue to avoid a {@link StackOverflowError} between channelReadComplete and reading from the
         * channel. It is expected that the {@link #EpollSocketChannel} implementations will track if we are in
         * edgeTriggered mode and all data was not read, and will force a EPOLLIN ready event.
         *
         * If EPOLLRDHUP has been received we must read until we get a read error.
         */
        return receivedRdHup || maybeMoreDataToRead() && config.isAutoRead() || super.continueReading();
    }
}
