/*
 * Copyright 2020 The Netty Project
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
package io.netty5.channel.epoll;

import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.MessageSizeEstimator;
import io.netty5.channel.RecvBufferAllocator;
import io.netty5.channel.WriteBufferWaterMark;
import io.netty5.channel.socket.DuplexChannelConfig;

import java.util.Map;

import static io.netty5.channel.ChannelOption.*;

public class EpollDuplexChannelConfig extends EpollChannelConfig implements DuplexChannelConfig {
    private volatile boolean allowHalfClosure;

    EpollDuplexChannelConfig(AbstractEpollChannel channel) {
        super(channel);
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(super.getOptions(), ALLOW_HALF_CLOSURE);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == ALLOW_HALF_CLOSURE) {
            return (T) Boolean.valueOf(isAllowHalfClosure());
        }
        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        validate(option, value);
        if (option == ALLOW_HALF_CLOSURE) {
            setAllowHalfClosure((Boolean) value);
        } else {
            return super.setOption(option, value);
        }
        return true;
    }

    @Override
    public boolean isAllowHalfClosure() {
        return allowHalfClosure;
    }

    @Override
    public EpollDuplexChannelConfig setAllowHalfClosure(boolean allowHalfClosure) {
        this.allowHalfClosure = allowHalfClosure;
        return this;
    }

    @Override
    public EpollDuplexChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        super.setConnectTimeoutMillis(connectTimeoutMillis);
        return this;
    }

    @SuppressWarnings("deprecation")
    @Override
    public EpollDuplexChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
        super.setMaxMessagesPerRead(maxMessagesPerRead);
        return this;
    }

    @Override
    public EpollDuplexChannelConfig setWriteSpinCount(int writeSpinCount) {
        super.setWriteSpinCount(writeSpinCount);
        return this;
    }

    @Override
    public EpollDuplexChannelConfig setAllocator(ByteBufAllocator allocator) {
        super.setAllocator(allocator);
        return this;
    }

    @Override
    public EpollDuplexChannelConfig setBufferAllocator(BufferAllocator bufferAllocator) {
        super.setBufferAllocator(bufferAllocator);
        return this;
    }

    @Override
    public EpollDuplexChannelConfig setRecvBufferAllocator(RecvBufferAllocator allocator) {
        super.setRecvBufferAllocator(allocator);
        return this;
    }

    @Override
    public EpollDuplexChannelConfig setAutoRead(boolean autoRead) {
        super.setAutoRead(autoRead);
        return this;
    }

    @SuppressWarnings("deprecation")
    @Override
    public EpollDuplexChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
        super.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
        return this;
    }

    @SuppressWarnings("deprecation")
    @Override
    public EpollDuplexChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
        super.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
        return this;
    }

    @Override
    public EpollDuplexChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
        super.setWriteBufferWaterMark(writeBufferWaterMark);
        return this;
    }

    @Override
    public EpollDuplexChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
        super.setMessageSizeEstimator(estimator);
        return this;
    }

    @Override
    public EpollDuplexChannelConfig setAutoClose(boolean autoClose) {
        super.setAutoClose(autoClose);
        return this;
    }
}
