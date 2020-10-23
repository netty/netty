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
package io.netty.channel.pool;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Promise;

/**
 * Handler which is called for various actions done by the {@link ChannelPool}.
 */
public interface ChannelPoolHandler {
    /**
     * Called once a {@link Channel} was released by calling {@link ChannelPool#release(Channel)} or
     * {@link ChannelPool#release(Channel, Promise)}.
     *
     * This method will be called by the {@link EventLoop} of the {@link Channel}.
     */
    void channelReleased(Channel ch) throws Exception;

    /**
     * Called once a {@link Channel} was acquired by calling {@link ChannelPool#acquire()} or
     * {@link ChannelPool#acquire(Promise)}.
     *
     * This method will be called by the {@link EventLoop} of the {@link Channel}.
     */
    void channelAcquired(Channel ch) throws Exception;

    /**
     * Called once a new {@link Channel} is created in the {@link ChannelPool}.
     *
     * This method will be called by the {@link EventLoop} of the {@link Channel}.
     */
    void channelCreated(Channel ch) throws Exception;
}
