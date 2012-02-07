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

import java.net.SocketAddress;

import io.netty.channel.AbstractChannelSink;
import io.netty.channel.ChannelEvent;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelStateEvent;
import io.netty.channel.socket.ServerSocketChannel;

/**
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
