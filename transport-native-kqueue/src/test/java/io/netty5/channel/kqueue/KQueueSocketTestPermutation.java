/*
 * Copyright 2016 The Netty Project
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
package io.netty5.channel.kqueue;

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

class KQueueSocketTestPermutation extends SocketTestPermutation {

    static final KQueueSocketTestPermutation INSTANCE = new KQueueSocketTestPermutation();

    static final EventLoopGroup KQUEUE_BOSS_GROUP =
            new MultithreadEventLoopGroup(BOSSES, new DefaultThreadFactory("testsuite-KQueue-boss", true),
                    KQueueHandler.newFactory());
    static final EventLoopGroup KQUEUE_WORKER_GROUP =
            new MultithreadEventLoopGroup(WORKERS, new DefaultThreadFactory("testsuite-KQueue-worker", true),
                    KQueueHandler.newFactory());

    @Override
    public List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> socket() {

        List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> list =
                combo(serverSocket(), clientSocketWithFastOpen());

        list.remove(list.size() - 1); // Exclude NIO x NIO test

        return list;
    }

    @Override
    public List<BootstrapFactory<ServerBootstrap>> serverSocket() {
        List<BootstrapFactory<ServerBootstrap>> toReturn = new ArrayList<>();
        toReturn.add(() -> new ServerBootstrap().group(KQUEUE_BOSS_GROUP, KQUEUE_WORKER_GROUP)
                                    .channel(KQueueServerSocketChannel.class));
        toReturn.add(() -> {
            ServerBootstrap serverBootstrap = new ServerBootstrap().group(KQUEUE_BOSS_GROUP, KQUEUE_WORKER_GROUP)
                                                                   .channel(KQueueServerSocketChannel.class);
            serverBootstrap.option(ChannelOption.TCP_FASTOPEN, 1);
            return serverBootstrap;
        });

        toReturn.add(() -> new ServerBootstrap().group(nioBossGroup, nioWorkerGroup)
                                    .channel(NioServerSocketChannel.class));

        return toReturn;
    }

    @Override
    public List<BootstrapFactory<Bootstrap>> clientSocket() {
        List<BootstrapFactory<Bootstrap>> toReturn = new ArrayList<BootstrapFactory<Bootstrap>>();

        toReturn.add(() -> new Bootstrap().group(KQUEUE_WORKER_GROUP).channel(KQueueSocketChannel.class));
        toReturn.add(() -> new Bootstrap().group(nioWorkerGroup).channel(NioSocketChannel.class));

        return toReturn;
    }

    @Override
    public List<BootstrapFactory<Bootstrap>> clientSocketWithFastOpen() {
        List<BootstrapFactory<Bootstrap>> factories = clientSocket();

        int insertIndex = factories.size() - 1; // Keep NIO fixture last.
        factories.add(insertIndex,
                      () -> new Bootstrap().group(KQUEUE_WORKER_GROUP).channel(KQueueSocketChannel.class)
                                           .option(ChannelOption.TCP_FASTOPEN_CONNECT, true));

        return factories;
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
                () -> new Bootstrap().group(KQUEUE_WORKER_GROUP).channel(KQueueDatagramChannel.class)
        );
        return combo(bfs, bfs);
    }

    public List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> domainSocket() {
        return combo(serverDomainSocket(), clientDomainSocket());
    }

    public List<BootstrapFactory<ServerBootstrap>> serverDomainSocket() {
        return Collections.singletonList(
                () -> new ServerBootstrap().group(KQUEUE_BOSS_GROUP, KQUEUE_WORKER_GROUP)
                        .channel(KQueueServerDomainSocketChannel.class)
        );
    }

    public List<BootstrapFactory<Bootstrap>> clientDomainSocket() {
        return Collections.singletonList(
                () -> new Bootstrap().group(KQUEUE_WORKER_GROUP).channel(KQueueDomainSocketChannel.class)
        );
    }

    @Override
    public List<BootstrapFactory<Bootstrap>> datagramSocket() {
        return Collections.singletonList(
                () -> new Bootstrap().group(KQUEUE_WORKER_GROUP).channel(KQueueDatagramChannel.class)
        );
    }

    public static DomainSocketAddress newSocketAddress() {
        return UnixTestUtils.newDomainSocketAddress();
    }

    public List<TestsuitePermutation.BootstrapComboFactory<Bootstrap, Bootstrap>> domainDatagram() {
        return combo(domainDatagramSocket(), domainDatagramSocket());
    }

    public List<BootstrapFactory<Bootstrap>> domainDatagramSocket() {
        return Collections.singletonList(
                () -> new Bootstrap().group(KQUEUE_WORKER_GROUP).channel(KQueueDomainDatagramChannel.class)
        );
    }
}
