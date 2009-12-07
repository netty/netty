package org.jboss.netty.channel.socket.httptunnel;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

/**
 * @author iain.mcginniss@onedrum.com
 */
public class HttpTunnelSoakTester {

    private static final int SERVER_PORT = 20100;

    static final Logger LOG = Logger.getLogger(HttpTunnelSoakTester.class.getName());

    private final ServerBootstrap serverBootstrap;
    private final ClientBootstrap clientBootstrap;
    final ChannelGroup channels;

    int expectedNextByte = 0;
    int nextWriteByte = 0;

    private final ExecutorService executor;
    final ScheduledExecutorService scheduledExecutor;
    final ConcurrentLinkedQueue<ChannelBuffer> verificationQueue;

    public HttpTunnelSoakTester() {
        try {
            System.in.read();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
        executor = Executors.newCachedThreadPool();
        verificationQueue = new ConcurrentLinkedQueue<ChannelBuffer>();
        ServerSocketChannelFactory serverChannelFactory = new NioServerSocketChannelFactory(executor, executor);
        HttpTunnelServerChannelFactory serverTunnelFactory = new HttpTunnelServerChannelFactory(serverChannelFactory);

        serverBootstrap = new ServerBootstrap(serverTunnelFactory);
        serverBootstrap.setPipelineFactory(createServerPipelineFactory());

        ClientSocketChannelFactory clientChannelFactory = new NioClientSocketChannelFactory(executor, executor);
        HttpTunnelClientChannelFactory clientTunnelFactory = new HttpTunnelClientChannelFactory(clientChannelFactory);

        clientBootstrap = new ClientBootstrap(clientTunnelFactory);
        clientBootstrap.setPipelineFactory(createClientPipelineFactory());
        configureProxy();

        channels = new DefaultChannelGroup();
    }

    private void configureProxy() {
        String proxyHost = System.getProperty("http.proxyHost");
        if(proxyHost != null && proxyHost.length() != 0) {
            int proxyPort = Integer.getInteger("http.proxyPort", 80);
            InetAddress chosenAddress = chooseAddress(proxyHost);
            InetSocketAddress proxyAddress = new InetSocketAddress(chosenAddress, proxyPort);
            if(!proxyAddress.isUnresolved()) {
                clientBootstrap.setOption(HttpTunnelClientChannelConfig.PROXY_ADDRESS_OPTION, proxyAddress);
                System.out.println("Using " + proxyAddress + " as a proxy for this test run");
            } else {
                System.err.println("Failed to resolve proxy address " + proxyAddress);
            }
        } else {
            System.out.println("No proxy specified, will connect to server directly");
        }
    }

    private InetAddress chooseAddress(String proxyHost) {
        try {
            InetAddress[] allByName = InetAddress.getAllByName(proxyHost);
            for(InetAddress address : allByName) {
                if(address.isAnyLocalAddress() || address.isLinkLocalAddress()) {
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

            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("responseVerifier", new ResponseVerifier());
                return pipeline;
            }
        };
    }

    protected ChannelPipelineFactory createServerPipelineFactory() {
        return new ChannelPipelineFactory() {

            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("echo", new EchoHandler());
                return pipeline;
            }
        };
    }

    public void run() {
        LOG.info("binding server channel");
        Channel serverChannel = serverBootstrap.bind(new InetSocketAddress(SERVER_PORT));
        channels.add(serverChannel);
        LOG.log(Level.INFO, "server channel bound to {0}", serverChannel.getLocalAddress());


        Channel clientChannel = createClientChannel();
        if(clientChannel == null) {
            LOG.severe("no client channel - bailing out");
            return;
        }
        channels.add(clientChannel);

        scheduledExecutor.execute(new VerificationTask());

        if(!pushData(clientChannel)) {
            LOG.severe("Data send cycle failed");
        } else {
            LOG.info("send cycle completed successfully");
        }

        LOG.info("closing channels");
        closeChannel(clientChannel);
        closeChannel(serverChannel);

        LOG.info("done!");
    }

    private void closeChannel(Channel channel) {
        try {
            if(!channel.close().await(1000L, TimeUnit.SECONDS)) {
                LOG.warning("Failed to close connection within reasonable amount of time");
            }
        } catch (InterruptedException e) {
            LOG.severe("Interrupted while closing connection");
        }
    }

    private boolean pushData(Channel clientChannel) {
        long totalBytesSent = 0;
        long runStartTime = System.currentTimeMillis();
        for(int i=0; i < 50000; i++) {
            byte[] randomBytesForSend = createRandomSizeByteArray();
            totalBytesSent += randomBytesForSend.length;

            long startTime = System.nanoTime();
            ChannelFuture writeFuture = clientChannel.write(ChannelBuffers.wrappedBuffer(randomBytesForSend));
            try {
                if(!writeFuture.await(10, TimeUnit.SECONDS)) {
                    LOG.log(Level.SEVERE, "Failed to write random array of length {0} in acceptable time", randomBytesForSend.length);
                    return false;
                }
                long endTime = System.nanoTime();
                long totalTime = endTime - startTime;
                if(totalTime > 1000 * 1000000) {
                    LOG.log(Level.WARNING, "Took {0}ns to write {1} bytes", new Object[] { endTime - startTime, randomBytesForSend.length });
                }
            } catch(InterruptedException e) {
                LOG.log(Level.SEVERE, "Interrupted while waiting for bytes to be written", e);
                return false;
            }

            if(i % 100 == 0 && i > 0) {
                LOG.log(Level.INFO, "{0} writes completed, totalling {1} bytes", new Object[] { i, totalBytesSent });
            }
        }

        long runEndTime = System.currentTimeMillis();
        long totalTime = runEndTime - runStartTime;
        long totalKB = totalBytesSent / 1024;
        double rate = totalKB / (totalTime / 1000.0);
        LOG.log(Level.INFO, "Average throughput: {0} KB/s", rate);

        return true;
    }

    private Channel createClientChannel() {
        InetSocketAddress serverAddress = new InetSocketAddress("localhost", SERVER_PORT);
        ChannelFuture clientChannelFuture = clientBootstrap.connect(serverAddress);
        try {
            if(!clientChannelFuture.await(1000, TimeUnit.MILLISECONDS)) {
                LOG.severe("did not connect within acceptable time period");
                return null;
            }
        } catch (InterruptedException e) {
            LOG.severe("Interrupted while waiting for client connect to be established");
            return null;
        }

        if(!clientChannelFuture.isSuccess()) {
            LOG.log(Level.SEVERE, "did not connect successfully", clientChannelFuture.getCause());
            return null;
        }

        return clientChannelFuture.getChannel();
    }

    private byte[] createRandomSizeByteArray() {
        Random random = new Random();
        int arraySize = random.nextInt(64 * 1024) + 1;
        byte[] randomBytes = new byte[arraySize];
        for(int i=0; i < randomBytes.length; i++) {
            randomBytes[i] = (byte)nextWriteByte;
            nextWriteByte = (nextWriteByte + 1) % 127;
        }

        return randomBytes;
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

    @ChannelPipelineCoverage("one")
    private static class EchoHandler extends SimpleChannelUpstreamHandler {

        EchoHandler() {
            super();
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            Channels.write(ctx.getChannel(), e.getMessage());
        }
    }

    @ChannelPipelineCoverage("one")
    private class ResponseVerifier extends SimpleChannelUpstreamHandler {

        ResponseVerifier() {
            super();
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            final ChannelBuffer receivedBytes = (ChannelBuffer) e.getMessage();
            verificationQueue.offer(receivedBytes);
        }

        @Override
        public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
            channels.add(ctx.getChannel());
        }
    }

    private class VerificationTask implements Runnable {

        VerificationTask() {
            super();
        }

        public void run() {
            ChannelBuffer bytesToVerify = verificationQueue.poll();
            if(bytesToVerify == null) {
                scheduledExecutor.schedule(this, 10, TimeUnit.MILLISECONDS);
            }


            while(bytesToVerify.readable()) {
                if(bytesToVerify.readByte() != expectedNextByte) {
                    LOG.severe("received a byte out of sequence");
                    System.exit(-1);
                    return;
                }

                expectedNextByte = (expectedNextByte + 1) % 127;
            }

            scheduledExecutor.execute(this);
        }
    }
}
