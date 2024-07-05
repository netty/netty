/*
 * Copyright 2024 The Netty Project
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

package io.netty.testsuite_jpms.test;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2FrameStream;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2LocalFlowController;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CodecHttp2Test {

    @Test
    public void smokeTest() throws Exception {

        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.channel(NioServerSocketChannel.class);
        MultiThreadIoEventLoopGroup eventLoopGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        serverBootstrap.group(eventLoopGroup);
        serverBootstrap.childHandler(new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(Http2FrameCodecBuilder.forServer().build(), new ChannelDuplexHandler() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        if (msg instanceof Http2HeadersFrame) {
                            Http2HeadersFrame streamFrame = (Http2HeadersFrame) msg;
                            if (streamFrame.isEndStream()) {
                                ByteBuf body = ctx.alloc().buffer();
                                body.writeCharSequence("Hello World", StandardCharsets.UTF_8);
                                Http2Headers headers = new DefaultHttp2Headers().status(OK.codeAsText());
                                Http2FrameStream stream = streamFrame.stream();
                                ctx.write(new DefaultHttp2HeadersFrame(headers).stream(stream));
                                ctx.write(new DefaultHttp2DataFrame(body, true).stream(stream));
                            }
                        } else {
                            super.channelRead(ctx, msg);
                        }
                    }
                });
            }
        });
        ChannelFuture server = serverBootstrap.bind("localhost", 8080).sync();

        try {
            Bootstrap clientBootstrap = new Bootstrap()
                    .group(eventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) {
                    Http2FrameCodec http2FrameCodec = Http2FrameCodecBuilder.forClient().build();
                    ch.pipeline().addLast(http2FrameCodec);
                    ch.pipeline().addLast(new Http2MultiplexHandler(new SimpleChannelInboundHandler<>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
                        }
                    }));
                }
            });

            CompletableFuture<String> responseFut = new CompletableFuture<>();

            Channel client = clientBootstrap.handler(new ChannelInitializer<SocketChannel>() {
                Http2Connection connection;
                Http2ConnectionHandler connectionHandler;
                @Override
                protected void initChannel(SocketChannel ch) {
                    connection = new DefaultHttp2Connection(false);
                    Http2EventAdapter http2EventHandler = new Http2EventAdapter() {
                        @Override
                        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings)
                                throws Http2Exception {
                            Http2Connection.Endpoint<Http2LocalFlowController> endpoint = connection.local();
                            Http2Stream stream = endpoint.createStream(1, false);
                            Http2Headers headers = new DefaultHttp2Headers()
                                    .method("GET")
                                    .path("/")
                                    .scheme("http");
                            ChannelPromise promise = ctx.newPromise();
                            Http2ConnectionEncoder encoder = connectionHandler.encoder();
                            encoder.writeHeaders(ctx, 1, headers, 0, true, promise);
                        }
                        @Override
                        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data,
                                              int padding, boolean endOfStream) throws Http2Exception {
                            if (endOfStream) {
                                responseFut.complete(data.toString(StandardCharsets.UTF_8));
                            }
                            return super.onDataRead(ctx, streamId, data, padding, endOfStream);
                        }
                    };
                    connectionHandler = new Http2ConnectionHandlerBuilder()
                            .frameListener(new DelegatingDecompressorFrameListener(
                                    connection,
                                    http2EventHandler))
                            .connection(connection)
                            .build();
                    ch.pipeline().addLast(connectionHandler);
                }
            }).connect("localhost", 8080).syncUninterruptibly().channel();

            String resp = responseFut.get(20, TimeUnit.SECONDS);
            assertEquals("Hello World", resp);

            // Wait until the connection is closed.
            server.channel().close().syncUninterruptibly();
            client.close().syncUninterruptibly();
        } finally {
            eventLoopGroup.shutdownGracefully();
        }
    }
}
