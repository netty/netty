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

import io.netty.bootstrap.AbstractBootstrap.ChannelFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.aio.AioEventLoopGroup;
import io.netty.channel.socket.aio.AioServerSocketChannel;
import io.netty.channel.socket.aio.AioSocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSctpServerChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.socket.nio.NioSctpChannel;
import io.netty.channel.socket.oio.OioDatagramChannel;
import io.netty.channel.socket.oio.OioEventLoopGroup;
import io.netty.channel.socket.oio.OioServerSocketChannel;
import io.netty.channel.socket.oio.OioSocketChannel;
import io.netty.testsuite.util.TestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;

final class SocketTestPermutation {

    static List<Entry<Factory<ServerBootstrap>, Factory<Bootstrap>>> socket() {
        List<Entry<Factory<ServerBootstrap>, Factory<Bootstrap>>> list =
                new ArrayList<Entry<Factory<ServerBootstrap>, Factory<Bootstrap>>>();

        // Make the list of ServerBootstrap factories.
        List<Factory<ServerBootstrap>> sbfs = serverSocket();

        // Make the list of Bootstrap factories.
        List<Factory<Bootstrap>> cbfs = clientSocket();

        // Populate the combinations
        for (Factory<ServerBootstrap> sbf: sbfs) {
            for (Factory<Bootstrap> cbf: cbfs) {
                final Factory<ServerBootstrap> sbf0 = sbf;
                final Factory<Bootstrap> cbf0 = cbf;
                list.add(new Entry<Factory<ServerBootstrap>, Factory<Bootstrap>>() {
                    @Override
                    public Factory<ServerBootstrap> getKey() {
                        return sbf0;
                    }

                    @Override
                    public Factory<Bootstrap> getValue() {
                        return cbf0;
                    }

                    @Override
                    public Factory<Bootstrap> setValue(Factory<Bootstrap> value) {
                        throw new UnsupportedOperationException();
                    }
                });
            }
        }

        // Remove the OIO-OIO case which often leads to a dead lock by its nature.
        list.remove(list.size() - 1);

        return list;
    }

    static List<Entry<Factory<Bootstrap>, Factory<Bootstrap>>> datagram() {
        List<Entry<Factory<Bootstrap>, Factory<Bootstrap>>> list =
                new ArrayList<Entry<Factory<Bootstrap>, Factory<Bootstrap>>>();

        // Make the list of Bootstrap factories.
        List<Factory<Bootstrap>> bfs =
                new ArrayList<Factory<Bootstrap>>();
        bfs.add(new Factory<Bootstrap>() {
            @Override
            public Bootstrap newInstance() {
                return new Bootstrap().group(new NioEventLoopGroup()).channelFactory(new ChannelFactory() {
                    @Override
                    public Channel newChannel() {
                       return new NioDatagramChannel(InternetProtocolFamily.IPv4);
                    }
                });
            }
        });
        bfs.add(new Factory<Bootstrap>() {
            @Override
            public Bootstrap newInstance() {
                return new Bootstrap().group(new OioEventLoopGroup()).channel(OioDatagramChannel.class);
            }
        });

        // Populate the combinations
        for (Factory<Bootstrap> sbf: bfs) {
            for (Factory<Bootstrap> cbf: bfs) {
                final Factory<Bootstrap> sbf0 = sbf;
                final Factory<Bootstrap> cbf0 = cbf;
                list.add(new Entry<Factory<Bootstrap>, Factory<Bootstrap>>() {
                    @Override
                    public Factory<Bootstrap> getKey() {
                        return sbf0;
                    }

                    @Override
                    public Factory<Bootstrap> getValue() {
                        return cbf0;
                    }

                    @Override
                    public Factory<Bootstrap> setValue(Factory<Bootstrap> value) {
                        throw new UnsupportedOperationException();
                    }
                });
            }
        }

        return list;
    }

    static List<Entry<Factory<ServerBootstrap>, Factory<Bootstrap>>> sctpChannel() {
        List<Entry<Factory<ServerBootstrap>, Factory<Bootstrap>>> list =
                new ArrayList<Entry<Factory<ServerBootstrap>, Factory<Bootstrap>>>();

        // Make the list of SCTP ServerBootstrap factories.
        List<Factory<ServerBootstrap>> sbfs = sctpServerChannel();

        // Make the list of SCTP Bootstrap factories.
        List<Factory<Bootstrap>> cbfs = sctpClientChannel();

        // Populate the combinations
        for (Factory<ServerBootstrap> sbf: sbfs) {
            for (Factory<Bootstrap> cbf: cbfs) {
                final Factory<ServerBootstrap> sbf0 = sbf;
                final Factory<Bootstrap> cbf0 = cbf;
                list.add(new Entry<Factory<ServerBootstrap>, Factory<Bootstrap>>() {
                    @Override
                    public Factory<ServerBootstrap> getKey() {
                        return sbf0;
                    }

                    @Override
                    public Factory<Bootstrap> getValue() {
                        return cbf0;
                    }

                    @Override
                    public Factory<Bootstrap> setValue(Factory<Bootstrap> value) {
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

    static List<Factory<Bootstrap>> clientSocket() {
        List<Factory<Bootstrap>> list = new ArrayList<Factory<Bootstrap>>();
        list.add(new Factory<Bootstrap>() {
            @Override
            public Bootstrap newInstance() {
                return new Bootstrap().group(new NioEventLoopGroup()).channel(NioSocketChannel.class);
            }
        });
        list.add(new Factory<Bootstrap>() {
            @Override
            public Bootstrap newInstance() {
                final AioEventLoopGroup loop = new AioEventLoopGroup();
                return new Bootstrap().group(loop).channelFactory(new ChannelFactory() {
                    @Override
                    public Channel newChannel() {
                        return new AioSocketChannel(loop);
                    }
                });
            }
        });
        list.add(new Factory<Bootstrap>() {
            @Override
            public Bootstrap newInstance() {
                return new Bootstrap().group(new OioEventLoopGroup()).channel(OioSocketChannel.class);
            }
        });
        return list;
    }

    static List<Factory<ServerBootstrap>> sctpServerChannel() {
        if (!TestUtils.isSctpSupported()) {
            return Collections.emptyList();
        }

        List<Factory<ServerBootstrap>> list = new ArrayList<Factory<ServerBootstrap>>();
        // Make the list of ServerBootstrap factories.
        list.add(new Factory<ServerBootstrap>() {
            @Override
            public ServerBootstrap newInstance() {
                return new ServerBootstrap().
                        group(new NioEventLoopGroup(), new NioEventLoopGroup()).
                        channel(NioSctpServerChannel.class);
            }
        });
        /*
         * Comment out till #632 is fixes
        list.add(new Factory<ServerBootstrap>() {
            @Override
            public ServerBootstrap newInstance() {
                return new ServerBootstrap().
                        group(new OioEventLoopGroup(), new OioEventLoopGroup()).
                        channel(OioSctpServerChannel.class);
            }
        });
        */

        return list;
    }

    static List<Factory<Bootstrap>> sctpClientChannel() {
        if (!TestUtils.isSctpSupported()) {
            return Collections.emptyList();
        }

        List<Factory<Bootstrap>> list = new ArrayList<Factory<Bootstrap>>();
        list.add(new Factory<Bootstrap>() {
            @Override
            public Bootstrap newInstance() {
                return new Bootstrap().group(new NioEventLoopGroup()).channel(NioSctpChannel.class);
            }
        });
        /*
         * Comment out till #632 is fixes
         * list.add(new Factory<Bootstrap>() {
            @Override
            public Bootstrap newInstance() {
                return new Bootstrap().group(new OioEventLoopGroup()).channel(OioSctpChannel.class);
            }
        });
        */
        return list;
    }

    private SocketTestPermutation() {}

    interface Factory<T> {
        T newInstance();
    }
}
