/*
 * Copyright 2013 The Netty Project
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
package io.netty.channel.group;


import io.netty.channel.Channel;

/**
 * Allows to only match some {@link Channel}'s for operations in {@link ChannelGroup}.
 *
 * {@link ChannelMatchers} provide you with helper methods for usual needed implementations.
 */
public interface ChannelMatcher {

    /**
     * Returns {@code true} if the operation should be also executed on the given {@link Channel}.
     */
    boolean matches(Channel channel);
}
