/*
 * Copyright 2014 The Netty Project
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

package io.netty.channel;

import io.netty.util.metrics.EventExecutorChooser;
import io.netty.util.metrics.MetricsCollector;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import io.netty.util.concurrent.MultithreadEventExecutorGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import java.util.List;

/**
 * Implement this interface to create your own mapping of {@link SocketChannel}s to {@link EventLoops}s.
 * For a reference implementation have a look at
 * {@link io.netty.util.concurrent.MultithreadEventExecutorGroup.RoundRobinChooser}.
 */
public interface EventLoopChooser extends EventExecutorChooser<EventLoop> {
    /**
     * This method is called whenever a newly accepted {@link SocketChannel} is to be mapped to an
     * {@link EventLoop} in which case remoteAddress contains its {@link SocketAddress}.
     * Furthermore, this method is also called to assign tasks (scheduled by Netty or by the user) to
     * {@link EventLoop}s in which case remoteAddress will be null. A developer must ensure that this
     * method works in both cases!
     *
     * @param remoteAddress the address of the {@link SocketChannel} or null.
     * @return  This method MUST NOT return null.
     */
    @Override
    EventLoop next(SocketAddress remoteAddress);

    /**
     * Add a new {@link EventLoop} and its corresponding {@link MetricsCollector}.
     * <p/>
     * If the {@link io.netty.util.metrics.EventExecutorChooser} is passed to the constructor of a
     * {@link MultithreadEventExecutorGroup} implementation (e.g. {@link NioEventLoopGroup}), then {@link #addChild}
     * is called once for each of its {@link EventLoop}s and the {@link MetricsCollector} objects attached to them.
     * Furthermore, it is guaranteed that {@link #addChild} will only ever be called with {@link MetricsCollector}
     * objects created by {@link #newMetricsCollector()}.
     */
    @Override
    void addChild(EventLoop executor, MetricsCollector metrics);

    /**
     * Returns an unmodifiable list of {@link EventLoop}s that are possible candidates to be returned by
     * a call to {@link #next(SocketAddress)}.
     */
    @Override
    List<EventLoop> children();

    /**
     * Factory method to create a new instance of a {@link MetricsCollector}.
     * <p/>
     * The main use case of this method is to create a new {@link MetricsCollector} object that is subsequently passed
     * to {@link #addChild(EventLoop, MetricsCollector)} and attached to the {@link EventLoop}.
     */
    @Override
    MetricsCollector newMetricsCollector();
}
