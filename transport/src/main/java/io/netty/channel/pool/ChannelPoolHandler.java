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
package io.netty.channel.pool;


import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Promise;

/**
 * Handler which is called for various actions done by the {@link ChannelPool}.
 *
 * @param <C>   the {@link Channel} type to pool.
 * @param <K>   the {@link ChannelPoolKey} that is used to store and lookup the {@link Channel}s.
 */
public interface ChannelPoolHandler<C extends Channel, K extends ChannelPoolKey> {
    /**
     * Called once a {@link PooledChannel} was released by calling {@link PooledChannel#release()} or
     * {@link PooledChannel#release(Promise)}.
     *
     * This method will be called by the {@link EventLoop} of the {@link PooledChannel}.
     *
     * @param ch        the {@link PooledChannel}
     */
    void channelReleased(PooledChannel<C, K> ch) throws Exception;

    /**
     * Called once a {@link PooledChannel} was acquired by calling {@link ChannelPool#acquire(ChannelPoolKey)} or
     * {@link ChannelPool#acquire(ChannelPoolKey, Promise)}.
     *
     * This method will be called by the {@link EventLoop} of the {@link PooledChannel}.
     *
     * @param ch       the {@link PooledChannel}
     */
    void channelAcquired(PooledChannel<C, K> ch) throws Exception;

    /**
     * Called once a new {@link PooledChannel} is created in the {@link ChannelPool}.
     *
     * This method will be called by the {@link EventLoop} of the {@link PooledChannel}.
     *
     * @param ch        the {@link PooledChannel}
     */
    void channelCreated(PooledChannel<C, K> ch) throws Exception;
}
