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
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.aio.AioEventLoop;
import io.netty.channel.socket.aio.AioServerSocketChannel;
import io.netty.channel.socket.aio.AioSocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioEventLoop;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.socket.oio.OioDatagramChannel;
import io.netty.channel.socket.oio.OioEventLoop;
import io.netty.channel.socket.oio.OioServerSocketChannel;
import io.netty.channel.socket.oio.OioSocketChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

final class SocketTestPermutation {

    static List<Entry<Factory<ServerBootstrap>, Factory<Bootstrap>>> socket() {
        List<Entry<Factory<ServerBootstrap>, Factory<Bootstrap>>> list =
                new ArrayList<Entry<Factory<ServerBootstrap>, Factory<Bootstrap>>>();

        // Make the list of ServerBootstrap factories.
        List<Factory<ServerBootstrap>> sbfs =
                new ArrayList<Factory<ServerBootstrap>>();
        sbfs.add(new Factory<ServerBootstrap>() {
            @Override
            public ServerBootstrap newInstance() {
                return new ServerBootstrap().
                                eventLoop(new NioEventLoop(), new NioEventLoop()).
                                channel(new NioServerSocketChannel());
            }
        });
        sbfs.add(new Factory<ServerBootstrap>() {
            @Override
            public ServerBootstrap newInstance() {
                EventLoop loop = new AioEventLoop();
                return new ServerBootstrap().
                                eventLoop(loop, loop).
                                channel(new AioServerSocketChannel());
            }
        });
        sbfs.add(new Factory<ServerBootstrap>() {
            @Override
            public ServerBootstrap newInstance() {
                return new ServerBootstrap().
                                eventLoop(new OioEventLoop(), new OioEventLoop()).
                                channel(new OioServerSocketChannel());
            }
        });

        // Make the list of Bootstrap factories.
        List<Factory<Bootstrap>> cbfs =
                new ArrayList<Factory<Bootstrap>>();
        cbfs.add(new Factory<Bootstrap>() {
            @Override
            public Bootstrap newInstance() {
                return new Bootstrap().eventLoop(new NioEventLoop()).channel(new NioSocketChannel());
            }
        });
        cbfs.add(new Factory<Bootstrap>() {
            @Override
            public Bootstrap newInstance() {
                return new Bootstrap().eventLoop(new AioEventLoop()).channel(new AioSocketChannel());
            }
        });
        cbfs.add(new Factory<Bootstrap>() {
            @Override
            public Bootstrap newInstance() {
                return new Bootstrap().eventLoop(new OioEventLoop()).channel(new OioSocketChannel());
            }
        });

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
                return new Bootstrap().eventLoop(new NioEventLoop()).channel(
                        new NioDatagramChannel(InternetProtocolFamily.IPv4));
            }
        });
        bfs.add(new Factory<Bootstrap>() {
            @Override
            public Bootstrap newInstance() {
                return new Bootstrap().eventLoop(new OioEventLoop()).channel(new OioDatagramChannel());
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
    private SocketTestPermutation() {}

    interface Factory<T> {
        T newInstance();
    }
}
