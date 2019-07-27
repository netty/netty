/*
 * Copyright 2018 The Netty Project
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
package io.netty.channel.epoll;

import java.util.ArrayList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SelectStrategy;
import io.netty.channel.SelectStrategyFactory;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.TestsuitePermutation.BootstrapComboFactory;
import io.netty.testsuite.transport.TestsuitePermutation.BootstrapFactory;
import io.netty.testsuite.transport.socket.SocketStringEchoTest;
import io.netty.util.IntSupplier;
import io.netty.util.concurrent.DefaultThreadFactory;

public class EpollSocketStringEchoBusyWaitTest extends SocketStringEchoTest {

    private static EventLoopGroup EPOLL_LOOP;

    @BeforeClass
    public static void setup() throws Exception {
        EPOLL_LOOP = new EpollEventLoopGroup(2, new DefaultThreadFactory("testsuite-epoll-busy-wait", true),
                new SelectStrategyFactory() {
                    @Override
                    public SelectStrategy newSelectStrategy() {
                        return new SelectStrategy() {
                            @Override
                            public int calculateStrategy(IntSupplier selectSupplier, boolean hasTasks) {
                                return SelectStrategy.BUSY_WAIT;
                            }
                        };
                    }
                });
    }

    @AfterClass
    public static void teardown() throws Exception {
        if (EPOLL_LOOP != null) {
            EPOLL_LOOP.shutdownGracefully();
        }
    }

    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> newFactories() {
        List<BootstrapComboFactory<ServerBootstrap, Bootstrap>> list =
                new ArrayList<BootstrapComboFactory<ServerBootstrap, Bootstrap>>();
        final BootstrapFactory<ServerBootstrap> sbf = serverSocket();
        final BootstrapFactory<Bootstrap> cbf = clientSocket();
        list.add(new BootstrapComboFactory<ServerBootstrap, Bootstrap>() {
            @Override
            public ServerBootstrap newServerInstance() {
                return sbf.newInstance();
            }

            @Override
            public Bootstrap newClientInstance() {
                return cbf.newInstance();
            }
        });

        return list;
    }

    private static BootstrapFactory<ServerBootstrap> serverSocket() {
        return new BootstrapFactory<ServerBootstrap>() {
            @Override
            public ServerBootstrap newInstance() {
                return new ServerBootstrap().group(EPOLL_LOOP, EPOLL_LOOP).channel(EpollServerSocketChannel.class);
            }
        };
    }

    private static BootstrapFactory<Bootstrap> clientSocket() {
        return new BootstrapFactory<Bootstrap>() {
            @Override
            public Bootstrap newInstance() {
                return new Bootstrap().group(EPOLL_LOOP).channel(EpollSocketChannel.class);
            }
        };
    }
}
