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

import io.netty.bootstrap.Bootstrap.ChannelFactory;
import io.netty.bootstrap.ClientBootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.aio.AioEventLoopGroup;
import io.netty.channel.socket.aio.AioServerSocketChannel;
import io.netty.channel.socket.aio.AioSocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.socket.oio.OioDatagramChannel;
import io.netty.channel.socket.oio.OioEventLoopGroup;
import io.netty.channel.socket.oio.OioServerSocketChannel;
import io.netty.channel.socket.oio.OioSocketChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

final class SocketTestPermutation {

    static List<Entry<Factory<ServerBootstrap>, Factory<ClientBootstrap>>> socket() {
        List<Entry<Factory<ServerBootstrap>, Factory<ClientBootstrap>>> list =
                new ArrayList<Entry<Factory<ServerBootstrap>, Factory<ClientBootstrap>>>();

        // Make the list of ServerBootstrap factories.
        List<Factory<ServerBootstrap>> sbfs = serverSocket();

        // Make the list of Bootstrap factories.
        List<Factory<ClientBootstrap>> cbfs = clientSocket();

        // Populate the combinations
        for (Factory<ServerBootstrap> sbf: sbfs) {
            for (Factory<ClientBootstrap> cbf: cbfs) {
                final Factory<ServerBootstrap> sbf0 = sbf;
                final Factory<ClientBootstrap> cbf0 = cbf;
                list.add(new Entry<Factory<ServerBootstrap>, Factory<ClientBootstrap>>() {
                    @Override
                    public Factory<ServerBootstrap> getKey() {
                        return sbf0;
                    }

                    @Override
                    public Factory<ClientBootstrap> getValue() {
                        return cbf0;
                    }

                    @Override
                    public Factory<ClientBootstrap> setValue(Factory<ClientBootstrap> value) {
                        throw new UnsupportedOperationException();
                    }
                });
            }
        }

        // Remove the OIO-OIO case which often leads to a dead lock by its nature.
        list.remove(list.size() - 1);

        return list;
    }

    static List<Entry<Factory<ClientBootstrap>, Factory<ClientBootstrap>>> datagram() {
        List<Entry<Factory<ClientBootstrap>, Factory<ClientBootstrap>>> list =
                new ArrayList<Entry<Factory<ClientBootstrap>, Factory<ClientBootstrap>>>();

        // Make the list of Bootstrap factories.
        List<Factory<ClientBootstrap>> bfs =
                new ArrayList<Factory<ClientBootstrap>>();
        bfs.add(new Factory<ClientBootstrap>() {
            @Override
            public ClientBootstrap newInstance() {
                return new ClientBootstrap().group(new NioEventLoopGroup()).channelFactory(new ChannelFactory() {
                    @Override
                    public Channel newChannel() {
                       return new NioDatagramChannel(InternetProtocolFamily.IPv4);
                    }
                });
            }
        });
        bfs.add(new Factory<ClientBootstrap>() {
            @Override
            public ClientBootstrap newInstance() {
                return new ClientBootstrap().group(new OioEventLoopGroup()).channel(OioDatagramChannel.class);
            }
        });

        // Populate the combinations
        for (Factory<ClientBootstrap> sbf: bfs) {
            for (Factory<ClientBootstrap> cbf: bfs) {
                final Factory<ClientBootstrap> sbf0 = sbf;
                final Factory<ClientBootstrap> cbf0 = cbf;
                list.add(new Entry<Factory<ClientBootstrap>, Factory<ClientBootstrap>>() {
                    @Override
                    public Factory<ClientBootstrap> getKey() {
                        return sbf0;
                    }

                    @Override
                    public Factory<ClientBootstrap> getValue() {
                        return cbf0;
                    }

                    @Override
                    public Factory<ClientBootstrap> setValue(Factory<ClientBootstrap> value) {
                        throw new UnsupportedOperationException();
                    }
                });
            }
        }

        return list;
    }

    static List<Factory<ServerBootstrap>> serverSocket() {
        List<Factory<ServerBootstrap>> list = new ArrayList<Factory<ServerBootstrap>>();

        // Make the list of ServerBootstrap factories.
        list.add(new Factory<ServerBootstrap>() {
            @Override
            public ServerBootstrap newInstance() {
                return new ServerBootstrap().
                                group(new NioEventLoopGroup(), new NioEventLoopGroup()).
                                channel(NioServerSocketChannel.class);
            }
        });
        list.add(new Factory<ServerBootstrap>() {
            @Override
            public ServerBootstrap newInstance() {
                final AioEventLoopGroup parentGroup = new AioEventLoopGroup();
                final AioEventLoopGroup childGroup = new AioEventLoopGroup();
                return new ServerBootstrap().group(parentGroup, childGroup).channelFactory(new ChannelFactory() {

                    @Override
                    public Channel newChannel() {
                        return new AioServerSocketChannel(parentGroup, childGroup);
                    }
                });
            }
        });
        list.add(new Factory<ServerBootstrap>() {
            @Override
            public ServerBootstrap newInstance() {
                return new ServerBootstrap().
                                group(new OioEventLoopGroup(), new OioEventLoopGroup()).
                                channel(OioServerSocketChannel.class);
            }
        });

        return list;
    }

    static List<Factory<ClientBootstrap>> clientSocket() {
        List<Factory<ClientBootstrap>> list = new ArrayList<Factory<ClientBootstrap>>();
        list.add(new Factory<ClientBootstrap>() {
            @Override
            public ClientBootstrap newInstance() {
                return new ClientBootstrap().group(new NioEventLoopGroup()).channel(NioSocketChannel.class);
            }
        });
        list.add(new Factory<ClientBootstrap>() {
            @Override
            public ClientBootstrap newInstance() {
                final AioEventLoopGroup loop = new AioEventLoopGroup();
                return new ClientBootstrap().group(loop).channelFactory(new ChannelFactory() {
                    @Override
                    public Channel newChannel() {
                        return new AioSocketChannel(loop);
                    }
                });
            }
        });
        list.add(new Factory<ClientBootstrap>() {
            @Override
            public ClientBootstrap newInstance() {
                return new ClientBootstrap().group(new OioEventLoopGroup()).channel(OioSocketChannel.class);
            }
        });
        return list;
    }

    private SocketTestPermutation() {}

    interface Factory<T> {
        T newInstance();
    }
}
