/*
 * Copyright 2024 The Netty Project
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
package io.netty.channel.uring;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.channel.unix.tests.UnixTestUtils;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.TestsuitePermutation.BootstrapComboFactory;
import io.netty.testsuite.transport.TestsuitePermutation.BootstrapFactory;
import io.netty.testsuite.transport.socket.SocketTestPermutation;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class IoUringSocketTestPermutation extends SocketTestPermutation {

    static final IoUringSocketTestPermutation INSTANCE = new IoUringSocketTestPermutation();
    static final short BGID = 0;
    static final EventLoopGroup IO_URING_GROUP = newGroup(false);
    static final EventLoopGroup IO_URING_INCREMENTAL_GROUP = newGroup(true);

    static IoUringIoHandlerConfig buildConfig(boolean incremental) {
        IoUringIoHandlerConfig config = new IoUringIoHandlerConfig();
        if (IoUring.isRegisterBufferRingSupported()) {
            config.setBufferRingConfig(
                    IoUringBufferRingConfig.builder()
                            .bufferGroupId(BGID)
                            .bufferRingSize((short) 16)
                            .batchSize(8)
                            .incremental(incremental)
                            .allocator(new IoUringFixedBufferRingAllocator(1024))
                            // Ensure we test both variants
                            .batchAllocation(ThreadLocalRandom.current().nextBoolean())
                            .build()
            );
        }
        return config;
    }

    private static EventLoopGroup newGroup(boolean incremental) {
        if (!IoUring.isRegisterBufferRingIncSupported() && incremental) {
            return null;
        }
        return new MultiThreadIoEventLoopGroup(
                NUM_THREADS, new DefaultThreadFactory(incremental ?
                "testsuite-io_uring-group-buffer-ring-incremental" : "testsuite-io_uring-group", true),
                IoUringIoHandler.newFactory(buildConfig(incremental)));
    }

    @Override
    public List<BootstrapComboFactory<ServerBootstrap, Bootstrap>> socket() {

        List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> list =
                combo(serverSocket(), clientSocket());

        list.remove(list.size() - 1); // Exclude NIO x NIO test

        return list;
    }

    @Override
    public List<BootstrapFactory<ServerBootstrap>> serverSocket() {
        List<BootstrapFactory<ServerBootstrap>> toReturn = new ArrayList<BootstrapFactory<ServerBootstrap>>();
        toReturn.add(new BootstrapFactory<ServerBootstrap>() {
            @Override
            public ServerBootstrap newInstance() {
                return new ServerBootstrap().group(IO_URING_GROUP)
                                            .channel(IoUringServerSocketChannel.class);
            }
        });
        if (IO_URING_INCREMENTAL_GROUP != null) {
            toReturn.add(new BootstrapFactory<ServerBootstrap>() {
                @Override
                public ServerBootstrap newInstance() {
                    return new ServerBootstrap().group(IO_URING_INCREMENTAL_GROUP)
                            .channel(IoUringServerSocketChannel.class);
                }
            });
        }
        if (IoUring.isTcpFastOpenServerSideAvailable()) {
            toReturn.add(new BootstrapFactory<ServerBootstrap>() {
                @Override
                public ServerBootstrap newInstance() {
                    ServerBootstrap serverBootstrap = new ServerBootstrap().group(IO_URING_GROUP)
                                                                           .channel(IoUringServerSocketChannel.class);
                    serverBootstrap.option(ChannelOption.TCP_FASTOPEN, 5);
                    return serverBootstrap;
                }
            });
            if (IO_URING_INCREMENTAL_GROUP != null) {
                toReturn.add(new BootstrapFactory<ServerBootstrap>() {
                    @Override
                    public ServerBootstrap newInstance() {
                        ServerBootstrap serverBootstrap = new ServerBootstrap().group(IO_URING_INCREMENTAL_GROUP)
                                .channel(IoUringServerSocketChannel.class);
                        serverBootstrap.option(ChannelOption.TCP_FASTOPEN, 5);
                        return serverBootstrap;
                    }
                });
            }
        }
        toReturn.add(new BootstrapFactory<ServerBootstrap>() {
            @Override
            public ServerBootstrap newInstance() {
                return new ServerBootstrap().group(NIO_GROUP)
                                            .channel(NioServerSocketChannel.class);
            }
        });

        return toReturn;
    }

    @Override
    public List<BootstrapFactory<Bootstrap>> clientSocket() {
        List<BootstrapFactory<Bootstrap>> toReturn = new ArrayList<>();
        toReturn.add(
                new BootstrapFactory<Bootstrap>() {
                    @Override
                    public Bootstrap newInstance() {
                        return new Bootstrap().group(IO_URING_GROUP).channel(IoUringSocketChannel.class);
                    }
                });
        if (IO_URING_INCREMENTAL_GROUP != null) {
            toReturn.add(
                    new BootstrapFactory<Bootstrap>() {
                        @Override
                        public Bootstrap newInstance() {
                            return new Bootstrap().group(IO_URING_INCREMENTAL_GROUP)
                                    .channel(IoUringSocketChannel.class);
                        }
                    });
        }
        toReturn.add(
                new BootstrapFactory<Bootstrap>() {
                    @Override
                    public Bootstrap newInstance() {
                        return new Bootstrap().group(NIO_GROUP).channel(NioSocketChannel.class);
                    }
                }
        );
        return toReturn;
    }

    @Override
    public List<BootstrapFactory<Bootstrap>> clientSocketWithFastOpen() {
        List<BootstrapFactory<Bootstrap>> factories = clientSocket();

        if (IoUring.isTcpFastOpenClientSideAvailable()) {
            int insertIndex = factories.size() - 1; // Keep NIO fixture last.
            factories.add(insertIndex, new BootstrapFactory<Bootstrap>() {
                @Override
                public Bootstrap newInstance() {
                    return new Bootstrap().group(IO_URING_GROUP).channel(IoUringSocketChannel.class)
                            .option(ChannelOption.TCP_FASTOPEN_CONNECT, true);
                }
            });
            if (IO_URING_INCREMENTAL_GROUP != null) {
                factories.add(insertIndex + 1, new BootstrapFactory<Bootstrap>() {
                    @Override
                    public Bootstrap newInstance() {
                        return new Bootstrap().group(IO_URING_INCREMENTAL_GROUP)
                                .channel(IoUringSocketChannel.class)
                                .option(ChannelOption.TCP_FASTOPEN_CONNECT, true);
                    }
                });
            }
        }

        return factories;
    }

    public List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> domainSocket() {
        return combo(serverDomainSocket(), clientDomainSocket());
    }

    @Override
    public List<TestsuitePermutation.BootstrapComboFactory<Bootstrap, Bootstrap>> datagram(
            final SocketProtocolFamily family) {
        // Make the list of Bootstrap factories.
        List<BootstrapFactory<Bootstrap>> bfs = Arrays.<BootstrapFactory<Bootstrap>>asList(
                new BootstrapFactory<Bootstrap>() {
                    @Override
                    public Bootstrap newInstance() {
                        return new Bootstrap().group(IO_URING_GROUP)
                                .channelFactory(new ChannelFactory<Channel>() {
                            @Override
                            public Channel newChannel() {
                                return new IoUringDatagramChannel(family);
                            }

                            @Override
                            public String toString() {
                                return IoUringDatagramChannel.class.getSimpleName() + ".class";
                            }
                        });
                    }
                },
                new BootstrapFactory<Bootstrap>() {
                    @Override
                    public Bootstrap newInstance() {
                        return new Bootstrap().group(NIO_GROUP).channelFactory(new ChannelFactory<Channel>() {
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
                }
        );

        return combo(bfs, bfs);
    }

    public List<BootstrapFactory<ServerBootstrap>> serverDomainSocket() {
        return Collections.<BootstrapFactory<ServerBootstrap>>singletonList(
                new BootstrapFactory<ServerBootstrap>() {
                    @Override
                    public ServerBootstrap newInstance() {
                        return new ServerBootstrap().group(IO_URING_GROUP)
                                .channel(IoUringServerDomainSocketChannel.class);
                    }
                }
        );
    }

    public List<BootstrapFactory<Bootstrap>> clientDomainSocket() {
        return Collections.<BootstrapFactory<Bootstrap>>singletonList(
                new BootstrapFactory<Bootstrap>() {
                    @Override
                    public Bootstrap newInstance() {
                        return new Bootstrap().group(IO_URING_GROUP)
                                .channel(IoUringDomainSocketChannel.class);
                    }
                }
        );
    }

    public static DomainSocketAddress newDomainSocketAddress() {
        return UnixTestUtils.newDomainSocketAddress();
    }

}
