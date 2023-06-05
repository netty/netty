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
package io.netty5.testsuite.transport.socket;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelHandler;
import io.netty5.util.concurrent.ImmediateEventExecutor;
import io.netty5.util.concurrent.Promise;
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
            serverChannel = sb.childHandler(new ChannelHandler() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) {
                    localAddressPromise.setSuccess(ctx.channel().localAddress());
                    remoteAddressPromise.setSuccess(ctx.channel().remoteAddress());
                }
            }).bind().asStage().get();

            clientChannel = cb.handler(new ChannelHandler() { }).register().asStage().get();

            assertNull(clientChannel.localAddress());
            assertNull(clientChannel.remoteAddress());

            if (withLocalAddress) {
                clientChannel.connect(serverChannel.localAddress(), newSocketAddress()).asStage().get();
            } else {
                clientChannel.connect(serverChannel.localAddress()).asStage().get();
            }

            assertAddress(clientChannel.localAddress());
            assertAddress(clientChannel.remoteAddress());

            assertAddress(localAddressPromise.asFuture().asStage().get());
            assertAddress(remoteAddressPromise.asFuture().asStage().get());
        } finally {
            if (clientChannel != null) {
                clientChannel.close().asStage().sync();
            }
            if (serverChannel != null) {
                serverChannel.close().asStage().sync();
            }
        }
    }
}
