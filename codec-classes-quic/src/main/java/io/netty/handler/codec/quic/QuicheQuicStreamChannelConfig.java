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
package io.netty.handler.codec.quic;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;

import java.util.Map;

final class QuicheQuicStreamChannelConfig extends DefaultChannelConfig implements QuicStreamChannelConfig {
    // We should use half-closure sementatics by default as this is what QUIC does by default.
    // If you receive a FIN you should still keep the stream open until you write a FIN as well.
    private volatile boolean allowHalfClosure = true;
    private volatile boolean readFrames;
    volatile DirectIoByteBufAllocator allocator;

    QuicheQuicStreamChannelConfig(QuicStreamChannel channel) {
        super(channel);
        allocator = new DirectIoByteBufAllocator(super.getAllocator());
    }

    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        if (isHalfClosureSupported()) {
            return getOptions(super.getOptions(), ChannelOption.ALLOW_HALF_CLOSURE, QuicChannelOption.READ_FRAMES);
        }
        return super.getOptions();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOption(ChannelOption<T> option) {
        if (option == ChannelOption.ALLOW_HALF_CLOSURE) {
            return (T) Boolean.valueOf(isAllowHalfClosure());
        }
        if (option == QuicChannelOption.READ_FRAMES) {
            return (T) Boolean.valueOf(isReadFrames());
        }
        return super.getOption(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        validate(option, value);

        if (option == ChannelOption.ALLOW_HALF_CLOSURE) {
            if (isHalfClosureSupported()) {
                setAllowHalfClosure((Boolean) value);
                return true;
            }
            return false;
        }
        if (option == QuicChannelOption.READ_FRAMES) {
            setReadFrames((Boolean) value);
        }
        return super.setOption(option, value);
    }

    @Override
    public QuicStreamChannelConfig setReadFrames(boolean readFrames) {
        this.readFrames = readFrames;
        return this;
    }

    @Override
    public boolean isReadFrames() {
        return readFrames;
    }

    @Override
    public QuicStreamChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        super.setConnectTimeoutMillis(connectTimeoutMillis);
        return this;
    }

    @Override
    public QuicStreamChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
        super.setMaxMessagesPerRead(maxMessagesPerRead);
        return this;
    }

    @Override
    public QuicStreamChannelConfig setWriteSpinCount(int writeSpinCount) {
        super.setWriteSpinCount(writeSpinCount);
        return this;
    }

    @Override
    public QuicStreamChannelConfig setAllocator(ByteBufAllocator allocator) {
        this.allocator = new DirectIoByteBufAllocator(allocator);
        return this;
    }

    @Override
    public QuicStreamChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
        super.setRecvByteBufAllocator(allocator);
        return this;
    }

    @Override
    public QuicStreamChannelConfig setAutoRead(boolean autoRead) {
        super.setAutoRead(autoRead);
        return this;
    }

    @Override
    public QuicStreamChannelConfig setAutoClose(boolean autoClose) {
        super.setAutoClose(autoClose);
        return this;
    }

    @Override
    public QuicStreamChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
        super.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
        return this;
    }

    @Override
    public QuicStreamChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
        super.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
        return this;
    }

    @Override
    public QuicStreamChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
        super.setWriteBufferWaterMark(writeBufferWaterMark);
        return this;
    }

    @Override
    public QuicStreamChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
        super.setMessageSizeEstimator(estimator);
        return this;
    }

    @Override
    public QuicStreamChannelConfig setAllowHalfClosure(boolean allowHalfClosure) {
        if (!isHalfClosureSupported()) {
            throw new UnsupportedOperationException("Undirectional streams don't support half-closure");
        }
        this.allowHalfClosure = allowHalfClosure;
        return this;
    }

    @Override
    public ByteBufAllocator getAllocator() {
        return allocator.wrapped();
    }

    @Override
    public boolean isAllowHalfClosure() {
        return allowHalfClosure;
    }

    private boolean isHalfClosureSupported() {
        return ((QuicStreamChannel) channel).type() == QuicStreamType.BIDIRECTIONAL;
    }
}
