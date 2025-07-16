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

    static final int DISABLE_WRITE_ZERO_COPY = -1;

    private volatile short bufferGroupId = -1;

    private volatile int writeZeroCopyThreshold = DISABLE_WRITE_ZERO_COPY;

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

        if (option == IoUringChannelOption.IO_URING_WRITE_ZERO_COPY_THRESHOLD) {
            return (T) Integer.valueOf(getWriteZeroCopyThreshold());
        }

        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        if (option == IoUringChannelOption.IO_URING_BUFFER_GROUP_ID) {
            setBufferGroupId((Short) value);
            return true;
        }

        if (option == IoUringChannelOption.IO_URING_WRITE_ZERO_COPY_THRESHOLD) {
            setWriteZeroCopyThreshold((Integer) value);
            return true;
        }

        return super.setOption(option, value);
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(super.getOptions(),
                IoUringChannelOption.IO_URING_BUFFER_GROUP_ID,
                IoUringChannelOption.IO_URING_WRITE_ZERO_COPY_THRESHOLD
        );
    }

    short getBufferGroupId() {
        return bufferGroupId;
    }

    private int getWriteZeroCopyThreshold() {
        return writeZeroCopyThreshold;
    }

    IoUringStreamChannelConfig setBufferGroupId(short bufferGroupId) {
        this.bufferGroupId = (short) ObjectUtil.checkPositiveOrZero(bufferGroupId, "bufferGroupId");
        return this;
    }

    IoUringStreamChannelConfig setWriteZeroCopyThreshold(int setWriteZeroCopyThreshold) {
        if (setWriteZeroCopyThreshold == DISABLE_WRITE_ZERO_COPY) {
            this.writeZeroCopyThreshold = DISABLE_WRITE_ZERO_COPY;
        } else {
            this.writeZeroCopyThreshold =
                    ObjectUtil.checkPositiveOrZero(setWriteZeroCopyThreshold, "setWriteZeroCopyThreshold");
        }
        return this;
    }

    boolean shouldWriteZeroCopy(int amount) {
        // This can reduce one read operation on a volatile field.
        int threshold = this.getWriteZeroCopyThreshold();
        return threshold != DISABLE_WRITE_ZERO_COPY && amount >= threshold;
    }
}
