/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.epoll;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.TestsuitePermutation.BootstrapFactory;
import io.netty.testsuite.transport.socket.SocketTestPermutation;
import io.netty.util.concurrent.DefaultExecutorServiceFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class EpollSocketTestPermutation extends SocketTestPermutation {

    static final EpollSocketTestPermutation INSTANCE = new EpollSocketTestPermutation();

    static final EventLoopGroup EPOLL_BOSS_GROUP =
            new EpollEventLoopGroup(BOSSES, new DefaultExecutorServiceFactory("testsuite-epoll-boss"));
    static final EventLoopGroup EPOLL_WORKER_GROUP =
            new EpollEventLoopGroup(WORKERS, new DefaultExecutorServiceFactory("testsuite-epoll-worker"));

    @Override
    public List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> socket() {

        List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> list =
                combo(serverSocket(), clientSocket());

        list.remove(list.size() - 1); // Exclude NIO x NIO test

        return list;
    }

    @Override
    public List<BootstrapFactory<ServerBootstrap>> serverSocket() {
        return Arrays.asList(
                new BootstrapFactory<ServerBootstrap>() {
                    @Override
                    public ServerBootstrap newInstance() {
                        return new ServerBootstrap().group(EPOLL_BOSS_GROUP, EPOLL_WORKER_GROUP)
                                .channel(EpollServerSocketChannel.class);
                    }
                },
                new BootstrapFactory<ServerBootstrap>() {
                    @Override
                    public ServerBootstrap newInstance() {
                        return new ServerBootstrap().group(nioBossGroup, nioWorkerGroup)
                                .channel(NioServerSocketChannel.class);
                    }
                }
        );
    }

    @Override
    public List<BootstrapFactory<Bootstrap>> clientSocket() {
        return Arrays.asList(
                new BootstrapFactory<Bootstrap>() {
                    @Override
                    public Bootstrap newInstance() {
                        return new Bootstrap().group(EPOLL_WORKER_GROUP).channel(EpollSocketChannel.class);
                    }
                },
                new BootstrapFactory<Bootstrap>() {
                    @Override
                    public Bootstrap newInstance() {
                        return new Bootstrap().group(nioWorkerGroup).channel(NioSocketChannel.class);
                    }
                }
        );
    }

    @Override
    public List<TestsuitePermutation.BootstrapComboFactory<Bootstrap, Bootstrap>> datagram() {
        // Make the list of Bootstrap factories.
        List<BootstrapFactory<Bootstrap>> bfs = Arrays.asList(
                new BootstrapFactory<Bootstrap>() {
                    @Override
                    public Bootstrap newInstance() {
                        return new Bootstrap().group(nioWorkerGroup).channelFactory(new ChannelFactory<Channel>() {
                            @Override
                            public Channel newChannel() {
                                return new NioDatagramChannel(InternetProtocolFamily.IPv4);
                            }

                            @Override
                            public String toString() {
                                return NioDatagramChannel.class.getSimpleName() + ".class";
                            }
                        });
                    }
                },
                new BootstrapFactory<Bootstrap>() {
                    @Override
                    public Bootstrap newInstance() {
                        return new Bootstrap().group(EPOLL_WORKER_GROUP).channel(EpollDatagramChannel.class);
                    }
                }
        );
        return combo(bfs, bfs);
    }

    public List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> domainSocket() {

        List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> list =
                combo(serverDomainSocket(), clientDomainSocket());
        return list;
    }

    public List<BootstrapFactory<ServerBootstrap>> serverDomainSocket() {
        return Collections.<BootstrapFactory<ServerBootstrap>>singletonList(
                new BootstrapFactory<ServerBootstrap>() {
                    @Override
                    public ServerBootstrap newInstance() {
                        return new ServerBootstrap().group(EPOLL_BOSS_GROUP, EPOLL_WORKER_GROUP)
                                .channel(EpollServerDomainSocketChannel.class);
                    }
                }
        );
    }

    public List<BootstrapFactory<Bootstrap>> clientDomainSocket() {
        return Collections.<BootstrapFactory<Bootstrap>>singletonList(
                new BootstrapFactory<Bootstrap>() {
                    @Override
                    public Bootstrap newInstance() {
                        return new Bootstrap().group(EPOLL_WORKER_GROUP).channel(EpollDomainSocketChannel.class);
                    }
                }
        );
    }

    public static DomainSocketAddress newSocketAddress() {
        try {
            File file = File.createTempFile("netty", "dsocket");
            file.delete();
            return new DomainSocketAddress(file);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
