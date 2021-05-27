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
package io.netty.channel.epoll;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.ConnectTimeoutException;
import io.netty.channel.EventLoopGroup;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EpollSocketTcpMd5Test {
    private static final byte[] SERVER_KEY = "abc".getBytes(CharsetUtil.US_ASCII);
    private static final byte[] BAD_KEY = "def".getBytes(CharsetUtil.US_ASCII);
    private static EventLoopGroup GROUP;
    private EpollServerSocketChannel server;

    @BeforeAll
    public static void beforeClass() {
        GROUP = new EpollEventLoopGroup(1);
    }

    @AfterAll
    public static void afterClass() {
        GROUP.shutdownGracefully();
    }

    @BeforeEach
    public void setup() {
        Bootstrap bootstrap = new Bootstrap();
        server = (EpollServerSocketChannel) bootstrap.group(GROUP)
                .channel(EpollServerSocketChannel.class)
                .handler(new ChannelInboundHandlerAdapter())
                .bind(new InetSocketAddress(NetUtil.LOCALHOST4, 0)).syncUninterruptibly().channel();
    }

    @AfterEach
    public void teardown() {
        server.close().syncUninterruptibly();
    }

    @Test
    public void testServerSocketChannelOption() throws Exception {
        server.config().setOption(EpollChannelOption.TCP_MD5SIG,
                Collections.<InetAddress, byte[]>singletonMap(NetUtil.LOCALHOST4, SERVER_KEY));
        server.config().setOption(EpollChannelOption.TCP_MD5SIG, Collections.<InetAddress, byte[]>emptyMap());
    }

    @Test
    public void testServerOption() throws Exception {
        Bootstrap bootstrap = new Bootstrap();
        EpollServerSocketChannel ch = (EpollServerSocketChannel) bootstrap.group(GROUP)
                .channel(EpollServerSocketChannel.class)
                .handler(new ChannelInboundHandlerAdapter())
                .bind(new InetSocketAddress(0)).syncUninterruptibly().channel();

        ch.config().setOption(EpollChannelOption.TCP_MD5SIG,
                Collections.<InetAddress, byte[]>singletonMap(NetUtil.LOCALHOST4, SERVER_KEY));
        ch.config().setOption(EpollChannelOption.TCP_MD5SIG, Collections.<InetAddress, byte[]>emptyMap());

        ch.close().syncUninterruptibly();
    }

    @Test
    public void testKeyMismatch() throws Exception {
        server.config().setOption(EpollChannelOption.TCP_MD5SIG,
                Collections.<InetAddress, byte[]>singletonMap(NetUtil.LOCALHOST4, SERVER_KEY));

        assertThrows(ConnectTimeoutException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                EpollSocketChannel client = (EpollSocketChannel) new Bootstrap().group(GROUP)
                        .channel(EpollSocketChannel.class)
                        .handler(new ChannelInboundHandlerAdapter())
                        .option(EpollChannelOption.TCP_MD5SIG,
                                Collections.<InetAddress, byte[]>singletonMap(NetUtil.LOCALHOST4, BAD_KEY))
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
                        .connect(server.localAddress()).syncUninterruptibly().channel();
                client.close().syncUninterruptibly();
            }
        });
    }

    @Test
    public void testKeyMatch() throws Exception {
        server.config().setOption(EpollChannelOption.TCP_MD5SIG,
                Collections.<InetAddress, byte[]>singletonMap(NetUtil.LOCALHOST4, SERVER_KEY));

        EpollSocketChannel client = (EpollSocketChannel) new Bootstrap().group(GROUP)
                .channel(EpollSocketChannel.class)
                .handler(new ChannelInboundHandlerAdapter())
                .option(EpollChannelOption.TCP_MD5SIG,
                        Collections.<InetAddress, byte[]>singletonMap(NetUtil.LOCALHOST4, SERVER_KEY))
                .connect(server.localAddress()).syncUninterruptibly().channel();
        client.close().syncUninterruptibly();
    }
}
