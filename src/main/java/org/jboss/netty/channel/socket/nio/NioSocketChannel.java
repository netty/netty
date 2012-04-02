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
package org.jboss.netty.channel.socket.nio;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;

public class NioSocketChannel extends AbstractNioChannel<SocketChannel>
                                implements org.jboss.netty.channel.socket.SocketChannel {

    private static final int ST_OPEN = 0;
    private static final int ST_BOUND = 1;
    private static final int ST_CONNECTED = 2;
    private static final int ST_CLOSED = -1;
    volatile int state = ST_OPEN;

    private final NioSocketChannelConfig config;


    public NioSocketChannel(
            Channel parent, ChannelFactory factory,
            ChannelPipeline pipeline, ChannelSink sink,
            SocketChannel socket, NioWorker worker) {
        super(parent, factory, pipeline, sink, worker, socket);
        config = new DefaultNioSocketChannelConfig(socket.socket());
    }

    @Override
    public NioWorker getWorker() {
        return (NioWorker) super.getWorker();
    }

    public NioSocketChannelConfig getConfig() {
        return config;
    }

    @Override
    public boolean isOpen() {
        return state >= ST_OPEN;
    }

    public boolean isBound() {
        return state >= ST_BOUND;
    }

    public boolean isConnected() {
        return state == ST_CONNECTED;
    }

    final void setBound() {
        assert state == ST_OPEN : "Invalid state: " + state;
        state = ST_BOUND;
    }

    final void setConnected() {
        if (state != ST_CLOSED) {
            state = ST_CONNECTED;
        }
    }

    protected boolean setClosed() {
        state = ST_CLOSED;
        return super.setClosed();
    }


    @Override
    InetSocketAddress getLocalSocketAddress() throws Exception {
        return (InetSocketAddress) channel.socket().getLocalSocketAddress();
    }

    @Override
    InetSocketAddress getRemoteSocketAddress() throws Exception {
        return (InetSocketAddress) channel.socket().getRemoteSocketAddress();
    }
    
    
    @Override
    public ChannelFuture write(Object message, SocketAddress remoteAddress) {
        if (remoteAddress == null || remoteAddress.equals(getRemoteAddress())) {
            return super.write(message, null);
        } else {
            return getUnsupportedOperationFuture();
        }
    }
}
