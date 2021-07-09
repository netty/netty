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
package io.netty.channel.epoll;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.channel.unix.tests.UnixTestUtils;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.TestsuitePermutation.BootstrapFactory;
import io.netty.testsuite.transport.socket.SocketTestPermutation;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.netty.channel.epoll.Native.IS_SUPPORTING_TCP_FASTOPEN_CLIENT;
import static io.netty.channel.epoll.Native.IS_SUPPORTING_TCP_FASTOPEN_SERVER;

class EpollSocketTestPermutation extends SocketTestPermutation {

    static final EpollSocketTestPermutation INSTANCE = new EpollSocketTestPermutation();

    static final EventLoopGroup EPOLL_BOSS_GROUP =
            new EpollEventLoopGroup(BOSSES, new DefaultThreadFactory("testsuite-epoll-boss", true));
    static final EventLoopGroup EPOLL_WORKER_GROUP =
            new EpollEventLoopGroup(WORKERS, new DefaultThreadFactory("testsuite-epoll-worker", true));

    @Override
    public List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> socket() {
        List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> list =
                combo(serverSocket(), clientSocketWithFastOpen());

        list.remove(list.size() - 1); // Exclude NIO x NIO test

        return list;
    }

    public List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> socketWithoutFastOpen() {
        List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> list =
                combo(serverSocket(), clientSocket());

        list.remove(list.size() - 1); // Exclude NIO x NIO test

        return list;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<BootstrapFactory<ServerBootstrap>> serverSocket() {
        List<BootstrapFactory<ServerBootstrap>> toReturn = new ArrayList<BootstrapFactory<ServerBootstrap>>();
        toReturn.add(new BootstrapFactory<ServerBootstrap>() {
            @Override
            public ServerBootstrap newInstance() {
                return new ServerBootstrap().group(EPOLL_BOSS_GROUP, EPOLL_WORKER_GROUP)
                                            .channel(EpollServerSocketChannel.class);
            }
        });
        if (IS_SUPPORTING_TCP_FASTOPEN_SERVER) {
            toReturn.add(new BootstrapFactory<ServerBootstrap>() {
                @Override
                public ServerBootstrap newInstance() {
                    ServerBootstrap serverBootstrap = new ServerBootstrap().group(EPOLL_BOSS_GROUP, EPOLL_WORKER_GROUP)
                                                                           .channel(EpollServerSocketChannel.class);
                    serverBootstrap.option(EpollChannelOption.TCP_FASTOPEN, 5);
                    return serverBootstrap;
                }
            });
        }
        toReturn.add(new BootstrapFactory<ServerBootstrap>() {
            @Override
            public ServerBootstrap newInstance() {
                return new ServerBootstrap().group(nioBossGroup, nioWorkerGroup)
                                            .channel(NioServerSocketChannel.class);
            }
        });

        return toReturn;
    }

    @Override
    public List<BootstrapFactory<Bootstrap>> clientSocket() {
        List<BootstrapFactory<Bootstrap>> toReturn = new ArrayList<BootstrapFactory<Bootstrap>>();

        toReturn.add(new BootstrapFactory<Bootstrap>() {
            @Override
            public Bootstrap newInstance() {
                return new Bootstrap().group(EPOLL_WORKER_GROUP).channel(EpollSocketChannel.class);
            }
        });

        toReturn.add(new BootstrapFactory<Bootstrap>() {
            @Override
            public Bootstrap newInstance() {
                return new Bootstrap().group(nioWorkerGroup).channel(NioSocketChannel.class);
            }
        });

        return toReturn;
    }

    @Override
    public List<BootstrapFactory<Bootstrap>> clientSocketWithFastOpen() {
        List<BootstrapFactory<Bootstrap>> factories = clientSocket();

        if (IS_SUPPORTING_TCP_FASTOPEN_CLIENT) {
            int insertIndex = factories.size() - 1; // Keep NIO fixture last.
            factories.add(insertIndex, new BootstrapFactory<Bootstrap>() {
                @Override
                public Bootstrap newInstance() {
                    return new Bootstrap().group(EPOLL_WORKER_GROUP).channel(EpollSocketChannel.class)
                            .option(ChannelOption.TCP_FASTOPEN_CONNECT, true);
                }
            });
        }

        return factories;
    }

    @Override
    public List<TestsuitePermutation.BootstrapComboFactory<Bootstrap, Bootstrap>> datagram(
            final InternetProtocolFamily family) {
        // Make the list of Bootstrap factories.
        List<BootstrapFactory<Bootstrap>> bfs = Arrays.asList(
                new BootstrapFactory<Bootstrap>() {
                    @Override
                    public Bootstrap newInstance() {
                        return new Bootstrap().group(nioWorkerGroup).channelFactory(new ChannelFactory<Channel>() {
                            @Override
                            public Channel newChannel() {
                                return new NioDatagramChannel(family);
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
                        return new Bootstrap().group(EPOLL_WORKER_GROUP).channelFactory(new ChannelFactory<Channel>() {
                            @Override
                            public Channel newChannel() {
                                return new EpollDatagramChannel(family);
                            }

                            @Override
                            public String toString() {
                                return InternetProtocolFamily.class.getSimpleName() + ".class";
                            }
                        });
                    }
                }
        );
        return combo(bfs, bfs);
    }

    List<TestsuitePermutation.BootstrapComboFactory<Bootstrap, Bootstrap>> epollOnlyDatagram(
            final InternetProtocolFamily family) {
        return combo(Collections.singletonList(datagramBootstrapFactory(family)),
                Collections.singletonList(datagramBootstrapFactory(family)));
    }

    private static BootstrapFactory<Bootstrap> datagramBootstrapFactory(final InternetProtocolFamily family) {
        return new BootstrapFactory<Bootstrap>() {
            @Override
            public Bootstrap newInstance() {
                return new Bootstrap().group(EPOLL_WORKER_GROUP).channelFactory(new ChannelFactory<Channel>() {
                    @Override
                    public Channel newChannel() {
                        return new EpollDatagramChannel(family);
                    }

                    @Override
                    public String toString() {
                        return InternetProtocolFamily.class.getSimpleName() + ".class";
                    }
                });
            }
        };
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

    @Override
    public List<BootstrapFactory<Bootstrap>> datagramSocket() {
        return Collections.<BootstrapFactory<Bootstrap>>singletonList(
                new BootstrapFactory<Bootstrap>() {
                    @Override
                    public Bootstrap newInstance() {
                        return new Bootstrap().group(EPOLL_WORKER_GROUP).channel(EpollDatagramChannel.class);
                    }
                }
        );
    }

    public List<TestsuitePermutation.BootstrapComboFactory<Bootstrap, Bootstrap>> domainDatagram() {
        return combo(domainDatagramSocket(), domainDatagramSocket());
    }

    public List<BootstrapFactory<Bootstrap>> domainDatagramSocket() {
        return Collections.<BootstrapFactory<Bootstrap>>singletonList(
                new BootstrapFactory<Bootstrap>() {
                    @Override
                    public Bootstrap newInstance() {
                        return new Bootstrap().group(EPOLL_WORKER_GROUP).channel(EpollDomainDatagramChannel.class);
                    }
                }
        );
    }

    public static DomainSocketAddress newSocketAddress() {
        return UnixTestUtils.newSocketAddress();
    }
}
