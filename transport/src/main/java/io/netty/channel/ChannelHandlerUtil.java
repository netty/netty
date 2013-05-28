/*
 * Copyright 2013 The Netty Project
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
 * Utility methods for use within your {@link ChannelHandler} implementation.
 */
public final class ChannelHandlerUtil {

    /**
     * Allocate a {@link ByteBuf} taking the {@link ChannelConfig#getDefaultHandlerByteBufType()}
     * setting into account.
     */
    public static ByteBuf allocate(ChannelHandlerContext ctx) {
        switch(ctx.channel().config().getDefaultHandlerByteBufType()) {
            case DIRECT:
                return ctx.alloc().directBuffer();
            case PREFER_DIRECT:
                return ctx.alloc().ioBuffer();
            case HEAP:
                return ctx.alloc().heapBuffer();
            default:
                throw new IllegalStateException();
        }
    }

    /**
     * Allocate a {@link ByteBuf} taking the {@link ChannelConfig#getDefaultHandlerByteBufType()}
     * setting into account.
     */
    public static ByteBuf allocate(ChannelHandlerContext ctx, int initialCapacity) {
        switch(ctx.channel().config().getDefaultHandlerByteBufType()) {
            case DIRECT:
                return ctx.alloc().directBuffer(initialCapacity);
            case PREFER_DIRECT:
                return ctx.alloc().ioBuffer(initialCapacity);
            case HEAP:
                return ctx.alloc().heapBuffer(initialCapacity);
            default:
                throw new IllegalStateException();
        }
    }

    /**
     * Allocate a {@link ByteBuf} taking the {@link ChannelConfig#getDefaultHandlerByteBufType()}
     * setting into account.
     */
    public static ByteBuf allocate(ChannelHandlerContext ctx, int initialCapacity, int maxCapacity) {
        switch(ctx.channel().config().getDefaultHandlerByteBufType()) {
            case DIRECT:
                return ctx.alloc().directBuffer(initialCapacity, maxCapacity);
            case PREFER_DIRECT:
                return ctx.alloc().ioBuffer(initialCapacity, maxCapacity);
            case HEAP:
                return ctx.alloc().heapBuffer(initialCapacity, maxCapacity);
            default:
                throw new IllegalStateException();
        }
    }

    private ChannelHandlerUtil() { }
}
