/*
 * Copyright 2017 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.internal.UnstableApi;

import static io.netty.buffer.Unpooled.*;
import static io.netty.util.CharsetUtil.UTF_8;

/**
 * Provide how starting http2 protocol by h2c (Upgrade) or h2 (Prior Knowledge) by peaking the
 * first bytes is prior knowledge or not, see {@link #PRI_KNOWLEDGE_FIRST_BYTES}
 * The dispatcher should be the first {@link ChannelInboundHandler} in the
 * {@link io.netty.channel.ChannelPipeline}
 * The msg from {@link #channelRead(ChannelHandlerContext, Object)} must be {@link ByteBuf}
 */
@UnstableApi
public abstract class ClearTextHttp2ServerDispatcher extends ChannelInboundHandlerAdapter {
    private static final String PRI_KNOWLEDGE_MAGIC = "PRI";
    private static final int PRI_KNOWLEDGE_PEAK_LENGTH = PRI_KNOWLEDGE_MAGIC.length();
    private static final ByteBuf PRI_KNOWLEDGE_FIRST_BYTES = unreleasableBuffer(
            directBuffer(PRI_KNOWLEDGE_PEAK_LENGTH).writeBytes(PRI_KNOWLEDGE_MAGIC.getBytes(UTF_8)));

    /**
     * Peak the first n bytes of msg, and verify that if it matches to {@link #PRI_KNOWLEDGE_FIRST_BYTES}
     * then assume client want to start HTTP/2 connection with prior knowledge,
     * call {@link #configurePriorKnowledge(ChannelHandlerContext)} to configure pipeline
     * for handling HTTP/2 connection. If it didn't match, then call
     * {@link #configureUpgrade(ChannelHandlerContext)} for HTTP/2 upgrade
     *
     * @param ctx {@link ChannelHandlerContext} of this handler
     * @param msg the message from inbound
     * @throws IllegalStateException
     *     if {@code !(msg instanceof io.netty.ByteBuf}
     *     or if {@code msg.readableBytes() < PRI_KNOWLEDGE_PEAK_LENGTH}
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof ByteBuf)) {
            throw new IllegalStateException("Only can handle ByteBuf");
        }

        ByteBuf inbound = (ByteBuf) msg;
        if (inbound.readableBytes() < PRI_KNOWLEDGE_PEAK_LENGTH) {
            throw new IllegalStateException(
                    String.format("must have first %d bytes to determine the protocol",
                                  PRI_KNOWLEDGE_PEAK_LENGTH));
        }

        if (ByteBufUtil.equals(PRI_KNOWLEDGE_FIRST_BYTES, 0,
                               inbound, 0, PRI_KNOWLEDGE_PEAK_LENGTH)) {
            configurePriorKnowledge(ctx);
        } else {
            configureUpgrade(ctx);
        }

        ctx.fireChannelRead(msg);

        // If the ctx removed before {@code ctx.fireChannelRead(msg)}, then the
        // message will disappear because {@code ctx.next} would be the tail of
        // pipeline.
        ctx.pipeline().remove(this);
    }

    public abstract void configureUpgrade(ChannelHandlerContext ctx);

    public abstract void configurePriorKnowledge(ChannelHandlerContext ctx);
}
