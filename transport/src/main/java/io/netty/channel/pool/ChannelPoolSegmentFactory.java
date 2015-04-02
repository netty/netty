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

/**
 * Creates new {@link ChannelPoolSegment}s that are used to store {@link Channel}s for a {@link ChannelPoolKey}.
 *
 * @param <C>   the type of the {@link Channel}.
 * @param <K>   the type of the {@link ChannelPoolKey}
 */
public interface ChannelPoolSegmentFactory<C extends Channel, K extends ChannelPoolKey> {

    /**
     * Create a new {@link ChannelPoolSegment}.
     */
    ChannelPoolSegment<C, K> newSegment();

    /**
     * Segment of a poll which contains {@link Channel}s. Implementations must be thread-safe!
     *
     * @param <C> the {@link Channel} type
     * @param <K>   the type of the {@link ChannelPoolKey}
     */
    interface ChannelPoolSegment<C extends Channel, K extends ChannelPoolKey> {
        /**
         * Remove the next {@link Channel} from the {@link ChannelPoolSegment} and return it. This will return
         * {@code null} if the {@link ChannelPoolSegment} is empty.
         */
        PooledChannel<C, K> poll();

        /**
         * Add the given {@link Channel} to the {@link ChannelPoolSegment} and return {@code true} if successful,
         * {@code false} otherwise.
         */
        boolean offer(PooledChannel<C, K> ch);
    }
}
