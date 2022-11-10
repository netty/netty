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

import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.socket.TunChannelConfig;
import io.netty.channel.socket.TunChannelOption;

import static io.netty.channel.epoll.EpollTunChannelOption.IFF_MULTI_QUEUE;
import static io.netty.channel.socket.TunChannelOption.TUN_MTU;

/**
 * A {@link ChannelConfig} for a {@link EpollTunChannel}.
 *
 * <h3>Available options</h3>
 * <p>
 * In addition to the options provided by {@link ChannelConfig}, {@link TunChannelConfig} allows the
 * following options in the option map:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th><th>Associated setter method</th>
 * </tr><tr>
 * <td>{@link EpollTunChannelOption#TUN_MTU}</td><td>{@link #setMtu(int)}</td>
 * </tr><tr>
 * <td>{@link EpollTunChannelOption#IFF_MULTI_QUEUE}</td><td>{@link #setMultiqueue(boolean)}</td>
 * </tr>
 * </table>
 */
public class EpollTunChannelConfig extends EpollChannelConfig implements TunChannelConfig {
    private int mtu;
    private boolean multiqueue;

    EpollTunChannelConfig(AbstractEpollChannel channel) {
        super(channel, new FixedRecvByteBufAllocator(2048));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == TUN_MTU) {
            return (T) Integer.valueOf(getMtu());
        }
        if (option == IFF_MULTI_QUEUE) {
            return (T) Boolean.valueOf(isMultiqueue());
        }
        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        if (!super.setOption(option, value)) {
            if (option == TUN_MTU) {
                setMtu((Integer) value);
            } else if (option == IFF_MULTI_QUEUE) {
                setMultiqueue((Boolean) value);
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
    public TunChannelConfig setMtu(int mtu) {
        if (mtu < 0) {
            throw new IllegalArgumentException("mtu must be non-negative.");
        }
        this.mtu = mtu;
        return this;
    }

    public boolean isMultiqueue() {
        return multiqueue;
    }

    public TunChannelConfig setMultiqueue(boolean multiqueue) {
        this.multiqueue = multiqueue;
        return this;
    }
}
