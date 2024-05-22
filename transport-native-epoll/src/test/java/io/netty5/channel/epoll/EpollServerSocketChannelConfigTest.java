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
package io.netty5.channel.epoll;

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

public class EpollServerSocketChannelConfigTest {

    private static EventLoopGroup group;
    private static EpollServerSocketChannel ch;

    @BeforeAll
    public static void before() throws Exception {
        group = new MultithreadEventLoopGroup(1, EpollIoHandler.newFactory());
        ServerBootstrap bootstrap = new ServerBootstrap();
        ch = (EpollServerSocketChannel) bootstrap.group(group)
                                                 .channel(EpollServerSocketChannel.class)
                                                 .childHandler(new ChannelHandler() { })
                                                 .bind(new InetSocketAddress(0)).asStage().get();
    }

    @AfterAll
    public static void after() throws Exception {
        try {
            ch.close().asStage().sync();
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testTcpDeferAccept() {
        ch.setOption(EpollChannelOption.TCP_DEFER_ACCEPT, 0);
        assertEquals(0,  ch.getOption(EpollChannelOption.TCP_DEFER_ACCEPT));
        ch.setOption(EpollChannelOption.TCP_DEFER_ACCEPT, 10);
        // The returned value may be bigger then what we set.
        // See https://www.spinics.net/lists/netdev/msg117330.html
        assertTrue(10 <= ch.getOption(EpollChannelOption.TCP_DEFER_ACCEPT));
    }

    @Test
    public void testFreeBind() {
        ch.setOption(EpollChannelOption.IP_FREEBIND, false);
        assertFalse(ch.getOption(EpollChannelOption.IP_FREEBIND));
        ch.setOption(EpollChannelOption.IP_FREEBIND, true);
        assertTrue(ch.getOption(EpollChannelOption.IP_FREEBIND));
    }
}
