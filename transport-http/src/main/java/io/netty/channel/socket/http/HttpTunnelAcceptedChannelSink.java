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

import java.util.concurrent.atomic.AtomicBoolean;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.AbstractChannelSink;
import io.netty.channel.Channel;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelStateEvent;
import io.netty.channel.Channels;
import io.netty.channel.MessageEvent;

/**
 * Sink for the server end of an http tunnel. Data sent down through the server end is dispatched
 * from here to the ServerMessageSwitch, which queues the data awaiting a poll request from the
 * client end of the tunnel.
 */
class HttpTunnelAcceptedChannelSink extends AbstractChannelSink {

    final SaturationManager saturationManager;

    private final ServerMessageSwitchDownstreamInterface messageSwitch;

    private final String tunnelId;

    private final AtomicBoolean active = new AtomicBoolean(false);

    private final HttpTunnelAcceptedChannelConfig config;

    public HttpTunnelAcceptedChannelSink(
            ServerMessageSwitchDownstreamInterface messageSwitch,
            String tunnelId, HttpTunnelAcceptedChannelConfig config) {
        this.messageSwitch = messageSwitch;
        this.tunnelId = tunnelId;
        this.config = config;
        saturationManager =
                new SaturationManager(config.getWriteBufferLowWaterMark(),
                        config.getWriteBufferHighWaterMark());
    }

    @Override
    public void eventSunk(ChannelPipeline pipeline, ChannelEvent e)
            throws Exception {
        if (e instanceof MessageEvent) {
            handleMessageEvent((MessageEvent) e);
        } else if (e instanceof ChannelStateEvent) {
            handleStateEvent((ChannelStateEvent) e);
        }
    }

    private void handleMessageEvent(MessageEvent ev) {
        if (!(ev.getMessage() instanceof ChannelBuffer)) {
            throw new IllegalArgumentException(
                    "Attempt to send data which is not a ChannelBuffer:" +
                            ev.getMessage());
        }

        final HttpTunnelAcceptedChannelReceiver channel =
                (HttpTunnelAcceptedChannelReceiver) ev.getChannel();
        final ChannelBuffer message = (ChannelBuffer) ev.getMessage();
        final int messageSize = message.readableBytes();
        final ChannelFuture future = ev.getFuture();

        saturationManager.updateThresholds(config.getWriteBufferLowWaterMark(),
                config.getWriteBufferHighWaterMark());
        channel.updateInterestOps(saturationManager
                .queueSizeChanged(messageSize));
        future.addListener(new ChannelFutureListener() {

            @Override
            public void operationComplete(ChannelFuture future)
                    throws Exception {
                channel.updateInterestOps(saturationManager
                        .queueSizeChanged(-messageSize));
            }
        });
        messageSwitch.routeOutboundData(tunnelId, message, future);
    }

    private void handleStateEvent(ChannelStateEvent ev) {
        /* TODO: as any of disconnect, unbind or close destroys a server
           channel, should we fire all three events always? */
        Channel owner = ev.getChannel();
        switch (ev.getState()) {
        case OPEN:
            if (Boolean.FALSE.equals(ev.getValue())) {
                messageSwitch.serverCloseTunnel(tunnelId);
                active.set(false);
                Channels.fireChannelClosed(owner);
            }
            break;
        case BOUND:
            if (ev.getValue() == null) {
                messageSwitch.serverCloseTunnel(tunnelId);
                active.set(false);
                Channels.fireChannelUnbound(owner);
            }
        case CONNECTED:
            if (ev.getValue() == null) {
                messageSwitch.serverCloseTunnel(tunnelId);
                active.set(false);
                Channels.fireChannelDisconnected(owner);
            }
        }
    }

    public boolean isActive() {
        return active.get();
    }
}
