/*
 * Copyright 2022 The Netty Project
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
package io.netty5.testsuite.transport.socket;

import io.netty5.channel.ChannelFactory;
import io.netty5.channel.EventLoop;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.ServerChannel;
import io.netty5.channel.ServerChannelFactory;
import io.netty5.channel.socket.DatagramChannel;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.channel.socket.nio.NioDatagramChannel;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.channel.socket.nio.NioSocketChannel;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.nio.channels.spi.SelectorProvider;

final class NioDomainSocketTestUtil {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NioDomainSocketTestUtil.class);

    private static final ProtocolFamily FAMILY;
    private static final boolean DATAGRAM_SUPPORTED;
    private static final boolean SOCKET_SUPPORTED;

    static {
        boolean datagramSupported = false;
        boolean socketSupported = false;
        ProtocolFamily family = null;
        try {
            family = StandardProtocolFamily.valueOf("UNIX");
            try {
                java.nio.channels.DatagramChannel channel = SelectorProvider.provider().openDatagramChannel(family);
                channel.close();
                datagramSupported = true;
            } catch (UnsupportedOperationException | IOException e) {
                logger.debug("SelectorProvider can't be used to support Datagram Unix Domain Sockets", e);
            }

            try {
                Method openServerSocketChannelMethod = SelectorProvider.class
                        .getMethod("openServerSocketChannel", ProtocolFamily.class);

                java.nio.channels.Channel channel = (java.nio.channels.Channel) openServerSocketChannelMethod
                        .invoke(SelectorProvider.provider(), family);
                channel.close();

                Method openSocketChannelMethod = SelectorProvider.class
                        .getMethod("openSocketChannel", ProtocolFamily.class);

                channel = (java.nio.channels.Channel) openSocketChannelMethod
                        .invoke(SelectorProvider.provider(), family);
                channel.close();

                socketSupported = true;
            } catch (NoSuchMethodException | IllegalAccessException |
                     InvocationTargetException | UnsupportedOperationException | IOException e) {
                logger.debug("SelectorProvider can't be used to support Socket Unix Domain Sockets", e);
            }
        } catch (IllegalArgumentException e) {
            logger.debug("StandardProtocolFamily doesn't support Unix Domain Sockets", e);
        }
        DATAGRAM_SUPPORTED = datagramSupported;
        SOCKET_SUPPORTED = socketSupported;
        FAMILY = family;
    }

    static boolean isDatagramSupported() {
        // As of this today this is not supported by the JDK but at some point it might, so let's prepare for it.
        return DATAGRAM_SUPPORTED;
    }

    static boolean isSocketSupported() {
        return SOCKET_SUPPORTED;
    }

    static ProtocolFamily domainSocketFamily() {
        return FAMILY;
    }

    static boolean isDomainSocketFamily(ProtocolFamily family) {
        return family != null && family == FAMILY;
    }

    static ChannelFactory<DatagramChannel> newDomainSocketDatagramChannelFactory() {
        if (DATAGRAM_SUPPORTED) {
            return new ChannelFactory<>() {
                @Override
                public DatagramChannel newChannel(EventLoop eventLoop)  {
                    return new NioDatagramChannel(eventLoop, NioDomainSocketTestUtil.domainSocketFamily());
                }

                @Override
                public String toString() {
                    return NioDatagramChannel.class.getSimpleName() + ".class";
                }
            };
        }
        throw new UnsupportedOperationException();
    }

    static ServerChannelFactory<ServerChannel> newDomainSocketServerChannelFactory() {
        if (SOCKET_SUPPORTED) {
            return new ServerChannelFactory<>() {
                @Override
                public ServerChannel newChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup)  {
                    return new NioServerSocketChannel(eventLoop, eventLoop, SelectorProvider.provider(), FAMILY);
                }

                @Override
                public String toString() {
                    return NioServerSocketChannel.class.getSimpleName() + ".class";
                }
            };
        }
        throw new UnsupportedOperationException();
    }

    static ChannelFactory<SocketChannel> newDomainSocketChannelFactory() {
        if (SOCKET_SUPPORTED) {
            return new ChannelFactory<>() {
                @Override
                public SocketChannel newChannel(EventLoop eventLoop)  {
                    return new NioSocketChannel(eventLoop, SelectorProvider.provider(), FAMILY);
                }

                @Override
                public String toString() {
                    return NioSocketChannel.class.getSimpleName() + ".class";
                }
            };
        }
        throw new UnsupportedOperationException();
    }

    private NioDomainSocketTestUtil() { }
}
