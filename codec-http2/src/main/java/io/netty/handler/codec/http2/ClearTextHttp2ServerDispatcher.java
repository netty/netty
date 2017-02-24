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
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.AsciiString;
import io.netty.util.internal.UnstableApi;

import java.util.List;

import static io.netty.buffer.Unpooled.*;

/**
 * Provide how starting http2 protocol by h2c (Upgrade) or h2 (Prior Knowledge) by peaking the
 * first bytes is prior knowledge or not, see {@link #PRI_KNOWLEDGE_FIRST_BYTES}
 * The dispatcher should be the first {@link ChannelInboundHandler} in the
 * {@link io.netty.channel.ChannelPipeline}
 * The msg from {@link #channelRead(ChannelHandlerContext, Object)} must be {@link ByteBuf}
 */
@UnstableApi
public abstract class ClearTextHttp2ServerDispatcher extends ByteToMessageDecoder {
    private static final AsciiString PRI_KNOWLEDGE_MAGIC = AsciiString.of("PRI");
    private static final int PRI_KNOWLEDGE_PEAK_LENGTH = PRI_KNOWLEDGE_MAGIC.length();
    private static final ByteBuf PRI_KNOWLEDGE_FIRST_BYTES = unreleasableBuffer(
            directBuffer(PRI_KNOWLEDGE_PEAK_LENGTH).writeBytes(PRI_KNOWLEDGE_MAGIC.array()));

    /**
     * Peak the first {@link #PRI_KNOWLEDGE_PEAK_LENGTH} bytes of msg, and verify that
     * if it matches to {@link #PRI_KNOWLEDGE_FIRST_BYTES} then assume client want to
     * start HTTP/2 connection with prior knowledge, call {@link #configurePriorKnowledge(ChannelHandlerContext)}
     * to configure pipeline for handling HTTP/2 connection. If it didn't match, then
     * call {@link #configureUpgrade(ChannelHandlerContext)} for HTTP/2 upgrade
     *
     * @throws IllegalStateException
     *     if {@code !(msg instanceof io.netty.ByteBuf}
     *     or if {@code msg.readableBytes() < PRI_KNOWLEDGE_PEAK_LENGTH}
     */
    @Override
    public void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < PRI_KNOWLEDGE_PEAK_LENGTH) {
            return;
        }

        if (ByteBufUtil.equals(PRI_KNOWLEDGE_FIRST_BYTES, 0,
                               in, 0, PRI_KNOWLEDGE_PEAK_LENGTH)) {
            configurePriorKnowledge(ctx);
        } else {
            configureUpgrade(ctx);
        }

        out.add(in.readBytes(in.readableBytes()));

        ctx.pipeline().remove(this);
    }

    /**
     * Invoked when dispatcher knows that the client want to start HTTP/2 by upgrade.
     * Implement this method to configure your pipeline for handling HTTP -> HTTP/2 upgrade.
     */
    protected abstract void configureUpgrade(ChannelHandlerContext ctx);

    /**
     * Invoked when dispatcher knows that the client want to start HTTP/2 by sending prior knowledge.
     * Implement this method to configure your pipeline for HTTP/2 protocol.
     */
    protected abstract void configurePriorKnowledge(ChannelHandlerContext ctx);
}
