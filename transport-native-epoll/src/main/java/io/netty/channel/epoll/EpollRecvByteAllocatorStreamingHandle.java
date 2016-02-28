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

final class EpollRecvByteAllocatorStreamingHandle extends EpollRecvByteAllocatorHandle {
    public EpollRecvByteAllocatorStreamingHandle(RecvByteBufAllocator.Handle handle, ChannelConfig config) {
        super(handle, config);
    }

    @Override
    boolean maybeMoreDataToRead() {
        /**
         * For stream oriented descriptors we can assume we are done reading if the last read attempt didn't produce
         * a full buffer (see Q9 in <a href="http://man7.org/linux/man-pages/man7/epoll.7.html">epoll man</a>).
         */
        return isEdgeTriggered() && lastBytesRead() == attemptedBytesRead();
    }
}
