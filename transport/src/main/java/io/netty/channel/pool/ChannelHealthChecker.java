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
import io.netty.util.concurrent.Promise;

/**
 * Called before a {@link Channel} will be returned via {@link ChannelPool#acquire()} or
 * {@link ChannelPool#acquire(Promise)}.
 */
public interface ChannelHealthChecker {

    /**
     * {@link ChannelHealthChecker} implementation that checks if {@link Channel#isActive()} returns {@code true}.
     */
    ChannelHealthChecker ACTIVE = new ChannelHealthChecker() {
        @Override
        public Future<Boolean> isHealthy(Channel channel) {
            EventLoop loop = channel.eventLoop();
            return channel.isActive()? loop.newSucceededFuture(Boolean.TRUE) : loop.newSucceededFuture(Boolean.FALSE);
        }
    };

    /**
     * Check if the given channel is healthy which means it can be used. The returned {@link Future} is notified once
     * the check is complete. If notified with {@link Boolean#TRUE} it can be used {@link Boolean#FALSE} otherwise.
     *
     * This method will be called by the {@link EventLoop} of the {@link Channel}.
     */
    Future<Boolean> isHealthy(Channel channel);
}
