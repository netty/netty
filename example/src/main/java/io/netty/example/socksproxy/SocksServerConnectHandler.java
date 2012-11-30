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
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.codec.socks.SocksCmdRequest;
import io.netty.codec.socks.SocksCmdResponse;
import io.netty.codec.socks.SocksMessage;


@ChannelHandler.Sharable
public final class SocksServerConnectHandler extends ChannelInboundMessageHandlerAdapter<SocksCmdRequest> {
    private static final String name = "SOCKS_SERVER_CONNECT_HANDLER";

    public static String getName() {
        return name;
    }

    private final Bootstrap b;

    public SocksServerConnectHandler() {
        super(SocksCmdRequest.class);
        b = new Bootstrap();
    }

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final SocksCmdRequest request) throws Exception {
        CallbackNotifier cb = new CallbackNotifier() {
            @Override
            public void onSuccess(final ChannelHandlerContext outboundCtx) {
                ctx.channel().write(new SocksCmdResponse(SocksMessage.CmdStatus.SUCCESS, request.getAddressType()))
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
                ctx.channel().write(new SocksCmdResponse(SocksMessage.CmdStatus.FAILURE, request.getAddressType()));
                SocksServerUtils.closeOnFlush(ctx.channel());
            }
        };

        final Channel inboundChannel = ctx.channel();
        b.group(inboundChannel.eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new DirectClientInitializer(cb))
                .remoteAddress(request.getHost(), request.getPort());
        b.connect();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        SocksServerUtils.closeOnFlush(ctx.channel());
    }
}
