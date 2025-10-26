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

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.unix.IntegerUnixChannelOption;
import io.netty.channel.unix.RawUnixChannelOption;

import java.io.IOException;
import java.nio.ByteBuffer;

abstract class IoUringChannelConfig extends DefaultChannelConfig {
    IoUringChannelConfig(AbstractIoUringChannel channel) {
        super(channel);
    }

    IoUringChannelConfig(AbstractIoUringChannel channel, RecvByteBufAllocator allocator) {
        super(channel, allocator);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(ChannelOption<T> option) {
        try {
            if (option instanceof IntegerUnixChannelOption) {
                IntegerUnixChannelOption opt = (IntegerUnixChannelOption) option;
                return (T) Integer.valueOf(((AbstractIoUringChannel) channel).socket.getIntOpt(
                        opt.level(), opt.optname()));
            }
            if (option instanceof RawUnixChannelOption) {
                RawUnixChannelOption opt = (RawUnixChannelOption) option;
                ByteBuffer out = ByteBuffer.allocate(opt.length());
                ((AbstractIoUringChannel) channel).socket.getRawOpt(opt.level(), opt.optname(), out);
                return (T) out.flip();
            }
        } catch (IOException e) {
            throw new ChannelException(e);
        }
        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        validate(option, value);
        try {
            if (option instanceof IntegerUnixChannelOption) {
                IntegerUnixChannelOption opt = (IntegerUnixChannelOption) option;
                ((AbstractIoUringChannel) channel).socket.setIntOpt(opt.level(), opt.optname(), (Integer) value);
                return true;
            } else if (option instanceof RawUnixChannelOption) {
                RawUnixChannelOption opt = (RawUnixChannelOption) option;
                ((AbstractIoUringChannel) channel).socket.setRawOpt(opt.level(), opt.optname(), (ByteBuffer) value);
                return true;
            }
        } catch (IOException e) {
            throw new ChannelException(e);
        }
        return super.setOption(option, value);
    }

    @Override
    public IoUringChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
        super.setMaxMessagesPerRead(maxMessagesPerRead);
        return this;
    }

    @Override
    public IoUringChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        super.setConnectTimeoutMillis(connectTimeoutMillis);
        return this;
    }

    @Override
    public IoUringChannelConfig setMaxMessagesPerWrite(int maxMessagesPerWrite) {
        super.setMaxMessagesPerWrite(maxMessagesPerWrite);
        return this;
    }

    @Override
    public IoUringChannelConfig setWriteSpinCount(int writeSpinCount) {
        super.setWriteSpinCount(writeSpinCount);
        return this;
    }

    @Override
    public IoUringChannelConfig setAllocator(ByteBufAllocator allocator) {
        super.setAllocator(allocator);
        return this;
    }

    @Override
    public IoUringChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
        super.setRecvByteBufAllocator(allocator);
        return this;
    }

    @Override
    public IoUringChannelConfig setAutoRead(boolean autoRead) {
        super.setAutoRead(autoRead);
        return this;
    }

    @Override
    public IoUringChannelConfig setAutoClose(boolean autoClose) {
        super.setAutoClose(autoClose);
        return this;
    }

    @Override
    public IoUringChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
        super.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
        return this;
    }

    @Override
    public IoUringChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
        super.setMessageSizeEstimator(estimator);
        return this;
    }

    @Override
    public IoUringChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
        super.setWriteBufferWaterMark(writeBufferWaterMark);
        return this;
    }

    @Override
    public IoUringChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
        super.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
        return this;
    }

    @Override
    protected void autoReadCleared() {
        ((AbstractIoUringChannel) channel).autoReadCleared();
    }
}
