/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class H2PrefaceTest {

    enum OpenMode {
        Blocked,
        Listener,
        SubmitInListener
    }

    @ParameterizedTest
    @EnumSource(OpenMode.class)
    void openStreamAfterBlockingConnect(OpenMode mode) throws Exception {
        StreamRequestResponseListener streamRequestResponseListener = new StreamRequestResponseListener();
        EventLoopGroup eventLoopGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

        Channel backend = new ServerBootstrap()
                .group(eventLoopGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(final SocketChannel ch) {
                        ch.pipeline().addLast(Http2FrameCodecBuilder.forServer().build());
                        ch.pipeline().addLast(new Http2MultiplexHandler(new ChannelInitializer<Http2StreamChannel>() {
                            @Override
                            protected void initChannel(final Http2StreamChannel ch) {
                                ch.pipeline().addLast(new H2ServerHandler());
                            }
                        }));
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                streamRequestResponseListener.responseHeaders.completeExceptionally(cause);
                            }
                        });
                    }
                })
                .bind(NetUtil.LOCALHOST, 0)
                .sync()
                .channel();

        ChannelFuture cf = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .remoteAddress(backend.localAddress())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(final SocketChannel ch) {
                        ch.pipeline().addLast(Http2FrameCodecBuilder.forClient()
                                .initialSettings(Http2Settings.defaultSettings())
                                .build());
                        ch.pipeline().addLast(new Http2MultiplexHandler(new ChannelInboundHandlerAdapter()));
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                streamRequestResponseListener.responseHeaders.completeExceptionally(cause);
                            }
                        });
                    }
                }).connect();
        final Channel channel = cf.channel();
        try {
            final Http2StreamChannelBootstrap streamChannelBootstrap = new Http2StreamChannelBootstrap(channel);

            switch (mode) {
                case Blocked:
                    cf.syncUninterruptibly();
                    streamChannelBootstrap.open().addListener(streamRequestResponseListener);
                    break;
                case Listener:
                    cf.addListener((ChannelFuture connectFuture) ->
                            streamChannelBootstrap.open().addListener(streamRequestResponseListener));
                    break;
                case SubmitInListener:
                    cf.addListener(f -> channel.eventLoop().submit(() ->
                        streamChannelBootstrap.open().addListener(streamRequestResponseListener)
                    ));
                    break;
                default:
                    throw new AssertionError();
            }

            assertEquals("200", streamRequestResponseListener.responseHeaders.get(
                    5, TimeUnit.SECONDS).headers().status().toString());
        } finally {
            channel.close().sync();
            backend.close().sync();
            eventLoopGroup.shutdownGracefully().sync();
        }
    }

    private static class H2ServerHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
            if (msg instanceof Http2HeadersFrame) {
                final Http2Headers responseHeaders = new DefaultHttp2Headers().status("200");
                ctx.write(new DefaultHttp2HeadersFrame(responseHeaders, false));
                ctx.writeAndFlush(
                        new DefaultHttp2DataFrame(Unpooled.copiedBuffer("hello world", StandardCharsets.UTF_8), true));
            }
        }
    }

    /// Send a request and wait for a response once an Http2StreamChannel is established
    private static final class StreamRequestResponseListener implements
            GenericFutureListener<Future<Http2StreamChannel>> {
        private final CompletableFuture<Http2HeadersFrame> responseHeaders = new CompletableFuture<>();

        @Override
        public void operationComplete(final Future<Http2StreamChannel> future) {
            final Http2StreamChannel streamChannel = future.getNow();
            streamChannel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    if (msg instanceof Http2HeadersFrame) {
                        responseHeaders.complete((Http2HeadersFrame) msg);
                    }
                    ctx.fireChannelRead(msg);
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)  {
                    responseHeaders.completeExceptionally(cause);
                }
            });
            final Http2Headers headers = new DefaultHttp2Headers()
                    .method("GET")
                    .path("/test")
                    .scheme("http");
            final Http2HeadersFrame headersFrame = new DefaultHttp2HeadersFrame(headers, true);
            streamChannel.writeAndFlush(headersFrame).addListener(f -> {
                if (!f.isSuccess()) {
                    responseHeaders.completeExceptionally(f.cause());
                }
            });
        }
    }
}
