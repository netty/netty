/*
 * Copyright 2025 The Netty Project
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
package io.netty.channel.uring;

import io.netty.channel.ChannelOption;
import io.netty.channel.RecvByteBufAllocator;

import java.util.Map;

abstract class IoUringStreamChannelConfig extends IoUringChannelConfig {

    private volatile boolean useIoUringBufferGroup;

    IoUringStreamChannelConfig(AbstractIoUringChannel channel) {
        super(channel);
    }

    IoUringStreamChannelConfig(AbstractIoUringChannel channel, RecvByteBufAllocator allocator) {
        super(channel, allocator);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == IoUringChannelOption.USE_IO_URING_BUFFER_GROUP) {
            return (T) Boolean.valueOf(getUseIoUringBufferGroup());
        }
        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        if (option == IoUringChannelOption.USE_IO_URING_BUFFER_GROUP) {
            setUseIoUringBufferGroup((Boolean) value);
            return true;
        }
        return super.setOption(option, value);
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(super.getOptions(), IoUringChannelOption.USE_IO_URING_BUFFER_GROUP);
    }

    boolean getUseIoUringBufferGroup() {
        return useIoUringBufferGroup;
    }

    IoUringStreamChannelConfig setUseIoUringBufferGroup(boolean useIoUringBufferGroup) {
        this.useIoUringBufferGroup = useIoUringBufferGroup;
        return this;
    }
}
