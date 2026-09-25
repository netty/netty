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

import io.netty.channel.EventLoopGroup;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

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
 * Selection can be customized via {@link TransportType}:
 * <pre>{@code
 * // Determine which transport was selected
 * TransportType type = Transports.selection().type();
 *
 * // Prefer a specific transport with fallbacks
 * Transports.selection(TransportType.KQUEUE);          // falls back to NIO
 * Transports.selection(TransportType.EPOLL,
 *                      TransportType.KQUEUE);          // Epoll, else KQueue, else NIO
 *
 * // Require a native transport, fail instead of falling back to NIO
 * Transports.selection(SelectionMode.NATIVE_ONLY, TransportType.IO_URING);
 *
 * // Use Unix domain sockets
 * if (Transports.selection().isDomainSocketSupported()) {
 *     Class uds = Transports.selection().domainSocketChannelClass();
 * }
 * }</pre>
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
 * Class channelClass = Transports.selection().socketChannelClass();
 *
 * // Create channels using the selected transport's ChannelFactory
 * io.netty.channel.ChannelFactory channelFactory =
 *     Transports.selection().socketChannelFactory();
 * SocketChannel ch = channelFactory.newChannel();
 * }</pre>
 */
public final class Transports {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Transports.class);

    private static final TransportType[] DEFAULT_ORDER = {
        TransportType.IO_URING,
        TransportType.EPOLL,
        TransportType.KQUEUE,
        TransportType.NIO
    };

    private Transports() {
    }

    /**
     * Returns the optimal {@link TransportSelection} for the current platform.
     * The result is computed once and cached.
     *
     * @return the transport selection for the current platform
     */
    public static @NotNull TransportSelection selection() {
        return SelectionHolder.INSTANCE;
    }

    /**
     * Returns the {@link TransportSelection} for the given transport types in priority order.
     * The first available transport in the list is selected, and NIO is used as a final
     * fallback if none of the requested transports are available.
     *
     * @param order the transport types in priority order
     * @return the transport selection for the current platform
     */
    public static @NotNull TransportSelection selection(TransportType... order) {
        return selection(SelectionMode.AUTO, order);
    }

    /**
     * Returns the {@link TransportSelection} for the given transport types in priority order.
     *
     * @param mode specifies whether to fall back to NIO when no requested transport is available,
     *             or to fail instead
     * @param order the transport types in priority order; if empty or {@code null}, the default
     *              order (IoUring, Epoll, Kqueue, NIO) is used
     * @return the transport selection for the current platform
     */
    public static @NotNull TransportSelection selection(SelectionMode mode, TransportType... order) {
        if (order == null || order.length == 0) {
            order = DEFAULT_ORDER;
        }

        for (TransportType type : order) {
            TransportSelection selection = type.trySelect();
            if (selection == null) {
                continue;
            }
            if (mode == SelectionMode.NATIVE_ONLY && type == TransportType.NIO) {
                throw new IllegalStateException(
                        "No native transport available (requested: " + Arrays.toString(order) + ')');
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Using {} transport", type);
            }
            return selection;
        }

        // None of the requested transports are available. Unless a native transport is hard-required,
        // fall back to NIO so the API always returns a usable transport.
        if (mode == SelectionMode.AUTO) {
            TransportSelection nio = TransportType.NIO.trySelect();
            if (nio != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("No available transport in requested order, using NIO");
                }
                return nio;
            }
        }

        throw new IllegalStateException(
                "No transport available (requested: " + Arrays.toString(order) + ')');
    }

    /**
     * Creates a new {@link EventLoopGroup} using the optimal transport for the current platform.
     * The number of threads used is the default (typically the number of available processors
     * multiplied by 2).
     *
     * @return a new {@link EventLoopGroup} with the optimal transport
     */
    public static @NotNull EventLoopGroup newEventLoopGroup() {
        return selection().newEventLoopGroup();
    }

    /**
     * Creates a new {@link EventLoopGroup} with the specified number of threads,
     * using the optimal transport for the current platform.
     *
     * @param nThreads the number of threads to use, or {@code 0} for the default number
     * @return a new {@link EventLoopGroup} with the optimal transport
     */
    public static @NotNull EventLoopGroup newEventLoopGroup(int nThreads) {
        return selection().newEventLoopGroup(nThreads);
    }

    /**
     * Creates a new {@link EventLoopGroup} using the optimal transport for the current platform
     * and the given {@link ThreadFactory}.
     *
     * @param threadFactory the {@link ThreadFactory} used for thread creation
     * @return a new {@link EventLoopGroup} with the optimal transport
     */
    public static @NotNull EventLoopGroup newEventLoopGroup(ThreadFactory threadFactory) {
        return selection().newEventLoopGroup(threadFactory);
    }

    /**
     * Creates a new {@link EventLoopGroup} with the specified number of threads,
     * using the optimal transport for the current platform and the given {@link ThreadFactory}.
     *
     * @param threadFactory the {@link ThreadFactory} used for thread creation
     * @param nThreads the number of threads to use, or {@code 0} for the default number
     * @return a new {@link EventLoopGroup} with the optimal transport
     */
    public static @NotNull EventLoopGroup newEventLoopGroup(ThreadFactory threadFactory, int nThreads) {
        return selection().newEventLoopGroup(threadFactory, nThreads);
    }

    /**
     * Creates a new {@link EventLoopGroup} using the optimal transport for the current platform
     * and the given {@link Executor}.
     *
     * @param executor the {@link Executor} used to run the event loops
     * @return a new {@link EventLoopGroup} with the optimal transport
     */
    public static @NotNull EventLoopGroup newEventLoopGroup(Executor executor) {
        return selection().newEventLoopGroup(executor);
    }

    /**
     * Creates a new {@link EventLoopGroup} with the specified number of threads,
     * using the optimal transport for the current platform and the given {@link Executor}.
     *
     * @param executor the {@link Executor} used to run the event loops
     * @param nThreads the number of threads to use, or {@code 0} for the default number
     * @return a new {@link EventLoopGroup} with the optimal transport
     */
    public static @NotNull EventLoopGroup newEventLoopGroup(Executor executor, int nThreads) {
        return selection().newEventLoopGroup(executor, nThreads);
    }

    /**
     * Holds the lazily-computed default {@link TransportSelection}.
     */
    private static final class SelectionHolder {
        static final TransportSelection INSTANCE = selection(SelectionMode.AUTO);

        private SelectionHolder() {
        }
    }
}
