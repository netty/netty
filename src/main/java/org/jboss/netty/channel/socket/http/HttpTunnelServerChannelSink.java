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
package org.jboss.netty.channel.socket.http;

import java.net.SocketAddress;

import org.jboss.netty.channel.AbstractChannelSink;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.socket.ServerSocketChannel;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Iain McGinniss (iain.mcginniss@onedrum.com)
 * @author OneDrum Ltd.
 */
class HttpTunnelServerChannelSink extends AbstractChannelSink {

    private ChannelFutureListener closeHook;

    private ServerSocketChannel realChannel;

    @Override
    public void eventSunk(ChannelPipeline pipeline, ChannelEvent e)
            throws Exception {

        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent ev = (ChannelStateEvent) e;
            switch (ev.getState()) {
            case OPEN:
                if (Boolean.FALSE.equals(ev.getValue())) {
                    realChannel.close().addListener(closeHook);
                }
                break;
            case BOUND:
                if (ev.getValue() != null) {
                    realChannel.bind((SocketAddress) ev.getValue())
                            .addListener(new ChannelFutureProxy(e.getFuture()));
                } else {
                    realChannel.unbind().addListener(
                            new ChannelFutureProxy(e.getFuture()));
                }
                break;
            }
        }
    }

    private static final class ChannelFutureProxy implements ChannelFutureListener {
        private final ChannelFuture upstreamFuture;

        ChannelFutureProxy(ChannelFuture upstreamFuture) {
            this.upstreamFuture = upstreamFuture;
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (future.isSuccess()) {
                upstreamFuture.setSuccess();
            } else {
                upstreamFuture.setFailure(future.getCause());
            }
        }
    }

    public void setRealChannel(ServerSocketChannel realChannel) {
        this.realChannel = realChannel;
    }

    public void setCloseListener(ChannelFutureListener closeHook) {
        this.closeHook = closeHook;
    }
}
