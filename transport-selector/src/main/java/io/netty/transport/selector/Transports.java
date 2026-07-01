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
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import java.util.function.IntFunction;
import java.util.function.Function;
import io.netty.channel.ChannelFactory;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.uring.IoUring;
import io.netty.channel.uring.IoUringDatagramChannel;
import io.netty.channel.uring.IoUringIoHandler;
import io.netty.channel.uring.IoUringServerSocketChannel;
import io.netty.channel.uring.IoUringSocketChannel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * Provides a simple, opinionated API for automatically selecting the optimal transport
 * (IoUring, Epoll, KQueue, or NIO) for the current platform.
 * <p>
 * The selection logic checks each native transport via its {@code isAvailable()} method
 * in priority order (IoUring, Epoll, KQueue) and falls back to NIO if none are available.
 * <p>
 * This eliminates the need for each library or application to implement its own
 * transport detection logic. When new native transport is added to Netty,
 * this class is updated so that all consumers of this API automatically gain support
 * for it.
 * <p>
 * <strong>Example usage:</strong>
 * <pre>{@code
 * // Create an EventLoopGroup with the best available transport
 * EventLoopGroup group = Transports.newEventLoopGroup();
 *
 * // Configure a bootstrap with the selected transport's channel class
 * ServerBootstrap sb = new ServerBootstrap();
 * sb.channel(Transports.selection().serverSocketChannelClass());
 *
 * // Get the specific channel classes for the selected transport
 * Class<? extends SocketChannel> channelClass = Transports.selection().socketChannelClass();
 * }</pre>
 */
public final class Transports {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Transports.class);

    private Transports() {
    }

    /**
     * Returns the optimal {@link TransportSelection} for the current platform.
     * The result is computed once and cached.
     *
     * @return the transport selection for the current platform
     */
    public static TransportSelection selection() {
        return TransportSelectionHolder.INSTANCE;
    }

    /**
     * Creates a new {@link EventLoopGroup} using the optimal transport for the current platform.
     * The number of threads used is the default (typically the number of available processors
     * multiplied by 2).
     *
     * @return a new {@link EventLoopGroup} with the optimal transport
     */
    public static EventLoopGroup newEventLoopGroup() {
        return selection().newEventLoopGroup();
    }

    /**
     * Creates a new {@link EventLoopGroup} with the specified number of threads,
     * using the optimal transport for the current platform.
     *
     * @param nThreads the number of threads to use, or {@code 0} for the default number
     * @return a new {@link EventLoopGroup} with the optimal transport
     */
    public static EventLoopGroup newEventLoopGroup(int nThreads) {
        return selection().newEventLoopGroup(nThreads);
    }

    /**
     * Holder for the lazily-computed transport selection.
     */
    private static final class TransportSelectionHolder {
        static final TransportSelection INSTANCE = computeSelection();

        private TransportSelectionHolder() {
        }

        private static TransportSelection computeSelection() {
            // Check native transports in priority order

            if (IoUring.isAvailable()) {
                logger.debug("Using IoUring transport");
                return new TransportSelection(
                        IoUringSocketChannel.class,
                        IoUringServerSocketChannel.class,
                        IoUringDatagramChannel.class,
                        IoUringDatagramChannel::new,
                        nThreads -> new MultiThreadIoEventLoopGroup(nThreads, IoUringIoHandler.newFactory())
                );
            }

            if (Epoll.isAvailable()) {
                logger.debug("Using Epoll transport");
                return new TransportSelection(
                        EpollSocketChannel.class,
                        EpollServerSocketChannel.class,
                        EpollDatagramChannel.class,
                        EpollDatagramChannel::new,
                        nThreads -> new MultiThreadIoEventLoopGroup(nThreads, EpollIoHandler.newFactory())
                );
            }

            if (KQueue.isAvailable()) {
                logger.debug("Using KQueue transport");
                return new TransportSelection(
                        KQueueSocketChannel.class,
                        KQueueServerSocketChannel.class,
                        KQueueDatagramChannel.class,
                        KQueueDatagramChannel::new,
                        nThreads -> new MultiThreadIoEventLoopGroup(nThreads, KQueueIoHandler.newFactory())
                );
            }

            logger.debug("No native transport available, using NIO");
            return new TransportSelection(
                    NioSocketChannel.class,
                    NioServerSocketChannel.class,
                    NioDatagramChannel.class,
                    NioDatagramChannel::new,
                    nThreads -> new MultiThreadIoEventLoopGroup(nThreads, NioIoHandler.newFactory())
            );
        }
    }

    /**
     * Contains the selected transport's {@link EventLoopGroup} and {@link Channel} classes,
     * along with convenience methods for configuration and instantiation.
     */
    public static final class TransportSelection {

        private final Class<? extends SocketChannel> socketChannelClass;
        private final Class<? extends ServerSocketChannel> serverSocketChannelClass;
        private final Class<? extends DatagramChannel> datagramChannelClass;
        private final Function<SocketProtocolFamily, DatagramChannel> datagramChannelFactory;
        private final IntFunction<EventLoopGroup> groupFactory;

        private TransportSelection(
                Class<? extends SocketChannel> socketChannelClass,
                Class<? extends ServerSocketChannel> serverSocketChannelClass,
                Class<? extends DatagramChannel> datagramChannelClass,
                Function<SocketProtocolFamily, DatagramChannel> datagramChannelFactory,
                IntFunction<EventLoopGroup> groupFactory) {
            this.socketChannelClass = socketChannelClass;
            this.serverSocketChannelClass = serverSocketChannelClass;
            this.datagramChannelClass = datagramChannelClass;
            this.datagramChannelFactory = datagramChannelFactory;
            this.groupFactory = groupFactory;
        }

        /**
         * Returns the {@link SocketChannel} class for the selected transport.
         *
         * @return the {@link SocketChannel} implementation class
         */
        public Class<? extends SocketChannel> socketChannelClass() {
            return socketChannelClass;
        }

        /**
         * Returns the {@link ServerSocketChannel} class for the selected transport.
         *
         * @return the {@link ServerSocketChannel} implementation class
         */
        public Class<? extends ServerSocketChannel> serverSocketChannelClass() {
            return serverSocketChannelClass;
        }

        /**
         * Returns the {@link DatagramChannel} class for the selected transport.
         *
         * @return the {@link DatagramChannel} implementation class
         */
        public Class<? extends DatagramChannel> datagramChannelClass() {
            return datagramChannelClass;
        }

        /**
         * Returns a {@link ChannelFactory} for creating {@link DatagramChannel} instances
         * using the selected transport and the given {@link SocketProtocolFamily}.
         *
         * @param family the protocol family to use, or {@code null} for the OS default
         * @return a new {@link ChannelFactory} for {@link DatagramChannel}
         */
        public ChannelFactory<? extends DatagramChannel> datagramChannelFactory(SocketProtocolFamily family) {
            return () -> datagramChannelFactory.apply(family);
        }

        /**
         * Creates a new {@link EventLoopGroup} with the selected transport,
         * using the default number of threads.
         *
         * @return a new {@link EventLoopGroup} instance
         */
        public EventLoopGroup newEventLoopGroup() {
            return newEventLoopGroup(0);
        }

        /**
         * Creates a new {@link EventLoopGroup} with the selected transport
         * and the specified number of threads.
         *
         * @param nThreads the number of threads to use, or {@code 0} for the default
         * @return a new {@link EventLoopGroup} instance
         */
        public EventLoopGroup newEventLoopGroup(int nThreads) {
            return groupFactory.apply(nThreads);
        }

        @Override
        public String toString() {
            return "TransportSelection[" +
                    "socketChannel=" + socketChannelClass.getSimpleName() +
                    ", serverSocketChannel=" + serverSocketChannelClass.getSimpleName() +
                    ", datagramChannel=" + datagramChannelClass.getSimpleName() +
                    ']';
        }
    }
}
