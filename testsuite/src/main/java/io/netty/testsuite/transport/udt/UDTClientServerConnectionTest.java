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
package io.netty.testsuite.transport.udt;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.udt.UdtChannel;
import io.netty.channel.udt.nio.NioUdtProvider;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.ThreadFactory;

import static org.junit.Assert.*;

/**
 * Verify UDT connect/disconnect life cycle.
 */
public class UDTClientServerConnectionTest {

    static class Client implements Runnable {

        static final Logger log = LoggerFactory.getLogger(Client.class);

        private final InetSocketAddress address;

        volatile Channel channel;
        volatile boolean isRunning;
        volatile boolean isShutdown;

        Client(InetSocketAddress address) {
            this.address = address;
        }

        @Override
        public void run() {
            final Bootstrap boot = new Bootstrap();
            final ThreadFactory clientFactory = new DefaultThreadFactory("client");
            final NioEventLoopGroup connectGroup = new NioEventLoopGroup(1,
                    clientFactory, NioUdtProvider.BYTE_PROVIDER);
            try {
                boot.group(connectGroup)
                        .channelFactory(NioUdtProvider.BYTE_CONNECTOR)
                        .handler(new ChannelInitializer<UdtChannel>() {

                            @Override
                            protected void initChannel(final UdtChannel ch)
                                    throws Exception {
                                final ChannelPipeline pipeline = ch.pipeline();
                                pipeline.addLast("framer",
                                        new DelimiterBasedFrameDecoder(8192,
                                                Delimiters.lineDelimiter()));
                                pipeline.addLast("decoder", new StringDecoder(
                                        CharsetUtil.UTF_8));
                                pipeline.addLast("encoder", new StringEncoder(
                                        CharsetUtil.UTF_8));
                                pipeline.addLast("handler", new ClientHandler());
                            }
                        });
                channel = boot.connect(address).sync().channel();
                isRunning = true;
                log.info("Client ready.");
                waitForRunning(false);
                log.info("Client closing...");
                channel.close().sync();
                isShutdown = true;
                log.info("Client is done.");
            } catch (final Throwable e) {
                log.error("Client failed.", e);
            } finally {
                connectGroup.shutdownGracefully().syncUninterruptibly();
            }
        }

        void shutdown() {
            isRunning = false;
        }

        void waitForActive(final boolean isActive) throws Exception {
            for (int k = 0; k < WAIT_COUNT; k++) {
                Thread.sleep(WAIT_SLEEP);
                final ClientHandler handler = channel.pipeline().get(
                        ClientHandler.class);
                if (handler != null && isActive == handler.isActive) {
                    return;
                }
            }
        }

        void waitForRunning(final boolean isRunning) throws Exception {
            for (int k = 0; k < WAIT_COUNT; k++) {
                if (isRunning == this.isRunning) {
                    return;
                }
                Thread.sleep(WAIT_SLEEP);
            }
        }

        private void waitForShutdown() throws Exception {
            for (int k = 0; k < WAIT_COUNT; k++) {
                if (isShutdown) {
                    return;
                }
                Thread.sleep(WAIT_SLEEP);
            }
        }
    }

    static class ClientHandler extends SimpleChannelInboundHandler<Object> {

        static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

        volatile boolean isActive;

        @Override
        public void channelActive(final ChannelHandlerContext ctx)
                throws Exception {
            isActive = true;
            log.info("Client active {}", ctx.channel());
            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(final ChannelHandlerContext ctx)
                throws Exception {
            isActive = false;
            log.info("Client inactive {}", ctx.channel());
            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(final ChannelHandlerContext ctx,
                final Throwable cause) throws Exception {
            log.warn("Client unexpected exception from downstream.", cause);
            ctx.close();
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            log.info("Client received: " + msg);
        }
    }

    static class Server implements Runnable {

        static final Logger log = LoggerFactory.getLogger(Server.class);

        final ChannelGroup group = new DefaultChannelGroup("server group", GlobalEventExecutor.INSTANCE);

        private final InetSocketAddress address;

        volatile Channel channel;
        volatile boolean isRunning;
        volatile boolean isShutdown;

        Server(InetSocketAddress address) {
            this.address = address;
        }

        @Override
        public void run() {
            final ServerBootstrap boot = new ServerBootstrap();
            final ThreadFactory acceptFactory = new DefaultThreadFactory("accept");
            final ThreadFactory serverFactory = new DefaultThreadFactory("server");
            final NioEventLoopGroup acceptGroup = new NioEventLoopGroup(1,
                    acceptFactory, NioUdtProvider.BYTE_PROVIDER);
            final NioEventLoopGroup connectGroup = new NioEventLoopGroup(1,
                    serverFactory, NioUdtProvider.BYTE_PROVIDER);
            try {
                boot.group(acceptGroup, connectGroup)
                        .channelFactory(NioUdtProvider.BYTE_ACCEPTOR)
                        .childHandler(new ChannelInitializer<UdtChannel>() {
                            @Override
                            protected void initChannel(final UdtChannel ch)
                                    throws Exception {
                                final ChannelPipeline pipeline = ch.pipeline();
                                pipeline.addLast("framer",
                                        new DelimiterBasedFrameDecoder(8192,
                                                Delimiters.lineDelimiter()));
                                pipeline.addLast("decoder", new StringDecoder(
                                        CharsetUtil.UTF_8));
                                pipeline.addLast("encoder", new StringEncoder(
                                        CharsetUtil.UTF_8));
                                pipeline.addLast("handler", new ServerHandler(
                                        group));
                            }
                        });
                channel = boot.bind(address).sync().channel();
                isRunning = true;
                log.info("Server ready.");
                waitForRunning(false);
                log.info("Server closing acceptor...");
                channel.close().sync();
                log.info("Server closing connectors...");
                group.close().sync();
                isShutdown = true;
                log.info("Server is done.");
            } catch (final Throwable e) {
                log.error("Server failure.", e);
            } finally {
                acceptGroup.shutdownGracefully();
                connectGroup.shutdownGracefully();

                acceptGroup.terminationFuture().syncUninterruptibly();
                connectGroup.terminationFuture().syncUninterruptibly();
            }
        }

        void shutdown() {
            isRunning = false;
        }

        void waitForActive(final boolean isActive) throws Exception {
            for (int k = 0; k < WAIT_COUNT; k++) {
                Thread.sleep(WAIT_SLEEP);
                if (isActive) {
                    for (final Channel channel : group) {
                        final ServerHandler handler = channel.pipeline().get(
                                ServerHandler.class);
                        if (handler != null && handler.isActive) {
                            return;
                        }
                    }
                } else {
                    if (group.isEmpty()) {
                        return;
                    }
                }
            }
        }

        void waitForRunning(final boolean isRunning) throws Exception {
            for (int k = 0; k < WAIT_COUNT; k++) {
                if (isRunning == this.isRunning) {
                    return;
                }
                Thread.sleep(WAIT_SLEEP);
            }
        }

        void waitForShutdown() throws Exception {
            for (int k = 0; k < WAIT_COUNT; k++) {
                if (isShutdown) {
                    return;
                }
                Thread.sleep(WAIT_SLEEP);
            }
        }
    }

    static class ServerHandler extends
            SimpleChannelInboundHandler<Object> {

        static final Logger log = LoggerFactory.getLogger(ServerHandler.class);

        final ChannelGroup group;

        volatile boolean isActive;

        ServerHandler(final ChannelGroup group) {
            this.group = group;
        }

        @Override
        public void channelActive(final ChannelHandlerContext ctx)
                throws Exception {
            group.add(ctx.channel());
            isActive = true;
            log.info("Server active  : {}", ctx.channel());
            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(final ChannelHandlerContext ctx)
                throws Exception {
            group.remove(ctx.channel());
            isActive = false;
            log.info("Server inactive: {}", ctx.channel());
            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(final ChannelHandlerContext ctx,
                final Throwable cause) {
            log.warn("Server close on exception.", cause);
            ctx.close();
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            log.info("Server received: " + msg);
        }
    }
    static final Logger log = LoggerFactory
            .getLogger(UDTClientServerConnectionTest.class);

    /**
     * Maximum wait time is 5 seconds.
     * <p>
     * wait-time = {@code WAIT_COUNT} * {@value #WAIT_SLEEP}
     */
    static final int WAIT_COUNT = 50;
    static final int WAIT_SLEEP = 100;

    /**
     * Verify UDT client/server connect and disconnect.
     */
    @Test
    public void connection() throws Exception {
        log.info("Starting server.");
        // Using LOCALHOST4 as UDT transport does not support IPV6 :(
        final Server server = new Server(new InetSocketAddress(NetUtil.LOCALHOST4, 0));
        final Thread serverTread = new Thread(server, "server-*");
        serverTread.start();
        server.waitForRunning(true);
        assertTrue(server.isRunning);

        log.info("Starting client.");
        final Client client = new Client((InetSocketAddress) server.channel.localAddress());
        final Thread clientThread = new Thread(client, "client-*");
        clientThread.start();
        client.waitForRunning(true);
        assertTrue(client.isRunning);

        log.info("Wait till connection is active.");
        client.waitForActive(true);
        server.waitForActive(true);

        log.info("Verify connection is active.");
        assertEquals("group must have one", 1, server.group.size());

        log.info("Stopping client.");
        client.shutdown();
        client.waitForShutdown();
        assertTrue(client.isShutdown);

        log.info("Wait till connection is inactive.");
        client.waitForActive(false);
        server.waitForActive(false);

        log.info("Verify connection is inactive.");
        assertEquals("group must be empty", 0, server.group.size());

        log.info("Stopping server.");
        server.shutdown();
        server.waitForShutdown();
        assertTrue(server.isShutdown);

        log.info("Finished server.");
    }

}
