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

import java.net.SocketAddress;

/**
 * A key that can be used to acquire and release {@link Channel}s from a {@link ChannelPool}.
 *
 * Please note that implementations <strong>MUST</strong> implement proper {@link #hashCode()} and
 * {@link #equals(Object)} methods.
 */
public interface ChannelPoolKey {

    /**
     * The {@link SocketAddress} of the remote peer.
     */
    SocketAddress remoteAddress();

    /**
     * The {@link EventLoop} that should be used or {@code null} if not specified.
     */
    EventLoop eventLoop();
}
