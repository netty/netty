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

import io.netty5.bootstrap.Bootstrap;
import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.socket.InternetProtocolFamily;
import io.netty5.testsuite.transport.AbstractComboTestsuiteTest;
import io.netty5.testsuite.transport.TestsuitePermutation;
import io.netty5.util.NetUtil;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

public abstract class AbstractDatagramTest extends AbstractComboTestsuiteTest<Bootstrap, Bootstrap> {
    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<Bootstrap, Bootstrap>> newFactories() {
        return SocketTestPermutation.INSTANCE.datagram(socketInternetProtocalFamily());
    }

    @Override
    protected void configure(Bootstrap b1, Bootstrap b2, ByteBufAllocator byteBufAllocator,
                             BufferAllocator bufferAllocator) {
        b1.option(ChannelOption.ALLOCATOR, byteBufAllocator);
        b2.option(ChannelOption.ALLOCATOR, byteBufAllocator);
        b1.option(ChannelOption.BUFFER_ALLOCATOR, bufferAllocator);
        b2.option(ChannelOption.BUFFER_ALLOCATOR, bufferAllocator);
    }

    protected SocketAddress newSocketAddress() {
        switch (socketInternetProtocalFamily()) {
            case IPv4:
                return new InetSocketAddress(NetUtil.LOCALHOST4, 0);
            case IPv6:
                return new InetSocketAddress(NetUtil.LOCALHOST6, 0);
            default:
                throw new AssertionError();
        }
    }

    protected InternetProtocolFamily internetProtocolFamily() {
        return InternetProtocolFamily.IPv4;
    }

    protected InternetProtocolFamily groupInternetProtocalFamily() {
        return internetProtocolFamily();
    }

    protected InternetProtocolFamily socketInternetProtocalFamily() {
        return internetProtocolFamily();
    }
}
