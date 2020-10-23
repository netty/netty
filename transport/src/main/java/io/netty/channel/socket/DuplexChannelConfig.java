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
package io.netty.channel.socket;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;

/**
 * A {@link ChannelConfig} for a {@link DuplexChannel}.
 *
 * <h3>Available options</h3>
 *
 * In addition to the options provided by {@link ChannelConfig},
 * {@link DuplexChannelConfig} allows the following options in the option map:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <td>{@link ChannelOption#ALLOW_HALF_CLOSURE}</td><td>{@link #setAllowHalfClosure(boolean)}</td>
 * </tr>
 * </table>
 */
public interface DuplexChannelConfig extends ChannelConfig {

    /**
     * Returns {@code true} if and only if the channel should not close itself when its remote
     * peer shuts down output to make the connection half-closed.  If {@code false}, the connection
     * is closed automatically when the remote peer shuts down output.
     */
    boolean isAllowHalfClosure();

    /**
     * Sets whether the channel should not close itself when its remote peer shuts down output to
     * make the connection half-closed.  If {@code true} the connection is not closed when the
     * remote peer shuts down output. Instead,
     * {@link ChannelInboundHandler#userEventTriggered(ChannelHandlerContext, Object)}
     * is invoked with a {@link ChannelInputShutdownEvent} object. If {@code false}, the connection
     * is closed automatically.
     */
    DuplexChannelConfig setAllowHalfClosure(boolean allowHalfClosure);

    @Override
    @Deprecated
    DuplexChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead);

    @Override
    DuplexChannelConfig setWriteSpinCount(int writeSpinCount);

    @Override
    DuplexChannelConfig setAllocator(ByteBufAllocator allocator);

    @Override
    DuplexChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator);

    @Override
    DuplexChannelConfig setAutoRead(boolean autoRead);

    @Override
    DuplexChannelConfig setAutoClose(boolean autoClose);

    @Override
    DuplexChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator);

    @Override
    DuplexChannelConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark);
}
