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
package io.netty.handler.codec.http3;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelShutdownType;
import io.netty.util.concurrent.CompletionHandler;
import io.netty.util.concurrent.Promise;

import java.net.SocketAddress;

import static io.netty.handler.codec.http3.Http3FrameValidationUtils.frameTypeUnexpected;
import static io.netty.handler.codec.http3.Http3FrameValidationUtils.validateFrameWritten;

class Http3FrameTypeDuplexValidationHandler<T extends Http3Frame> extends Http3FrameTypeInboundValidationHandler<T>
        implements ChannelOutboundHandler {

    Http3FrameTypeDuplexValidationHandler(Class<T> frameType) {
        super(frameType);
    }

    @Override
    public final void write(ChannelHandlerContext ctx, Object msg, CompletionHandler<Void> handler) {
        T frame = validateFrameWritten(frameType, msg);
        if (frame != null) {
            write(ctx, frame, handler);
        } else {
            writeFrameDiscarded(msg, handler);
        }
    }

    void write(ChannelHandlerContext ctx, T msg, CompletionHandler<Void> handler) {
        ctx.write(msg, handler);
    }

    void writeFrameDiscarded(Object discardedFrame, CompletionHandler<Void> handler) {
        frameTypeUnexpected(handler, discardedFrame);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void register(ChannelHandlerContext ctx, CompletionHandler<Void> handler) {
        ctx.register(handler);
    }

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, CompletionHandler<Void> handler) {
        ctx.bind(localAddress, handler);
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
                        CompletionHandler<Void> handler) {
        ctx.connect(remoteAddress, localAddress, handler);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, CompletionHandler<Void> handler) {
        ctx.disconnect(handler);
    }

    @Override
    public void close(ChannelHandlerContext ctx, CompletionHandler<Void> handler) {
        ctx.close(handler);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, CompletionHandler<Void> handler) {
        ctx.deregister(handler);
    }

    @Override
    public void read(ChannelHandlerContext ctx) {
        ctx.read();
    }

    @Override
    public void shutdown(ChannelHandlerContext ctx, ChannelShutdownType type, CompletionHandler<Void> handler) {
        ctx.shutdown(type, handler);
    }
}
