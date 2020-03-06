/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.transport.selector;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerDomainSocketChannel;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.unix.DomainSocketChannel;
import io.netty.channel.unix.ServerDomainSocketChannel;

import java.util.concurrent.ThreadFactory;

/**
 * Helper class which will hand you the <strong>optimal</strong> implementation of a transport depending on the
 * used platform and the native libraries on the classpath.
 *
 * It is important to note that if you use any of the methods to select the right {@link Channel} you should also use
 * the methods provided by this class to select the right {@link EventLoopGroup}. Otherwise you may run into
 * miss-matches between {@link Channel} and {@link EventLoopGroup} implementations.
 */
public final class TransportSelector {

    private TransportSelector() { }

    /**
     * Returns the {@link SocketChannel} {@link Class} to use on this platform.
     */
    public static Class<? extends SocketChannel> socketChannel() {
        if (Epoll.isAvailable()) {
            return EpollSocketChannel.class;
        }
        if (KQueue.isAvailable()) {
            return KQueueSocketChannel.class;
        }
        return NioSocketChannel.class;
    }

    /**
     * Returns the {@link ServerSocketChannel} {@link Class} to use on this platform.
     */
    public static Class<? extends ServerSocketChannel> serverSocketChannel() {
        if (Epoll.isAvailable()) {
            return EpollServerSocketChannel.class;
        }
        if (KQueue.isAvailable()) {
            return KQueueServerSocketChannel.class;
        }
        return NioServerSocketChannel.class;
    }

    /**
     * Returns the {@link DatagramChannel} {@link Class} to use on this platform.
     */
    public static Class<? extends DatagramChannel> datagramChannel() {
        if (Epoll.isAvailable()) {
            return EpollDatagramChannel.class;
        }
        if (KQueue.isAvailable()) {
            return KQueueDatagramChannel.class;
        }
        return NioDatagramChannel.class;
    }

    /**
     * Returns the {@link DomainSocketChannel} {@link Class} to use on this platform or throws an
     * {@link UnsupportedOperationException} if not supported by the platform.
     */
    public static Class<? extends DomainSocketChannel> domainSocketChannel() {
        if (Epoll.isAvailable()) {
            return EpollDomainSocketChannel.class;
        }
        if (KQueue.isAvailable()) {
            return KQueueDomainSocketChannel.class;
        }
        throw new UnsupportedOperationException("DomainSocketChannel is not supported by this platform");
    }

    /**
     * Returns the {@link ServerDomainSocketChannel} {@link Class} to use on this platform or throws an
     * {@link UnsupportedOperationException} if not supported by the platform.
     */
    public static Class<? extends ServerDomainSocketChannel> serverDomainSocketChannel() {
        if (Epoll.isAvailable()) {
            return EpollServerDomainSocketChannel.class;
        }
        if (KQueue.isAvailable()) {
            return KQueueServerDomainSocketChannel.class;
        }
        throw new UnsupportedOperationException("ServerDomainSocketChannel is not supported by this platform");
    }

    /**
     * Returns a new {@link EventLoopGroup} instance to use on this platform.
     */
    public static EventLoopGroup newEventLoopGroup() {
        if (Epoll.isAvailable()) {
            return new EpollEventLoopGroup();
        }
        if (KQueue.isAvailable()) {
            return new KQueueEventLoopGroup();
        }
        return new NioEventLoopGroup();
    }

    /**
     * Returns a new {@link EventLoopGroup} instance to use on this platform.
     */
    public static EventLoopGroup newEventLoopGroup(int nThreads) {
        if (Epoll.isAvailable()) {
            return new EpollEventLoopGroup(nThreads);
        }
        if (KQueue.isAvailable()) {
            return new KQueueEventLoopGroup(nThreads);
        }
        return new NioEventLoopGroup(nThreads);
    }

    /**
     * Returns a new {@link EventLoopGroup} instance to use on this platform.
     */
    public static EventLoopGroup newEventLoopGroup(int nThreads, ThreadFactory threadFactory) {
        if (Epoll.isAvailable()) {
            return new EpollEventLoopGroup(nThreads, threadFactory);
        }
        if (KQueue.isAvailable()) {
            return new KQueueEventLoopGroup(nThreads, threadFactory);
        }
        return new NioEventLoopGroup(nThreads, threadFactory);
    }
}
