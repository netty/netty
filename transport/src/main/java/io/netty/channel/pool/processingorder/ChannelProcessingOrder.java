/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.pool.processingorder;

import io.netty.channel.Channel;

/**
 * Beyond FIFO (First In, First Out) and LIFO (Last In, First Out), there are other types of order types commonly
 * used in programming and data structures.
 */
public interface ChannelProcessingOrder {

    /**
     * Poll a {@link Channel} out of the internal storage to reuse it. This will return {@code null} if no
     * {@link Channel} is ready to be reused. Be aware that implementations of these methods needs to be thread-safe!
     */
    Channel pollChannel();

    /**
     * Offer a {@link Channel} back to the internal storage. This will return {@code true} if the {@link Channel}
     * could be added, {@code false} otherwise. Be aware that implementations of these methods needs to be thread-safe!
     */
    boolean offerChannel(Channel channel);
}
