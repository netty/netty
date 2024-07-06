/*
 * Copyright 2020 The Netty Project
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
package io.netty5.channel.uring;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.nio.NioIoHandler;
import io.netty5.testsuite.transport.TestsuitePermutation;
import io.netty5.testsuite.transport.socket.SocketMultipleConnectTest;
import org.junit.jupiter.api.BeforeAll;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class IoUringSocketMultipleConnectTest extends SocketMultipleConnectTest {

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> newFactories() {
        List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> factories = new ArrayList<>();
        for (var comboFactory : IoUringSocketTestPermutation.INSTANCE.socket()) {
            EventLoopGroup group = comboFactory.newClientInstance().config().group();
            if (group.isIoType(NioIoHandler.class) || group.isIoType(IoUringIoHandler.class)) {
                factories.add(comboFactory);
            }
        }
        return factories;
    }
}
