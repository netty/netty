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

import io.netty.channel.AbstractChannelSink;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelStateEvent;
import io.netty.channel.MessageEvent;

/**
 * Sink of a client channel, deals with sunk events and then makes appropriate calls
 * on the channel itself to push data.
 */
class HttpTunnelClientChannelSink extends AbstractChannelSink {

    @Override
    public void eventSunk(ChannelPipeline pipeline, ChannelEvent e)
            throws Exception {
        if (e instanceof ChannelStateEvent) {
            handleChannelStateEvent((ChannelStateEvent) e);
        } else if (e instanceof MessageEvent) {
            handleMessageEvent((MessageEvent) e);
        }
    }

    private void handleMessageEvent(MessageEvent e) {
        HttpTunnelClientChannel channel =
                (HttpTunnelClientChannel) e.getChannel();
        channel.sendData(e);
    }

    private void handleChannelStateEvent(ChannelStateEvent e) {
        HttpTunnelClientChannel channel =
                (HttpTunnelClientChannel) e.getChannel();

        switch (e.getState()) {
        case CONNECTED:
            if (e.getValue() != null) {
                channel.onConnectRequest(e.getFuture(),
                        (InetSocketAddress) e.getValue());
            } else {
                channel.onDisconnectRequest(e.getFuture());
            }
            break;
        case BOUND:
            if (e.getValue() != null) {
                channel.onBindRequest((InetSocketAddress) e.getValue(),
                        e.getFuture());
            } else {
                channel.onUnbindRequest(e.getFuture());
            }
            break;
        case OPEN:
            if (Boolean.FALSE.equals(e.getValue())) {
                channel.onCloseRequest(e.getFuture());
            }
            break;
        }
    }
}
