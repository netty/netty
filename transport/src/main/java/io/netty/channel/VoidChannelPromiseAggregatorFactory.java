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

final class VoidChannelPromiseAggregatorFactory extends VoidChannelPromise
                                                implements ChannelPromiseAggregatorFactory {
    /**
     * Creates a new instance.
     *
     * @param channel       the {@link Channel} associated with this future
     * @param fireException
     */
    VoidChannelPromiseAggregatorFactory(Channel channel, boolean fireException) {
        super(channel, fireException);
    }

    @Override
    public ChannelPromise newPromise() {
        return this;
    }

    @Override
    public ChannelPromise doneAllocatingPromises() {
        return this;
    }
}
