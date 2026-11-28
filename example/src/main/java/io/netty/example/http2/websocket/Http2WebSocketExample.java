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
package io.netty.example.http2.websocket;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2SettingsFrame;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.codec.http2.Http2WebSocketHandshaker;
import io.netty.util.AsciiString;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Promise;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/** Opens a WebSocket on a cleartext HTTP/2 loopback connection, echoes one message, then closes the stream. */
public final class Http2WebSocketExample {
    private static final Http2WebSocketHandshaker WEBSOCKET = new Http2WebSocketHandshaker(65536, true);

    private Http2WebSocketExample() { }

    public static void main(String[] args) throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(2, NioIoHandler.newFactory());
        try {
            Channel listener = new ServerBootstrap().group(group).channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(Http2FrameCodecBuilder.forServer()
                                    .initialSettings(new Http2Settings().connectProtocolEnabled(true)).build(),
                                    new Http2MultiplexHandler(new ChannelInitializer<Channel>() {
                                        @Override
                                        protected void initChannel(Channel stream) {
                                            stream.pipeline().addLast(new EchoServerHandler());
                                        }
                                    }));
                        }
                    }).bind(NetUtil.LOCALHOST, 0).sync().channel();
            InetSocketAddress address = (InetSocketAddress) listener.localAddress();
            Promise<String> echo = group.next().newPromise();
            new Bootstrap().group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(Http2FrameCodecBuilder.forClient().build(),
                            new Http2MultiplexHandler(new ChannelInboundHandlerAdapter()),
                            new SimpleChannelInboundHandler<Http2SettingsFrame>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, Http2SettingsFrame msg) {
                                    if (Boolean.TRUE.equals(msg.settings().connectProtocolEnabled())) {
                                        ctx.pipeline().remove(this);
                                        new Http2StreamChannelBootstrap(ch)
                                                .handler(new EchoClientHandler(address.getPort(), echo))
                                                .open().addListener(f -> {
                                                    if (!f.isSuccess()) {
                                                        echo.tryFailure(f.cause());
                                                    }
                                                });
                                    }
                                }
                            });
                }
            }).connect(address).sync();
            System.out.println(echo.get(10, TimeUnit.SECONDS));
        } finally {
            group.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
        }
    }

    private static final class EchoServerHandler extends SimpleChannelInboundHandler<Object> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof Http2HeadersFrame) {
                Http2HeadersFrame request = (Http2HeadersFrame) msg;
                if (Http2WebSocketHandshaker.isWebSocketRequest(request.headers()) &&
                        AsciiString.contentEquals("/echo", request.headers().path())) {
                    WEBSOCKET.accept(ctx, request, new DefaultHttp2Headers());
                } else {
                    ctx.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers().status("404"), true));
                }
            } else if (msg instanceof TextWebSocketFrame) {
                ctx.writeAndFlush(((TextWebSocketFrame) msg).retain());
            }
        }
    }

    private static final class EchoClientHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
        private final int port;
        private final Promise<String> echo;

        EchoClientHandler(int port, Promise<String> echo) {
            this.port = port;
            this.echo = echo;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            Http2Headers request = new DefaultHttp2Headers().scheme("http").authority("localhost:" + port)
                    .path("/echo");
            WEBSOCKET.connect(ctx, request).addListener(f -> {
                if (f.isSuccess()) {
                    ctx.writeAndFlush(new TextWebSocketFrame("Hello over HTTP/2"));
                } else {
                    echo.tryFailure(f.cause());
                }
            });
            ctx.fireChannelActive();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) {
            String text = msg.text();
            ctx.close().addListener(f -> {
                if (f.isSuccess()) {
                    echo.trySuccess(text);
                } else {
                    echo.tryFailure(f.cause());
                }
            });
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            echo.tryFailure(cause);
            ctx.close();
        }
    }
}
