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
package io.netty.handler.codec.http2;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;

public class DefaultHttp2PushPromiseFrameTest {

    private final EventLoopGroup eventLoopGroup = new NioEventLoopGroup(2);
    private final ClientHandler clientHandler = new ClientHandler();
    private final Map<Integer, String> contentMap = new ConcurrentHashMap<Integer, String>();

    private ChannelFuture connectionFuture;

    @Before
    public void setup() throws InterruptedException {
        ServerBootstrap serverBootstrap = new ServerBootstrap()
                .group(eventLoopGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();

                        Http2FrameCodec frameCodec = Http2FrameCodecBuilder.forServer()
                                .autoAckSettingsFrame(true)
                                .autoAckPingFrame(true)
                                .build();

                        pipeline.addLast(frameCodec);
                        pipeline.addLast(new ServerHandler());
                    }
                });

        ChannelFuture channelFuture = serverBootstrap.bind(0).sync();

        final Bootstrap bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();

                        Http2FrameCodec frameCodec = Http2FrameCodecBuilder.forClient()
                                .autoAckSettingsFrame(true)
                                .autoAckPingFrame(true)
                                .initialSettings(Http2Settings.defaultSettings().pushEnabled(true))
                                .build();

                        pipeline.addLast(frameCodec);
                        pipeline.addLast(clientHandler);
                    }
                });

        connectionFuture = bootstrap.connect(channelFuture.channel().localAddress());
    }

    @Test
    public void send() {
        connectionFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                clientHandler.write();
            }
        });
    }

    @After
    public void shutdown() {
        eventLoopGroup.shutdownGracefully();
    }

    private final class ServerHandler extends Http2ChannelDuplexHandler {

        @Override
        public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {

            if (msg instanceof Http2HeadersFrame) {
                final Http2HeadersFrame receivedFrame = (Http2HeadersFrame) msg;

                Http2Headers pushRequestHeaders = new DefaultHttp2Headers();
                pushRequestHeaders.path("/meow")
                        .method("GET")
                        .scheme("https")
                        .authority("localhost:5555");

                // Write PUSH_PROMISE request headers
                final Http2FrameStream newPushFrameStream = newStream();
                Http2PushPromiseFrame pushPromiseFrame = new DefaultHttp2PushPromiseFrame(pushRequestHeaders);
                pushPromiseFrame.stream(receivedFrame.stream());
                pushPromiseFrame.pushStream(newPushFrameStream);
                ctx.writeAndFlush(pushPromiseFrame).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) {
                        contentMap.put(newPushFrameStream.id(), "Meow, I am Pushed via HTTP/2");

                        // Write headers for actual request
                        Http2Headers http2Headers = new DefaultHttp2Headers();
                        http2Headers.status("200");
                        http2Headers.add("push", "false");
                        Http2HeadersFrame headersFrame = new DefaultHttp2HeadersFrame(http2Headers, false);
                        headersFrame.stream(receivedFrame.stream());
                        ChannelFuture channelFuture = ctx.writeAndFlush(headersFrame);

                        // Write Data of actual request
                        channelFuture.addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                Http2DataFrame dataFrame = new DefaultHttp2DataFrame(
                                        Unpooled.wrappedBuffer("Meow".getBytes()), true);
                                dataFrame.stream(receivedFrame.stream());
                                ctx.writeAndFlush(dataFrame);
                            }
                        });
                    }
                });
            } else if (msg instanceof Http2PriorityFrame) {
                Http2PriorityFrame priorityFrame = (Http2PriorityFrame) msg;
                String content = contentMap.get(priorityFrame.stream().id());
                if (content == null) {
                    ctx.writeAndFlush(new DefaultHttp2GoAwayFrame(Http2Error.REFUSED_STREAM));
                    return;
                }

                // Write headers for Priority request
                Http2Headers http2Headers = new DefaultHttp2Headers();
                http2Headers.status("200");
                http2Headers.add("push", "true");
                Http2HeadersFrame headersFrame = new DefaultHttp2HeadersFrame(http2Headers, false);
                headersFrame.stream(priorityFrame.stream());
                ctx.writeAndFlush(headersFrame);

                // Write Data of Priority request
                Http2DataFrame dataFrame = new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(content.getBytes()), true);
                dataFrame.stream(priorityFrame.stream());
                ctx.writeAndFlush(dataFrame);
            }
        }
    }

    private static final class ClientHandler extends Http2ChannelDuplexHandler {

        private ChannelHandlerContext ctx;

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws InterruptedException {
            this.ctx = ctx;
        }

        void write() {
            Http2Headers http2Headers = new DefaultHttp2Headers();
            http2Headers.path("/")
                    .authority("localhost")
                    .method("GET")
                    .scheme("https");

            Http2HeadersFrame headersFrame = new DefaultHttp2HeadersFrame(http2Headers, true);
            headersFrame.stream(newStream());
            ctx.writeAndFlush(headersFrame);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {

            if (msg instanceof Http2PushPromiseFrame) {
                Http2PushPromiseFrame pushPromiseFrame = (Http2PushPromiseFrame) msg;

                assertEquals("/meow", pushPromiseFrame.http2Headers().path().toString());
                assertEquals("GET", pushPromiseFrame.http2Headers().method().toString());
                assertEquals("https", pushPromiseFrame.http2Headers().scheme().toString());
                assertEquals("localhost:5555", pushPromiseFrame.http2Headers().authority().toString());

                Http2PriorityFrame priorityFrame = new DefaultHttp2PriorityFrame(pushPromiseFrame.stream().id(),
                        Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT, true);
                priorityFrame.stream(pushPromiseFrame.pushStream());
                ctx.writeAndFlush(priorityFrame);
            } else if (msg instanceof Http2HeadersFrame) {
                Http2HeadersFrame headersFrame = (Http2HeadersFrame) msg;

                if (headersFrame.stream().id() == 3) {
                    assertEquals("200", headersFrame.headers().status().toString());
                    assertEquals("false", headersFrame.headers().get("push").toString());
                } else if (headersFrame.stream().id() == 2) {
                    assertEquals("200", headersFrame.headers().status().toString());
                    assertEquals("true", headersFrame.headers().get("push").toString());
                } else {
                    ctx.writeAndFlush(new DefaultHttp2GoAwayFrame(Http2Error.REFUSED_STREAM));
                }
            } else if (msg instanceof Http2DataFrame) {
                Http2DataFrame dataFrame = (Http2DataFrame) msg;

                try {
                    if (dataFrame.stream().id() == 3) {
                        assertEquals("Meow", dataFrame.content().toString(CharsetUtil.UTF_8));
                    } else if (dataFrame.stream().id() == 2) {
                        assertEquals("Meow, I am Pushed via HTTP/2", dataFrame.content().toString(CharsetUtil.UTF_8));
                    } else {
                        ctx.writeAndFlush(new DefaultHttp2GoAwayFrame(Http2Error.REFUSED_STREAM));
                    }
                } finally {
                    ReferenceCountUtil.release(dataFrame);
                }
            }
        }
    }
}
