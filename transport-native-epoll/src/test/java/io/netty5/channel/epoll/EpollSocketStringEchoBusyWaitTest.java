/*
 * Copyright 2018 The Netty Project
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
package io.netty5.channel.epoll;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.SelectStrategy;
import io.netty5.channel.SelectStrategyFactory;
import io.netty5.testsuite.transport.TestsuitePermutation;
import io.netty5.testsuite.transport.TestsuitePermutation.BootstrapComboFactory;
import io.netty5.testsuite.transport.TestsuitePermutation.BootstrapFactory;
import io.netty5.testsuite.transport.socket.SocketStringEchoTest;
import io.netty5.util.IntSupplier;
import io.netty5.util.concurrent.DefaultThreadFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.util.ArrayList;
import java.util.List;

public class EpollSocketStringEchoBusyWaitTest extends SocketStringEchoTest {

    private static EventLoopGroup EPOLL_LOOP;

    @BeforeAll
    public static void setup() throws Exception {
        EPOLL_LOOP = new MultithreadEventLoopGroup(2, new DefaultThreadFactory("testsuite-epoll-busy-wait", true),
                EpollHandler.newFactory(0, () -> (selectSupplier, hasTasks) -> SelectStrategy.BUSY_WAIT));
    }

    @AfterAll
    public static void teardown() throws Exception {
        if (EPOLL_LOOP != null) {
            EPOLL_LOOP.shutdownGracefully();
        }
    }

    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> newFactories() {
        List<BootstrapComboFactory<ServerBootstrap, Bootstrap>> list =
                new ArrayList<>();
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
        return () -> new ServerBootstrap().group(EPOLL_LOOP, EPOLL_LOOP).channel(EpollServerSocketChannel.class);
    }

    private static BootstrapFactory<Bootstrap> clientSocket() {
        return () -> new Bootstrap().group(EPOLL_LOOP).channel(EpollSocketChannel.class);
    }
}
