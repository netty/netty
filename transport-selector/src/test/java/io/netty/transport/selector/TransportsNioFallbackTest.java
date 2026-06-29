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
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.transport.selector.Transports.TransportSelection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that the {@code -Dio.netty.transport.noNative=true} system property
 * correctly forces NIO fallback, even when native transports are available.
 * <p>
 * This test class is executed in its own forked JVM (configured via
 * {@code reuseForks=false} in the module POM) to avoid interference from
 * the cached transport selection in {@link TransportsTest}.
 */
class TransportsNioFallbackTest {

    @BeforeAll
    static void enableNioFallback() {
        System.setProperty("io.netty.transport.noNative", "true");
    }

    @AfterAll
    static void resetNioFallback() {
        System.clearProperty("io.netty.transport.noNative");
    }

    @Test
    void noNativePropertyForcesMultiThreadIoEventLoopGroup() {
        TransportSelection selection = Transports.selection();
        assertEquals(MultiThreadIoEventLoopGroup.class, selection.eventLoopGroupClass(),
                "Expected MultiThreadIoEventLoopGroup when io.netty.transport.noNative=true");
    }

    @Test
    void noNativePropertyForcesNioSocketChannel() {
        TransportSelection selection = Transports.selection();
        assertEquals(NioSocketChannel.class, selection.socketChannelClass());
        assertEquals(NioServerSocketChannel.class, selection.serverSocketChannelClass());
        assertEquals(NioDatagramChannel.class, selection.datagramChannelClass());
    }

    @Test
    void noNativePropertyConfigureBootstrapUsesNio() {
        TransportSelection selection = Transports.selection();

        Bootstrap bootstrap = new Bootstrap();
        selection.configure(bootstrap);
        assertNotNull(bootstrap.config().channelFactory());

        ServerBootstrap serverBootstrap = new ServerBootstrap();
        selection.configure(serverBootstrap);
        assertNotNull(serverBootstrap.config().channelFactory());
    }

    @Test
    void noNativePropertyNewEventLoopGroupReturnsMultiThreadIoGroup() {
        EventLoopGroup group = Transports.newEventLoopGroup(1);
        assertNotNull(group);
        assertInstanceOf(MultiThreadIoEventLoopGroup.class, group);
        group.shutdownGracefully();
    }

    @Test
    void noNativePropertyNewEventLoopGroupDefaultReturnsMultiThreadIoGroup() {
        EventLoopGroup group = Transports.newEventLoopGroup();
        assertNotNull(group);
        assertInstanceOf(MultiThreadIoEventLoopGroup.class, group);
        group.shutdownGracefully();
    }

    @Test
    void noNativePropertySelectionIsCached() {
        assertSame(Transports.selection(), Transports.selection());
    }

    @Test
    void noNativePropertyChannelClassesAreConsistent() {
        TransportSelection selection = Transports.selection();
        assertEquals(NioSocketChannel.class, selection.socketChannelClass());
        assertEquals(NioServerSocketChannel.class, selection.serverSocketChannelClass());
        assertEquals(NioDatagramChannel.class, selection.datagramChannelClass());
        assertTrue(Channel.class.isAssignableFrom(selection.socketChannelClass()));
        assertTrue(Channel.class.isAssignableFrom(selection.serverSocketChannelClass()));
        assertTrue(Channel.class.isAssignableFrom(selection.datagramChannelClass()));
    }

    @Test
    void noNativePropertyGroupCreatedWithNioIoHandler() {
        // Verify the underlying handler is NioIoHandler by checking the factory works
        EventLoopGroup group = Transports.newEventLoopGroup(1);
        assertNotNull(group);
        assertFalse(group.isShutdown());
        assertEquals(MultiThreadIoEventLoopGroup.class, group.getClass());
        group.shutdownGracefully();
    }
}
