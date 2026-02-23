/*
 * Copyright 2026 The Netty Project
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
package io.netty.channel.uring;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoop;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.socket.AbstractSocketTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class IoUringRegisterTest extends AbstractSocketTest {

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> newFactories() {
        return IoUringSocketTestPermutation.INSTANCE.socket();
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testDeregisterRegisterSameEventLoop(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testDeregisterRegister(serverBootstrap, bootstrap, true);
            }
        });
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testDeregisterRegisterDifferentEventLoop(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testDeregisterRegister(serverBootstrap, bootstrap, false);
            }
        });
    }

    private void testDeregisterRegister(ServerBootstrap sb, Bootstrap cb, boolean sameLoop) throws Throwable {
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            serverChannel = sb.childHandler(new ChannelInboundHandlerAdapter()).bind().syncUninterruptibly().channel();
            clientChannel = cb.handler(new ChannelInboundHandlerAdapter() { })
                    .connect(serverChannel.localAddress()).syncUninterruptibly().channel();
            EventLoop loop = clientChannel.eventLoop();
            if (!sameLoop) {
                for (;;) {
                    EventLoop next = loop.parent().next();
                    if (next != loop) {
                        loop = next;
                        break;
                    }
                }
            }
            clientChannel.deregister().sync();
            loop.register(clientChannel).sync();
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
