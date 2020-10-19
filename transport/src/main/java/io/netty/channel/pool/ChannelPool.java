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
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.io.Closeable;

/**
 * Allows to acquire and release {@link Channel} and so act as a pool of these.
 */
public interface ChannelPool extends Closeable {

    /**
     * Acquire a {@link Channel} from this {@link ChannelPool}. The returned {@link Future} is notified once
     * the acquire is successful and failed otherwise.
     *
     * <strong>Its important that an acquired is always released to the pool again, even if the {@link Channel}
     * is explicitly closed..</strong>
     */
    Future<Channel> acquire();

    /**
     * Acquire a {@link Channel} from this {@link ChannelPool}. The given {@link Promise} is notified once
     * the acquire is successful and failed otherwise.
     *
     * <strong>Its important that an acquired is always released to the pool again, even if the {@link Channel}
     * is explicitly closed..</strong>
     */
    Future<Channel> acquire(Promise<Channel> promise);

    /**
     * Release a {@link Channel} back to this {@link ChannelPool}. The returned {@link Future} is notified once
     * the release is successful and failed otherwise. When failed the {@link Channel} will automatically closed.
     */
    Future<Void> release(Channel channel);

    /**
     * Release a {@link Channel} back to this {@link ChannelPool}. The given {@link Promise} is notified once
     * the release is successful and failed otherwise. When failed the {@link Channel} will automatically closed.
     */
    Future<Void> release(Channel channel, Promise<Void> promise);

    @Override
    void close();
}
