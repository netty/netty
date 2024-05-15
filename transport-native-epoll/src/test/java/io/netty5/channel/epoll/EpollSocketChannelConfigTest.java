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
package io.netty5.channel.epoll;

import io.github.artsok.RepeatedIfExceptionsTest;
import io.netty5.bootstrap.Bootstrap;
import io.netty5.channel.ChannelException;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.opentest4j.TestAbortedException;

import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class EpollSocketChannelConfigTest {

    private static EventLoopGroup group;
    private static EpollSocketChannel ch;
    private static Random rand;

    @BeforeAll
    public static void beforeClass() {
        rand = new Random();
        group = new MultithreadEventLoopGroup(1, EpollIoHandler.newFactory());
    }

    @AfterAll
    public static void afterClass() {
        group.shutdownGracefully();
    }

    @BeforeEach
    public void setup() throws Exception {
        Bootstrap bootstrap = new Bootstrap();
        ch = (EpollSocketChannel) bootstrap.group(group)
                                           .channel(EpollSocketChannel.class)
                                           .handler(new ChannelHandler() { })
                                           .bind(new InetSocketAddress(0)).asStage().get();
    }

    @AfterEach
    public void tearDown() throws Exception {
        ch.close().asStage().sync();
    }

    private static long randLong(long min, long max) {
        return min + nextLong(max - min + 1);
    }

    private static long nextLong(long n) {
        long bits, val;
        do {
           bits = rand.nextLong() << 1 >>> 1;
           val = bits % n;
        } while (bits - val + n - 1 < 0L);
        return val;
     }

    @Test
    public void testRandomTcpNotSentLowAt() {
        final long expected = randLong(0, 0xFFFFFFFFL);
        final long actual;
        try {
            ch.setOption(EpollChannelOption.TCP_NOTSENT_LOWAT, expected);
            actual = ch.getOption(EpollChannelOption.TCP_NOTSENT_LOWAT);
        } catch (RuntimeException e) {
            throw new TestAbortedException("assumeNoException", e);
        }
        assertEquals(expected, actual);
    }

    @Test
    public void testInvalidHighTcpNotSentLowAt() {
        try {
            final long value = 0xFFFFFFFFL + 1;
            ch.setOption(EpollChannelOption.TCP_NOTSENT_LOWAT, value);
        } catch (IllegalArgumentException e) {
            return;
        } catch (RuntimeException e) {
            throw new TestAbortedException("assumeNoException", e);
        }
        fail();
    }

    @Test
    public void testInvalidLowTcpNotSentLowAt() {
        try {
            final long value = -1;
            ch.setOption(EpollChannelOption.TCP_NOTSENT_LOWAT, value);
        } catch (IllegalArgumentException e) {
            return;
        } catch (RuntimeException e) {
            throw new TestAbortedException("assumeNoException", e);
        }
        fail();
    }

    @Test
    public void testTcpCork() {
        ch.setOption(EpollChannelOption.TCP_CORK, false);
        assertFalse(ch.getOption(EpollChannelOption.TCP_CORK));
        ch.setOption(EpollChannelOption.TCP_CORK, true);
        assertTrue(ch.getOption(EpollChannelOption.TCP_CORK));
    }

    @Test
    public void testTcpQickAck() {
        ch.setOption(EpollChannelOption.TCP_QUICKACK, false);
        assertFalse(ch.getOption(EpollChannelOption.TCP_QUICKACK));
        ch.setOption(EpollChannelOption.TCP_QUICKACK, true);
        assertTrue(ch.getOption(EpollChannelOption.TCP_QUICKACK));
    }

    // For this test to pass, we are relying on the sockets file descriptor not being reused after the socket is closed.
    // This is inherently racy, so we allow getSoLinger to throw ChannelException a few of times, but eventually we do
    // want to see a ClosedChannelException for the test to pass.
    @RepeatedIfExceptionsTest(repeats = 4)
    public void testSetOptionWhenClosed() throws Exception {
        ch.close().asStage().sync();
        ChannelException e = assertThrows(ChannelException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                ch.setOption(ChannelOption.SO_LINGER, 0);
            }
        });
        assertThat(e).hasCauseInstanceOf(ClosedChannelException.class);
    }

    // For this test to pass, we are relying on the sockets file descriptor not being reused after the socket is closed.
    // This is inherently racy, so we allow getSoLinger to throw ChannelException a few of times, but eventually we do
    // want to see a ClosedChannelException for the test to pass.
    @RepeatedIfExceptionsTest(repeats = 4)
    public void testGetOptionWhenClosed() throws Exception {
        ch.close().asStage().sync();
        ChannelException e = assertThrows(ChannelException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                ch.getOption(ChannelOption.SO_LINGER);
            }
        });
        assertThat(e).hasCauseInstanceOf(ClosedChannelException.class);
    }
}
