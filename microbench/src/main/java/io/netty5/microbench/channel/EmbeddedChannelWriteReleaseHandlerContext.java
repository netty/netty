/*
 * Copyright 2015 The Netty Project
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
package io.netty5.microbench.channel;

import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.util.ReferenceCounted;
import io.netty5.util.concurrent.Future;

public abstract class EmbeddedChannelWriteReleaseHandlerContext extends EmbeddedChannelHandlerContext {
    protected EmbeddedChannelWriteReleaseHandlerContext(ByteBufAllocator alloc, ChannelHandler handler) {
        this(alloc, handler, new EmbeddedChannel());
    }

    protected EmbeddedChannelWriteReleaseHandlerContext(ByteBufAllocator alloc, ChannelHandler handler,
            EmbeddedChannel channel) {
        super(alloc, handler, channel);
    }
    protected EmbeddedChannelWriteReleaseHandlerContext(BufferAllocator alloc, ChannelHandler handler) {
        this(alloc, handler, new EmbeddedChannel());
    }

    protected EmbeddedChannelWriteReleaseHandlerContext(BufferAllocator alloc, ChannelHandler handler,
            EmbeddedChannel channel) {
        super(alloc, handler, channel);
    }

    @Override
    protected abstract void handleException(Throwable t);

    @Override
    public final Future<Void> write(Object msg) {
        try {
            if (msg instanceof ReferenceCounted) {
                ((ReferenceCounted) msg).release();
                return channel().newSucceededFuture();
            }
            return channel().write(msg);
        } catch (Exception e) {
            handleException(e);
            return channel().newFailedFuture(e);
        }
    }

    @Override
    public final Future<Void> writeAndFlush(Object msg) {
        try {
            if (msg instanceof ReferenceCounted) {
                ((ReferenceCounted) msg).release();
                return channel().newSucceededFuture();
            }
            return channel().writeAndFlush(msg);
        } catch (Exception e) {
            handleException(e);
            return channel().newFailedFuture(e);
        }
    }
}
