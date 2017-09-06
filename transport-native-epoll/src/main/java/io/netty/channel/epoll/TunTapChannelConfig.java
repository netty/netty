/*
 * Copyright 2014 The Netty Project
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

package io.netty.channel.epoll;

import java.util.Map;

import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.RecvByteBufAllocator;
import static io.netty.channel.epoll.TunTapChannelOption.*;

public class TunTapChannelConfig extends EpollChannelConfig {

    private Integer readBufSize = 2048;
    private static final RecvByteBufAllocator defaultRcvBufAllocator = new FixedRecvByteBufAllocator(2048);

    public TunTapChannelConfig(AbstractEpollChannel channel) {
        super(channel);
        setRecvByteBufAllocator(defaultRcvBufAllocator);
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(super.getOptions(), READ_BUF_SIZE);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == READ_BUF_SIZE) {
            return (T) readBufSize;
        }
        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        validate(option, value);
        if (option == READ_BUF_SIZE) {
            readBufSize = (Integer) value;
            if (getRecvByteBufAllocator().getClass().equals(FixedRecvByteBufAllocator.class)) {
                setRecvByteBufAllocator(new FixedRecvByteBufAllocator(readBufSize));
            }
        } else {
            return super.setOption(option, value);
        }
        return true;
    }
}
