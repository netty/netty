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
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.nio.NioHandler;
import io.netty.channel.sctp.nio.NioSctpChannel;
import io.netty.channel.sctp.nio.NioSctpServerChannel;
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
            new MultithreadEventLoopGroup(BOSSES, new DefaultThreadFactory("testsuite-sctp-nio-boss", true),
                    NioHandler.newFactory());
    private static final EventLoopGroup nioWorkerGroup =
            new MultithreadEventLoopGroup(WORKERS, new DefaultThreadFactory("testsuite-sctp-nio-worker", true),
                    NioHandler.newFactory());

    static List<BootstrapFactory<ServerBootstrap>> sctpServerChannel() {
        if (!TestUtils.isSctpSupported()) {
            return Collections.emptyList();
        }

        // Make the list of ServerBootstrap factories.
        return Collections.<BootstrapFactory<ServerBootstrap>>singletonList(new BootstrapFactory<ServerBootstrap>() {
            @Override
            public ServerBootstrap newInstance() {
                return new ServerBootstrap().
                        group(nioBossGroup, nioWorkerGroup).
                        channel(NioSctpServerChannel.class);
            }
        });
    }

    static List<BootstrapFactory<Bootstrap>> sctpClientChannel() {
        if (!TestUtils.isSctpSupported()) {
            return Collections.emptyList();
        }

        return Collections.<BootstrapFactory<Bootstrap>>singletonList(new BootstrapFactory<Bootstrap>() {
            @Override
            public Bootstrap newInstance() {
                return new Bootstrap().group(nioWorkerGroup).channel(NioSctpChannel.class);
            }
        });
    }

    static List<BootstrapComboFactory<ServerBootstrap, Bootstrap>> sctpChannel() {
        List<BootstrapComboFactory<ServerBootstrap, Bootstrap>> list =
                new ArrayList<>();

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
