/*
 * Copyright 2023 The Netty Project
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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNull;

public abstract class SocketAddressesTest extends AbstractSocketTest {

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testAddresses(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testAddresses(serverBootstrap, bootstrap, true);
            }
        });
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testAddressesConnectWithoutLocalAddress(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testAddresses(serverBootstrap, bootstrap, false);
            }
        });
    }

    protected abstract void assertAddress(SocketAddress address);

    private void testAddresses(ServerBootstrap sb, Bootstrap cb, boolean withLocalAddress) throws Throwable {
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            final Promise<SocketAddress> localAddressPromise = ImmediateEventExecutor.INSTANCE.newPromise();
            final Promise<SocketAddress> remoteAddressPromise = ImmediateEventExecutor.INSTANCE.newPromise();
            serverChannel = sb.childHandler(new ChannelInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) {
                    localAddressPromise.setSuccess(ctx.channel().localAddress());
                    remoteAddressPromise.setSuccess(ctx.channel().remoteAddress());
                }
            }).bind().syncUninterruptibly().channel();

            clientChannel = cb.handler(new ChannelInboundHandlerAdapter()).register().syncUninterruptibly().channel();

            assertNull(clientChannel.localAddress());
            assertNull(clientChannel.remoteAddress());

            if (withLocalAddress) {
                clientChannel.connect(serverChannel.localAddress(), newSocketAddress()).syncUninterruptibly().channel();
            } else {
                clientChannel.connect(serverChannel.localAddress()).syncUninterruptibly().channel();
            }

            assertAddress(clientChannel.localAddress());
            assertAddress(clientChannel.remoteAddress());

            assertAddress(localAddressPromise.get());
            assertAddress(remoteAddressPromise.get());
        } finally {
            if (clientChannel != null) {
                clientChannel.close().syncUninterruptibly();
            }
            if (serverChannel != null) {
                serverChannel.close().syncUninterruptibly();
            }
        }
    }
}
