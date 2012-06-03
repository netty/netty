package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.socket.nio.NioEventLoop;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.socket.oio.OioEventLoop;
import io.netty.channel.socket.oio.OioServerSocketChannel;
import io.netty.channel.socket.oio.OioSocketChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class SocketTestCombination {

    static List<Map.Entry<Factory<ServerBootstrap>, Factory<Bootstrap>>> all() {
        List<Map.Entry<Factory<ServerBootstrap>, Factory<Bootstrap>>> list =
                new ArrayList<Map.Entry<Factory<ServerBootstrap>, Factory<Bootstrap>>>();

        // Make the list of ServerBootstrap factories.
        List<Factory<ServerBootstrap>> sbfs =
                new ArrayList<SocketTestCombination.Factory<ServerBootstrap>>();
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
                return new ServerBootstrap().
                                eventLoop(new OioEventLoop(), new OioEventLoop()).
                                channel(new OioServerSocketChannel());
            }
        });

        // Make the list of Bootstrap factories.
        List<Factory<Bootstrap>> cbfs =
                new ArrayList<SocketTestCombination.Factory<Bootstrap>>();
        cbfs.add(new Factory<Bootstrap>() {
            @Override
            public Bootstrap newInstance() {
                return new Bootstrap().eventLoop(new NioEventLoop()).channel(new NioSocketChannel());
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
                list.add(new Map.Entry<SocketTestCombination.Factory<ServerBootstrap>, SocketTestCombination.Factory<Bootstrap>>() {
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

    private SocketTestCombination() {}

    static interface Factory<T> {
        T newInstance();
    }
}
