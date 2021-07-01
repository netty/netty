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
package io.netty.handler.codec.h2new;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.api.Buffer;
import io.netty.buffer.api.BufferAllocator;
import io.netty.buffer.api.adaptor.ByteBufAdaptor;
import io.netty.buffer.api.adaptor.ByteBufAllocatorAdaptor;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

/**
 * A {@link ChannelHandler} that converts {@link ByteBuf} to {@link Buffer} when written.
 */
final class EnsureBufferOutbound extends ChannelHandlerAdapter {
    private BufferAllocator allocator;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        final ByteBufAllocator alloc = ctx.alloc();
        if (alloc instanceof ByteBufAllocatorAdaptor) {
            allocator = ((ByteBufAllocatorAdaptor) alloc).getOnHeap();
        } else {
            allocator = BufferAllocator.onHeapPooled();
        }
    }

    @Override
    public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ByteBuf) {
            return ctx.write(ByteBufAdaptor.extractOrCopy(allocator, (ByteBuf) msg));
        } else {
            return ctx.write(msg);
        }
    }
}
