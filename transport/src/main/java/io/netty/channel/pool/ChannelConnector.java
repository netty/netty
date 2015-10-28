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
import io.netty.channel.ChannelConfig;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Future;

/**
 * Implementations are responsible to connect a {@link Channel}.
 */
public interface ChannelConnector {

    /**
     * Try to connect a {@link} Channel and notify the {@link Future} once the operation completes.
     * Returned {@link Channel}s must have {@link ChannelConfig#isAutoRead()} {@code false}. It's the responsibility
     * of the {@link ChannelPool} to set {@link ChannelConfig#isAutoRead()} {@code false}.
     */
    Future<Channel> connect();

    /**
     * The {@link EventLoopGroup} that is used to handle the connect operations and is used to register
     * {@link Channel}s to it as part of the {@link #connect()}.
     */
    EventLoopGroup group();
}
