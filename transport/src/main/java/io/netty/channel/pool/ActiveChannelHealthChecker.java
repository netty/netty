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
import io.netty.util.concurrent.Future;

/**
 * {@link ChannelHealthChecker} implementation that checks if {@link Channel#isActive()} returns {@code true}.
 *
 * @param <C>   the {@link Channel} type to pool.
 * @param <K>   the {@link ChannelPoolKey} that is used to store and lookup the {@link Channel}s.
 */
public final class ActiveChannelHealthChecker<C extends Channel, K extends ChannelPoolKey>
        implements ChannelHealthChecker<C, K> {

    private ActiveChannelHealthChecker() { }

    @SuppressWarnings("rawtypes")
    private static final ChannelHealthChecker INSTANCE = new ActiveChannelHealthChecker<Channel, ChannelPoolKey>();

    @SuppressWarnings("unchecked")
    public static <C extends Channel, K extends ChannelPoolKey> ChannelHealthChecker<C, K> instance() {
        return INSTANCE;
    }

    @Override
    public Future<Boolean> isHealthy(PooledChannel<C, K> channel) {
        EventLoop loop = channel.eventLoop();
        return channel.isActive()? loop.newSucceededFuture(Boolean.TRUE) : loop.newSucceededFuture(Boolean.FALSE);
    }
}
