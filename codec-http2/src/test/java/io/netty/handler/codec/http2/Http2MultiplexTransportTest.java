/*
 * Copyright 2019 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertFalse;

public class Http2MultiplexTransportTest {
    private static final ChannelHandler DISCARD_HANDLER = new ChannelInboundHandlerAdapter() {

        @Override
        public boolean isSharable() {
            return true;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ReferenceCountUtil.release(msg);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            ReferenceCountUtil.release(evt);
        }
    };

    private EventLoopGroup eventLoopGroup;
    private Channel clientChannel;
    private Channel serverChannel;
    private Channel serverConnectedChannel;

    @Before
    public void setup() {
        eventLoopGroup = new NioEventLoopGroup();
    }

    @After
    public void teardown() {
        if (clientChannel != null) {
            clientChannel.close();
        }
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (serverConnectedChannel != null) {
            serverConnectedChannel.close();
        }
        eventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS);
    }

    @Test(timeout = 10000)
    public void asyncSettingsAckWithMultiplexCodec() throws InterruptedException {
        asyncSettingsAck0(new Http2MultiplexCodecBuilder(true, DISCARD_HANDLER).build(), null);
    }

    @Test(timeout = 10000)
    public void asyncSettingsAckWithMultiplexHandler() throws InterruptedException {
        asyncSettingsAck0(new Http2FrameCodecBuilder(true).build(),
                new Http2MultiplexHandler(DISCARD_HANDLER));
    }

    private void asyncSettingsAck0(final Http2FrameCodec codec, final ChannelHandler multiplexer)
            throws InterruptedException {
        // The client expects 2 settings frames. One from the connection setup and one from this test.
        final CountDownLatch serverAckOneLatch = new CountDownLatch(1);
        final CountDownLatch serverAckAllLatch = new CountDownLatch(2);
        final CountDownLatch clientSettingsLatch = new CountDownLatch(2);
        final CountDownLatch serverConnectedChannelLatch = new CountDownLatch(1);
        final AtomicReference<Channel> serverConnectedChannelRef = new AtomicReference<Channel>();
        ServerBootstrap sb = new ServerBootstrap();
        sb.group(eventLoopGroup);
        sb.channel(NioServerSocketChannel.class);
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(codec);
                if (multiplexer != null) {
                    ch.pipeline().addLast(multiplexer);
                }
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) {
                        serverConnectedChannelRef.set(ctx.channel());
                        serverConnectedChannelLatch.countDown();
                    }

                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        if (msg instanceof Http2SettingsAckFrame) {
                            serverAckOneLatch.countDown();
                            serverAckAllLatch.countDown();
                        }
                        ReferenceCountUtil.release(msg);
                    }
                });
            }
        });
        serverChannel = sb.bind(new InetSocketAddress(NetUtil.LOCALHOST, 0)).awaitUninterruptibly().channel();

        Bootstrap bs = new Bootstrap();
        bs.group(eventLoopGroup);
        bs.channel(NioSocketChannel.class);
        bs.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(Http2MultiplexCodecBuilder
                        .forClient(DISCARD_HANDLER).autoAckSettingsFrame(false).build());
                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        if (msg instanceof Http2SettingsFrame) {
                            clientSettingsLatch.countDown();
                        }
                        ReferenceCountUtil.release(msg);
                    }
                });
            }
        });
        clientChannel = bs.connect(serverChannel.localAddress()).awaitUninterruptibly().channel();
        serverConnectedChannelLatch.await();
        serverConnectedChannel = serverConnectedChannelRef.get();

        serverConnectedChannel.writeAndFlush(new DefaultHttp2SettingsFrame(new Http2Settings()
                .maxConcurrentStreams(10))).sync();

        clientSettingsLatch.await();

        // We expect a timeout here because we want to asynchronously generate the SETTINGS ACK below.
        assertFalse(serverAckOneLatch.await(300, MILLISECONDS));

        // We expect 2 settings frames, the initial settings frame during connection establishment and the setting frame
        // written in this test. We should ack both of these settings frames.
        clientChannel.writeAndFlush(Http2SettingsAckFrame.INSTANCE).sync();
        clientChannel.writeAndFlush(Http2SettingsAckFrame.INSTANCE).sync();

        serverAckAllLatch.await();
    }

    @Test(timeout = 5000L)
    public void testFlushNotDiscarded()
            throws InterruptedException {
        final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

        try {
            ServerBootstrap sb = new ServerBootstrap();
            sb.group(eventLoopGroup);
            sb.channel(NioServerSocketChannel.class);
            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new Http2FrameCodecBuilder(true).build());
                    ch.pipeline().addLast(new Http2MultiplexHandler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(final ChannelHandlerContext ctx, Object msg) {
                            if (msg instanceof Http2HeadersFrame && ((Http2HeadersFrame) msg).isEndStream()) {
                                executorService.schedule(new Runnable() {
                                    @Override
                                    public void run() {
                                        ctx.writeAndFlush(new DefaultHttp2HeadersFrame(
                                                new DefaultHttp2Headers(), false)).addListener(
                                                        new ChannelFutureListener() {
                                            @Override
                                            public void operationComplete(ChannelFuture future) {
                                                ctx.write(new DefaultHttp2DataFrame(
                                                        Unpooled.copiedBuffer("Hello World", CharsetUtil.US_ASCII),
                                                        true));
                                                ctx.channel().eventLoop().execute(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        ctx.flush();
                                                    }
                                                });
                                            }
                                        });
                                    }
                                }, 500, TimeUnit.MILLISECONDS);
                            }
                            ReferenceCountUtil.release(msg);
                        }
                    }));
                }
            });
            serverChannel = sb.bind(new InetSocketAddress(NetUtil.LOCALHOST, 0)).syncUninterruptibly().channel();

            final CountDownLatch latch = new CountDownLatch(1);
            Bootstrap bs = new Bootstrap();
            bs.group(eventLoopGroup);
            bs.channel(NioSocketChannel.class);
            bs.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new Http2FrameCodecBuilder(false).build());
                    ch.pipeline().addLast(new Http2MultiplexHandler(DISCARD_HANDLER));
                }
            });
            clientChannel = bs.connect(serverChannel.localAddress()).syncUninterruptibly().channel();
            Http2StreamChannelBootstrap h2Bootstrap = new Http2StreamChannelBootstrap(clientChannel);
            h2Bootstrap.handler(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    if (msg instanceof Http2DataFrame && ((Http2DataFrame) msg).isEndStream()) {
                        latch.countDown();
                    }
                    ReferenceCountUtil.release(msg);
                }
            });
            Http2StreamChannel streamChannel = h2Bootstrap.open().syncUninterruptibly().getNow();
            streamChannel.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers(), true))
                    .syncUninterruptibly();

            latch.await();
        } finally {
            executorService.shutdown();
        }
    }
}
