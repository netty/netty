/*
 * Copyright 2016 The Netty Project
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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultithreadEventLoopGroup;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.InetSocketAddress;

import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;

public class KQueueServerSocketChannelConfigTest {

    private static EventLoopGroup group;
    private static KQueueServerSocketChannel ch;

    @BeforeClass
    public static void before() {
        group = new MultithreadEventLoopGroup(1, KQueueHandler.newFactory());
        ServerBootstrap bootstrap = new ServerBootstrap();
        ch = (KQueueServerSocketChannel) bootstrap.group(group)
                .channel(KQueueServerSocketChannel.class)
                .childHandler(new ChannelInboundHandlerAdapter())
                .bind(new InetSocketAddress(0)).syncUninterruptibly().channel();
    }

    @AfterClass
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
        assumeThat(currentFilter, not(AcceptFilter.PLATFORM_UNSUPPORTED));

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
