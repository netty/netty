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

import io.netty.channel.metrics.EventLoopMetrics;
import io.netty.util.concurrent.EventExecutorScheduler;


import java.util.Set;

/**
 * Derive from this interface to create your own {@link EventLoopScheduler}. An {@link EventLoopScheduler} can be
 * plugged into an {@link EventLoopGroup} to decide on how to distribute work among its {@linkplain EventLoop}s.
 * <p>
 * The central point of any {@link EventLoopScheduler} is the {@linkplain #next()} method. It is called whenever the
 * {@link EventLoopGroup} needs an {@link EventLoop} to perform some piece of work (e.g. registering a {@link Channel}
 * to an {@link EventLoop}). The default implementation of {@linkplain #next()} used throughout Netty is a simple
 * round-robin like algorithm that returns {@linkplain EventLoop}s sequentially and begins again at the first
 * {@link EventLoop} in a circular manner. However, more advanced algorithms may implement a different strategy and
 * even take into account information gathered by {@link EventLoopMetrics}.
 *
 * @see AbstractEventLoopScheduler
 */
public interface EventLoopScheduler extends EventExecutorScheduler<EventLoop, EventLoopMetrics> {

    /**
     * Returns one of the {@linkplain EventLoop}s managed by this {@link EventLoopScheduler}.
     * This method must be implemented thread-safe. This method must not return {@code null}.
     */
    @Override
    EventLoop next();

    /**
     * Add an {@link EventLoop} and its corresponding {@link EventLoopMetrics} object.
     */
    @Override
    void addChild(EventLoop executor, EventLoopMetrics metrics);

    /**
     * Returns an unmodifiable set of all {@link EventLoop}s managed by this {@link EventLoopScheduler}.
     */
    @Override
    Set<EventLoop> children();

    /**
     * Factory method to create a new instance of {@link EventLoopMetrics}.
     * <p>
     * The main use case of this method is to create a new {@link EventLoopMetrics} object that is subsequently
     * attached to an {@link EventLoop}. The two objects are then jointly passed to
     * {@link #addChild(EventLoop, EventLoopMetrics)}.
     */
    @Override
    EventLoopMetrics newMetrics();
}
