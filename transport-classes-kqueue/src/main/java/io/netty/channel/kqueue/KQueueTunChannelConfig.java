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
package io.netty.channel.kqueue;

import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.socket.TunChannelConfig;

import static io.netty.channel.kqueue.KQueueTunChannel.AF_HEADER_LENGTH;
import static io.netty.channel.socket.TunChannelOption.TUN_MTU;

/**
 * A {@link ChannelConfig} for a {@link KQueueTunChannel}.
 */
public class KQueueTunChannelConfig extends KQueueChannelConfig implements TunChannelConfig {
    private int mtu;

    KQueueTunChannelConfig(final AbstractKQueueChannel channel) {
        super(channel, new FixedRecvByteBufAllocator(2048 + AF_HEADER_LENGTH));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(final ChannelOption<T> option) {
        if (option == TUN_MTU) {
            return (T) Integer.valueOf(getMtu());
        }
        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        if (!super.setOption(option, value)) {
            if (option == TUN_MTU) {
                setMtu((Integer) value);
            } else {
                return false;
            }
        }
        return true;
    }

    @Override
    public int getMtu() {
        return mtu;
    }

    @Override
    public TunChannelConfig setMtu(final int mtu) {
        if (mtu < 0) {
            throw new IllegalArgumentException("mtu must be non-negative.");
        }
        this.mtu = mtu;
        return this;
    }
}
