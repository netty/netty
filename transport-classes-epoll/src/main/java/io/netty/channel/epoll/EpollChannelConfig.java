/*
 * Copyright 2015 The Netty Project
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

import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.unix.IntegerUnixChannelOption;
import io.netty.channel.unix.RawUnixChannelOption;

import java.io.IOException;
import java.nio.ByteBuffer;

import static io.netty.channel.unix.Limits.SSIZE_MAX;

public class EpollChannelConfig extends DefaultChannelConfig {

    private volatile long maxBytesPerGatheringWrite = SSIZE_MAX;

    protected EpollChannelConfig(Channel channel) {
        super(checkAbstractEpollChannel(channel));
    }

    protected EpollChannelConfig(Channel channel, RecvByteBufAllocator recvByteBufAllocator) {
        super(checkAbstractEpollChannel(channel), recvByteBufAllocator);
    }

    protected LinuxSocket socket() {
        return ((AbstractEpollChannel) channel).socket;
    }

    private static Channel checkAbstractEpollChannel(Channel channel) {
        if (!(channel instanceof AbstractEpollChannel)) {
            throw new IllegalArgumentException("channel is not AbstractEpollChannel: " + channel.getClass());
        }
        return channel;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(ChannelOption<T> option) {
        try {
            if (option instanceof IntegerUnixChannelOption) {
                IntegerUnixChannelOption opt = (IntegerUnixChannelOption) option;
                return (T) Integer.valueOf(((AbstractEpollChannel) channel).socket.getIntOpt(
                        opt.level(), opt.optname()));
            }
            if (option instanceof RawUnixChannelOption) {
                RawUnixChannelOption opt = (RawUnixChannelOption) option;
                ByteBuffer out = ByteBuffer.allocate(opt.length());
                ((AbstractEpollChannel) channel).socket.getRawOpt(opt.level(), opt.optname(), out);
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
                ((AbstractEpollChannel) channel).socket.setIntOpt(opt.level(), opt.optname(), (Integer) value);
                return true;
            } else if (option instanceof RawUnixChannelOption) {
                RawUnixChannelOption opt = (RawUnixChannelOption) option;
                ((AbstractEpollChannel) channel).socket.setRawOpt(opt.level(), opt.optname(), (ByteBuffer) value);
                return true;
            }
        } catch (IOException e) {
            throw new ChannelException(e);
        }
        return super.setOption(option, value);
    }

    @Override
    public ChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
        if (!(allocator.newHandle() instanceof RecvByteBufAllocator.ExtendedHandle)) {
            throw new IllegalArgumentException("allocator.newHandle() must return an object of type: " +
                    RecvByteBufAllocator.ExtendedHandle.class);
        }
        super.setRecvByteBufAllocator(allocator);
        return this;
    }

    private void checkChannelNotRegistered() {
        if (channel.isRegistered()) {
            throw new IllegalStateException("EpollMode can only be changed before channel is registered");
        }
    }

    @Override
    protected final void autoReadCleared() {
        ((AbstractEpollChannel) channel).clearEpollIn();
    }

    protected final void setMaxBytesPerGatheringWrite(long maxBytesPerGatheringWrite) {
        this.maxBytesPerGatheringWrite = maxBytesPerGatheringWrite;
    }

    protected final long getMaxBytesPerGatheringWrite() {
        return maxBytesPerGatheringWrite;
    }
}
