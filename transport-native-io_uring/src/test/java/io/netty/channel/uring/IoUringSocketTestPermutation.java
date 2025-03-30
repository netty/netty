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
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.TestsuitePermutation.BootstrapComboFactory;
import io.netty.testsuite.transport.TestsuitePermutation.BootstrapFactory;
import io.netty.testsuite.transport.socket.SocketTestPermutation;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IoUringSocketTestPermutation extends SocketTestPermutation {

    static final IoUringSocketTestPermutation INSTANCE = new IoUringSocketTestPermutation();
    static final short BGID = 0;
    static final EventLoopGroup IO_URING_BOSS_GROUP = new MultiThreadIoEventLoopGroup(
            BOSSES, new DefaultThreadFactory("testsuite-io_uring-boss", true), IoUringIoHandler.newFactory());
    static final EventLoopGroup IO_URING_WORKER_GROUP = new MultiThreadIoEventLoopGroup(
            WORKERS, new DefaultThreadFactory("testsuite-io_uring-worker", true),
            IoUringIoHandler.newFactory(buildConfig()));

    static IoUringIoHandlerConfig buildConfig() {
        IoUringIoHandlerConfig config = new IoUringIoHandlerConfig();
        if (IoUring.isRegisterBufferRingSupported()) {
            config.setBufferRingConfig(
                    new IoUringBufferRingConfig(BGID, (short) 16, 16 * 16,
                            new IoUringFixedBufferRingAllocator(1024)));
        }
        return config;
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
                return new ServerBootstrap().group(IO_URING_BOSS_GROUP, IO_URING_WORKER_GROUP)
                                            .channel(IoUringServerSocketChannel.class);
            }
        });
        if (IoUring.isTcpFastOpenServerSideAvailable()) {
            toReturn.add(new BootstrapFactory<ServerBootstrap>() {
                @Override
                public ServerBootstrap newInstance() {
                    ServerBootstrap serverBootstrap = new ServerBootstrap().group(IO_URING_BOSS_GROUP,
                                                                                  IO_URING_WORKER_GROUP)
                                                                           .channel(IoUringServerSocketChannel.class);
                    serverBootstrap.option(ChannelOption.TCP_FASTOPEN, 5);
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
        return Arrays.asList(
                new BootstrapFactory<Bootstrap>() {
                    @Override
                    public Bootstrap newInstance() {
                        return new Bootstrap().group(IO_URING_WORKER_GROUP).channel(IoUringSocketChannel.class);
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
    public List<BootstrapFactory<Bootstrap>> clientSocketWithFastOpen() {
        List<BootstrapFactory<Bootstrap>> factories = clientSocket();

        if (IoUring.isTcpFastOpenClientSideAvailable()) {
            int insertIndex = factories.size() - 1; // Keep NIO fixture last.
            factories.add(insertIndex, new BootstrapFactory<Bootstrap>() {
                @Override
                public Bootstrap newInstance() {
                    return new Bootstrap().group(IO_URING_WORKER_GROUP).channel(IoUringSocketChannel.class)
                            .option(ChannelOption.TCP_FASTOPEN_CONNECT, true);
                }
            });
        }

        return factories;
    }

    @Override
    public List<TestsuitePermutation.BootstrapComboFactory<Bootstrap, Bootstrap>> datagram(
            final SocketProtocolFamily family) {
        // Make the list of Bootstrap factories.
        List<BootstrapFactory<Bootstrap>> bfs = Arrays.<BootstrapFactory<Bootstrap>>asList(
                new BootstrapFactory<Bootstrap>() {
                    @Override
                    public Bootstrap newInstance() {
                        return new Bootstrap().group(IO_URING_WORKER_GROUP)
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
                }
        );

        return combo(bfs, bfs);
    }
}
