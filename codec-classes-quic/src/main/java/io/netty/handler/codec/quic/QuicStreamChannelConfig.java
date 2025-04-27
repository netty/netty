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
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.DuplexChannelConfig;

/**
 * {@link DuplexChannelConfig} for QUIC streams.
 */
public interface QuicStreamChannelConfig extends DuplexChannelConfig {
    /**
     * Set this to {@code true} if the {@link QuicStreamChannel} should read {@link QuicStreamFrame}s and fire these
     * through the {@link io.netty.channel.ChannelPipeline}, {@code false} if it uses {@link io.netty.buffer.ByteBuf}.
     *
     * @param readFrames    {@code true} if {@link QuicStreamFrame}s should be used, {@code false} if
     *                      {@link io.netty.buffer.ByteBuf} should be used.
     * @return              this instance itself.
     *
     */
    QuicStreamChannelConfig setReadFrames(boolean readFrames);

    /**
     * Returns {@code true} if the {@link QuicStreamChannel} will read {@link QuicStreamFrame}s and fire these through
     * the {@link io.netty.channel.ChannelPipeline}, {@code false} if it uses {@link io.netty.buffer.ByteBuf}.
     *
     * @return  {@code true} if {@link QuicStreamFrame}s should be used, {@code false} if
     *          {@link io.netty.buffer.ByteBuf} should be used.
     */
    boolean isReadFrames();

    @Override
    QuicStreamChannelConfig setAllowHalfClosure(boolean allowHalfClosure);

    @Override
    QuicStreamChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead);

    @Override
    QuicStreamChannelConfig setWriteSpinCount(int writeSpinCount);

    @Override
    QuicStreamChannelConfig setAllocator(ByteBufAllocator allocator);

    @Override
    QuicStreamChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator);

    @Override
    QuicStreamChannelConfig setAutoRead(boolean autoRead);

    @Override
    QuicStreamChannelConfig setAutoClose(boolean autoClose);

    @Override
    QuicStreamChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator);

    @Override
    QuicStreamChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark);

    @Override
    QuicStreamChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis);

    @Override
    QuicStreamChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark);

    @Override
    QuicStreamChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark);
}
