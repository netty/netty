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
package io.netty.testsuite.transport.sctp;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.oio.OioEventLoopGroup;
import io.netty.channel.sctp.nio.NioSctpChannel;
import io.netty.channel.sctp.nio.NioSctpServerChannel;
import io.netty.channel.sctp.oio.OioSctpChannel;
import io.netty.channel.sctp.oio.OioSctpServerChannel;
import io.netty.testsuite.util.TestUtils;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class SctpTestPermutation {

    private static final int BOSSES = 2;
    private static final int WORKERS = 3;
    private static final EventLoopGroup nioBossGroup =
            new NioEventLoopGroup(BOSSES, new DefaultThreadFactory("testsuite-sctp-nio-boss", true));
    private static final EventLoopGroup nioWorkerGroup =
            new NioEventLoopGroup(WORKERS, new DefaultThreadFactory("testsuite-sctp-nio-worker", true));
    private static final EventLoopGroup oioBossGroup =
            new OioEventLoopGroup(Integer.MAX_VALUE, new DefaultThreadFactory("testsuite-sctp-oio-boss", true));
    private static final EventLoopGroup oioWorkerGroup =
            new OioEventLoopGroup(Integer.MAX_VALUE, new DefaultThreadFactory("testsuite-sctp-oio-worker", true));

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
                        group(nioBossGroup, nioWorkerGroup).
                        channel(NioSctpServerChannel.class);
            }
        });
        list.add(new Factory<ServerBootstrap>() {
            @Override
            public ServerBootstrap newInstance() {
                return new ServerBootstrap().
                        group(oioBossGroup, oioWorkerGroup).
                        channel(OioSctpServerChannel.class);
            }
        });

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
                return new Bootstrap().group(nioWorkerGroup).channel(NioSctpChannel.class);
            }
        });
        list.add(new Factory<Bootstrap>() {
            @Override
            public Bootstrap newInstance() {
                return new Bootstrap().group(oioWorkerGroup).channel(OioSctpChannel.class);
            }
        });
        return list;
    }

    static List<Map.Entry<Factory<ServerBootstrap>, Factory<Bootstrap>>> sctpChannel() {
        List<Map.Entry<Factory<ServerBootstrap>, Factory<Bootstrap>>> list =
                new ArrayList<Map.Entry<Factory<ServerBootstrap>, Factory<Bootstrap>>>();

        // Make the list of SCTP ServerBootstrap factories.
        List<Factory<ServerBootstrap>> sbfs = sctpServerChannel();

        // Make the list of SCTP Bootstrap factories.
        List<Factory<Bootstrap>> cbfs = sctpClientChannel();

        // Populate the combinations
        for (Factory<ServerBootstrap> sbf: sbfs) {
            for (Factory<Bootstrap> cbf: cbfs) {
                final Factory<ServerBootstrap> sbf0 = sbf;
                final Factory<Bootstrap> cbf0 = cbf;
                list.add(new Map.Entry<Factory<ServerBootstrap>, Factory<Bootstrap>>() {
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

    static List<ByteBufAllocator> allocator() {
        List<ByteBufAllocator> allocators = new ArrayList<ByteBufAllocator>();
        allocators.add(UnpooledByteBufAllocator.DEFAULT);
        allocators.add(PooledByteBufAllocator.DEFAULT);
        return allocators;
    }

    private SctpTestPermutation() { }

    interface Factory<T> {
        T newInstance();
    }
}
