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
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.util.ReferenceCountUtil;

import static io.netty.buffer.Unpooled.unreleasableBuffer;
import static io.netty.handler.codec.http2.Http2CodecUtil.connectionPrefaceBuf;

/**
 * This handler is designed for server-side use to automatically detect and differentiate
 * between HTTP/1.1 and HTTP/2.0 protocols in plaintext (non-TLS) connections
 * by leveraging the HTTP/2 connection preface.
 */
public class ClearTextHttpProtocolSniffChannelHandle extends ChannelInboundHandlerAdapter {
    private static final ByteBuf HTTP2_PREFACE_MAGIC_NUMBER =
            unreleasableBuffer(connectionPrefaceBuf()).asReadOnly();

    private final ChannelConfigure http1Configure;

    private final ChannelConfigure http2Configure;

    private CompositeByteBuf compositeByteBuf;

    public ClearTextHttpProtocolSniffChannelHandle(ChannelConfigure http1Configure, ChannelConfigure http2Configure) {
        this.http1Configure = http1Configure;
        this.http2Configure = http2Configure;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof ByteBuf)) {
            super.channelRead(ctx, msg);
            return;
        }
        ByteBuf request = (ByteBuf) msg;
        boolean first = compositeByteBuf == null;
        // The happy path
        // For most cases, the first ByteBuf that read from the socket
        // is significantly larger than the length of the HTTP/2 preface.
        int http2MagicNumberLength = HTTP2_PREFACE_MAGIC_NUMBER.readableBytes();
        if (first && request.readableBytes() >= http2MagicNumberLength) {
            boolean isHttp2 = ByteBufUtil.equals(HTTP2_PREFACE_MAGIC_NUMBER, request.slice(0, http2MagicNumberLength));
            determiningProtocol(isHttp2, ctx, request);
            return;
        }
        // Bad path
        if (first) {
            compositeByteBuf = ctx.alloc().compositeBuffer();
        }
        compositeByteBuf.addComponents(true, request);
        if (compositeByteBuf.readableBytes() >= http2MagicNumberLength) {
            boolean isHttp2 = ByteBufUtil.equals(
                    HTTP2_PREFACE_MAGIC_NUMBER,
                    compositeByteBuf.slice(0, http2MagicNumberLength)
            );
            CompositeByteBuf byteBufHolder = compositeByteBuf;
            compositeByteBuf = null;
            determiningProtocol(isHttp2, ctx, byteBufHolder);
        }
    }

    private void determiningProtocol(boolean isHttp2, ChannelHandlerContext ctx, ByteBuf firstRequest) {
        ChannelPipeline pipeline = ctx.pipeline();
        if (isHttp2) {
            http2Configure.config(ctx.channel());
        } else {
            http1Configure.config(ctx.channel());
        }
        ctx.fireChannelRead(firstRequest);
        pipeline.remove(this);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        CompositeByteBuf localBytebuf = compositeByteBuf;
        if (localBytebuf != null) {
            // The channel is now inactive, but the protocol type has not yet been determined.
            // So we need to release the buffer.
            ReferenceCountUtil.safeRelease(localBytebuf);
        }
    }
    public interface ChannelConfigure {
        void config(Channel channel);
    }
}
