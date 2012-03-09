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
package org.jboss.netty.channel.socket.oio;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.socket.DefaultSocketChannelConfig;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.channel.socket.SocketChannelConfig;

abstract class OioSocketChannel extends AbstractOioChannel
                                implements SocketChannel {

    final Socket socket;
    private final SocketChannelConfig config;

    OioSocketChannel(
            Channel parent,
            ChannelFactory factory,
            ChannelPipeline pipeline,
            ChannelSink sink,
            Socket socket) {

        super(parent, factory, pipeline, sink);

        this.socket = socket;
        config = new DefaultSocketChannelConfig(socket);
    }

    public SocketChannelConfig getConfig() {
        return config;
    }

    abstract PushbackInputStream getInputStream();
    abstract OutputStream getOutputStream();

    @Override
    boolean isSocketBound() {
        return socket.isBound();
    }

    @Override
    boolean isSocketConnected() {
        return socket.isConnected();
    }

    @Override
    InetSocketAddress getLocalSocketAddress() throws Exception {
        return (InetSocketAddress) socket.getLocalSocketAddress();
    }

    @Override
    InetSocketAddress getRemoteSocketAddress() throws Exception {
        return (InetSocketAddress) socket.getRemoteSocketAddress();
    }

    @Override
    void closeSocket() throws IOException {
        socket.close();
    }

    @Override
    boolean isSocketClosed() {
        return socket.isClosed();
    }
}
