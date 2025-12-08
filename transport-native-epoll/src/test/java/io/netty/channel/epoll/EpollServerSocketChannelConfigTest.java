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
package io.netty.channel.epoll;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;

import io.netty.channel.MultiThreadIoEventLoopGroup;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.net.InetSocketAddress;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EpollServerSocketChannelConfigTest {

    private static EventLoopGroup group;
    private static EpollServerSocketChannel ch;

    @BeforeAll
    public static void before() {
        group = new MultiThreadIoEventLoopGroup(1, EpollIoHandler.newFactory());
        ServerBootstrap bootstrap = new ServerBootstrap();
        ch = (EpollServerSocketChannel) bootstrap.group(group)
                .channel(EpollServerSocketChannel.class)
                .childHandler(new ChannelInboundHandler() { })
                .bind(new InetSocketAddress(0)).syncUninterruptibly().channel();
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
    public void testTcpDeferAccept() {
        ch.config().setOption(EpollChannelOption.TCP_DEFER_ACCEPT, 0);
        assertEquals(0, ch.config().getOption(EpollChannelOption.TCP_DEFER_ACCEPT));
        ch.config().setOption(EpollChannelOption.TCP_DEFER_ACCEPT, 10);
        // The returned value may be bigger then what we set.
        // See https://www.spinics.net/lists/netdev/msg117330.html
        assertTrue(10 <= ch.config().getOption(EpollChannelOption.TCP_DEFER_ACCEPT));
    }

    @Test
    public void testReusePort() {
        ch.config().setOption(EpollChannelOption.SO_REUSEPORT, false);
        assertFalse(ch.config().getOption(EpollChannelOption.SO_REUSEPORT));
        ch.config().setOption(EpollChannelOption.SO_REUSEPORT, true);
        assertTrue(ch.config().getOption(EpollChannelOption.SO_REUSEPORT));
    }

    @Test
    public void testFreeBind() {
        ch.config().setOption(EpollChannelOption.IP_FREEBIND, false);
        assertFalse(ch.config().getOption(EpollChannelOption.IP_FREEBIND));
        ch.config().setOption(EpollChannelOption.IP_FREEBIND, true);
        assertTrue(ch.config().getOption(EpollChannelOption.IP_FREEBIND));
    }

    @Test
    public void getGetOptions() {
        Map<ChannelOption<?>, Object> map = ch.config().getOptions();
        assertFalse(map.isEmpty());
    }

    @Test
    public void testFastOpen() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                ch.config().setOption(ChannelOption.TCP_FASTOPEN, -1);
            }
        });
        ch.config().setOption(ChannelOption.TCP_FASTOPEN, 10);
        assertEquals(10, ch.config().getOption(ChannelOption.TCP_FASTOPEN));
    }
}
