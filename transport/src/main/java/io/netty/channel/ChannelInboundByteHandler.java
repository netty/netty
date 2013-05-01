/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import io.netty.buffer.ByteBuf;

/**
 * {@link ChannelInboundHandler} which offers a {@link ByteBuf} to store inbound data in.
 *
 */
public interface ChannelInboundByteHandler extends ChannelInboundHandler {
    /**
     * {@inheritDoc}
     * <p>
     * An implementation should respect the {@link ChannelConfig#getDefaultHandlerByteBufType()} setting unless
     * there's a good reason to ignore it.  If in doubt, use {@link ChannelHandlerUtil#allocate(ChannelHandlerContext)}.
     * </p>
     */
    @Override
    ByteBuf newInboundBuffer(ChannelHandlerContext ctx) throws Exception;

    /**
     * Discards the read bytes of the inbound buffer and optionally trims its unused portion to reduce memory
     * consumption. The most common implementation of this method will look like the following:
     * <pre>
     *     ctx.inboundByteBuffer().discardSomeReadBytes();
     * </pre>
     */
    void discardInboundReadBytes(ChannelHandlerContext ctx) throws Exception;
}
