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
package io.netty.channel.socket;

import io.netty.channel.ChannelOption;

/**
 * Provides {@link ChannelOption}s for {@link TunChannel}s.
 */
public class TunChannelOption<T> extends ChannelOption<T> {
    /**
     * Defines MTU for the created tun device.
     * <p>
     * Increasing the MTU may also require you to adjust {@link #RCVBUF_ALLOCATOR}.
     * It is necessary, that the {@link #RCVBUF_ALLOCATOR} always yields buffers that can hold a complete IP packet.
     * <p>
     * If kqueue is used, buffers capacity must be at least 4 bytes greater than the MTU.
     */
    public static final ChannelOption<Integer> TUN_MTU = valueOf("TUN_MTU");

    protected TunChannelOption() {
        super(null);
    }
}
