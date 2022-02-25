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
package io.netty5.testsuite.transport.socket;

import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.ChannelOption;
import io.netty5.testsuite.transport.AbstractTestsuiteTest;
import io.netty5.testsuite.transport.TestsuitePermutation;
import io.netty5.util.NetUtil;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

public abstract class AbstractServerSocketTest extends AbstractTestsuiteTest<ServerBootstrap> {

    @Override
    protected List<TestsuitePermutation.BootstrapFactory<ServerBootstrap>> newFactories() {
        return SocketTestPermutation.INSTANCE.serverSocket();
    }

    @Override
    protected void configure(ServerBootstrap bootstrap, ByteBufAllocator byteBufAllocator,
                             BufferAllocator bufferAllocator) {
        bootstrap.localAddress(newSocketAddress());
        bootstrap.option(ChannelOption.ALLOCATOR, byteBufAllocator);
        bootstrap.childOption(ChannelOption.ALLOCATOR, byteBufAllocator);
        bootstrap.option(ChannelOption.BUFFER_ALLOCATOR, bufferAllocator);
        bootstrap.childOption(ChannelOption.BUFFER_ALLOCATOR, bufferAllocator);
    }

    protected SocketAddress newSocketAddress() {
        return new InetSocketAddress(NetUtil.LOCALHOST, 0);
    }
}
