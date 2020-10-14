/*
 * Copyright 2015 The Netty Project
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
package io.netty.channel.epoll;

import static org.junit.Assert.*;
import static org.junit.Assume.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;

import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.Map;
import java.util.Random;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class EpollSocketChannelConfigTest {

    private static EventLoopGroup group;
    private static EpollSocketChannel ch;
    private static Random rand;

    @BeforeClass
    public static void beforeClass() {
        rand = new Random();
        group = new EpollEventLoopGroup(1);
    }

    @AfterClass
    public static void afterClass() {
        group.shutdownGracefully();
    }

    @Before
    public void setup() {
        Bootstrap bootstrap = new Bootstrap();
        ch = (EpollSocketChannel) bootstrap.group(group)
                .channel(EpollSocketChannel.class)
                .handler(new ChannelInboundHandlerAdapter())
                .bind(new InetSocketAddress(0)).syncUninterruptibly().channel();
    }

    @After
    public void teardown() {
        ch.close().syncUninterruptibly();
    }

    private static long randLong(long min, long max) {
        return min + nextLong(max - min + 1);
    }

    private static long nextLong(long n) {
        long bits, val;
        do {
           bits = (rand.nextLong() << 1) >>> 1;
           val = bits % n;
        } while (bits - val + (n - 1) < 0L);
        return val;
     }

    @Test
    public void testRandomTcpNotSentLowAt() {
        final long expected = randLong(0, 0xFFFFFFFFL);
        final long actual;
        try {
            ch.config().setTcpNotSentLowAt(expected);
            actual = ch.config().getTcpNotSentLowAt();
        } catch (RuntimeException e) {
            assumeNoException(e);
            return; // Needed to prevent compile error for final variables to be used below
        }
        assertEquals(expected, actual);
    }

    @Test
    public void testInvalidHighTcpNotSentLowAt() {
        try {
            final long value = 0xFFFFFFFFL + 1;
            ch.config().setTcpNotSentLowAt(value);
        } catch (IllegalArgumentException e) {
            return;
        } catch (RuntimeException e) {
            assumeNoException(e);
        }
        fail();
    }

    @Test
    public void testInvalidLowTcpNotSentLowAt() {
        try {
            final long value = -1;
            ch.config().setTcpNotSentLowAt(value);
        } catch (IllegalArgumentException e) {
            return;
        } catch (RuntimeException e) {
            assumeNoException(e);
        }
        fail();
    }

    @Test
    public void testTcpCork() {
        ch.config().setTcpCork(false);
        assertFalse(ch.config().isTcpCork());
        ch.config().setTcpCork(true);
        assertTrue(ch.config().isTcpCork());
    }

    @Test
    public void testTcpQickAck() {
        ch.config().setTcpQuickAck(false);
        assertFalse(ch.config().isTcpQuickAck());
        ch.config().setTcpQuickAck(true);
        assertTrue(ch.config().isTcpQuickAck());
    }

    @Test
    public void testSetOptionWhenClosed() {
        ch.close().syncUninterruptibly();
        try {
            ch.config().setSoLinger(0);
            fail();
        } catch (ChannelException e) {
            assertTrue(e.getCause() instanceof ClosedChannelException);
        }
    }

    @Test
    public void testGetOptionWhenClosed() {
        ch.close().syncUninterruptibly();
        try {
            ch.config().getSoLinger();
            fail();
        } catch (ChannelException e) {
            if (!(e.getCause() instanceof ClosedChannelException)) {
                AssertionError error = new AssertionError(
                        "Expected the suppressed exception to be an instance of ClosedChannelException.");
                error.addSuppressed(e.getCause());
                throw error;
            }
        }
    }

    @Test
    public void getGetOptions() {
        Map<ChannelOption<?>, Object> map = ch.config().getOptions();
        assertFalse(map.isEmpty());
    }
}
