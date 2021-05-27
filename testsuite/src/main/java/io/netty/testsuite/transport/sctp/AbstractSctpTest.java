/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.testsuite.transport.AbstractComboTestsuiteTest;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.util.NetUtil;

import java.net.InetSocketAddress;
import java.util.List;

public abstract class AbstractSctpTest extends AbstractComboTestsuiteTest<ServerBootstrap, Bootstrap> {
    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> newFactories() {
        return SctpTestPermutation.sctpChannel();
    }

    @Override
    protected void configure(ServerBootstrap serverBootstrap, Bootstrap bootstrap, ByteBufAllocator allocator) {
        serverBootstrap.localAddress(new InetSocketAddress(NetUtil.LOCALHOST, 0));
        serverBootstrap.option(ChannelOption.ALLOCATOR, allocator);
        serverBootstrap.childOption(ChannelOption.ALLOCATOR, allocator);
        bootstrap.option(ChannelOption.ALLOCATOR, allocator);
    }
}
