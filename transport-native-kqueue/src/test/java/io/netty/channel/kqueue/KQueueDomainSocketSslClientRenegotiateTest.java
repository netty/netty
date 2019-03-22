/*
 * Copyright 2019 The Netty Project
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
package io.netty.channel.kqueue;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.handler.ssl.SslContext;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.socket.SocketSslClientRenegotiateTest;

import java.net.SocketAddress;
import java.util.List;

public class KQueueDomainSocketSslClientRenegotiateTest extends SocketSslClientRenegotiateTest {

    public KQueueDomainSocketSslClientRenegotiateTest(SslContext serverCtx, SslContext clientCtx, boolean delegate) {
        super(serverCtx, clientCtx, delegate);
    }

    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> newFactories() {
        return KQueueSocketTestPermutation.INSTANCE.domainSocket();
    }

    @Override
    protected SocketAddress newSocketAddress() {
        return KQueueSocketTestPermutation.newSocketAddress();
    }
}
