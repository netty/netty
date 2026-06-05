/*
 * Copyright 2015 The Netty Project
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
package io.netty.channel.kqueue;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.ThreadLocalRandom;

import static io.netty.channel.kqueue.BsdSocket.BSD_SND_LOW_AT_MAX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class KQueueSocketChannelConfigTest {

    private static EventLoopGroup group;
    private static KQueueSocketChannel ch;

    @BeforeAll
    public static void beforeClass() {
        group = new MultiThreadIoEventLoopGroup(1, KQueueIoHandler.newFactory());
    }

    @AfterAll
    public static void afterClass() {
        group.shutdownGracefully();
    }

    @BeforeEach
    public void setup() throws Exception {
        Bootstrap bootstrap = new Bootstrap();
        ch = (KQueueSocketChannel) bootstrap.group(group)
                .channel(KQueueSocketChannel.class)
                .handler(new ChannelInboundHandler() { })
                .bind(new InetSocketAddress(0)).get();
    }

    @AfterEach
    public void teardown() {
        ch.close().syncUninterruptibly();
    }

    @Test
    public void testRandomSndLowAt() {
        final int expected = Math.min(BSD_SND_LOW_AT_MAX, Math.abs(ThreadLocalRandom.current().nextInt()));
        final int actual;
        try {
            ch.config().setOption(KQueueChannelOption.SO_SNDLOWAT, expected);
            actual = ch.config().getOption(KQueueChannelOption.SO_SNDLOWAT);
        } catch (RuntimeException e) {
            throw new TestAbortedException("assumeNoException", e);
        }
        assertEquals(expected, actual);
    }

    @Test
    public void testInvalidHighSndLowAt() {
        try {
            ch.config().setOption(KQueueChannelOption.SO_SNDLOWAT, Integer.MIN_VALUE);
        } catch (ChannelException e) {
            return;
        } catch (RuntimeException e) {
            throw new TestAbortedException("assumeNoException", e);
        }
        fail();
    }

    @Test
    public void testTcpNoPush() {
        ch.config().setOption(KQueueChannelOption.TCP_NOPUSH, false);
        assertFalse(ch.config().getOption(KQueueChannelOption.TCP_NOPUSH));
        ch.config().setOption(KQueueChannelOption.TCP_NOPUSH, true);
        assertTrue(ch.config().getOption(KQueueChannelOption.TCP_NOPUSH));
    }

    @Test
    public void testSetOptionWhenClosed() {
        ch.close().syncUninterruptibly();
        try {
            ch.config().setOption(ChannelOption.SO_LINGER, 0);
            fail();
        } catch (ChannelException e) {
            assertTrue(e.getCause() instanceof ClosedChannelException);
        }
    }

    @Test
    public void testGetOptionWhenClosed() {
        ch.close().syncUninterruptibly();
        try {
            ch.config().getOption(ChannelOption.SO_LINGER);
            fail();
        } catch (ChannelException e) {
            assertTrue(e.getCause() instanceof ClosedChannelException);
        }
    }
}
