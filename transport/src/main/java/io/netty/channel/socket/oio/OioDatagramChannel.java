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
import java.net.SocketAddress;
import java.net.SocketException;

import io.netty.channel.AbstractChannel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelSink;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramChannelConfig;
import io.netty.channel.socket.DefaultDatagramChannelConfig;

final class OioDatagramChannel extends AbstractChannel
                                implements DatagramChannel {

    final MulticastSocket socket;
    final Object interestOpsLock = new Object();
    private final DatagramChannelConfig config;
    volatile Thread workerThread;
    private volatile InetSocketAddress localAddress;
    volatile InetSocketAddress remoteAddress;

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
    public InetSocketAddress getLocalAddress() {
        InetSocketAddress localAddress = this.localAddress;
        if (localAddress == null) {
            try {
                this.localAddress = localAddress =
                    (InetSocketAddress) socket.getLocalSocketAddress();
            } catch (Throwable t) {
                // Sometimes fails on a closed socket in Windows.
                return null;
            }
        }
        return localAddress;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        InetSocketAddress remoteAddress = this.remoteAddress;
        if (remoteAddress == null) {
            try {
                this.remoteAddress = remoteAddress =
                    (InetSocketAddress) socket.getRemoteSocketAddress();
            } catch (Throwable t) {
                // Sometimes fails on a closed socket in Windows.
                return null;
            }
        }
        return remoteAddress;
    }

    @Override
    public boolean isBound() {
        return isOpen() && socket.isBound();
    }

    @Override
    public boolean isConnected() {
        return isOpen() && socket.isConnected();
    }

    @Override
    protected boolean setClosed() {
        return super.setClosed();
    }

    @Override
    protected void setInterestOpsNow(int interestOps) {
        super.setInterestOpsNow(interestOps);
    }

    @Override
    public ChannelFuture write(Object message, SocketAddress remoteAddress) {
        if (remoteAddress == null || remoteAddress.equals(getRemoteAddress())) {
            return super.write(message, null);
        } else {
            return super.write(message, remoteAddress);
        }
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
}
