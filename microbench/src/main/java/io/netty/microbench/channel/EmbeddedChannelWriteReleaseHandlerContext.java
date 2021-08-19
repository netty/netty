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
package io.netty.microbench.channel;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

public abstract class EmbeddedChannelWriteReleaseHandlerContext extends EmbeddedChannelHandlerContext {
    protected EmbeddedChannelWriteReleaseHandlerContext(ByteBufAllocator alloc, ChannelHandler handler) {
        this(alloc, handler, new EmbeddedChannel());
    }

    protected EmbeddedChannelWriteReleaseHandlerContext(ByteBufAllocator alloc, ChannelHandler handler,
            EmbeddedChannel channel) {
        super(alloc, handler, channel);
    }

    @Override
    protected abstract void handleException(Throwable t);

    @Override
    public final Future<Void> write(Object msg) {
        Promise<Void> promise = newPromise();
        write(msg, promise);
        return promise;
    }

    @Override
    public final ChannelHandlerContext write(Object msg, Promise<Void> promise) {
        try {
            if (msg instanceof ReferenceCounted) {
                ((ReferenceCounted) msg).release();
                promise.setSuccess(null);
            } else {
                channel().write(msg, promise);
            }
        } catch (Exception e) {
            promise.setFailure(e);
            handleException(e);
        }
        return this;
    }

    @Override
    public final ChannelHandlerContext writeAndFlush(Object msg, Promise<Void> promise) {
        try {
            if (msg instanceof ReferenceCounted) {
                ((ReferenceCounted) msg).release();
                promise.setSuccess(null);
            } else {
                channel().writeAndFlush(msg, promise);
            }
        } catch (Exception e) {
            promise.setFailure(e);
            handleException(e);
        }
        return this;
    }

    @Override
    public final Future<Void> writeAndFlush(Object msg) {
        Promise<Void> promise = newPromise();
        writeAndFlush(msg, newPromise());
        return promise;
    }
}
