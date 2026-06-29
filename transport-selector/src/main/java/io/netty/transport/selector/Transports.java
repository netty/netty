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

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.reflect.Method;

/**
 * Provides a simple, opinionated API for automatically selecting the optimal transport
 * (IoUring, Epoll, KQueue, or NIO) for the current platform.
 * <p>
 * The selection logic checks for available native transports in priority order
 * (IoUring, Epoll, KQueue) and falls back to NIO if none are available.
 * <p>
 * This eliminates the need for each library or application to implement its own
 * transport detection logic. When new native transport is added to Netty,
 * all consumers of this API automatically gain support for it.
 * <p>
 * The {@code -Dio.netty.transport.noNative=true} system property can be used
 * to disable native transport selection and always use NIO.
 * <p>
 * Native transport classes are accessed via reflection, so the class can be
 * loaded without the native transport dependencies on the classpath.
 * If native transport is not available, the selection logic simply skips it.
 * <p>
 * <strong>Example usage:</strong>
 * <pre>{@code
 * // Create an EventLoopGroup with the best available transport
 * EventLoopGroup group = Transports.newEventLoopGroup();
 *
 * // Configure a bootstrap with the selected transport
 * ServerBootstrap sb = new ServerBootstrap();
 * Transports.selection().configure(sb);
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
            if (SystemPropertyUtil.getBoolean("io.netty.transport.noNative", false)) {
                logger.debug("Native transport explicitly disabled via " +
                        "-Dio.netty.transport.noNative=true, using NIO");
                return NioTransport.INSTANCE;
            }

            // Check native transports in priority order using reflection
            TransportSelection selection;
            selection = tryLoadNative("io.netty.channel.uring.IoUring");
            if (selection != null) {
                return selection;
            }
            selection = tryLoadNative("io.netty.channel.epoll.Epoll");
            if (selection != null) {
                return selection;
            }
            selection = tryLoadNative("io.netty.channel.kqueue.KQueue");
            if (selection != null) {
                return selection;
            }

            logger.debug("No native transport available, using NIO");
            return NioTransport.INSTANCE;
        }
    }

    /**
     * Attempts to load native transport by its fully-qualified check class name
     * (e.g. {@code "io.netty.channel.epoll.Epoll"}).
     * <p>
     * Uses reflection so that missing native transport jars do not cause
     * {@link NoClassDefFoundError} at class loading time.
     *
     * @param checkClassName fully qualified class name of the transport marker class
     * @return a {@link TransportSelection} if the transport is available, or {@code null}
     */
    private static TransportSelection tryLoadNative(String checkClassName) {
        try {
            Class<?> checkClass = Class.forName(checkClassName);
            Method isAvailable = checkClass.getMethod("isAvailable");
            if (!(boolean) isAvailable.invoke(null)) {
                return null;
            }

            String simpleName = checkClass.getSimpleName();
            String pkg = checkClassName.substring(0, checkClassName.lastIndexOf('.'));

            // Load channel classes
            @SuppressWarnings("unchecked")
            Class<? extends SocketChannel> socketChannelClass =
                    (Class<? extends SocketChannel>) Class.forName(pkg + "." + simpleName + "SocketChannel");
            @SuppressWarnings("unchecked")
            Class<? extends ServerSocketChannel> serverSocketChannelClass =
                    (Class<? extends ServerSocketChannel>) Class.forName(pkg + "." + simpleName + "ServerSocketChannel");
            @SuppressWarnings("unchecked")
            Class<? extends DatagramChannel> datagramChannelClass =
                    (Class<? extends DatagramChannel>) Class.forName(pkg + "." + simpleName + "DatagramChannel");

            // Create IoHandlerFactory via reflection
            Class<?> handlerClass = Class.forName(pkg + "." + simpleName + "IoHandler");
            Method newFactoryMethod = handlerClass.getMethod("newFactory");
            IoHandlerFactory factory = (IoHandlerFactory) newFactoryMethod.invoke(null);

            logger.debug("Using {} transport (reflective)", simpleName);

            return new TransportSelection(
                    MultiThreadIoEventLoopGroup.class,
                    socketChannelClass,
                    serverSocketChannelClass,
                    datagramChannelClass,
                    nThreads -> new MultiThreadIoEventLoopGroup(nThreads, factory)
            );
        } catch (Exception e) {
            logger.debug("Native transport {} not available: {}", checkClassName, e.getMessage());
            return null;
        }
    }

    /**
     * Contains the selected transport's {@link EventLoopGroup} and {@link Channel} classes,
     * along with convenience methods for configuration and instantiation.
     */
    public static final class TransportSelection {

        private final Class<? extends EventLoopGroup> eventLoopGroupClass;
        private final Class<? extends SocketChannel> socketChannelClass;
        private final Class<? extends ServerSocketChannel> serverSocketChannelClass;
        private final Class<? extends DatagramChannel> datagramChannelClass;
        private final GroupFactory groupFactory;

        private TransportSelection(
                Class<? extends EventLoopGroup> eventLoopGroupClass,
                Class<? extends SocketChannel> socketChannelClass,
                Class<? extends ServerSocketChannel> serverSocketChannelClass,
                Class<? extends DatagramChannel> datagramChannelClass,
                GroupFactory groupFactory) {
            this.eventLoopGroupClass = eventLoopGroupClass;
            this.socketChannelClass = socketChannelClass;
            this.serverSocketChannelClass = serverSocketChannelClass;
            this.datagramChannelClass = datagramChannelClass;
            this.groupFactory = groupFactory;
        }

        /**
         * Returns the {@link EventLoopGroup} class for the selected transport.
         *
         * @return the {@link EventLoopGroup} implementation class
         */
        public Class<? extends EventLoopGroup> eventLoopGroupClass() {
            return eventLoopGroupClass;
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
         * Configures the given {@link Bootstrap} with the selected transport's
         * {@link SocketChannel} class.
         *
         * @param b the bootstrap to configure
         */
        public void configure(Bootstrap b) {
            b.channel(socketChannelClass);
        }

        /**
         * Configures the given {@link ServerBootstrap} with the selected transport's
         * {@link ServerSocketChannel} class.
         *
         * @param b the server bootstrap to configure
         */
        public void configure(ServerBootstrap b) {
            b.channel(serverSocketChannelClass);
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
            return groupFactory.newGroup(nThreads);
        }

        @Override
        public String toString() {
            return "TransportSelection[" +
                    "eventLoopGroup=" + eventLoopGroupClass.getSimpleName() +
                    ", socketChannel=" + socketChannelClass.getSimpleName() +
                    ", serverSocketChannel=" + serverSocketChannelClass.getSimpleName() +
                    ", datagramChannel=" + datagramChannelClass.getSimpleName() +
                    ']';
        }

        @FunctionalInterface
        private interface GroupFactory {
            EventLoopGroup newGroup(int nThreads);
        }
    }

    /**
     * NIO transport selection. NIO is always available via the {@code netty-transport} module
     * and is used as the fallback when no native transport is available.
     */
    private static final class NioTransport {
        static final TransportSelection INSTANCE = new TransportSelection(
                MultiThreadIoEventLoopGroup.class,
                NioSocketChannel.class,
                NioServerSocketChannel.class,
                NioDatagramChannel.class,
                nThreads -> new MultiThreadIoEventLoopGroup(nThreads, NioIoHandler.newFactory())
        );

        private NioTransport() {
        }
    }
}
