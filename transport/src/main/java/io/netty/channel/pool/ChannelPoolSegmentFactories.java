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
import io.netty.channel.pool.ChannelPoolSegmentFactory.ChannelPoolSegment;
import io.netty.util.internal.PlatformDependent;

import java.util.Deque;
import java.util.Queue;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Utilites for {@link ChannelPoolSegmentFactories}.
 */
public final class ChannelPoolSegmentFactories {

    private ChannelPoolSegmentFactories() {
        // Use static methods.
    }

    /**
     * Creates a new {@link ChannelPoolSegmentFactory} that creates {@link ChannelPoolSegment} that use LIFO order.
     *
     * @param <C>   the {@link Channel} type
     */
    public static <C extends Channel, K extends ChannelPoolKey> ChannelPoolSegmentFactory<C, K> newLifoFactory() {
        return new ChannelPoolSegmentFactory<C, K>() {
            @Override
            public ChannelPoolSegment<C, K> newSegment() {
                return newLifoSegment(PlatformDependent.<PooledChannel<C, K>>newConcurrentDeque());
            }
        };
    }

    /**
     * Creates a new {@link ChannelPoolSegmentFactory} that creates {@link ChannelPoolSegment} that use FIFO order.
     *
     * @param <C>   the {@link Channel} type
     */
    public static <C extends Channel, K extends ChannelPoolKey> ChannelPoolSegmentFactory<C, K> newFifoFactory() {
        return new ChannelPoolSegmentFactory<C, K>() {
            @Override
            public ChannelPoolSegment<C, K> newSegment() {
                return newFifoSegment(PlatformDependent.<PooledChannel<C, K>>newConcurrentDeque());
            }
        };
    }

    /**
     * Wraps a {@link Queue} and creates a new {@link ChannelPoolSegment} out of it that will use FIFO order.
     * The given {@link Queue} needs to be thread-safe!
     */
    public static <C extends Channel, K extends ChannelPoolKey> ChannelPoolSegment<C, K> newFifoSegment(
            final Queue<PooledChannel<C, K>> queue) {
        checkNotNull(queue, "queue");
        return new ChannelPoolSegment<C, K>() {
            @Override
            public PooledChannel<C, K> poll() {
                return queue.poll();
            }

            @Override
            public boolean offer(PooledChannel<C, K> ch) {
                return queue.offer(ch);
            }
        };
    }

    /**
     * Wraps a {@link Queue} and creates a new {@link ChannelPoolSegment} out of it that will use LIFO order.
     * The given {@link Deque} needs to be thread-safe!
     */
    public static <C extends Channel, K extends ChannelPoolKey> ChannelPoolSegment<C, K> newLifoSegment(
            final Deque<PooledChannel<C, K>> deque) {
        checkNotNull(deque, "deque");
        return new ChannelPoolSegment<C, K>() {
            @Override
            public PooledChannel<C, K> poll() {
                return deque.pollLast();
            }

            @Override
            public boolean offer(PooledChannel<C, K> ch) {
                return deque.offer(ch);
            }
        };
    }
}
