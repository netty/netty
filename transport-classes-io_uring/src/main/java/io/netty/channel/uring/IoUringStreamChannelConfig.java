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
import io.netty.util.internal.ObjectUtil;

import java.util.Map;

abstract class IoUringStreamChannelConfig extends IoUringChannelConfig {

    private volatile short bufferGroupId = -1;
    private volatile int sendfilePipeSize;

    IoUringStreamChannelConfig(AbstractIoUringChannel channel) {
        super(channel);
    }

    IoUringStreamChannelConfig(AbstractIoUringChannel channel, RecvByteBufAllocator allocator) {
        super(channel, allocator);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == IoUringChannelOption.IO_URING_BUFFER_GROUP_ID) {
            return (T) Short.valueOf(getBufferGroupId());
        }
        if (option == IoUringChannelOption.IO_URING_SENDFILE_PIPE_SIZE) {
            return (T) Integer.valueOf(getSendfilePipeSize());
        }
        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        if (option == IoUringChannelOption.IO_URING_BUFFER_GROUP_ID) {
            setBufferGroupId((Short) value);
            return true;
        }
        if (option == IoUringChannelOption.IO_URING_SENDFILE_PIPE_SIZE) {
            setSendfilePipeSize((Integer) value);
            return true;
        }
        return super.setOption(option, value);
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(super.getOptions(),
                IoUringChannelOption.IO_URING_BUFFER_GROUP_ID,
                IoUringChannelOption.IO_URING_SENDFILE_PIPE_SIZE
        );
    }

    short getBufferGroupId() {
        return bufferGroupId;
    }

    IoUringStreamChannelConfig setBufferGroupId(short bufferGroupId) {
        this.bufferGroupId = (short) ObjectUtil.checkPositiveOrZero(bufferGroupId, "bufferGroupId");
        return this;
    }

    int getSendfilePipeSize() {
        return sendfilePipeSize;
    }

    IoUringStreamChannelConfig setSendfilePipeSize(int sendfilePipeSize) {
        this.sendfilePipeSize = ObjectUtil.checkPositiveOrZero(sendfilePipeSize, "sendfilePipeSize");
        return this;
    }
}
