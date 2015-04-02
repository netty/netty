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
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

/**
 * Channel which is acquired from a {@link ChannelPool} and wrap an actual {@link Channel} implementaton.
 *
 * @param <C>   the {@link Channel} type to pool.
 * @param <K>   the {@link ChannelPoolKey} that is used to store and lookup the {@link Channel}s.
 */
public interface PooledChannel<C extends Channel, K extends ChannelPoolKey> extends Channel {

    /**
     * Returns the real {@link Channel} that is pooled.
     */
    C unwrap();

    /**
     * Returns the {@link ChannelPool} from which this {@link PooledChannel} was acquired.
     */
    ChannelPool<C, K> pool();

    /**
     * Returns the {@link ChannelPoolKey} which was used to acquire this {@link PooledChannel}.
     */
    K key();

    /**
     * Release a {@link Channel} back to this {@link ChannelPool}. The returned {@link Future} is notified once
     * the release is successful and failed otherwise. When failed the {@link Channel} will automatically closed.
     */
    Future<Void> release();

    /**
     * Release a {@link Channel} back to this {@link ChannelPool}.  The given {@link Promise} is notified once
     * the release is successful and failed otherwise. When failed the {@link Channel} will automatically closed.
     */
    Future<Void> release(Promise<Void> promise);
}
