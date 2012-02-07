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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.AbstractChannel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.Channels;
import io.netty.channel.MessageEvent;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.socket.ClientSocketChannelFactory;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpChunkAggregator;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

/**
 * The client end of an HTTP tunnel, created by an {@link HttpTunnelClientChannelFactory}. Channels of
 * this type are designed to emulate a normal TCP based socket channel as far as is feasible within the limitations
 * of the HTTP 1.1 protocol, and the usage patterns permitted by commonly used HTTP proxies and firewalls.
 */
final class HttpTunnelClientChannel extends AbstractChannel implements
        SocketChannel {

    static final InternalLogger LOG = InternalLoggerFactory
            .getInstance(HttpTunnelClientChannel.class);

    private final HttpTunnelClientChannelConfig config;

    final SocketChannel sendChannel;

    final SocketChannel pollChannel;

    volatile String tunnelId;

    volatile ChannelFuture connectFuture;

    volatile boolean connected;

    volatile boolean bound;

    volatile InetSocketAddress serverAddress;

    volatile String serverHostName;

    private final WorkerCallbacks callbackProxy;

    private final SaturationManager saturationManager;

    protected static HttpTunnelClientChannel create(ChannelFactory factory,
            ChannelPipeline pipeline, HttpTunnelClientChannelSink sink,
            ClientSocketChannelFactory outboundFactory,
            ChannelGroup realConnections) {
        HttpTunnelClientChannel instance = new HttpTunnelClientChannel(factory, pipeline, sink,
                        outboundFactory, realConnections);
        Channels.fireChannelOpen(instance);
        return instance;
    }

    /**
     * @see HttpTunnelClientChannelFactory#newChannel(ChannelPipeline)
     */
    private HttpTunnelClientChannel(ChannelFactory factory,
            ChannelPipeline pipeline, HttpTunnelClientChannelSink sink,
            ClientSocketChannelFactory outboundFactory,
            ChannelGroup realConnections) {
        super(null, factory, pipeline, sink);

        callbackProxy = new WorkerCallbacks();

        sendChannel = outboundFactory.newChannel(createSendPipeline());
        pollChannel = outboundFactory.newChannel(createPollPipeline());
        config =
                new HttpTunnelClientChannelConfig(sendChannel.getConfig(),
                        pollChannel.getConfig());
        saturationManager =
                new SaturationManager(config.getWriteBufferLowWaterMark(),
                        config.getWriteBufferHighWaterMark());
        serverAddress = null;

        realConnections.add(sendChannel);
        realConnections.add(pollChannel);

    }

    @Override
    public HttpTunnelClientChannelConfig getConfig() {
        return config;
    }

    @Override
    public boolean isBound() {
        return bound;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return sendChannel.getLocalAddress();
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return serverAddress;
    }

    @Override
    protected boolean setClosed() {
        return super.setClosed();
    }

    void onConnectRequest(ChannelFuture connectFuture,
            InetSocketAddress remoteAddress) {
        this.connectFuture = connectFuture;
        /* if we are using a proxy, the remoteAddress is swapped here for the address of the proxy.
         * The send and poll channels can later ask for the correct server address using
         * getServerHostName().
         */
        serverAddress = remoteAddress;

        SocketAddress connectTarget;
        if (config.getProxyAddress() != null) {
            connectTarget = config.getProxyAddress();
        } else {
            connectTarget = remoteAddress;
        }

        Channels.connect(sendChannel, connectTarget);
    }

    void onDisconnectRequest(final ChannelFuture disconnectFuture) {
        ChannelFutureListener disconnectListener =
                new ConsolidatingFutureListener(disconnectFuture, 2);
        sendChannel.disconnect().addListener(disconnectListener);
        pollChannel.disconnect().addListener(disconnectListener);

        disconnectFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future)
                    throws Exception {
                serverAddress = null;
            }
        });
    }

    void onBindRequest(InetSocketAddress localAddress,
            final ChannelFuture bindFuture) {
        ChannelFutureListener bindListener =
                new ConsolidatingFutureListener(bindFuture, 2);
        // bind the send channel to the specified local address, and the poll channel to
        // an ephemeral port on the same interface as the send channel
        sendChannel.bind(localAddress).addListener(bindListener);
        InetSocketAddress pollBindAddress;
        if (localAddress.isUnresolved()) {
            pollBindAddress =
                    InetSocketAddress.createUnresolved(
                            localAddress.getHostName(), 0);
        } else {
            pollBindAddress =
                    new InetSocketAddress(localAddress.getAddress(), 0);
        }
        pollChannel.bind(pollBindAddress).addListener(bindListener);
    }

    void onUnbindRequest(final ChannelFuture unbindFuture) {
        ChannelFutureListener unbindListener =
                new ConsolidatingFutureListener(unbindFuture, 2);
        sendChannel.unbind().addListener(unbindListener);
        pollChannel.unbind().addListener(unbindListener);
    }

    void onCloseRequest(final ChannelFuture closeFuture) {
        ChannelFutureListener closeListener =
                new CloseConsolidatingFutureListener(closeFuture, 2);
        sendChannel.close().addListener(closeListener);
        pollChannel.close().addListener(closeListener);
    }

    private ChannelPipeline createSendPipeline() {
        ChannelPipeline pipeline = Channels.pipeline();

        pipeline.addLast("reqencoder", new HttpRequestEncoder()); // downstream
        pipeline.addLast("respdecoder", new HttpResponseDecoder()); // upstream
        pipeline.addLast("aggregator", new HttpChunkAggregator(
                HttpTunnelMessageUtils.MAX_BODY_SIZE)); // upstream
        pipeline.addLast("sendHandler", new HttpTunnelClientSendHandler(
                callbackProxy)); // both
        pipeline.addLast("writeFragmenter", new WriteFragmenter(
                HttpTunnelMessageUtils.MAX_BODY_SIZE));

        return pipeline;
    }

    private ChannelPipeline createPollPipeline() {
        ChannelPipeline pipeline = Channels.pipeline();

        pipeline.addLast("reqencoder", new HttpRequestEncoder()); // downstream
        pipeline.addLast("respdecoder", new HttpResponseDecoder()); // upstream
        pipeline.addLast("aggregator", new HttpChunkAggregator(
                HttpTunnelMessageUtils.MAX_BODY_SIZE)); // upstream
        pipeline.addLast(HttpTunnelClientPollHandler.NAME,
                new HttpTunnelClientPollHandler(callbackProxy)); // both

        return pipeline;
    }

    void setTunnelIdForPollChannel() {
        HttpTunnelClientPollHandler pollHandler =
                pollChannel.getPipeline()
                        .get(HttpTunnelClientPollHandler.class);
        pollHandler.setTunnelId(tunnelId);
    }

    void sendData(final MessageEvent e) {
        saturationManager.updateThresholds(config.getWriteBufferLowWaterMark(),
                config.getWriteBufferHighWaterMark());
        final ChannelFuture originalFuture = e.getFuture();
        final ChannelBuffer message = (ChannelBuffer) e.getMessage();
        final int messageSize = message.readableBytes();
        updateSaturationStatus(messageSize);
        Channels.write(sendChannel, e.getMessage()).addListener(
                new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future)
                            throws Exception {
                        if (future.isSuccess()) {
                            originalFuture.setSuccess();
                        } else {
                            originalFuture.setFailure(future.getCause());
                        }
                        updateSaturationStatus(-messageSize);
                    }
                });
    }

    void updateSaturationStatus(int queueSizeDelta) {
        SaturationStateChange transition =
                saturationManager.queueSizeChanged(queueSizeDelta);
        switch (transition) {
        case SATURATED:
            fireWriteEnabled(false);
            break;
        case DESATURATED:
            fireWriteEnabled(true);
            break;
        case NO_CHANGE:
            break;
        }
    }

    private void fireWriteEnabled(boolean enabled) {
        int ops = OP_READ;
        if (!enabled) {
            ops |= OP_WRITE;
        }

        setInterestOpsNow(ops);
        Channels.fireChannelInterestChanged(this);
    }

    private static class ConsolidatingFutureListener implements ChannelFutureListener {

        private final ChannelFuture completionFuture;

        private final AtomicInteger eventsLeft;

        public ConsolidatingFutureListener(ChannelFuture completionFuture,
                int numToConsolidate) {
            this.completionFuture = completionFuture;
            eventsLeft = new AtomicInteger(numToConsolidate);
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (!future.isSuccess()) {
                futureFailed(future);
            } else {
                if (eventsLeft.decrementAndGet() == 0) {
                    allFuturesComplete();
                }
            }
        }

        protected void allFuturesComplete() {
            completionFuture.setSuccess();
        }

        protected void futureFailed(ChannelFuture future) {
            completionFuture.setFailure(future.getCause());
        }
    }

    /**
     * Close futures are a special case, as marking them as successful or failed has no effect.
     * Instead, we must call setClosed() on the channel itself, once all the child channels are
     * closed or if we fail to close them for whatever reason.
     */
    private final class CloseConsolidatingFutureListener extends
            ConsolidatingFutureListener {

        public CloseConsolidatingFutureListener(ChannelFuture completionFuture,
                int numToConsolidate) {
            super(completionFuture, numToConsolidate);
        }

        @Override
        protected void futureFailed(ChannelFuture future) {
            LOG.warn("Failed to close one of the child channels of tunnel " +
                    tunnelId);
            setClosed();
        }

        @Override
        protected void allFuturesComplete() {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Tunnel " + tunnelId + " closed");
            }
            setClosed();
        }

    }

    /**
     * Contains the implementing methods of HttpTunnelClientWorkerOwner, so that these are hidden
     * from the public API.
     */
    class WorkerCallbacks implements HttpTunnelClientWorkerOwner {

        @Override
        public void onConnectRequest(ChannelFuture connectFuture,
                InetSocketAddress remoteAddress) {
            HttpTunnelClientChannel.this.onConnectRequest(connectFuture,
                    remoteAddress);
        }

        @Override
        public void onTunnelOpened(String tunnelId) {
            HttpTunnelClientChannel.this.tunnelId = tunnelId;
            setTunnelIdForPollChannel();
            Channels.connect(pollChannel, sendChannel.getRemoteAddress());
        }

        @Override
        public void fullyEstablished() {
            if (!bound) {
                bound = true;
                Channels.fireChannelBound(HttpTunnelClientChannel.this,
                        getLocalAddress());
            }

            connected = true;
            connectFuture.setSuccess();
            Channels.fireChannelConnected(HttpTunnelClientChannel.this,
                    getRemoteAddress());
        }

        @Override
        public void onMessageReceived(ChannelBuffer content) {
            Channels.fireMessageReceived(HttpTunnelClientChannel.this, content);
        }

        @Override
        public String getServerHostName() {
            if (serverHostName == null) {
                serverHostName =
                        HttpTunnelMessageUtils
                                .convertToHostString(serverAddress);
            }

            return serverHostName;
        }

    }
}
