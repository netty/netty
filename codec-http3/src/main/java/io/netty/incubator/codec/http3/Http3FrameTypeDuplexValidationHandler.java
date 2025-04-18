/*
 * Copyright 2021 The Netty Project
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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPromise;

import java.net.SocketAddress;

import static io.netty.incubator.codec.http3.Http3FrameValidationUtils.frameTypeUnexpected;
import static io.netty.incubator.codec.http3.Http3FrameValidationUtils.validateFrameWritten;

class Http3FrameTypeDuplexValidationHandler<T extends Http3Frame> extends Http3FrameTypeInboundValidationHandler<T>
        implements ChannelOutboundHandler {

    Http3FrameTypeDuplexValidationHandler(Class<T> frameType) {
        super(frameType);
    }

    @Override
    public final void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        T frame = validateFrameWritten(frameType, msg);
        if (frame != null) {
            write(ctx, frame, promise);
        } else {
            writeFrameDiscarded(msg, promise);
        }
    }

    void write(ChannelHandlerContext ctx, T msg, ChannelPromise promise) {
        ctx.write(msg, promise);
    }

    void writeFrameDiscarded(Object discardedFrame, ChannelPromise promise) {
        frameTypeUnexpected(promise, discardedFrame);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) {
        ctx.bind(localAddress, promise);
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
                        ChannelPromise promise) throws Exception {
        ctx.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) {
        ctx.disconnect(promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.close(promise);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.deregister(promise);
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        ctx.read();
    }
}
