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

import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.kqueue.KQueueServerDomainSocketChannel;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioDomainSocketChannel;
import io.netty.channel.socket.nio.NioServerDomainSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.uring.IoUring;
import io.netty.channel.uring.IoUringDatagramChannel;
import io.netty.channel.uring.IoUringDomainSocketChannel;
import io.netty.channel.uring.IoUringIoHandler;
import io.netty.channel.uring.IoUringServerDomainSocketChannel;
import io.netty.channel.uring.IoUringServerSocketChannel;
import io.netty.channel.uring.IoUringSocketChannel;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * The available transport implementations and their priority.
 */
public enum TransportType {
    /**
     * The Linux {@code io_uring} transport.
     */
    IO_URING {
        @Override
        boolean available() {
            return IoUring.isAvailable();
        }

        @Override
        TransportSelection select() {
            return ioUringSelection();
        }
    },
    /**
     * The Linux {@code epoll} transport.
     */
    EPOLL {
        @Override
        boolean available() {
            return Epoll.isAvailable();
        }

        @Override
        TransportSelection select() {
            return epollSelection();
        }
    },
    /**
     * The macOS / BSD {@code kqueue} transport.
     */
    KQUEUE {
        @Override
        boolean available() {
            return KQueue.isAvailable();
        }

        @Override
        TransportSelection select() {
            return kqueueSelection();
        }
    },
    /**
     * The pure-Java {@code NIO} transport, always available.
     */
    NIO {
        @Override
        boolean available() {
            return true;
        }

        @Override
        TransportSelection select() {
            return nioSelection();
        }
    };

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(TransportType.class);

    /** Returns {@code true} if the transport is available on the current platform. */
    public boolean isAvailable() {
        return available();
    }

    abstract boolean available();

    abstract TransportSelection select();

    final TransportSelection trySelect() {
        try {
            if (available()) {
                return select();
            }
        } catch (LinkageError e) {
            // The transport-classes jar for this transport is not on the classpath, or its native
            // library could not be linked. Treat it as unavailable so we can degrade gracefully to
            // another transport (e.g. NIO) instead of failing the whole selection.
            if (logger.isDebugEnabled()) {
                logger.debug("Unable to select {} transport", this, e);
            }
        }
        return null;
    }

    private static TransportSelection ioUringSelection() {
        return new TransportSelection(
                IO_URING,
                IoUringIoHandler.newFactory(),
                IoUringSocketChannel.class,
                IoUringSocketChannel::new,
                IoUringServerSocketChannel.class,
                IoUringServerSocketChannel::new,
                IoUringDatagramChannel.class,
                IoUringDatagramChannel::new,
                true,
                IoUringDomainSocketChannel.class,
                IoUringDomainSocketChannel::new,
                IoUringServerDomainSocketChannel.class,
                IoUringServerDomainSocketChannel::new);
    }

    private static TransportSelection epollSelection() {
        return new TransportSelection(
                EPOLL,
                EpollIoHandler.newFactory(),
                EpollSocketChannel.class,
                EpollSocketChannel::new,
                EpollServerSocketChannel.class,
                EpollServerSocketChannel::new,
                EpollDatagramChannel.class,
                EpollDatagramChannel::new,
                true,
                EpollDomainSocketChannel.class,
                EpollDomainSocketChannel::new,
                EpollServerDomainSocketChannel.class,
                EpollServerDomainSocketChannel::new);
    }

    private static TransportSelection kqueueSelection() {
        return new TransportSelection(
                KQUEUE,
                KQueueIoHandler.newFactory(),
                KQueueSocketChannel.class,
                KQueueSocketChannel::new,
                KQueueServerSocketChannel.class,
                KQueueServerSocketChannel::new,
                KQueueDatagramChannel.class,
                KQueueDatagramChannel::new,
                true,
                KQueueDomainSocketChannel.class,
                KQueueDomainSocketChannel::new,
                KQueueServerDomainSocketChannel.class,
                KQueueServerDomainSocketChannel::new);
    }

    private static TransportSelection nioSelection() {
        return new TransportSelection(
                NIO,
                NioIoHandler.newFactory(),
                NioSocketChannel.class,
                NioSocketChannel::new,
                NioServerSocketChannel.class,
                NioServerSocketChannel::new,
                NioDatagramChannel.class,
                NioDatagramChannel::new,
                PlatformDependent.javaVersion() >= 16,
                NioDomainSocketChannel.class,
                NioDomainSocketChannel::new,
                NioServerDomainSocketChannel.class,
                NioServerDomainSocketChannel::new);
    }
}
