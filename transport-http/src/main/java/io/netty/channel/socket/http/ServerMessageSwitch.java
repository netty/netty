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
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureAggregator;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.Channels;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

/**
 * This is the gateway between the accepted TCP channels that are used to communicate with the client
 * ends of the http tunnel and the virtual server accepted tunnel. As a tunnel can last for longer than
 * the lifetime of the client channels that are used to service it, this layer of abstraction is
 * necessary.
 */
class ServerMessageSwitch implements ServerMessageSwitchUpstreamInterface,
        ServerMessageSwitchDownstreamInterface {

    private static final InternalLogger LOG = InternalLoggerFactory
            .getInstance(ServerMessageSwitch.class.getName());

    private final String tunnelIdPrefix;

    private final HttpTunnelAcceptedChannelFactory newChannelFactory;

    private final ConcurrentHashMap<String, TunnelInfo> tunnelsById;

    public ServerMessageSwitch(
            HttpTunnelAcceptedChannelFactory newChannelFactory) {
        this.newChannelFactory = newChannelFactory;
        tunnelIdPrefix = Long.toHexString(new Random().nextLong());
        tunnelsById = new ConcurrentHashMap<String, TunnelInfo>();
    }

    @Override
    public String createTunnel(InetSocketAddress remoteAddress) {
        String newTunnelId =
                String.format("%s_%s", tunnelIdPrefix,
                        newChannelFactory.generateTunnelId());
        TunnelInfo newTunnel = new TunnelInfo();
        newTunnel.tunnelId = newTunnelId;
        tunnelsById.put(newTunnelId, newTunnel);
        newTunnel.localChannel =
                newChannelFactory.newChannel(newTunnelId, remoteAddress);
        return newTunnelId;
    }

    @Override
    public boolean isOpenTunnel(String tunnelId) {
        TunnelInfo tunnel = tunnelsById.get(tunnelId);
        return tunnel != null;
    }

    @Override
    public void pollOutboundData(String tunnelId, Channel channel) {
        TunnelInfo tunnel = tunnelsById.get(tunnelId);
        if (tunnel == null) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Poll request for tunnel " + tunnelId +
                        " which does not exist or already closed");
            }
            respondAndClose(channel, HttpTunnelMessageUtils.createRejection(
                    null, "Unknown tunnel, possibly already closed"));
            return;
        }

        if (!tunnel.responseChannel.compareAndSet(null, channel)) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Duplicate poll request detected for tunnel " +
                        tunnelId);
            }
            respondAndClose(channel, HttpTunnelMessageUtils.createRejection(
                    null, "Only one poll request at a time per tunnel allowed"));
            return;
        }

        sendQueuedData(tunnel);
    }

    private void respondAndClose(Channel channel, HttpResponse response) {
        Channels.write(channel, response).addListener(
                ChannelFutureListener.CLOSE);
    }

    private void sendQueuedData(TunnelInfo state) {
        Queue<QueuedResponse> queuedData = state.queuedResponses;
        Channel responseChannel = state.responseChannel.getAndSet(null);
        if (responseChannel == null) {
            // no response channel, or another thread has already used it
            return;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("sending response for tunnel id " + state.tunnelId +
                    " to " + responseChannel.getRemoteAddress());
        }
        QueuedResponse messageToSend = queuedData.poll();
        if (messageToSend == null) {
            // no data to send, restore the response channel and bail out
            state.responseChannel.set(responseChannel);
            return;
        }

        HttpResponse response =
                HttpTunnelMessageUtils
                        .createRecvDataResponse(messageToSend.data);
        final ChannelFuture originalFuture = messageToSend.writeFuture;
        Channels.write(responseChannel, response).addListener(
                new RelayedChannelFutureListener(originalFuture));
    }

    @Override
    public TunnelStatus routeInboundData(String tunnelId,
            ChannelBuffer inboundData) {
        TunnelInfo tunnel = tunnelsById.get(tunnelId);
        if (tunnel == null) {
            return TunnelStatus.CLOSED;
        }

        if (tunnel.closing.get()) {
            // client has now been notified, forget the tunnel
            tunnelsById.remove(tunnel);
            return TunnelStatus.CLOSED;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("routing inbound data for tunnel " + tunnelId);
        }
        tunnel.localChannel.dataReceived(inboundData);
        return TunnelStatus.ALIVE;
    }

    @Override
    public void clientCloseTunnel(String tunnelId) {
        TunnelInfo tunnel = tunnelsById.get(tunnelId);
        if (tunnel == null) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("attempt made to close tunnel id " +
                        tunnelId + " which is unknown or closed");
            }

            return;
        }

        tunnel.localChannel.clientClosed();
        tunnelsById.remove(tunnelId);
    }

    @Override
    public void serverCloseTunnel(String tunnelId) {
        TunnelInfo tunnel = tunnelsById.get(tunnelId);
        if (tunnel == null) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("attempt made to close tunnel id " +
                        tunnelId + " which is unknown or closed");
            }

            return;
        }

        tunnel.closing.set(true);

        Channel responseChannel = tunnel.responseChannel.getAndSet(null);
        if (responseChannel == null) {
            // response channel is already in use, client will be notified
            // of close at next opportunity
            return;
        }

        respondAndClose(responseChannel,
                HttpTunnelMessageUtils.createTunnelCloseResponse());
        // client has been notified, forget the tunnel
        tunnelsById.remove(tunnelId);
    }

    @Override
    public void routeOutboundData(String tunnelId, ChannelBuffer data,
            ChannelFuture writeFuture) {
        TunnelInfo tunnel = tunnelsById.get(tunnelId);
        if (tunnel == null) {
            // tunnel is closed
            if (LOG.isWarnEnabled()) {
                LOG.warn("attempt made to send data out on tunnel id " +
                        tunnelId + " which is unknown or closed");
            }
            return;
        }

        ChannelFutureAggregator aggregator =
                new ChannelFutureAggregator(writeFuture);
        List<ChannelBuffer> fragments =
                WriteSplitter.split(data, HttpTunnelMessageUtils.MAX_BODY_SIZE);

        if (LOG.isDebugEnabled()) {
            LOG.debug("routing outbound data for tunnel " + tunnelId);
        }
        for (ChannelBuffer fragment: fragments) {
            ChannelFuture fragmentFuture =
                    Channels.future(writeFuture.getChannel());
            aggregator.addFuture(fragmentFuture);
            tunnel.queuedResponses.offer(new QueuedResponse(fragment,
                    fragmentFuture));
        }

        sendQueuedData(tunnel);
    }

    /**
     * Used to pass the result received from one ChannelFutureListener to another verbatim.
     */
    private static final class RelayedChannelFutureListener implements
            ChannelFutureListener {
        private final ChannelFuture originalFuture;

        RelayedChannelFutureListener(ChannelFuture originalFuture) {
            this.originalFuture = originalFuture;
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (future.isSuccess()) {
                originalFuture.setSuccess();
            } else {
                originalFuture.setFailure(future.getCause());
            }
        }
    }

    private static final class TunnelInfo {
        TunnelInfo() {
        }

        public String tunnelId;

        public HttpTunnelAcceptedChannelReceiver localChannel;

        public final AtomicReference<Channel> responseChannel =
                new AtomicReference<Channel>(null);

        public final Queue<QueuedResponse> queuedResponses =
                new ConcurrentLinkedQueue<QueuedResponse>();

        public final AtomicBoolean closing = new AtomicBoolean(false);
    }

    private static final class QueuedResponse {
        public ChannelBuffer data;

        public ChannelFuture writeFuture;

        QueuedResponse(ChannelBuffer data, ChannelFuture writeFuture) {
            this.data = data;
            this.writeFuture = writeFuture;
        }
    }
}
