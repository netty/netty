/*
 * Copyright 2026 The Netty Project
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
package io.netty.transport.selector;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketProtocolFamily;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Contains the selected transport's {@link EventLoopGroup} and {@link Channel} classes,
 * along with convenience methods for configuration, channel creation, and instantiation.
 */
public final class TransportSelection {

    private final TransportType type;
    private final IoHandlerFactory ioHandlerFactory;
    private final Class<? extends SocketChannel> socketChannelClass;
    private final Supplier<SocketChannel> socketChannelFactory;
    private final Class<? extends ServerSocketChannel> serverSocketChannelClass;
    private final Supplier<ServerSocketChannel> serverSocketChannelFactory;
    private final Class<? extends DatagramChannel> datagramChannelClass;
    private final Function<SocketProtocolFamily, DatagramChannel> datagramChannelFactory;
    private final boolean domainSocketsSupported;
    private final Class<? extends Channel> domainSocketChannelClass;
    private final Supplier<Channel> domainSocketChannelFactory;
    private final Class<? extends ServerChannel> serverDomainSocketChannelClass;
    private final Supplier<ServerChannel> serverDomainSocketChannelFactory;

    TransportSelection(
            TransportType type,
            IoHandlerFactory ioHandlerFactory,
            Class<? extends SocketChannel> socketChannelClass,
            Supplier<SocketChannel> socketChannelFactory,
            Class<? extends ServerSocketChannel> serverSocketChannelClass,
            Supplier<ServerSocketChannel> serverSocketChannelFactory,
            Class<? extends DatagramChannel> datagramChannelClass,
            Function<SocketProtocolFamily, DatagramChannel> datagramChannelFactory,
            boolean domainSocketsSupported,
            Class<? extends Channel> domainSocketChannelClass,
            Supplier<Channel> domainSocketChannelFactory,
            Class<? extends ServerChannel> serverDomainSocketChannelClass,
            Supplier<ServerChannel> serverDomainSocketChannelFactory) {
        this.type = type;
        this.ioHandlerFactory = ioHandlerFactory;
        this.socketChannelClass = socketChannelClass;
        this.socketChannelFactory = socketChannelFactory;
        this.serverSocketChannelClass = serverSocketChannelClass;
        this.serverSocketChannelFactory = serverSocketChannelFactory;
        this.datagramChannelClass = datagramChannelClass;
        this.datagramChannelFactory = datagramChannelFactory;
        this.domainSocketsSupported = domainSocketsSupported;
        this.domainSocketChannelClass = domainSocketChannelClass;
        this.domainSocketChannelFactory = domainSocketChannelFactory;
        this.serverDomainSocketChannelClass = serverDomainSocketChannelClass;
        this.serverDomainSocketChannelFactory = serverDomainSocketChannelFactory;
    }

    /**
     * Returns the {@link TransportType} selected for the current platform.
     *
     * @return the selected transport type
     */
    public @NotNull TransportType type() {
        return type;
    }

    /**
     * Creates a new {@link EventLoopGroup} with the selected transport,
     * using the default number of threads.
     *
     * @return a new {@link EventLoopGroup} instance
     */
    public @NotNull EventLoopGroup newEventLoopGroup() {
        return new MultiThreadIoEventLoopGroup(ioHandlerFactory);
    }

    /**
     * Creates a new {@link EventLoopGroup} with the selected transport
     * and the specified number of threads.
     *
     * @param nThreads the number of threads to use, or {@code 0} for the default
     * @return a new {@link EventLoopGroup} instance
     */
    public @NotNull EventLoopGroup newEventLoopGroup(int nThreads) {
        return new MultiThreadIoEventLoopGroup(nThreads, ioHandlerFactory);
    }

    /**
     * Creates a new {@link EventLoopGroup} with the selected transport,
     * using the given {@link ThreadFactory} and the default number of threads.
     *
     * @param threadFactory the {@link ThreadFactory} used for thread creation
     * @return a new {@link EventLoopGroup} instance
     */
    public @NotNull EventLoopGroup newEventLoopGroup(@NotNull ThreadFactory threadFactory) {
        return new MultiThreadIoEventLoopGroup(threadFactory, ioHandlerFactory);
    }

    /**
     * Creates a new {@link EventLoopGroup} with the selected transport,
     * the given {@link ThreadFactory}, and the specified number of threads.
     *
     * @param threadFactory the {@link ThreadFactory} used for thread creation
     * @param nThreads the number of threads to use, or {@code 0} for the default
     * @return a new {@link EventLoopGroup} instance
     */
    public @NotNull EventLoopGroup newEventLoopGroup(@NotNull ThreadFactory threadFactory, int nThreads) {
        return new MultiThreadIoEventLoopGroup(nThreads, threadFactory, ioHandlerFactory);
    }

    /**
     * Creates a new {@link EventLoopGroup} with the selected transport,
     * using the given {@link Executor} and the default number of threads.
     *
     * @param executor the {@link Executor} used to run the event loops
     * @return a new {@link EventLoopGroup} instance
     */
    public @NotNull EventLoopGroup newEventLoopGroup(@NotNull Executor executor) {
        return new MultiThreadIoEventLoopGroup(executor, ioHandlerFactory);
    }

    /**
     * Creates a new {@link EventLoopGroup} with the selected transport,
     * the given {@link Executor}, and the specified number of threads.
     *
     * @param executor the {@link Executor} used to run the event loops
     * @param nThreads the number of threads to use, or {@code 0} for the default
     * @return a new {@link EventLoopGroup} instance
     */
    public @NotNull EventLoopGroup newEventLoopGroup(@NotNull Executor executor, int nThreads) {
        return new MultiThreadIoEventLoopGroup(nThreads, executor, ioHandlerFactory);
    }

    /**
     * Returns the {@link SocketChannel} class for the selected transport.
     *
     * @return the {@link SocketChannel} implementation class
     */
    public @NotNull Class<? extends SocketChannel> socketChannelClass() {
        return socketChannelClass;
    }

    /**
     * Returns a {@link ChannelFactory} for creating {@link SocketChannel} instances
     * using the selected transport.
     *
     * @return a new {@link ChannelFactory} for {@link SocketChannel}
     */
    public @NotNull ChannelFactory<? extends SocketChannel> socketChannelFactory() {
        return socketChannelFactory::get;
    }

    /**
     * Returns the {@link ServerSocketChannel} class for the selected transport.
     *
     * @return the {@link ServerSocketChannel} implementation class
     */
    public @NotNull Class<? extends ServerSocketChannel> serverSocketChannelClass() {
        return serverSocketChannelClass;
    }

    /**
     * Returns a {@link ChannelFactory} for creating {@link ServerSocketChannel} instances
     * using the selected transport.
     *
     * @return a new {@link ChannelFactory} for {@link ServerSocketChannel}
     */
    public @NotNull ChannelFactory<? extends ServerSocketChannel> serverSocketChannelFactory() {
        return serverSocketChannelFactory::get;
    }

    /**
     * Returns the {@link DatagramChannel} class for the selected transport.
     *
     * @return the {@link DatagramChannel} implementation class
     */
    public @NotNull Class<? extends DatagramChannel> datagramChannelClass() {
        return datagramChannelClass;
    }

    /**
     * Returns a {@link ChannelFactory} for creating {@link DatagramChannel} instances
     * using the selected transport and the given {@link SocketProtocolFamily}.
     *
     * @param family the protocol family to use, or {@code null} for the OS default
     * @return a new {@link ChannelFactory} for {@link DatagramChannel}
     */
    public @NotNull ChannelFactory<? extends DatagramChannel> datagramChannelFactory(
            @Nullable SocketProtocolFamily family) {
        return () -> datagramChannelFactory.apply(family);
    }

    /**
     * Returns {@code true} if the selected transport supports Unix domain sockets.
     *
     * @return {@code true} if domain sockets are supported
     */
    public boolean isDomainSocketSupported() {
        return domainSocketsSupported;
    }

    /**
     * Returns the Unix domain socket {@link Channel} class for the selected transport.
     * Note that the returned class is not guaranteed to implement
     * {@link io.netty.channel.unix.DomainSocketChannel} (e.g. the NIO transport returns
     * a plain {@link io.netty.channel.socket.DuplexChannel} implementation). Use
     * {@link #isDomainSocketSupported()} to check whether the selected transport supports
     * Unix domain sockets.
     *
     * @return the domain socket {@link Channel} implementation class
     */
    public @NotNull Class<? extends Channel> domainSocketChannelClass() {
        return domainSocketChannelClass;
    }

    /**
     * Returns a {@link ChannelFactory} for creating Unix domain socket {@link Channel}
     * instances using the selected transport. Use {@link #isDomainSocketSupported()} to
     * check whether the selected transport supports Unix domain sockets.
     *
     * @return a new {@link ChannelFactory} for domain sockets
     */
    public @NotNull ChannelFactory<? extends Channel> domainSocketChannelFactory() {
        return domainSocketChannelFactory::get;
    }

    /**
     * Returns the Unix domain server socket {@link ServerChannel} class for the selected
     * transport. Use {@link #isDomainSocketSupported()} to check whether the selected
     * transport supports Unix domain server sockets.
     *
     * @return the domain server socket {@link ServerChannel} implementation class
     */
    public @NotNull Class<? extends ServerChannel> serverDomainSocketChannelClass() {
        return serverDomainSocketChannelClass;
    }

    /**
     * Returns a {@link ChannelFactory} for creating Unix domain server socket
     * {@link ServerChannel} instances using the selected transport. Use
     * {@link #isDomainSocketSupported()} to check whether the selected transport
     * supports Unix domain server sockets.
     *
     * @return a new {@link ChannelFactory} for domain server sockets
     */
    public @NotNull ChannelFactory<? extends ServerChannel> serverDomainSocketChannelFactory() {
        return serverDomainSocketChannelFactory::get;
    }

    @Override
    public @NotNull String toString() {
        return "TransportSelection[" +
                "type=" + type +
                ", ioHandlerFactory=" + ioHandlerFactory.getClass().getSimpleName() +
                ", socketChannel=" + socketChannelClass.getSimpleName() +
                ", serverSocketChannel=" + serverSocketChannelClass.getSimpleName() +
                ", datagramChannel=" + datagramChannelClass.getSimpleName() +
                ", domainSocketsSupported=" + domainSocketsSupported +
                ", domainSocketChannel=" + domainSocketChannelClass.getSimpleName() +
                ", serverDomainSocketChannel=" + serverDomainSocketChannelClass.getSimpleName() +
                ']';
    }
}
