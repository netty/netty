/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel.oio;


import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ThreadPerChannelEventLoopGroup;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/**
 * {@link EventLoopGroup} which is used to handle OIO {@link Channel}'s. Each {@link Channel} will be handled by its
 * own {@link EventLoop} to not block others.
 *
 * @deprecated use NIO / EPOLL / KQUEUE transport.
 */
@Deprecated
public class OioEventLoopGroup extends ThreadPerChannelEventLoopGroup {

    /**
     * Create a new {@link OioEventLoopGroup} with no limit in place.
     */
    public OioEventLoopGroup() {
        this(0);
    }

    /**
     * Create a new {@link OioEventLoopGroup}.
     *
     * @param maxChannels       the maximum number of channels to handle with this instance. Once you try to register
     *                          a new {@link Channel} and the maximum is exceed it will throw an
     *                          {@link ChannelException} on the {@link #register(Channel)} and
     *                          {@link #register(ChannelPromise)} method.
     *                          Use {@code 0} to use no limit
     */
    public OioEventLoopGroup(int maxChannels) {
        this(maxChannels, (ThreadFactory) null);
    }

    /**
     * Create a new {@link OioEventLoopGroup}.
     *
     * @param maxChannels       the maximum number of channels to handle with this instance. Once you try to register
     *                          a new {@link Channel} and the maximum is exceed it will throw an
     *                          {@link ChannelException} on the {@link #register(Channel)} and
     *                          {@link #register(ChannelPromise)} method.
     *                          Use {@code 0} to use no limit
     * @param executor     the {@link Executor} used to create new {@link Thread} instances that handle the
     *                          registered {@link Channel}s
     */
    public OioEventLoopGroup(int maxChannels, Executor executor) {
        super(maxChannels, executor);
    }

    /**
     * Create a new {@link OioEventLoopGroup}.
     *
     * @param maxChannels       the maximum number of channels to handle with this instance. Once you try to register
     *                          a new {@link Channel} and the maximum is exceed it will throw an
     *                          {@link ChannelException} on the {@link #register(Channel)} and
     *                          {@link #register(ChannelPromise)} method.
     *                          Use {@code 0} to use no limit
     * @param threadFactory     the {@link ThreadFactory} used to create new {@link Thread} instances that handle the
     *                          registered {@link Channel}s
     */
    public OioEventLoopGroup(int maxChannels, ThreadFactory threadFactory) {
        super(maxChannels, threadFactory);
    }
}
