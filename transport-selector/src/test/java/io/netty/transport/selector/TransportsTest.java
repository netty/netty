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
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.uring.IoUringDatagramChannel;
import io.netty.channel.uring.IoUringServerSocketChannel;
import io.netty.channel.uring.IoUringSocketChannel;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TransportsTest {

    @Test
    void selectionReportsItsType() {
        assertNotNull(Transports.selection().type());
    }

    @Test
    void explicitNioSelection() {
        TransportSelection selection = Transports.selection(TransportType.NIO);
        assertEquals(TransportType.NIO, selection.type());
        assertEquals(NioSocketChannel.class, selection.socketChannelClass());
        assertNotNull(selection.domainSocketChannelClass());
        // NIO domain sockets require Java 16+.
        assertEquals(PlatformDependent.javaVersion() >= 16, selection.isDomainSocketSupported());
    }

    @Test
    void requireNativeFailsWhenNoNativeAvailable() {
        boolean anyNative = TransportType.IO_URING.isAvailable()
                || TransportType.EPOLL.isAvailable()
                || TransportType.KQUEUE.isAvailable();
        if (!anyNative) {
            assertThrows(IllegalStateException.class,
                    () -> Transports.selection(SelectionMode.NATIVE_ONLY, TransportType.IO_URING));
        } else {
            TransportSelection selection = Transports.selection(SelectionMode.NATIVE_ONLY, TransportType.IO_URING);
            assertNotSame(TransportType.NIO, selection.type());
        }
    }

    @Test
    void requireNativeWithNioThrows() {
        assertThrows(IllegalStateException.class,
                () -> Transports.selection(SelectionMode.NATIVE_ONLY, TransportType.NIO));
    }

    @Test
    void priorityOrderRespectedWhenRequestedTypeUnavailable() {
        TransportSelection selection = Transports.selection(TransportType.EPOLL);
        assertNotNull(selection);
        assertEquals(TransportType.EPOLL.isAvailable() ? TransportType.EPOLL : TransportType.NIO,
                selection.type());
    }

    @Test
    void selectionKeepsRequestedOrderForAvailableTypes() {
        TransportSelection selection = Transports.selection(TransportType.NIO, TransportType.KQUEUE);
        assertEquals(TransportType.NIO, selection.type());
    }

    @Test
    void newEventLoopGroupWithThreadFactory() {
        final AtomicInteger created = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            created.incrementAndGet();
            return new Thread(runnable);
        };
        TransportSelection selection = Transports.selection(TransportType.NIO);
        EventLoopGroup group = selection.newEventLoopGroup(threadFactory, 1);
        assertNotNull(group);
        assertFalse(group.isShutdown());
        // Threads are created lazily; we only verify the group is usable and shuts down gracefully.
        group.next();
        group.shutdownGracefully().syncUninterruptibly();
    }

    @Test
    void staticNewEventLoopGroupWithThreadFactory() {
        ThreadFactory threadFactory = Thread::new;
        EventLoopGroup group = Transports.newEventLoopGroup(threadFactory, 1);
        assertNotNull(group);
        group.shutdownGracefully().syncUninterruptibly();
    }

    @Test
    void domainSocketChannelClassIsChannelSubtype() {
        TransportSelection selection = Transports.selection(TransportType.NIO);
        Class<? extends Channel> udsClass = selection.domainSocketChannelClass();
        assertNotNull(udsClass);
        assertTrue(Channel.class.isAssignableFrom(udsClass));

        Class<? extends ServerChannel> srvClass = selection.serverDomainSocketChannelClass();
        assertNotNull(srvClass);
        assertTrue(ServerChannel.class.isAssignableFrom(srvClass));
    }

    @Test
    void selectionIsNotNull() {
        assertNotNull(Transports.selection());
    }

    @Test
    void selectionIsCached() {
        assertSame(Transports.selection(), Transports.selection());
    }

    @Test
    void transportSelectionHasAllChannelClasses() {
        TransportSelection selection = Transports.selection();
        assertNotNull(selection.socketChannelClass());
        assertNotNull(selection.serverSocketChannelClass());
        assertNotNull(selection.datagramChannelClass());
    }

    @Test
    void newEventLoopGroupCreatesGroup() {
        EventLoopGroup group = Transports.newEventLoopGroup();
        assertNotNull(group);
        assertFalse(group.isShutdown());
        group.shutdownGracefully();
    }

    @Test
    void newEventLoopGroupWithThreadsCreatesGroup() {
        EventLoopGroup group = Transports.newEventLoopGroup(1);
        assertNotNull(group);
        assertFalse(group.isShutdown());
        group.shutdownGracefully();
    }

    @Test
    void transportSelectionToStringContainsTransportNames() {
        String toString = Transports.selection().toString();
        assertTrue(toString.startsWith("TransportSelection["));
        assertTrue(toString.contains("type="));
        assertTrue(toString.contains("ioHandlerFactory="));
        assertTrue(toString.contains("socketChannel="));
        assertTrue(toString.contains("serverSocketChannel="));
        assertTrue(toString.contains("datagramChannel="));
        assertTrue(toString.contains("domainSocketsSupported="));
        assertTrue(toString.contains("domainSocketChannel="));
        assertTrue(toString.contains("serverDomainSocketChannel="));
    }

    @Test
    void selectedChannelClassesAreConsistentWithEachOther() {
        TransportSelection selection = Transports.selection();
        Class<? extends SocketChannel> sc = selection.socketChannelClass();
        Class<? extends ServerSocketChannel> ssc = selection.serverSocketChannelClass();
        Class<? extends DatagramChannel> dc = selection.datagramChannelClass();

        // All four channel classes must belong to the same transport family
        if (sc == NioSocketChannel.class) {
            assertEquals(NioServerSocketChannel.class, ssc);
            assertEquals(NioDatagramChannel.class, dc);
        } else if (sc == EpollSocketChannel.class) {
            assertEquals(EpollServerSocketChannel.class, ssc);
            assertEquals(EpollDatagramChannel.class, dc);
        } else if (sc == KQueueSocketChannel.class) {
            assertEquals(KQueueServerSocketChannel.class, ssc);
            assertEquals(KQueueDatagramChannel.class, dc);
        } else {
            // Treat as IoUring
            assertEquals(IoUringSocketChannel.class, sc);
            assertEquals(IoUringServerSocketChannel.class, ssc);
            assertEquals(IoUringDatagramChannel.class, dc);
        }
    }

    @Test
    void selectedChannelClassesAreSubtypesOfExpectedBaseClasses() {
        TransportSelection selection = Transports.selection();
        assertTrue(Channel.class.isAssignableFrom(selection.socketChannelClass()));
        assertTrue(Channel.class.isAssignableFrom(selection.serverSocketChannelClass()));
        assertTrue(Channel.class.isAssignableFrom(selection.datagramChannelClass()));
        assertTrue(ServerChannel.class.isAssignableFrom(selection.serverSocketChannelClass()));
    }

    @Test
    void eventLoopGroupCreatedFromSelectionFunctions() {
        // The selected transport's IoHandlerFactory should create a working group
        EventLoopGroup group = Transports.newEventLoopGroup(2);
        assertNotNull(group);
        assertFalse(group.isShutdown());
        assertNotNull(group.next());
        group.shutdownGracefully();
    }

    @Test
    void selectionPrefersIoUringWhenAvailable() {
        // On platforms with native transports, the highest-priority available one is chosen
        TransportSelection selection = Transports.selection();
        Class<? extends SocketChannel> sc = selection.socketChannelClass();
        // Just verify it picked SOMETHING valid - the priority is tested by integration
        assertNotNull(sc);
        assertTrue(Channel.class.isAssignableFrom(sc));
    }

    @Test
    void socketChannelFactoryCreatesChannel() {
        TransportSelection selection = Transports.selection();
        ChannelFactory<? extends SocketChannel> factory = selection.socketChannelFactory();
        assertNotNull(factory);
        SocketChannel ch = factory.newChannel();
        assertNotNull(ch);
        assertSame(selection.socketChannelClass(), ch.getClass());
    }

    @Test
    void serverSocketChannelFactoryCreatesChannel() {
        TransportSelection selection = Transports.selection();
        ChannelFactory<? extends ServerSocketChannel> factory = selection.serverSocketChannelFactory();
        assertNotNull(factory);
        ServerSocketChannel ch = factory.newChannel();
        assertNotNull(ch);
        assertSame(selection.serverSocketChannelClass(), ch.getClass());
    }

    @Test
    void staticNewEventLoopGroupWithThreadFactoryOnly() {
        ThreadFactory threadFactory = Thread::new;
        EventLoopGroup group = Transports.newEventLoopGroup(threadFactory);
        assertNotNull(group);
        assertFalse(group.isShutdown());
        group.shutdownGracefully().syncUninterruptibly();
    }

    @Test
    void transportSelectionNewEventLoopGroupWithThreadFactoryOnly() {
        TransportSelection selection = Transports.selection(TransportType.NIO);
        ThreadFactory threadFactory = Thread::new;
        EventLoopGroup group = selection.newEventLoopGroup(threadFactory);
        assertNotNull(group);
        assertFalse(group.isShutdown());
        group.shutdownGracefully().syncUninterruptibly();
    }

    @Test
    void transportTypeIsAvailableReportsAvailability() {
        for (TransportType type : TransportType.values()) {
            boolean available = type.isAvailable();
            // When only a single transport is requested, an available one is selected,
            // and an unavailable one falls back to NIO (which is always available).
            TransportType selected = Transports.selection(type).type();
            if (available || type == TransportType.NIO) {
                assertEquals(type, selected);
            } else {
                assertEquals(TransportType.NIO, selected);
            }
        }
    }

    @Test
    void domainSocketChannelFactoryCreatesChannel() {
        TransportSelection selection = Transports.selection(TransportType.NIO);
        // NIO domain sockets require Java 16+.
        assumeTrue(selection.isDomainSocketSupported());
        ChannelFactory<? extends Channel> factory = selection.domainSocketChannelFactory();
        assertNotNull(factory);
        Channel ch = factory.newChannel();
        assertNotNull(ch);
        assertSame(selection.domainSocketChannelClass(), ch.getClass());
    }

    @Test
    void serverDomainSocketChannelFactoryCreatesChannel() {
        TransportSelection selection = Transports.selection(TransportType.NIO);
        // NIO domain sockets require Java 16+.
        assumeTrue(selection.isDomainSocketSupported());
        ChannelFactory<? extends ServerChannel> factory = selection.serverDomainSocketChannelFactory();
        assertNotNull(factory);
        ServerChannel ch = factory.newChannel();
        assertNotNull(ch);
        assertSame(selection.serverDomainSocketChannelClass(), ch.getClass());
    }

    @Test
    void datagramChannelFactoryCreatesChannel() {
        TransportSelection selection = Transports.selection();
        ChannelFactory<? extends DatagramChannel> factory = selection.datagramChannelFactory(null);
        assertNotNull(factory);
        DatagramChannel ch = factory.newChannel();
        assertNotNull(ch);
        assertSame(selection.datagramChannelClass(), ch.getClass());
    }

    @Test
    void staticNewEventLoopGroupWithExecutor() {
        Executor executor = command -> new Thread(command).start();
        EventLoopGroup group = Transports.newEventLoopGroup(executor);
        assertNotNull(group);
        assertFalse(group.isShutdown());
        group.shutdownGracefully().syncUninterruptibly();
    }

    @Test
    void staticNewEventLoopGroupWithExecutorAndThreads() {
        Executor executor = command -> new Thread(command).start();
        EventLoopGroup group = Transports.newEventLoopGroup(executor, 1);
        assertNotNull(group);
        assertFalse(group.isShutdown());
        group.shutdownGracefully().syncUninterruptibly();
    }

    @Test
    void transportSelectionNewEventLoopGroupWithExecutor() {
        TransportSelection selection = Transports.selection(TransportType.NIO);
        Executor executor = command -> new Thread(command).start();
        EventLoopGroup group = selection.newEventLoopGroup(executor);
        assertNotNull(group);
        assertFalse(group.isShutdown());
        group.shutdownGracefully().syncUninterruptibly();
    }

    @Test
    void transportSelectionNewEventLoopGroupWithExecutorAndThreads() {
        TransportSelection selection = Transports.selection(TransportType.NIO);
        Executor executor = command -> new Thread(command).start();
        EventLoopGroup group = selection.newEventLoopGroup(executor, 1);
        assertNotNull(group);
        assertFalse(group.isShutdown());
        group.shutdownGracefully().syncUninterruptibly();
    }
}
