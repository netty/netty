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
import io.netty.handler.codec.socksx.SocksMessage;
import io.netty.handler.codec.socksx.SocksRequest;
import io.netty.handler.codec.socksx.v4.SocksV4CmdRequest;
import io.netty.handler.codec.socksx.v4.SocksV4CmdResponse;
import io.netty.handler.codec.socksx.v4.SocksV4CmdStatus;
import io.netty.handler.codec.socksx.v5.SocksV5CmdRequest;
import io.netty.handler.codec.socksx.v5.SocksV5CmdResponse;
import io.netty.handler.codec.socksx.v5.SocksV5CmdStatus;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;

@ChannelHandler.Sharable
public final class SocksServerConnectHandler extends SimpleChannelInboundHandler<SocksRequest> {

    private final Bootstrap b = new Bootstrap();

    @Override
    public void messageReceived(final ChannelHandlerContext ctx, final SocksRequest message) throws Exception {
        if (message instanceof SocksV4CmdRequest) {
            final SocksV4CmdRequest request = (SocksV4CmdRequest) message;
            Promise<Channel> promise = ctx.executor().newPromise();
            promise.addListener(
                    new GenericFutureListener<Future<Channel>>() {
                        @Override
                        public void operationComplete(final Future<Channel> future) throws Exception {
                            final Channel outboundChannel = future.getNow();
                            if (future.isSuccess()) {
                                ctx.channel().writeAndFlush(new SocksV4CmdResponse(SocksV4CmdStatus.SUCCESS))
                                        .addListener(new ChannelFutureListener() {
                                            @Override
                                            public void operationComplete(ChannelFuture channelFuture) {
                                                ctx.pipeline().remove(SocksServerConnectHandler.this);
                                                outboundChannel.pipeline().addLast(new RelayHandler(ctx.channel()));
                                                ctx.pipeline().addLast(new RelayHandler(outboundChannel));
                                            }
                                        });
                            } else {
                                ctx.channel().writeAndFlush(
                                        new SocksV4CmdResponse(SocksV4CmdStatus.REJECTED_OR_FAILED)
                                );
                                SocksServerUtils.closeOnFlush(ctx.channel());
                            }
                        }
                    });

            final Channel inboundChannel = ctx.channel();
            b.group(inboundChannel.eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new DirectClientHandler(promise));

            b.connect(request.host(), request.port()).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        // Connection established use handler provided results
                    } else {
                        // Close the connection if the connection attempt has failed.
                        ctx.channel().writeAndFlush(
                                new SocksV4CmdResponse(SocksV4CmdStatus.REJECTED_OR_FAILED)
                        );
                        SocksServerUtils.closeOnFlush(ctx.channel());
                    }
                }
            });
        } else if (message instanceof SocksV5CmdRequest) {
            final SocksV5CmdRequest request = (SocksV5CmdRequest) message;
            Promise<Channel> promise = ctx.executor().newPromise();
            promise.addListener(
                    new GenericFutureListener<Future<Channel>>() {
                        @Override
                        public void operationComplete(final Future<Channel> future) throws Exception {
                            final Channel outboundChannel = future.getNow();
                            if (future.isSuccess()) {
                                ctx.channel().writeAndFlush(
                                        new SocksV5CmdResponse(SocksV5CmdStatus.SUCCESS, request.addressType())
                                ).addListener(new ChannelFutureListener() {
                                            @Override
                                            public void operationComplete(ChannelFuture channelFuture) {
                                                ctx.pipeline().remove(SocksServerConnectHandler.this);
                                                outboundChannel.pipeline().addLast(new RelayHandler(ctx.channel()));
                                                ctx.pipeline().addLast(new RelayHandler(outboundChannel));
                                            }
                                        }
                                );
                            } else {
                                ctx.channel().writeAndFlush(
                                        new SocksV5CmdResponse(SocksV5CmdStatus.FAILURE, request.addressType()));
                                SocksServerUtils.closeOnFlush(ctx.channel());
                            }
                        }
                    });

            final Channel inboundChannel = ctx.channel();
            b.group(inboundChannel.eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new DirectClientHandler(promise));

            b.connect(request.host(), request.port()).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        // Connection established use handler provided results
                    } else {
                        // Close the connection if the connection attempt has failed.
                        ctx.channel().writeAndFlush(
                                new SocksV5CmdResponse(SocksV5CmdStatus.FAILURE, request.addressType()));
                        SocksServerUtils.closeOnFlush(ctx.channel());
                    }
                }
            });
        } else {
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        SocksServerUtils.closeOnFlush(ctx.channel());
    }
}
