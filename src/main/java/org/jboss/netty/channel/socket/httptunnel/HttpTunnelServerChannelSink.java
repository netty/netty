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
 * @version $Rev$, $Date$
 */
class HttpTunnelServerChannelSink extends AbstractChannelSink {

    private ServerSocketChannel realChannel;
    
    public void eventSunk(ChannelPipeline pipeline, ChannelEvent e) throws Exception {
        
        if(e instanceof ChannelStateEvent) {
            ChannelStateEvent ev = (ChannelStateEvent) e;
            switch(ev.getState()) {
            case OPEN:
                if(Boolean.FALSE.equals(ev.getValue())) {
                    realChannel.close().addListener(new ChannelFutureProxy(e.getFuture()));
                }
                break;
            case BOUND:
                if(ev.getValue() != null) {
                    realChannel.bind((SocketAddress)ev.getValue()).addListener(new ChannelFutureProxy(e.getFuture()));
                } else {
                    realChannel.unbind().addListener(new ChannelFutureProxy(e.getFuture()));
                }
                break;
            }
        }
    }

    private final class ChannelFutureProxy implements ChannelFutureListener {
        private final ChannelFuture upstreamFuture;

        private ChannelFutureProxy(ChannelFuture upstreamFuture) {
            this.upstreamFuture = upstreamFuture;
        }

        public void operationComplete(ChannelFuture future) throws Exception {
            if(future.isSuccess()) {
                upstreamFuture.setSuccess();
            } else {
                upstreamFuture.setFailure(future.getCause());
            }
        }
    }

    public void setRealChannel(ServerSocketChannel realChannel) {
        this.realChannel = realChannel;
    }
}
