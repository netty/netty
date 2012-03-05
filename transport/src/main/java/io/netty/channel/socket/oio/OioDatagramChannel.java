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
package io.netty.channel.socket.oio;

import static io.netty.channel.Channels.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;

import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelSink;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramChannelConfig;
import io.netty.channel.socket.DefaultDatagramChannelConfig;

final class OioDatagramChannel extends AbstractOioChannel
                                implements DatagramChannel {

    final MulticastSocket socket;
    private final DatagramChannelConfig config;


    static OioDatagramChannel create(ChannelFactory factory,
            ChannelPipeline pipeline, ChannelSink sink) {
        OioDatagramChannel instance =
                new OioDatagramChannel(factory, pipeline, sink);
        fireChannelOpen(instance);
        return instance;
    }

    private OioDatagramChannel(
            ChannelFactory factory,
            ChannelPipeline pipeline,
            ChannelSink sink) {

        super(null, factory, pipeline, sink);

        try {
            socket = new MulticastSocket(null);
        } catch (IOException e) {
            throw new ChannelException("Failed to open a datagram socket.", e);
        }

        try {
            socket.setSoTimeout(10);
            socket.setBroadcast(false);
        } catch (SocketException e) {
            throw new ChannelException(
                    "Failed to configure the datagram socket timeout.", e);
        }
        config = new DefaultDatagramChannelConfig(socket);
    }

    @Override
    public DatagramChannelConfig getConfig() {
        return config;
    }

    @Override
    public void joinGroup(InetAddress multicastAddress) {
        ensureBound();
        try {
            socket.joinGroup(multicastAddress);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void joinGroup(
            InetSocketAddress multicastAddress, NetworkInterface networkInterface) {
        ensureBound();
        try {
            socket.joinGroup(multicastAddress, networkInterface);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    private void ensureBound() {
        if (!isBound()) {
            throw new IllegalStateException(
                    DatagramChannel.class.getName() +
                    " must be bound to join a group.");
        }
    }

    @Override
    public void leaveGroup(InetAddress multicastAddress) {
        try {
            socket.leaveGroup(multicastAddress);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    public void leaveGroup(
            InetSocketAddress multicastAddress, NetworkInterface networkInterface) {
        try {
            socket.leaveGroup(multicastAddress, networkInterface);
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

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
