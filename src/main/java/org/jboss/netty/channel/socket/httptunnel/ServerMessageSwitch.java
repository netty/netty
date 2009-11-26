/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel.socket.httptunnel;

import java.net.SocketAddress;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @version $Rev$, $Date$
 */
class ServerMessageSwitch implements ServerMessageSwitchUpstreamInterface, ServerMessageSwitchDownstreamInterface {

    private static final InternalLogger LOG = InternalLoggerFactory.getInstance(ServerMessageSwitch.class.getName());

    private final String tunnelIdPrefix;
    private final AtomicInteger tunnelIdSequence;

    private final HttpTunnelAcceptedChannelFactory newChannelFactory;
    private final ConcurrentHashMap<String, TunnelInfo> tunnelsById;

    public ServerMessageSwitch(HttpTunnelAcceptedChannelFactory newChannelFactory) {
        this.newChannelFactory = newChannelFactory;
        tunnelIdPrefix = Long.toHexString(new Random().nextLong());
        tunnelIdSequence = new AtomicInteger(0);
        tunnelsById = new ConcurrentHashMap<String, TunnelInfo>();
    }

    public String createTunnel(SocketAddress remoteAddress) {
        // FIXME Tunnel ID must be generated from SecureRandom by default.
        //       Add HttpTunnelServerChannelConfig.tunnelIdGenerator
        String newTunnelId = tunnelIdPrefix + '_' + tunnelIdSequence.incrementAndGet();
        TunnelInfo newTunnel = new TunnelInfo();
        newTunnel.tunnelId = newTunnelId;
        tunnelsById.put(newTunnelId, newTunnel);
        newTunnel.localChannel = newChannelFactory.newChannel(newTunnelId, remoteAddress);
        return newTunnelId;
    }

    public boolean isOpenTunnel(String tunnelId) {
        TunnelInfo tunnel = tunnelsById.get(tunnelId);
        return tunnel != null;
    }

    public void pollOutboundData(String tunnelId, Channel channel) {
        TunnelInfo tunnel = tunnelsById.get(tunnelId);
        if (tunnel == null) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Poll request for tunnel " + tunnelId + " which does not exist or already closed");
            }
            Channels.write(channel, HttpTunnelMessageUtils.createRejection(null, "Unknown tunnel, possibly already closed"));
            return;
        }

        if (!tunnel.responseChannel.compareAndSet(null, channel)) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Duplicate poll request detected for tunnel " + tunnelId);
            }
            Channels.write(channel, HttpTunnelMessageUtils.createRejection(null, "Only one poll request at a time per tunnel allowed"));
            return;
        }

        sendQueuedData(tunnel);
    }

    private void sendQueuedData(TunnelInfo state) {
        ConcurrentLinkedQueue<QueuedResponse> queuedData = state.queuedResponses;
        if (queuedData.peek() == null) {
            // no data to send, or it has already been dealt with by another thread
            return;
        }

        Channel responseChannel = state.responseChannel.getAndSet(null);
        if (responseChannel == null) {
            // no response channel, or another thread has already used it
            return;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("sending response for tunnel id " + state.tunnelId + " to " + responseChannel.getRemoteAddress());
        }
        QueuedResponse messageToSend = queuedData.poll();
        HttpResponse response = HttpTunnelMessageUtils.createRecvDataResponse(messageToSend.data);
        final ChannelFuture originalFuture = messageToSend.writeFuture;
        Channels.write(responseChannel, response).addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    originalFuture.setSuccess();
                } else {
                    originalFuture.setFailure(future.getCause());
                }
            }
        });
    }

    public void routeInboundData(String tunnelId, ChannelBuffer inboundData) {
        TunnelInfo tunnel = tunnelsById.get(tunnelId);
        if (tunnel == null) {
            return;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("routing inbound data for tunnel " + tunnelId);
        }
        Channels.fireMessageReceived(tunnel.localChannel, inboundData);
    }

    public void closeTunnel(String tunnelId) {
        tunnelsById.remove(tunnelId);
    }

    public void routeOutboundData(String tunnelId, ChannelBuffer data, ChannelFuture writeFuture) {
        TunnelInfo tunnel = tunnelsById.get(tunnelId);
        if (tunnel == null) {
            // tunnel is closed
            if (LOG.isWarnEnabled()) {
                LOG.warn("attempt made to send data out on tunnel id " + tunnelId + " which is unknown or closed");
            }
            return;
        }

        WriteSplitter splitter = new WriteSplitter(HttpTunnelMessageUtils.MAX_BODY_SIZE);

        ChannelFutureAggregator aggregator = new ChannelFutureAggregator(writeFuture);
        List<ChannelBuffer> fragments = splitter.split(data);

        if (LOG.isDebugEnabled()) {
            LOG.debug("routing outbound data for tunnel " + tunnelId);
        }
        for(ChannelBuffer fragment : fragments) {
            ChannelFuture fragmentFuture = Channels.future(writeFuture.getChannel());
            aggregator.addFuture(fragmentFuture);
            tunnel.queuedResponses.offer(new QueuedResponse(fragment, fragmentFuture));
        }

        sendQueuedData(tunnel);
    }

    private static final class TunnelInfo {
        public String tunnelId;
        public Channel localChannel;
        public AtomicReference<Channel> responseChannel = new AtomicReference<Channel>(null);
        public ConcurrentLinkedQueue<QueuedResponse> queuedResponses = new ConcurrentLinkedQueue<QueuedResponse>();

        TunnelInfo() {
            super();
        }
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
