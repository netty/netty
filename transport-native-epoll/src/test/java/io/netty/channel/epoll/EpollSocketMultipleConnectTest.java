/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.socket.SocketMultipleConnectTest;

import java.util.ArrayList;
import java.util.List;

public class EpollSocketMultipleConnectTest extends SocketMultipleConnectTest {

    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> newFactories() {
        List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> factories
                = new ArrayList<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>>();
        for (TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap> comboFactory
                : EpollSocketTestPermutation.INSTANCE.socket()) {
            EventLoopGroup group = comboFactory.newClientInstance().config().group();
            if (group instanceof NioEventLoopGroup || group instanceof EpollEventLoopGroup) {
                factories.add(comboFactory);
            }
        }
        return factories;
    }
}
