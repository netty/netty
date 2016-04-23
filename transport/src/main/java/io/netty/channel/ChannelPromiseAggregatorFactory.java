/*
 * Copyright 2016 The Netty Project
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

/**
 * Aggregates promise completion events and delays notification of a {@link ChannelPromise} based upon
 * how many {@link #newPromise()} have been called before {@link #doneAllocatingPromises()} is called.
 * <p>
 * Implementations may not be thread safe. Use only in {@link EventLoop} unless you know the implementation is thread
 * safe.
 */
public interface ChannelPromiseAggregatorFactory {
    /**
     * Allocate a new promise which will be used to aggregate the overall success of this promise aggregator.
     * @return A promise whose completion will be aggregated to a single {@link ChannelPromise}.
     * {@code null} if {@link #doneAllocatingPromises()} was previously called.
     */
    ChannelPromise newPromise();

    /**
     * Signify that no more {@link #newPromise()} allocations will be made.
     * The aggregation can not be successful until this method is called.
     * @return The promise that is the aggregation of all promises allocated with {@link #newPromise()}.
     */
    ChannelPromise doneAllocatingPromises();
}
