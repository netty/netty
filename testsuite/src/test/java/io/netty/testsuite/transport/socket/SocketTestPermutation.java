/*
 * Copyright 2012 The Netty Project
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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ChannelFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.oio.OioEventLoopGroup;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.socket.oio.OioDatagramChannel;
import io.netty.channel.socket.oio.OioServerSocketChannel;
import io.netty.channel.socket.oio.OioSocketChannel;
import io.netty.testsuite.transport.TestsuitePermutation.BootstrapComboFactory;
import io.netty.testsuite.transport.TestsuitePermutation.BootstrapFactory;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.ArrayList;
import java.util.List;

public final class SocketTestPermutation {
    private SocketTestPermutation() {
        // utility
    }

    private static final int BOSSES = 2;
    private static final int WORKERS = 3;
    private static final EventLoopGroup nioBossGroup =
            new NioEventLoopGroup(BOSSES, new DefaultThreadFactory("testsuite-nio-boss", true));
    private static final EventLoopGroup nioWorkerGroup =
            new NioEventLoopGroup(WORKERS, new DefaultThreadFactory("testsuite-nio-worker", true));
    private static final EventLoopGroup oioBossGroup =
            new OioEventLoopGroup(Integer.MAX_VALUE, new DefaultThreadFactory("testsuite-oio-boss", true));
    private static final EventLoopGroup oioWorkerGroup =
            new OioEventLoopGroup(Integer.MAX_VALUE, new DefaultThreadFactory("testsuite-oio-worker", true));

    static List<BootstrapComboFactory<ServerBootstrap, Bootstrap>> socket() {
        List<BootstrapComboFactory<ServerBootstrap, Bootstrap>> list =
                new ArrayList<BootstrapComboFactory<ServerBootstrap, Bootstrap>>();

        // Make the list of ServerBootstrap factories.
        List<BootstrapFactory<ServerBootstrap>> sbfs = serverSocket();

        // Make the list of Bootstrap factories.
        List<BootstrapFactory<Bootstrap>> cbfs = clientSocket();

        // Populate the combinations
        for (BootstrapFactory<ServerBootstrap> sbf: sbfs) {
            for (BootstrapFactory<Bootstrap> cbf: cbfs) {
                final BootstrapFactory<ServerBootstrap> sbf0 = sbf;
                final BootstrapFactory<Bootstrap> cbf0 = cbf;
                list.add(new BootstrapComboFactory<ServerBootstrap, Bootstrap>() {
                    @Override
                    public ServerBootstrap newServerInstance() {
                        return sbf0.newInstance();
                    }

                    @Override
                    public Bootstrap newClientInstance() {
                        return cbf0.newInstance();
                    }
                });
            }
        }

        // Remove the OIO-OIO case which often leads to a dead lock by its nature.
        list.remove(list.size() - 1);

        return list;
    }

    static List<BootstrapComboFactory<Bootstrap, Bootstrap>> datagram() {
        List<BootstrapComboFactory<Bootstrap, Bootstrap>> list =
                new ArrayList<BootstrapComboFactory<Bootstrap, Bootstrap>>();

        // Make the list of Bootstrap factories.
        List<BootstrapFactory<Bootstrap>> bfs =
                new ArrayList<BootstrapFactory<Bootstrap>>();
        bfs.add(new BootstrapFactory<Bootstrap>() {
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
        });
        bfs.add(new BootstrapFactory<Bootstrap>() {
            @Override
            public Bootstrap newInstance() {
                return new Bootstrap().group(oioWorkerGroup).channel(OioDatagramChannel.class);
            }
        });

        // Populate the combinations
        for (BootstrapFactory<Bootstrap> sbf: bfs) {
            for (BootstrapFactory<Bootstrap> cbf: bfs) {
                final BootstrapFactory<Bootstrap> sbf0 = sbf;
                final BootstrapFactory<Bootstrap> cbf0 = cbf;
                list.add(new BootstrapComboFactory<Bootstrap, Bootstrap>() {
                    @Override
                    public Bootstrap newServerInstance() {
                        return sbf0.newInstance();
                    }

                    @Override
                    public Bootstrap newClientInstance() {
                        return cbf0.newInstance();
                    }
                });
            }
        }

        return list;
    }

    static List<BootstrapFactory<ServerBootstrap>> serverSocket() {
        List<BootstrapFactory<ServerBootstrap>> list = new ArrayList<BootstrapFactory<ServerBootstrap>>();

        // Make the list of ServerBootstrap factories.
        list.add(new BootstrapFactory<ServerBootstrap>() {
            @Override
            public ServerBootstrap newInstance() {
                return new ServerBootstrap().group(nioBossGroup, nioWorkerGroup)
                                            .channel(NioServerSocketChannel.class);
            }
        });
        list.add(new BootstrapFactory<ServerBootstrap>() {
            @Override
            public ServerBootstrap newInstance() {
                return new ServerBootstrap().group(oioBossGroup, oioWorkerGroup)
                                            .channel(OioServerSocketChannel.class);
            }
        });

        return list;
    }

    static List<BootstrapFactory<Bootstrap>> clientSocket() {
        List<BootstrapFactory<Bootstrap>> list = new ArrayList<BootstrapFactory<Bootstrap>>();
        list.add(new BootstrapFactory<Bootstrap>() {
            @Override
            public Bootstrap newInstance() {
                return new Bootstrap().group(nioWorkerGroup).channel(NioSocketChannel.class);
            }
        });
        list.add(new BootstrapFactory<Bootstrap>() {
            @Override
            public Bootstrap newInstance() {
                return new Bootstrap().group(oioWorkerGroup).channel(OioSocketChannel.class);
            }
        });
        return list;
    }

}
