/*
 * Copyright 2012 The Netty Project
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
package io.netty.example.socksproxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socks.SocksCmdRequest;
import io.netty.handler.codec.socks.SocksCmdResponse;
import io.netty.handler.codec.socks.SocksCmdStatus;

@ChannelHandler.Sharable
public final class SocksServerConnectHandler extends SimpleChannelInboundHandler<SocksCmdRequest> {
    private static final String name = "SOCKS_SERVER_CONNECT_HANDLER";

    public static String getName() {
        return name;
    }

    private final Bootstrap b = new Bootstrap();

    @Override
    public void messageReceived0(final ChannelHandlerContext ctx, final SocksCmdRequest request) throws Exception {
        CallbackNotifier cb = new CallbackNotifier() {
            @Override
            public void onSuccess(final ChannelHandlerContext outboundCtx) {
                ctx.channel().write(new SocksCmdResponse(SocksCmdStatus.SUCCESS, request.addressType())).flush()
                             .addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture channelFuture) throws Exception {
                        ctx.pipeline().remove(getName());
                        outboundCtx.channel().pipeline().addLast(new RelayHandler(ctx.channel()));
                        ctx.channel().pipeline().addLast(new RelayHandler(outboundCtx.channel()));
                    }
                });
            }

            @Override
            public void onFailure(ChannelHandlerContext outboundCtx, Throwable cause) {
                ctx.channel().write(new SocksCmdResponse(SocksCmdStatus.FAILURE, request.addressType()));
                SocksServerUtils.closeOnFlush(ctx.channel());
            }
        };

        final Channel inboundChannel = ctx.channel();
        b.group(inboundChannel.eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new DirectClientInitializer(cb));

        b.connect(request.host(), request.port());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        SocksServerUtils.closeOnFlush(ctx.channel());
    }
}
