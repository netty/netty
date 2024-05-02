/*
 * Copyright 2022 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.ssl.ocsp;

import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoop;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * {@link IoTransport} holds {@link EventLoop}, {@link SocketChannel}
 * and {@link DatagramChannel} for DNS I/O.
 */
public final class IoTransport {
    private final EventLoop eventLoop;
    private final ChannelFactory<SocketChannel> socketChannel;
    private final ChannelFactory<DatagramChannel> datagramChannel;

    /**
     * Default {@link IoTransport} which uses {@link NioIoHandler}, {@link NioSocketChannel}
     * and {@link NioDatagramChannel}.
     */
    public static final IoTransport DEFAULT = new IoTransport(
            new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory()).next(),
            new ChannelFactory<SocketChannel>() {
                @Override
                public SocketChannel newChannel() {
                    return new NioSocketChannel();
                }
            },
            new ChannelFactory<DatagramChannel>() {
                @Override
                public DatagramChannel newChannel() {
                    return new NioDatagramChannel();
                }
            });

    /**
     * Create a new {@link IoTransport} instance
     *
     * @param eventLoop       {@link EventLoop} to use for I/O
     * @param socketChannel   {@link SocketChannel} for TCP DNS lookup and OCSP query
     * @param datagramChannel {@link DatagramChannel} for UDP DNS lookup
     * @return {@link NullPointerException} if any parameter is {@code null}
     */
    public static IoTransport create(EventLoop eventLoop, ChannelFactory<SocketChannel> socketChannel,
                                     ChannelFactory<DatagramChannel> datagramChannel) {
        return new IoTransport(eventLoop, socketChannel, datagramChannel);
    }

    private IoTransport(EventLoop eventLoop, ChannelFactory<SocketChannel> socketChannel,
                        ChannelFactory<DatagramChannel> datagramChannel) {
        this.eventLoop = checkNotNull(eventLoop, "EventLoop");
        this.socketChannel = checkNotNull(socketChannel, "SocketChannel");
        this.datagramChannel = checkNotNull(datagramChannel, "DatagramChannel");
    }

    public EventLoop eventLoop() {
        return eventLoop;
    }

    public ChannelFactory<SocketChannel> socketChannel() {
        return socketChannel;
    }

    public ChannelFactory<DatagramChannel> datagramChannel() {
        return datagramChannel;
    }
}
