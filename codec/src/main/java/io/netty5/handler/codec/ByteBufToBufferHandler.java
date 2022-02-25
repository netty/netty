/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.handler.codec;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.api.Buffer;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.util.concurrent.Future;

import static io.netty5.buffer.api.adaptor.ByteBufAdaptor.extractOrCopy;

/**
 * A {@link ChannelHandler} that converts {@link ByteBuf} read or written from/to a {@link ChannelPipeline} to a
 * {@link Buffer}.
 */
public final class ByteBufToBufferHandler extends ChannelHandlerAdapter {
    public static final ChannelHandler BYTEBUF_TO_BUFFER_HANDLER = new ByteBufToBufferHandler();

    private ByteBufToBufferHandler() {
        // singleton
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            msg = extractOrCopy(ctx.bufferAllocator(), (ByteBuf) msg);
        }

        ctx.fireChannelRead(msg);
    }

    @Override
    public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf)) {
            return ctx.write(msg);
        }
        return ctx.write(extractOrCopy(ctx.bufferAllocator(), (ByteBuf) msg));
    }

    @Override
    public boolean isSharable() {
        return true;
    }
}
