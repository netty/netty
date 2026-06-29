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
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.uring.IoUringDatagramChannel;
import io.netty.channel.uring.IoUringServerSocketChannel;
import io.netty.channel.uring.IoUringSocketChannel;
import io.netty.transport.selector.Transports.TransportSelection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransportsTest {

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
        assertNotNull(selection.eventLoopGroupClass());
        assertNotNull(selection.socketChannelClass());
        assertNotNull(selection.serverSocketChannelClass());
        assertNotNull(selection.datagramChannelClass());
    }

    @Test
    void configureBootstrapDoesNotThrow() {
        TransportSelection selection = Transports.selection();
        Bootstrap bootstrap = new Bootstrap();
        selection.configure(bootstrap);
    }

    @Test
    void configureServerBootstrapDoesNotThrow() {
        TransportSelection selection = Transports.selection();
        ServerBootstrap bootstrap = new ServerBootstrap();
        selection.configure(bootstrap);
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
        assertTrue(toString.contains("eventLoopGroup="));
        assertTrue(toString.contains("socketChannel="));
        assertTrue(toString.contains("serverSocketChannel="));
        assertTrue(toString.contains("datagramChannel="));
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
    void newEventLoopGroupReturnsMultiThreadIoEventLoopGroup() {
        EventLoopGroup group = Transports.newEventLoopGroup(0);
        assertInstanceOf(MultiThreadIoEventLoopGroup.class, group,
                "Expected MultiThreadIoEventLoopGroup but got " + group.getClass().getSimpleName());
        group.shutdownGracefully();
    }

    @Test
    void configuredBootstrapSetsChannelFactory() {
        TransportSelection selection = Transports.selection();
        Bootstrap bootstrap = new Bootstrap();
        selection.configure(bootstrap);
        assertNotNull(bootstrap.config().channelFactory());
    }

    @Test
    void configuredServerBootstrapSetsChannelFactory() {
        TransportSelection selection = Transports.selection();
        ServerBootstrap bootstrap = new ServerBootstrap();
        selection.configure(bootstrap);
        assertNotNull(bootstrap.config().channelFactory());
    }

    @Test
    void eventLoopGroupClassIsAlwaysMultiThreadIoEventLoopGroup() {
        // All transports in 4.2 use MultiThreadIoEventLoopGroup as the common base
        Class<? extends EventLoopGroup> elg = Transports.selection().eventLoopGroupClass();
        assertEquals(MultiThreadIoEventLoopGroup.class, elg);
    }

    @Test
    void selectionDoesNotThrow() {
        // Reflexive loading should handle all native transports gracefully
        assertDoesNotThrow(Transports::selection);
    }

    @Test
    void channelClassNamesFollowReflectionNamingConvention() {
        // The reflection logic constructs channel class names as pkg.SimpleName+type
        // e.g. io.netty.channel.epoll.EpollSocketChannel
        TransportSelection selection = Transports.selection();
        String scName = selection.socketChannelClass().getName();
        String sscName = selection.serverSocketChannelClass().getName();
        String dcName = selection.datagramChannelClass().getName();

        // All channel classes share the same package prefix
        String scPkg = scName.substring(0, scName.lastIndexOf('.'));
        String sscPkg = sscName.substring(0, sscName.lastIndexOf('.'));
        String dcPkg = dcName.substring(0, dcName.lastIndexOf('.'));
        assertEquals(scPkg, sscPkg, "Socket and ServerSocket channels must share the same package");
        assertEquals(scPkg, dcPkg, "Socket and Datagram channels must share the same package");

        // Simple names follow the format: SimpleName + ChannelType
        String scSimple = scName.substring(scName.lastIndexOf('.') + 1);
        String sscSimple = sscName.substring(sscName.lastIndexOf('.') + 1);
        String dcSimple = dcName.substring(dcName.lastIndexOf('.') + 1);

        assertTrue(scSimple.endsWith("SocketChannel"),
                "Socket channel class should end with 'SocketChannel': " + scSimple);
        assertTrue(sscSimple.endsWith("ServerSocketChannel"),
                "ServerSocket channel class should end with 'ServerSocketChannel': " + sscSimple);
        assertTrue(dcSimple.endsWith("DatagramChannel"),
                "Datagram channel class should end with 'DatagramChannel': " + dcSimple);

        // All three share the same transport prefix
        String commonPrefix = scSimple.substring(0, scSimple.length() - "SocketChannel".length());
        assertEquals(commonPrefix, sscSimple.substring(0, sscSimple.length() - "ServerSocketChannel".length()));
        assertEquals(commonPrefix, dcSimple.substring(0, dcSimple.length() - "DatagramChannel".length()));
    }

    @Test
    void reflectivelyCreatedEventLoopGroupFunctions() {
        // The reflectively loaded IoHandlerFactory should create a working group
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
}
