/*
 * Copyright 2020 The Netty Project
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
package io.netty.incubator.codec.http3;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.util.ReferenceCountUtil;

class Http3FrameTypeValidationHandler<T extends Http3Frame> extends ChannelDuplexHandler {

    private final Class<T> frameType;

    Http3FrameTypeValidationHandler(Class<T> frameType) {
        this.frameType = frameType;
    }

    @SuppressWarnings("unchecked")
    private T cast(Object msg) {
        return (T) msg;
    }

    @Override
    public final void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (frameType.isInstance(msg)) {
            write(ctx, cast(msg), promise);
        } else {
            ReferenceCountUtil.release(msg);
            // TODO: Handle me with the right error.
            promise.setFailure(new UnsupportedMessageTypeException());
        }
    }

    public void write(ChannelHandlerContext ctx, T msg, ChannelPromise promise) throws Exception {
        ctx.write(msg, promise);
    }

    @Override
    public final void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (frameType.isInstance(msg)) {
            channelRead(ctx, cast(msg));
        } else {
            ReferenceCountUtil.release(msg);
            // TODO: Handle me with the right error.
            throw new UnsupportedMessageTypeException();
        }
    }

    public void channelRead(ChannelHandlerContext ctx, T frame) throws Exception {
        ctx.fireChannelRead(frame);
    }
}
