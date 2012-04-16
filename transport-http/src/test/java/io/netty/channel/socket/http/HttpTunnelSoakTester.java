/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.channel.socket.http;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.bootstrap.ClientBootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPipelineFactory;
import io.netty.channel.ChannelStateEvent;
import io.netty.channel.Channels;
import io.netty.channel.MessageEvent;
import io.netty.channel.SimpleChannelUpstreamHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.ClientSocketChannelFactory;
import io.netty.channel.socket.ServerSocketChannelFactory;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioClientSocketChannelFactory;
import io.netty.channel.socket.nio.NioServerSocketChannelFactory;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

/**
 * Tests HTTP tunnel soaking
 */
public class HttpTunnelSoakTester {

    private static final int SERVER_PORT = 20100;

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(HttpTunnelSoakTester.class);

    private static final long BYTES_TO_SEND = 1024 * 1024 * 1024;

    private static final int MAX_WRITE_SIZE = 64 * 1024;

    private final ServerBootstrap serverBootstrap;

    private final ClientBootstrap clientBootstrap;

    final ChannelGroup channels;

    final ExecutorService executor;

    final ScheduledExecutorService scheduledExecutor;

    final DataSender c2sDataSender = new DataSender("C2S");

    final DataSender s2cDataSender = new DataSender("S2C");

    final DataVerifier c2sVerifier = new DataVerifier("C2S-Verifier");

    final DataVerifier s2cVerifier = new DataVerifier("S2C-Verifier");

    private static final byte[] SEND_STREAM;

    static {
        SEND_STREAM = new byte[MAX_WRITE_SIZE + 127];
        for (int i = 0; i < SEND_STREAM.length; i ++) {
            SEND_STREAM[i] = (byte) (i % 127);
        }
    }

    public HttpTunnelSoakTester() {
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
        executor = Executors.newCachedThreadPool();
        ServerSocketChannelFactory serverChannelFactory =
                new NioServerSocketChannelFactory(executor);
        HttpTunnelServerChannelFactory serverTunnelFactory =
                new HttpTunnelServerChannelFactory(serverChannelFactory);

        serverBootstrap = new ServerBootstrap(serverTunnelFactory);
        serverBootstrap.setPipelineFactory(createServerPipelineFactory());

        ClientSocketChannelFactory clientChannelFactory =
                new NioClientSocketChannelFactory(executor);
        HttpTunnelClientChannelFactory clientTunnelFactory =
                new HttpTunnelClientChannelFactory(clientChannelFactory);

        clientBootstrap = new ClientBootstrap(clientTunnelFactory);
        clientBootstrap.setPipelineFactory(createClientPipelineFactory());
        configureProxy();

        channels = new DefaultChannelGroup();
    }

    private void configureProxy() {
        String proxyHost = System.getProperty("http.proxyHost");
        if (proxyHost != null && proxyHost.length() != 0) {
            int proxyPort = Integer.getInteger("http.proxyPort", 80);
            InetAddress chosenAddress = chooseAddress(proxyHost);
            InetSocketAddress proxyAddress =
                    new InetSocketAddress(chosenAddress, proxyPort);
            if (!proxyAddress.isUnresolved()) {
                clientBootstrap.setOption(
                        HttpTunnelClientChannelConfig.PROXY_ADDRESS_OPTION,
                        proxyAddress);
                logger.info("Using " + proxyAddress +
                        " as a proxy for this test run");
            } else {
                logger.error("Failed to resolve proxy address " +
                        proxyAddress);
            }
        } else {
            logger.info("No proxy specified, will connect to server directly");
        }
    }

    private InetAddress chooseAddress(String proxyHost) {
        try {
            InetAddress[] allByName = InetAddress.getAllByName(proxyHost);
            for (InetAddress address: allByName) {
                if (address.isAnyLocalAddress() || address.isLinkLocalAddress()) {
                    continue;
                }

                return address;
            }

            return null;
        } catch (UnknownHostException e) {
            return null;
        }
    }

    protected ChannelPipelineFactory createClientPipelineFactory() {
        return new ChannelPipelineFactory() {

            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("s2cVerifier", s2cVerifier);
                pipeline.addLast("throttleControl", new SendThrottle(
                        c2sDataSender));
                return pipeline;
            }
        };
    }

    protected ChannelPipelineFactory createServerPipelineFactory() {
        return new ChannelPipelineFactory() {

            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("c2sVerifier", c2sVerifier);
                pipeline.addLast("throttleControl", new SendThrottle(
                        s2cDataSender));
                pipeline.addLast("sendStarter",
                        new SimpleChannelUpstreamHandler() {
                            @Override
                            public void channelConnected(
                                    ChannelHandlerContext ctx,
                                    ChannelStateEvent e) throws Exception {
                                Channel childChannel = e.getChannel();
                                channels.add(childChannel);
                                s2cDataSender.setChannel(childChannel);
                                executor.execute(s2cDataSender);
                            }
                        });
                return pipeline;
            }
        };
    }

    public void run() throws InterruptedException {
        logger.info("binding server channel");
        Channel serverChannel =
                serverBootstrap.bind(new InetSocketAddress(SERVER_PORT));
        channels.add(serverChannel);
        logger.info(String.format("server channel bound to {0}",
                serverChannel.getLocalAddress()));

        SocketChannel clientChannel = createClientChannel();
        if (clientChannel == null) {
            logger.error("no client channel - bailing out");
            return;
        }

        channels.add(clientChannel);
        c2sDataSender.setChannel(clientChannel);

        executor.execute(c2sDataSender);

        if (!c2sDataSender.waitForFinish(5, TimeUnit.MINUTES)) {
            logger.error("Data send from client to server failed");
        }

        if (!s2cDataSender.waitForFinish(5, TimeUnit.MINUTES)) {
            logger.error("Data send from server to client failed");
        }

        logger.info("Waiting for verification to complete");
        if (!c2sVerifier.waitForCompletion(30L, TimeUnit.SECONDS)) {
            logger.warn("Timed out waiting for verification of client-to-server stream");
        }

        if (!s2cVerifier.waitForCompletion(30L, TimeUnit.SECONDS)) {
            logger.warn("Timed out waiting for verification of server-to-client stream");
        }

        logger.info("closing client channel");
        closeChannel(clientChannel);
        logger.info("server channel status: " +
                (serverChannel.isOpen()? "open" : "closed"));
        logger.info("closing server channel");
        closeChannel(serverChannel);
    }

    private void closeChannel(Channel channel) {
        try {
            if (!channel.close().await(5L, TimeUnit.SECONDS)) {
                logger.warn("Failed to close connection within reasonable amount of time");
            }
        } catch (InterruptedException e) {
            logger.error("Interrupted while closing connection");
        }

    }

    private SocketChannel createClientChannel() {
        InetSocketAddress serverAddress =
                new InetSocketAddress("localhost", SERVER_PORT);
        ChannelFuture clientChannelFuture =
                clientBootstrap.connect(serverAddress);
        try {
            if (!clientChannelFuture.await(1000, TimeUnit.MILLISECONDS)) {
                logger.error("did not connect within acceptable time period");
                return null;
            }
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for client connect to be established");
            return null;
        }

        if (!clientChannelFuture.isSuccess()) {
            logger.error("did not connect successfully",
                    clientChannelFuture.getCause());
            return null;
        }

        HttpTunnelClientChannelConfig config =
                (HttpTunnelClientChannelConfig) clientChannelFuture
                .getChannel().getConfig();
        config.setWriteBufferHighWaterMark(2 * 1024 * 1024);
        config.setWriteBufferLowWaterMark(1024 * 1024);

        return (SocketChannel) clientChannelFuture.getChannel();
    }

    ChannelBuffer createRandomSizeBuffer(AtomicInteger nextWriteByte) {
        Random random = new Random();
        int arraySize = random.nextInt(MAX_WRITE_SIZE) + 1;

        // cheaply create the buffer by wrapping an appropriately sized section of the pre-built array
        ChannelBuffer buffer =
                ChannelBuffers.wrappedBuffer(SEND_STREAM, nextWriteByte.get(),
                        arraySize);
        nextWriteByte.set((nextWriteByte.get() + arraySize) % 127);

        return buffer;
    }

    public static void main(String[] args) throws Exception {
        HttpTunnelSoakTester soakTester = new HttpTunnelSoakTester();
        try {
            soakTester.run();
        } finally {
            soakTester.shutdown();
        }
    }

    private void shutdown() {
        serverBootstrap.releaseExternalResources();
        clientBootstrap.releaseExternalResources();
        executor.shutdownNow();
        scheduledExecutor.shutdownNow();
    }

    private class DataVerifier extends SimpleChannelUpstreamHandler {
        private final String name;

        private int expectedNext = 0;

        private int verifiedBytes = 0;

        private final CountDownLatch completionLatch = new CountDownLatch(1);

        public DataVerifier(String name) {
            this.name = name;
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
                throws Exception {
            ChannelBuffer bytesToVerify = (ChannelBuffer) e.getMessage();

            while (bytesToVerify.readable()) {
                byte readByte = bytesToVerify.readByte();
                if (readByte != expectedNext) {
                    logger.error(String.format(
                            "{0}: received a byte out of sequence. Expected {1}, got {2}",
                            new Object[] { name, expectedNext, readByte }));
                    System.exit(-1);
                    return;
                }

                expectedNext = (expectedNext + 1) % 127;
                verifiedBytes ++;
            }

            if (verifiedBytes >= BYTES_TO_SEND) {
                completionLatch.countDown();
            }
        }

        @Override
        public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
                throws Exception {
            channels.add(ctx.getChannel());
        }

        public boolean waitForCompletion(long timeout, TimeUnit timeoutUnit)
                throws InterruptedException {
            return completionLatch.await(timeout, timeoutUnit);
        }
    }

    private class SendThrottle extends SimpleChannelUpstreamHandler {
        private final DataSender sender;

        public SendThrottle(DataSender sender) {
            this.sender = sender;
        }

        @Override
        public void channelInterestChanged(ChannelHandlerContext ctx,
                ChannelStateEvent e) throws Exception {
            boolean writeEnabled = ctx.getChannel().isWritable();
            sender.setWriteEnabled(writeEnabled);

        }
    }

    private class DataSender implements Runnable {

        private final AtomicReference<Channel> channel =
                new AtomicReference<Channel>();

        private long totalBytesSent = 0;

        private long numWrites = 0;

        private long runStartTime = System.currentTimeMillis();

        private boolean firstRun = true;

        private final AtomicBoolean writeEnabled = new AtomicBoolean(true);

        private final AtomicBoolean running = new AtomicBoolean(false);

        private final CountDownLatch finishLatch = new CountDownLatch(1);

        private final String name;

        private final AtomicInteger nextWriteByte = new AtomicInteger(0);

        public DataSender(String name) {
            this.name = name;
        }

        public void setChannel(Channel channel) {
            this.channel.set(channel);
        }

        public void setWriteEnabled(boolean enabled) {
            writeEnabled.set(enabled);
            if (enabled && !isRunning() && finishLatch.getCount() > 0) {
                executor.execute(this);
            }
        }

        @Override
        public void run() {
            if (!running.compareAndSet(false, true)) {
                logger.warn(String.format(
                        "{0}: Attempt made to run duplicate sender!", name));
                return;
            }

            if (finishLatch.getCount() == 0) {
                logger.error(String.format(
                        "{0}: Attempt made to run after completion!", name));
            }

            if (firstRun) {
                firstRun = false;
                runStartTime = System.currentTimeMillis();
                logger.info(String.format("{0}: sending data", name));
            }

            while (totalBytesSent < BYTES_TO_SEND) {
                if (!writeEnabled.get()) {
                    running.set(false);
                    return;
                }

                ChannelBuffer randomBytesForSend =
                        createRandomSizeBuffer(nextWriteByte);
                totalBytesSent += randomBytesForSend.readableBytes();

                channel.get().write(
                        ChannelBuffers.wrappedBuffer(randomBytesForSend));

                numWrites ++;
                if (numWrites % 100 == 0) {
                    logger.info(String.format(
                            "{0}: {1} writes dispatched, totalling {2} bytes",
                            new Object[] { name, numWrites, totalBytesSent }));
                }
            }

            logger.info(String.format("{0}: completed send cycle", name));

            long runEndTime = System.currentTimeMillis();
            long totalTime = runEndTime - runStartTime;
            long totalKB = totalBytesSent / 1024;
            double rate = totalKB / (totalTime / 1000.0);
            logger.info(String.format("{0}: Sent {1} bytes", new Object[] { name,
                    totalBytesSent }));
            logger.info(String.format("{0}: Average throughput: {1} KB/s",
                    new Object[] { name, rate }));

            finishLatch.countDown();
            running.set(false);
        }

        public boolean isRunning() {
            return running.get();
        }

        public boolean waitForFinish(long timeout, TimeUnit timeoutUnit)
                throws InterruptedException {
            return finishLatch.await(timeout, timeoutUnit);
        }

    }
}
