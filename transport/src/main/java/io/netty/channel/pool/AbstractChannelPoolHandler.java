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

/**
 * Base class for {@link ChannelPoolHandler} implementations.
 *
 * @param <C>   the {@link Channel} type to pool.
 * @param <K>   the {@link ChannelPoolKey} that is used to store and lookup the {@link Channel}s.
 */
public abstract class AbstractChannelPoolHandler<C extends Channel, K extends ChannelPoolKey>
        implements ChannelPoolHandler<C, K> {

    /**
     * NOOP implementation, sub-classes may override this.
     */
    @Override
    public void channelAcquired(@SuppressWarnings("unused") PooledChannel<C, K> ch) throws Exception {
        // NOOP
    }

    /**
     * NOOP implementation, sub-classes may override this.
     */
    @Override
    public void channelReleased(@SuppressWarnings("unused") PooledChannel<C, K> ch) throws Exception {
        // NOOP
    }
}
