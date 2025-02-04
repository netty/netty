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

import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.util.internal.ObjectUtil;

import java.util.Map;

abstract class IoUringStreamChannelConfig extends IoUringChannelConfig {

    static final short DISABLE_BUFFER_SELECT_READ = -1;

    private volatile short bufferGroupId = DISABLE_BUFFER_SELECT_READ;

    IoUringStreamChannelConfig(Channel channel) {
        super(channel);
    }

    IoUringStreamChannelConfig(Channel channel, RecvByteBufAllocator allocator) {
        super(channel, allocator);
    }

    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == IoUringChannelOption.IO_URING_BUFFER_GROUP_ID) {
            return (T) Short.valueOf(getBufferGroupId());
        }
        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        if (option == IoUringChannelOption.IO_URING_BUFFER_GROUP_ID) {
            setBufferGroupId((Short) value);
            return true;
        }
        return super.setOption(option, value);
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(super.getOptions(), IoUringChannelOption.IO_URING_BUFFER_GROUP_ID);
    }

    @Override
    boolean getPollInFirst() {
        return bufferGroupId == DISABLE_BUFFER_SELECT_READ;
    }

    /**
     * Returns the buffer group id.
     *
     * @return the buffer group id.
     */
    short getBufferGroupId() {
        return bufferGroupId;
    }

    /**
     * Set the buffer group id that will be used to select the correct ring buffer. This must have been configured
     * via {@link IoUringBufferRingConfig}.
     *
     * @param bufferGroupId the buffer group id.
     * @return              itself.
     */
    IoUringStreamChannelConfig setBufferGroupId(short bufferGroupId) {
        this.bufferGroupId = (short) ObjectUtil.checkInRange(bufferGroupId, -1, Short.MAX_VALUE, "bufferGroupId");
        return this;
    }
}
