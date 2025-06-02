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
package io.netty.channel.pool.processingorder;

import io.netty.channel.Channel;
import io.netty.util.internal.PlatformDependent;

import java.util.Deque;

/**
 * FIFO (First In, First Out)
 */
public class FifoChannelProcessingOrder implements ChannelProcessingOrder {

    private final Deque<Channel> deque = PlatformDependent.newConcurrentDeque();

    @Override
    public Channel pollChannel() {
        return this.deque.pollFirst();
    }

    @Override
    public boolean offerChannel(Channel channel) {
        return this.deque.offer(channel);
    }
}
