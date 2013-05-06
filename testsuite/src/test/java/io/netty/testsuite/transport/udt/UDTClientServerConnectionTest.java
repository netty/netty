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

import static org.junit.Assert.assertTrue;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.BufType;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.udt.UdtChannel;
import io.netty.channel.udt.nio.NioUdtProvider;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.testsuite.transport.udt.UDTClientServerConnectionTest.Server.TestThreadFactory;
import io.netty.util.CharsetUtil;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Test;

public class UDTClientServerConnectionTest {

    @Test
    public void test() throws InterruptedException {
        // first start server

        int port = 1234;
        Server server = new Server(port);
        Thread serverTread = new Thread(server);
        serverTread.start();

        Thread.sleep(1000);

        // start a client
        final String host = "localhost";

        TestClient client = new TestClient(host, port);
        Thread clientThread = new Thread(client);
        clientThread.start();

        Thread.sleep(1000);

        // check number of connections
        assertTrue(server.connectedClients() == 1);

        // close client
        client.shutdown();

        Thread.sleep(1000);

        // check connections again
        assertTrue(server.connectedClients() == 0);
    }

    static class Server implements Runnable {
        private final ChannelGroup channels = new DefaultChannelGroup(
                "all channels");
        private Channel serverChannel;

        private static final Logger log = Logger.getLogger(Server.class
                .getName());

        private final int port;
        private ServerBootstrap b;
        private boolean running;
        private boolean shutdown;

        public Server(final int port) {
            this.port = port;
        }

        public void shutdown() {
            log.info("shutting down server...");
            running = false;
            while (!shutdown) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    //ignore
                }
            }
        }

        public void run() {
            running = true;
            b = new ServerBootstrap();
            final ThreadFactory acceptFactory = new TestThreadFactory("accept");
            final ThreadFactory connectFactory = new TestThreadFactory(
                    "connect");
            final NioEventLoopGroup acceptGroup = new NioEventLoopGroup(1,
                    acceptFactory, NioUdtProvider.BYTE_PROVIDER);
            final NioEventLoopGroup connectGroup = new NioEventLoopGroup(1,
                    connectFactory, NioUdtProvider.BYTE_PROVIDER);
            try {
                // Configure the server.
                b.group(acceptGroup, connectGroup)
                        .channelFactory(NioUdtProvider.BYTE_ACCEPTOR)
                        .childHandler(new ChannelInitializer<UdtChannel>() {

                            @Override
                            protected void initChannel(UdtChannel ch)
                                    throws Exception {
                                ChannelPipeline pipeline = ch.pipeline();
                                pipeline.addLast("framer",
                                        new DelimiterBasedFrameDecoder(8192,
                                                Delimiters.lineDelimiter()));
                                pipeline.addLast("decoder", new StringDecoder(
                                        CharsetUtil.UTF_8));
                                pipeline.addLast("encoder", new StringEncoder(
                                        BufType.BYTE));

                                pipeline.addLast("handler", new ServerHandler());
                                channels.add(ch);
                            }

                            @Override
                            public void channelInactive(
                                    ChannelHandlerContext ctx) throws Exception {
                                log.log(Level.INFO,
                                        "channel inactive, removing from channelgroup");
                                channels.remove(ctx.channel());
                            }
                        });
                // Start the server.
                serverChannel = b.bind(port).sync().channel();
                waitForShutdownCommand();
                log.info("closing server channel...");
                serverChannel.close().sync();
                log.info("closing all accepted gateway channels...");
                channels.close().sync();
                log.info("channels closed");
            } catch (Exception e) {
                log.log(Level.SEVERE, "GATEWAY SERVER DIED!", e);
            } finally {
                acceptGroup.shutdownGracefully();
                connectGroup.shutdownGracefully();
            }
            shutdown = true;
        }

        public int connectedClients() {
            return channels.size();
        }

        private void waitForShutdownCommand() {
            while (running) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        static class TestThreadFactory implements ThreadFactory {

            private static final AtomicInteger counter = new AtomicInteger();

            private final String name;

            public TestThreadFactory(final String name) {
                this.name = name;
            }

            @Override
            public Thread newThread(final Runnable runnable) {
                return new Thread(runnable, name + '-'
                        + counter.getAndIncrement());
            }
        }
    }

    static class ServerHandler extends
            ChannelInboundMessageHandlerAdapter<String> {

        private boolean isClosed;
        private static final Logger log = Logger.getLogger(ServerHandler.class
                .getName());

        @Override
        public void exceptionCaught(final ChannelHandlerContext ctx,
                final Throwable cause) {
            log.log(Level.WARNING,
                    "close the connection when an exception is raised", cause);
            ctx.close();
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, String message)
                throws Exception {
            log.log(Level.INFO, "received: " + message);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.log(Level.INFO, "channel inactive");
            isClosed = true;
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx)
                throws Exception {
            log.log(Level.INFO, "channel unregistered");
        }

        public boolean isClosed() {
            return isClosed;
        }
    }

    static class TestClient implements Runnable {

        private static final Logger log = Logger.getLogger(TestClient.class
                .getName());

        private final String host;
        private final int port;
        private Channel channel;
        private boolean running;

        public TestClient(final String host, final int port) {
            this.host = host;
            this.port = port;
        }

        public void run() {
            running = true;
            // Configure the client.
            final Bootstrap boot = new Bootstrap();
            final ThreadFactory connectFactory = new TestThreadFactory(
                    "connect");
            final NioEventLoopGroup connectGroup = new NioEventLoopGroup(1,
                    connectFactory, NioUdtProvider.BYTE_PROVIDER);
            try {
                boot.group(connectGroup)
                        .channelFactory(NioUdtProvider.BYTE_CONNECTOR)
                        .handler(new ChannelInitializer<UdtChannel>() {

                            @Override
                            protected void initChannel(UdtChannel ch)
                                    throws Exception {
                                ChannelPipeline pipeline = ch.pipeline();

                                // On top of the SSL handler, add the text line
                                // codec.
                                pipeline.addLast("framer",
                                        new DelimiterBasedFrameDecoder(8192,
                                                Delimiters.lineDelimiter()));
                                pipeline.addLast("decoder", new StringDecoder());
                                pipeline.addLast("encoder", new StringEncoder(
                                        BufType.BYTE));

                                // and then business logic.
                                pipeline.addLast("handler", new TestClientHandler());
                            }
                        });
                // Start the connection attempt.
                channel = boot.connect(host, port).sync().channel();
                waitForShutdownCommand();
                channel.disconnect().sync();
                channel.close().sync(); // close the channel and wait until done

                // channel.closeFuture().sync(); //wait for the channel to close

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // Shut down the event loop to terminate all threads.
                connectGroup.shutdownGracefully();
            }
            log.info("test client done");
        }

        private void waitForShutdownCommand() {
            while (running) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        public void shutdown() {
            running = false;
        }
    }

    static class TestClientHandler extends
            ChannelInboundMessageHandlerAdapter<String> {

        TestClientHandler() {
        }

        private static final Logger logger = Logger
                .getLogger(TestClientHandler.class.getName());

        @Override
        public void messageReceived(ChannelHandlerContext ctx, String msg)
                throws Exception {
            logger.info("client received: " + msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                throws Exception {
            logger.log(Level.WARNING, "Unexpected exception from downstream.",
                    cause);
            ctx.close();
        }
    }
}
