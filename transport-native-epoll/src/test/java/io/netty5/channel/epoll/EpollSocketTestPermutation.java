/*
 * Copyright 2014 The Netty Project
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
package io.netty5.channel.epoll;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelFactory;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.EventLoop;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.socket.InternetProtocolFamily;
import io.netty5.channel.socket.nio.NioDatagramChannel;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.channel.socket.nio.NioSocketChannel;
import io.netty5.channel.unix.DomainSocketAddress;
import io.netty5.channel.unix.tests.UnixTestUtils;
import io.netty5.testsuite.transport.TestsuitePermutation;
import io.netty5.testsuite.transport.TestsuitePermutation.BootstrapFactory;
import io.netty5.testsuite.transport.socket.SocketTestPermutation;
import io.netty5.util.concurrent.DefaultThreadFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.netty5.channel.epoll.Native.IS_SUPPORTING_TCP_FASTOPEN_CLIENT;
import static io.netty5.channel.epoll.Native.IS_SUPPORTING_TCP_FASTOPEN_SERVER;

class EpollSocketTestPermutation extends SocketTestPermutation {

    static final EpollSocketTestPermutation INSTANCE = new EpollSocketTestPermutation();

    static final EventLoopGroup EPOLL_BOSS_GROUP =
            new MultithreadEventLoopGroup(BOSSES, new DefaultThreadFactory("testsuite-epoll-boss", true),
                    EpollHandler.newFactory());
    static final EventLoopGroup EPOLL_WORKER_GROUP =
            new MultithreadEventLoopGroup(WORKERS, new DefaultThreadFactory("testsuite-epoll-worker", true),
                    EpollHandler.newFactory());

    @Override
    public List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> socket() {
        List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> list =
                combo(serverSocket(), clientSocketWithFastOpen());

        return list.subList(0, list.size() - 1); // Exclude NIO x NIO test
    }

    public List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> socketWithoutFastOpen() {
        List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> list =
                combo(serverSocket(), clientSocket());

        list.remove(list.size() - 1); // Exclude NIO x NIO test

        return list;
    }

    @Override
    public List<BootstrapFactory<ServerBootstrap>> serverSocket() {
        List<BootstrapFactory<ServerBootstrap>> toReturn = new ArrayList<>();
        toReturn.add(() -> new ServerBootstrap().group(EPOLL_BOSS_GROUP, EPOLL_WORKER_GROUP)
                                    .channel(EpollServerSocketChannel.class));
        if (IS_SUPPORTING_TCP_FASTOPEN_SERVER) {
            toReturn.add(() -> {
                ServerBootstrap serverBootstrap = new ServerBootstrap().group(EPOLL_BOSS_GROUP, EPOLL_WORKER_GROUP)
                                                                       .channel(EpollServerSocketChannel.class);
                serverBootstrap.option(ChannelOption.TCP_FASTOPEN, 5);
                return serverBootstrap;
            });
        }
        toReturn.add(() -> new ServerBootstrap().group(nioBossGroup, nioWorkerGroup)
                                    .channel(NioServerSocketChannel.class));

        return toReturn;
    }

    @Override
    public List<BootstrapFactory<Bootstrap>> clientSocket() {
        List<BootstrapFactory<Bootstrap>> toReturn = new ArrayList<>();
        toReturn.add(() -> new Bootstrap().group(EPOLL_WORKER_GROUP).channel(EpollSocketChannel.class));
        toReturn.add(() -> new Bootstrap().group(nioWorkerGroup).channel(NioSocketChannel.class));
        return toReturn;
    }

    @Override
    public List<BootstrapFactory<Bootstrap>> clientSocketWithFastOpen() {
        List<BootstrapFactory<Bootstrap>> factories = clientSocket();

        if (IS_SUPPORTING_TCP_FASTOPEN_CLIENT) {
            int insertIndex = factories.size() - 1; // Keep NIO fixture last.
            factories.add(insertIndex, () -> new Bootstrap().group(EPOLL_WORKER_GROUP).channel(EpollSocketChannel.class)
                    .option(ChannelOption.TCP_FASTOPEN_CONNECT, true));
        }
        return clientSocket();
    }

    @Override
    public List<TestsuitePermutation.BootstrapComboFactory<Bootstrap, Bootstrap>> datagram(
            final InternetProtocolFamily family) {
        // Make the list of Bootstrap factories.
        List<BootstrapFactory<Bootstrap>> bfs = Arrays.asList(
                () -> new Bootstrap().group(nioWorkerGroup).channelFactory(new ChannelFactory<Channel>() {
                    @Override
                    public Channel newChannel(EventLoop eventLoop) {
                        return new NioDatagramChannel(eventLoop, family);
                    }

                    @Override
                    public String toString() {
                        return NioDatagramChannel.class.getSimpleName() + ".class";
                    }
                }),
                () -> new Bootstrap().group(EPOLL_WORKER_GROUP).channelFactory(new ChannelFactory<Channel>() {
                    @Override
                    public Channel newChannel(EventLoop eventLoop) {
                        return new EpollDatagramChannel(eventLoop, family);
                    }

                    @Override
                    public String toString() {
                        return InternetProtocolFamily.class.getSimpleName() + ".class";
                    }
                })
        );
        return combo(bfs, bfs);
    }

    List<TestsuitePermutation.BootstrapComboFactory<Bootstrap, Bootstrap>> epollOnlyDatagram(
            final InternetProtocolFamily family) {
        return combo(Collections.singletonList(datagramBootstrapFactory(family)),
                Collections.singletonList(datagramBootstrapFactory(family)));
    }

    private static BootstrapFactory<Bootstrap> datagramBootstrapFactory(final InternetProtocolFamily family) {
        return () -> new Bootstrap().group(EPOLL_WORKER_GROUP).channelFactory(new ChannelFactory<Channel>() {
            @Override
            public Channel newChannel(EventLoop eventLoop) {
                return new EpollDatagramChannel(eventLoop, family);
            }

            @Override
            public String toString() {
                return InternetProtocolFamily.class.getSimpleName() + ".class";
            }
        });
    }

    public List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> domainSocket() {
        return combo(serverDomainSocket(), clientDomainSocket());
    }

    public List<BootstrapFactory<ServerBootstrap>> serverDomainSocket() {
        return Collections.singletonList(
                () -> new ServerBootstrap().group(EPOLL_BOSS_GROUP, EPOLL_WORKER_GROUP)
                        .channel(EpollServerDomainSocketChannel.class)
        );
    }

    public List<BootstrapFactory<Bootstrap>> clientDomainSocket() {
        return Collections.singletonList(
                () -> new Bootstrap().group(EPOLL_WORKER_GROUP).channel(EpollDomainSocketChannel.class)
        );
    }

    @Override
    public List<BootstrapFactory<Bootstrap>> datagramSocket() {
        return Collections.singletonList(
                () -> new Bootstrap().group(EPOLL_WORKER_GROUP).channel(EpollDatagramChannel.class)
        );
    }

    public static DomainSocketAddress newDomainSocketAddress() {
        return UnixTestUtils.newDomainSocketAddress();
    }

    public List<TestsuitePermutation.BootstrapComboFactory<Bootstrap, Bootstrap>> domainDatagram() {
        return combo(domainDatagramSocket(), domainDatagramSocket());
    }

    public List<BootstrapFactory<Bootstrap>> domainDatagramSocket() {
        return Collections.singletonList(
                () -> new Bootstrap().group(EPOLL_WORKER_GROUP).channel(EpollDomainDatagramChannel.class)
        );
    }
}
