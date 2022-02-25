/*
 * Copyright 2016 The Netty Project
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
package io.netty5.channel.kqueue;

import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class KQueueServerSocketChannelConfigTest {

    private static EventLoopGroup group;
    private static KQueueServerSocketChannel ch;

    @BeforeAll
    public static void before() throws Exception {
        group = new MultithreadEventLoopGroup(1, KQueueHandler.newFactory());
        ServerBootstrap bootstrap = new ServerBootstrap();
        ch = (KQueueServerSocketChannel) bootstrap.group(group)
                .channel(KQueueServerSocketChannel.class)
                .childHandler(new ChannelHandler() { })
                .bind(new InetSocketAddress(0)).get();
    }

    @AfterAll
    public static void after() {
        try {
            ch.close().syncUninterruptibly();
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testReusePort() {
        ch.config().setReusePort(false);
        assertFalse(ch.config().isReusePort());
        ch.config().setReusePort(true);
        assertTrue(ch.config().isReusePort());
    }

    @Test
    public void testAcceptFilter() {
        AcceptFilter currentFilter = ch.config().getAcceptFilter();
        // Not all platforms support this option (e.g. MacOS doesn't) so test if we support the option first.
        assumeTrue(currentFilter != AcceptFilter.PLATFORM_UNSUPPORTED);

        AcceptFilter af = new AcceptFilter("test", "foo");
        ch.config().setAcceptFilter(af);
        assertEquals(af, ch.config().getAcceptFilter());
    }

    @Test
    public void testOptionsDoesNotThrow() {
        // If there are some options that are not fully supported they shouldn't throw but instead return some "default"
        // object.
        assertFalse(ch.config().getOptions().isEmpty());
    }
}
