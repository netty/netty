/*
 * Copyright 2012 The Netty Project
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
package io.netty.testsuite.transport.sctp;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.oio.OioEventLoopGroup;
import io.netty.channel.sctp.nio.NioSctpChannel;
import io.netty.channel.sctp.nio.NioSctpServerChannel;
import io.netty.channel.sctp.oio.OioSctpChannel;
import io.netty.channel.sctp.oio.OioSctpServerChannel;
import io.netty.testsuite.util.TestUtils;
import io.netty.testsuite.transport.TestsuitePermutation.BootstrapComboFactory;
import io.netty.testsuite.transport.TestsuitePermutation.BootstrapFactory;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    static List<BootstrapFactory<ServerBootstrap>> sctpServerChannel() {
        if (!TestUtils.isSctpSupported()) {
            return Collections.emptyList();
        }

        List<BootstrapFactory<ServerBootstrap>> list = new ArrayList<BootstrapFactory<ServerBootstrap>>();
        // Make the list of ServerBootstrap factories.
        list.add(new BootstrapFactory<ServerBootstrap>() {
            @Override
            public ServerBootstrap newInstance() {
                return new ServerBootstrap().
                        group(nioBossGroup, nioWorkerGroup).
                        channel(NioSctpServerChannel.class);
            }
        });
        list.add(new BootstrapFactory<ServerBootstrap>() {
            @Override
            public ServerBootstrap newInstance() {
                return new ServerBootstrap().
                        group(oioBossGroup, oioWorkerGroup).
                        channel(OioSctpServerChannel.class);
            }
        });

        return list;
    }

    static List<BootstrapFactory<Bootstrap>> sctpClientChannel() {
        if (!TestUtils.isSctpSupported()) {
            return Collections.emptyList();
        }

        List<BootstrapFactory<Bootstrap>> list = new ArrayList<BootstrapFactory<Bootstrap>>();
        list.add(new BootstrapFactory<Bootstrap>() {
            @Override
            public Bootstrap newInstance() {
                return new Bootstrap().group(nioWorkerGroup).channel(NioSctpChannel.class);
            }
        });
        list.add(new BootstrapFactory<Bootstrap>() {
            @Override
            public Bootstrap newInstance() {
                return new Bootstrap().group(oioWorkerGroup).channel(OioSctpChannel.class);
            }
        });
        return list;
    }

    static List<BootstrapComboFactory<ServerBootstrap, Bootstrap>> sctpChannel() {
        List<BootstrapComboFactory<ServerBootstrap, Bootstrap>> list =
                new ArrayList<BootstrapComboFactory<ServerBootstrap, Bootstrap>>();

        // Make the list of SCTP ServerBootstrap factories.
        List<BootstrapFactory<ServerBootstrap>> sbfs = sctpServerChannel();

        // Make the list of SCTP Bootstrap factories.
        List<BootstrapFactory<Bootstrap>> cbfs = sctpClientChannel();

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

        return list;
    }

    private SctpTestPermutation() { }
}
