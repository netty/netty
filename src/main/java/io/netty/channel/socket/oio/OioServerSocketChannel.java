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
package io.netty.channel.socket.oio;

import static io.netty.channel.Channels.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.netty.channel.AbstractServerChannel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelSink;
import io.netty.channel.socket.DefaultServerSocketChannelConfig;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.ServerSocketChannelConfig;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

/**
 *
 * @author <a href="http://netty.io/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 */
class OioServerSocketChannel extends AbstractServerChannel
                             implements ServerSocketChannel {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(OioServerSocketChannel.class);

    final ServerSocket socket;
    final Lock shutdownLock = new ReentrantLock();
    private final ServerSocketChannelConfig config;

    static OioServerSocketChannel create(ChannelFactory factory,
            ChannelPipeline pipeline, ChannelSink sink) {
        OioServerSocketChannel instance =
                new OioServerSocketChannel(factory, pipeline, sink);
        fireChannelOpen(instance);
        return instance;
    }

    private OioServerSocketChannel(
            ChannelFactory factory,
            ChannelPipeline pipeline,
            ChannelSink sink) {

        super(factory, pipeline, sink);

        try {
            socket = new ServerSocket();
        } catch (IOException e) {
            throw new ChannelException(
                    "Failed to open a server socket.", e);
        }

        try {
            socket.setSoTimeout(1000);
        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException e2) {
                logger.warn(
                        "Failed to close a partially initialized socket.", e2);
            }
            throw new ChannelException(
                    "Failed to set the server socket timeout.", e);
        }

        config = new DefaultServerSocketChannelConfig(socket);
    }

    @Override
    public ServerSocketChannelConfig getConfig() {
        return config;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return (InetSocketAddress) socket.getLocalSocketAddress();
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return null;
    }

    @Override
    public boolean isBound() {
        return isOpen() && socket.isBound();
    }

    @Override
    protected boolean setClosed() {
        return super.setClosed();
    }
}
