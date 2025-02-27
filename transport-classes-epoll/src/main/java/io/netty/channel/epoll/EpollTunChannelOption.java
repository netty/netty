/*
 * Copyright 2022 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.channel.ChannelOption;
import io.netty.channel.socket.TunChannelOption;

/**
 * Provides {@link ChannelOption}s for {@link EpollTunChannel}s.
 */
public final class EpollTunChannelOption<T> extends TunChannelOption<T> {
    /**
     * Enables/Disables the IFF_MULTI_QUEUE flag.
     * <p>
     * If enabled, multiple {@link EpollTunChannel}s can be assigned to the same device to parallelize
     * packet sending or receiving.
     */
    public static final ChannelOption<Boolean> IFF_MULTI_QUEUE = valueOf("IFF_MULTI_QUEUE");
}
