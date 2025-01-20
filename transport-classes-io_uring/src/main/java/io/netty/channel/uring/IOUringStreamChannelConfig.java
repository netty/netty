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

import java.util.Map;

abstract class IOUringStreamChannelConfig extends IOUringChannelConfig {

    private static final short DISABLE_BUFFER_SELECT_READ = -1;

    private volatile short bufferGroupId;

    IOUringStreamChannelConfig(Channel channel) {
        super(channel);
    }

    IOUringStreamChannelConfig(Channel channel, RecvByteBufAllocator allocator) {
        super(channel, allocator);
    }

    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == IoUringChannelOption.IO_URING_BUFFER_GROUP_ID) {
            return (T) Short.valueOf(getBufferRingConfig());
        }
        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        if (option == IoUringChannelOption.IO_URING_BUFFER_GROUP_ID) {
            setBufferGroupId((Short) value);
        }
        return super.setOption(option, value);
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(super.getOptions(), IoUringChannelOption.IO_URING_BUFFER_GROUP_ID);
    }

    /**
     * Returns {@code true}
     * if <a href="https://man7.org/linux/man-pages/man3/io_uring_setup_buf_ring.3.html">Provider Buffer Read</a>
     * is enabled, {@code false}
     * otherwise.
     */
    boolean isEnableBufferSelectRead() {
        return bufferGroupId != DISABLE_BUFFER_SELECT_READ;
    }

    /**
     * enable provider buffer, See this <a href="https://lwn.net/Articles/815491/">LWN article</a> for more info
     */
    public IOUringStreamChannelConfig disableBufferSelectRead() {
        setBufferGroupId(DISABLE_BUFFER_SELECT_READ);
        return this;
    }

    /**
     * Returns the buffer ring config.
     *
     * @return the buffer ring config.
     */
    public short getBufferRingConfig() {
        return bufferGroupId;
    }

    /**
     * Set the buffer ring config.
     *
     * @param bufferGroupId the buffer group id.
     * @return
     */
    public IOUringStreamChannelConfig setBufferGroupId(short bufferGroupId) {
        this.bufferGroupId = bufferGroupId;
        return this;
    }
}
