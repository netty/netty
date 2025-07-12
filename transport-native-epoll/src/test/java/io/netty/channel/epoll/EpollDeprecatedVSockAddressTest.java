/*
 * Copyright 2025 The Netty Project
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
import io.netty.testsuite.transport.TestsuitePermutation;

import java.net.SocketAddress;
import java.util.List;

/**
 * Test to confirm the original VSockAddress in epoll transport classes still works.
 * This test can be removed when io.netty.channel.epoll.VSockAddress is removed.
 */
public class EpollDeprecatedVSockAddressTest extends EpollSocketEchoTest {
    @Override
    protected SocketAddress newSocketAddress() {
        return new io.netty.channel.epoll.VSockAddress(io.netty.channel.unix.VSockAddress.VMADDR_CID_LOCAL, 8080);
    }

    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> newFactories() {
        return EpollSocketTestPermutation.INSTANCE.vSock();
    }
}
