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

import io.netty.channel.RecvByteBufAllocator;

/**
 * Respects termination conditions for EPOLL message (aka packet) based protocols.
 */
final class EpollRecvByteAllocatorMessageHandle extends EpollRecvByteAllocatorHandle {
    public EpollRecvByteAllocatorMessageHandle(RecvByteBufAllocator.Handle handle, boolean isEdgeTriggered) {
        super(handle, isEdgeTriggered);
    }

    @Override
    public boolean continueReading() {
        /**
         * If edgeTriggered is used we need to read all bytes/messages as we are not notified again otherwise. For
         * packet oriented descriptors must read until we get a EAGAIN
         * (see Q9 in <a href="http://man7.org/linux/man-pages/man7/epoll.7.html">epoll man</a>).
         *
         * If EPOLLRDHUP has been received we must read until we get a read error.
         */
        return isEdgeTriggered() || isRdHup() ? true : super.continueReading();
    }
}
