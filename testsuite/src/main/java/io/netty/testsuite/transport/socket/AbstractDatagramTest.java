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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.testsuite.transport.AbstractComboTestsuiteTest;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.util.NetUtil;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;

public abstract class AbstractDatagramTest extends AbstractComboTestsuiteTest<Bootstrap, Bootstrap> {

    protected AbstractDatagramTest() {
        super(Bootstrap.class, Bootstrap.class);
    }

    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<Bootstrap, Bootstrap>> newFactories() {
        return SocketTestPermutation.INSTANCE.datagram();
    }

    @Override
    protected void configure(Bootstrap bootstrap, Bootstrap bootstrap2, ByteBufAllocator allocator) {
        bootstrap.option(ChannelOption.ALLOCATOR, allocator);
        bootstrap2.option(ChannelOption.ALLOCATOR, allocator);
    }

    protected SocketAddress newSocketAddress() {
        // We use LOCALHOST4 as we use InternetProtocolFamily.IPv4 when creating the DatagramChannel and its
        // not supported to bind to and IPV6 address in this case.
        //
        // See also http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/e74259b3eadc/
        // src/share/classes/sun/nio/ch/DatagramChannelImpl.java#l684
        return new InetSocketAddress(NetUtil.LOCALHOST4, 0);
    }
}
