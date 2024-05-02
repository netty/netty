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

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.unix.IntegerUnixChannelOption;
import io.netty.channel.unix.RawUnixChannelOption;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import static io.netty.channel.unix.Limits.SSIZE_MAX;

public class EpollChannelConfig extends DefaultChannelConfig {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(EpollChannelConfig.class);

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

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(super.getOptions(), EpollChannelOption.EPOLL_MODE);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == EpollChannelOption.EPOLL_MODE) {
            return (T) getEpollMode();
        }
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
        if (option == EpollChannelOption.EPOLL_MODE) {
            setEpollMode((EpollMode) value);
        } else {
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
        return true;
    }

    @Override
    public EpollChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        super.setConnectTimeoutMillis(connectTimeoutMillis);
        return this;
    }

    @Override
    @Deprecated
    public EpollChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
        super.setMaxMessagesPerRead(maxMessagesPerRead);
        return this;
    }

    @Override
    public EpollChannelConfig setWriteSpinCount(int writeSpinCount) {
        super.setWriteSpinCount(writeSpinCount);
        return this;
    }

    @Override
    public EpollChannelConfig setAllocator(ByteBufAllocator allocator) {
        super.setAllocator(allocator);
        return this;
    }

    @Override
    public EpollChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
        if (!(allocator.newHandle() instanceof RecvByteBufAllocator.ExtendedHandle)) {
            throw new IllegalArgumentException("allocator.newHandle() must return an object of type: " +
                    RecvByteBufAllocator.ExtendedHandle.class);
        }
        super.setRecvByteBufAllocator(allocator);
        return this;
    }

    @Override
    public EpollChannelConfig setAutoRead(boolean autoRead) {
        super.setAutoRead(autoRead);
        return this;
    }

    @Override
    @Deprecated
    public EpollChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
        super.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
        return this;
    }

    @Override
    @Deprecated
    public EpollChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
        super.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
        return this;
    }

    @Override
    public EpollChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
        super.setWriteBufferWaterMark(writeBufferWaterMark);
        return this;
    }

    @Override
    public EpollChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
        super.setMessageSizeEstimator(estimator);
        return this;
    }

    /**
     * Return the {@link EpollMode} used. Default is
     * {@link EpollMode#EDGE_TRIGGERED}. If you want to use {@link #isAutoRead()} {@code false} or
     * {@link #getMaxMessagesPerRead()} and have an accurate behaviour you should use
     * {@link EpollMode#LEVEL_TRIGGERED}.
     *
     * @deprecated Netty always uses level-triggered mode and so this method is just a no-op.
     */
    @Deprecated
    public EpollMode getEpollMode() {
        return EpollMode.LEVEL_TRIGGERED;
    }

    /**
     * Set the {@link EpollMode} used. Default is
     * {@link EpollMode#EDGE_TRIGGERED}. If you want to use {@link #isAutoRead()} {@code false} or
     * {@link #getMaxMessagesPerRead()} and have an accurate behaviour you should use
     * {@link EpollMode#LEVEL_TRIGGERED}.
     *
     * <strong>Be aware this config setting can only be adjusted before the channel was registered.</strong>
     *
     * @deprecated Netty always uses level-triggered mode and so this method is just a no-op.
     */
    @Deprecated
    public EpollChannelConfig setEpollMode(EpollMode mode) {
        LOGGER.debug("Changing the EpollMode is not supported anymore, this is just a no-op");
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
