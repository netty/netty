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
import io.netty.channel.unix.tests.UnixTestUtils;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.TestsuitePermutation.BootstrapFactory;
import io.netty.testsuite.transport.socket.SocketTestPermutation;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class EpollSocketTestPermutation extends SocketTestPermutation {

    static final EpollSocketTestPermutation INSTANCE = new EpollSocketTestPermutation();

    static final EventLoopGroup EPOLL_BOSS_GROUP =
            new EpollEventLoopGroup(BOSSES, new DefaultThreadFactory("testsuite-epoll-boss", true));
    static final EventLoopGroup EPOLL_WORKER_GROUP =
            new EpollEventLoopGroup(WORKERS, new DefaultThreadFactory("testsuite-epoll-worker", true));

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(EpollSocketTestPermutation.class);

    @Override
    public List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> socket() {

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
        if (isServerFastOpen()) {
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

    @SuppressWarnings("unchecked")
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
        @SuppressWarnings("unchecked")
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

    public boolean isServerFastOpen() {
        return AccessController.doPrivileged(new PrivilegedAction<Integer>() {
            @Override
            public Integer run() {
                int fastopen = 0;
                File file = new File("/proc/sys/net/ipv4/tcp_fastopen");
                if (file.exists()) {
                    BufferedReader in = null;
                    try {
                        in = new BufferedReader(new FileReader(file));
                        fastopen = Integer.parseInt(in.readLine());
                        if (logger.isDebugEnabled()) {
                            logger.debug("{}: {}", file, fastopen);
                        }
                    } catch (Exception e) {
                        logger.debug("Failed to get TCP_FASTOPEN from: {}", file, e);
                    } finally {
                        if (in != null) {
                            try {
                                in.close();
                            } catch (Exception e) {
                                // Ignored.
                            }
                        }
                    }
                } else {
                    if (logger.isDebugEnabled()) {
                        logger.debug("{}: {} (non-existent)", file, fastopen);
                    }
                }
                return fastopen;
            }
        }) == 3;
    }

    public static DomainSocketAddress newSocketAddress() {
        return UnixTestUtils.newSocketAddress();
    }
}
