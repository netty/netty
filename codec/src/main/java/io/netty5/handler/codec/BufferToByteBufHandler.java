/*
 * Copyright 2022 The Netty Project
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

import static io.netty5.buffer.api.adaptor.ByteBufAdaptor.intoByteBuf;

/**
 * A {@link ChannelHandler} that converts {@link ByteBuf} read or written from/to a {@link ChannelPipeline} to a
 * {@link Buffer}.
 */
public final class BufferToByteBufHandler extends ChannelHandlerAdapter {
    public static final ChannelHandler BUFFER_TO_BYTEBUF_HANDLER = new BufferToByteBufHandler();

    private BufferToByteBufHandler() {
        // singleton
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Buffer) {
            msg = intoByteBuf((Buffer) msg);
        }

        ctx.fireChannelRead(msg);
    }

    @Override
    public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof Buffer)) {
            return ctx.write(msg);
        }
        return ctx.write(intoByteBuf((Buffer) msg));
    }

    @Override
    public boolean isSharable() {
        return true;
    }
}
