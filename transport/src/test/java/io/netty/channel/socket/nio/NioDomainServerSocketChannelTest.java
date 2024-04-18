/*
 * Copyright 2018 The Netty Project
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
package io.netty.channel.socket.nio;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.nio.channels.NetworkChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledForJreRange(min = JRE.JAVA_16)
public class NioDomainServerSocketChannelTest extends AbstractNioDomainChannelTest<NioDomainServerSocketChannel> {
    private static final Method OF_METHOD;

    static {
        Method method;
        try {
            Class<?> clazz = Class.forName("java.net.UnixDomainSocketAddress");
            method = clazz.getMethod("of", String.class);
        } catch (Throwable error) {
            method = null;
        }
        OF_METHOD = method;
    }

    public static SocketAddress newUnixDomainSocketAddress(String path) {
        if (OF_METHOD == null) {
            throw new IllegalStateException();
        }
        try {
            return (SocketAddress) OF_METHOD.invoke(null, path);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void testCloseOnError() throws Exception {
        ServerSocketChannel jdkChannel = NioDomainServerSocketChannel.newChannel(SelectorProvider.provider());
        NioDomainServerSocketChannel serverSocketChannel = new NioDomainServerSocketChannel(jdkChannel);
        EventLoopGroup group = new NioEventLoopGroup(1);
        File file = new File(System.getProperty("java.io.tmpdir") + UUID.randomUUID());
        try {
            group.register(serverSocketChannel).syncUninterruptibly();
            serverSocketChannel.bind(newUnixDomainSocketAddress(file.getAbsolutePath()))
                    .syncUninterruptibly();
            assertFalse(serverSocketChannel.closeOnReadError(new IOException()));
            serverSocketChannel.close().syncUninterruptibly();
        } finally {
            group.shutdownGracefully();
            file.delete();
        }
    }

    @Test
    public void testIsActiveFalseAfterClose() throws Exception {
        NioDomainServerSocketChannel serverSocketChannel = new NioDomainServerSocketChannel();
        EventLoopGroup group = new NioEventLoopGroup(1);
        File file = new File(System.getProperty("java.io.tmpdir") + UUID.randomUUID());
        try {
            group.register(serverSocketChannel).syncUninterruptibly();
            Channel channel = serverSocketChannel.bind(
                    newUnixDomainSocketAddress(file.getAbsolutePath()))
                    .syncUninterruptibly().channel();
            assertTrue(channel.isActive());
            assertTrue(channel.isOpen());
            channel.close().syncUninterruptibly();
            assertFalse(channel.isOpen());
            assertFalse(channel.isActive());
        } finally {
            group.shutdownGracefully();
            file.delete();
        }
    }

    @Override
    protected NioDomainServerSocketChannel newNioChannel() {
        return new NioDomainServerSocketChannel();
    }

    @Override
    protected NetworkChannel jdkChannel(NioDomainServerSocketChannel channel) {
        return channel.javaChannel();
    }

    @Override
    protected SocketOption<?> newInvalidOption() {
        return StandardSocketOptions.IP_MULTICAST_IF;
    }
}
