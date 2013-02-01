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
import io.netty.channel.sctp.nio.NioSctpChannel;
import io.netty.channel.sctp.nio.NioSctpServerChannel;
import io.netty.channel.sctp.oio.OioSctpChannel;
import io.netty.channel.sctp.oio.OioSctpServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.oio.OioEventLoopGroup;
import io.netty.testsuite.util.TestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SctpTestPermutation {


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
        list.add(new Factory<ServerBootstrap>() {
            @Override
            public ServerBootstrap newInstance() {
                return new ServerBootstrap().
                        group(new OioEventLoopGroup(), new OioEventLoopGroup()).
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
                return new Bootstrap().group(new NioEventLoopGroup()).channel(NioSctpChannel.class);
            }
        });
        list.add(new Factory<Bootstrap>() {
            @Override
            public Bootstrap newInstance() {
                return new Bootstrap().group(new OioEventLoopGroup()).channel(OioSctpChannel.class);
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


    private SctpTestPermutation() {}

    interface Factory<T> {
        T newInstance();
    }
}
