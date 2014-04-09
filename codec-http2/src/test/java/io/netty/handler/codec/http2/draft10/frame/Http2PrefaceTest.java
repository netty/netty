/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2.draft10.frame;

import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.CONNECTION_PREFACE;
import static io.netty.util.CharsetUtil.UTF_8;
import static org.junit.Assert.assertTrue;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http2.draft10.connection.Http2ConnectionHandler;
import io.netty.handler.codec.http2.draft10.frame.decoder.Http2FrameDecoder;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorGroup;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the handling of HTTP connection preface and initial settings between client and server.
 */
public class Http2PrefaceTest {

    private CaptureHandler serverHandler;
    private CaptureHandler clientHandler;
    private Channel serverChannel;
    private int serverPort;
    private List<EventExecutorGroup> groups;

    @Before
    public void setup() throws Exception {
        groups = new ArrayList<EventExecutorGroup>();

        groups.add(new NioEventLoopGroup());
        serverHandler = new CaptureHandler(groups.get(0).next());
        clientHandler = new CaptureHandler(groups.get(0).next());

        ServerBootstrap sb = new ServerBootstrap();
        sb.group(new NioEventLoopGroup(), new NioEventLoopGroup());
        sb.channel(NioServerSocketChannel.class);
        serverHandler = new CaptureHandler(sb.group().next());
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast("codec", new Http2FrameCodec());
                p.addLast("connection", new Http2ConnectionHandler(true));
                p.addLast("handler", serverHandler);
            }
        });
        groups.add(sb.group());
        serverChannel = sb.bind(new InetSocketAddress(0)).sync().channel();
        serverPort = ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    @After
    public void teardown() throws Exception {
        serverChannel.close().sync();
        for (EventExecutorGroup group : groups) {
            group.shutdownGracefully();
        }
    }

    @Test
    public void badPrefaceShouldCloseConnection() throws Exception {
        createClientChannel(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast("badPrefaceGenerator", new ChannelHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                        ByteBuf buffer = ctx.alloc().buffer();
                        buffer.writeBytes("BAD_PREFACE".getBytes());
                        ctx.writeAndFlush(buffer);
                        super.channelActive(ctx);
                    }
                });
                p.addLast("decoder", new Http2FrameDecoder());
                p.addLast("handler", clientHandler);
            }
        });

        // Wait a bit and verify that the connection was closed.
        assertTrue(serverHandler.awaitClose());
        assertTrue(clientHandler.awaitClose());
    }

    @Test
    public void prefaceNotFollowedBySettingsShouldCloseConnection() throws Exception {
        createClientChannel(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast("codec", new Http2FrameCodec());
                p.addLast("wrongFrameGenerator", new ChannelHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                        if (msg == CONNECTION_PREFACE) {
                            ByteBuf buf = Unpooled.copiedBuffer("01234567", UTF_8);
                            Http2PingFrame frame =
                                    new DefaultHttp2PingFrame.Builder().setData(buf).build();
                            ctx.writeAndFlush(frame);
                        } else {
                            super.channelRead(ctx, msg);
                        }
                    }
                });
                p.addLast("handler", clientHandler);
            }
        });

        // Wait a bit and verify that the connection was closed.
        assertTrue(serverHandler.awaitClose());
        assertTrue(clientHandler.awaitClose());
    }

    @Test
    public void settingsShouldBeExchangedAtStartup() throws Exception {
        createClientChannel(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast("codec", new Http2FrameCodec());
                p.addLast("connection", new Http2ConnectionHandler(false));
                p.addLast("handler", clientHandler);
            }
        });

        // Wait a bit and verify that the settings were exchanged.
        serverHandler.settings.get(1, TimeUnit.SECONDS);
        serverHandler.settingsAck.get(1, TimeUnit.SECONDS);
        clientHandler.settings.get(1, TimeUnit.SECONDS);
        clientHandler.settingsAck.get(1, TimeUnit.SECONDS);
    }

    private Channel createClientChannel(ChannelHandler handler) {
        Bootstrap cb = new Bootstrap();
        cb.group(new NioEventLoopGroup());
        cb.channel(NioSocketChannel.class);
        cb.handler(handler);
        groups.add(cb.group());

        ChannelFuture ccf = cb.connect(new InetSocketAddress(NetUtil.LOCALHOST, serverPort));
        assertTrue(ccf.awaitUninterruptibly().isSuccess());
        return ccf.channel();
    }

    private static class CaptureHandler extends ChannelHandlerAdapter {
        final DefaultPromise<Http2SettingsFrame> settings;
        final DefaultPromise<Http2SettingsFrame> settingsAck;
        final DefaultPromise<Void> initFuture;
        Channel channel;

        CaptureHandler(EventExecutor executor) {
            settings = new DefaultPromise<Http2SettingsFrame>(executor);
            settingsAck = new DefaultPromise<Http2SettingsFrame>(executor);
            initFuture = new DefaultPromise<Void>(executor);
        }

        public boolean awaitClose() throws Exception {
            initFuture.await();
            for (int i = 0; channel.isOpen() && i < 5; ++i) {
                Thread.sleep(10);
            }
            return !channel.isOpen();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            channel = ctx.channel();
            initFuture.setSuccess(null);

            super.channelActive(ctx);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (!(msg instanceof Http2SettingsFrame)) {
                throw new Exception("Received wrong frame type: " + msg.getClass().getName());
            }

            Http2SettingsFrame frame = (Http2SettingsFrame) msg;
            if (frame.isAck()) {
                if (settingsAck.isDone()) {
                    throw new Exception("Already received settings ack");
                }
                settingsAck.setSuccess(frame);
            } else {
                if (settings.isDone()) {
                    throw new Exception("Already received settings");
                }
                settings.setSuccess(frame);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (!settingsAck.isDone()) {
                settingsAck.setFailure(cause);
            }
            if (!settings.isDone()) {
                settings.setFailure(cause);
            }
        }
    }
}
