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

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ConnectTimeoutException;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.util.CharsetUtil;
import io.netty5.util.NetUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EpollSocketTcpMd5Test {
    private static final byte[] SERVER_KEY = "abc".getBytes(CharsetUtil.US_ASCII);
    private static final byte[] BAD_KEY = "def".getBytes(CharsetUtil.US_ASCII);
    private static EventLoopGroup GROUP;
    private EpollServerSocketChannel server;

    @BeforeAll
    public static void beforeClass() {
        GROUP = new MultithreadEventLoopGroup(1, EpollHandler.newFactory());
    }

    @AfterAll
    public static void afterClass() {
        GROUP.shutdownGracefully();
    }

    @BeforeEach
    public void setup() throws Exception {
        ServerBootstrap bootstrap = new ServerBootstrap();
        server = (EpollServerSocketChannel) bootstrap.group(GROUP)
                .channel(EpollServerSocketChannel.class)
                .childHandler(new ChannelHandler() { })
                .bind(new InetSocketAddress(NetUtil.LOCALHOST4, 0)).get();
    }

    @AfterEach
    public void teardown() {
        server.close().syncUninterruptibly();
    }

    @Test
    public void testServerSocketChannelOption() throws Exception {
        server.config().setOption(EpollChannelOption.TCP_MD5SIG,
                Collections.singletonMap(NetUtil.LOCALHOST4, SERVER_KEY));
        server.config().setOption(EpollChannelOption.TCP_MD5SIG, Collections.emptyMap());
    }

    @Test
    public void testServerOption() throws Exception {
        ServerBootstrap bootstrap = new ServerBootstrap();
        EpollServerSocketChannel ch = (EpollServerSocketChannel) bootstrap.group(GROUP)
                .channel(EpollServerSocketChannel.class)
                .childHandler(new ChannelHandler() { })
                .bind(new InetSocketAddress(0)).get();

        ch.config().setOption(EpollChannelOption.TCP_MD5SIG,
                Collections.singletonMap(NetUtil.LOCALHOST4, SERVER_KEY));
        ch.config().setOption(EpollChannelOption.TCP_MD5SIG, Collections.emptyMap());

        ch.close().syncUninterruptibly();
    }

    @Test
    public void testKeyMismatch() throws Throwable {
        server.config().setOption(EpollChannelOption.TCP_MD5SIG,
                Collections.singletonMap(NetUtil.LOCALHOST4, SERVER_KEY));

        ExecutionException completion = assertThrows(ExecutionException.class, () -> {
            EpollSocketChannel client = (EpollSocketChannel) new Bootstrap().group(GROUP)
                    .channel(EpollSocketChannel.class)
                    .handler(new ChannelHandler() {
                    })
                    .option(EpollChannelOption.TCP_MD5SIG,
                            Collections.singletonMap(NetUtil.LOCALHOST4, BAD_KEY))
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
                    .connect(server.localAddress()).get();
            client.close().syncUninterruptibly();
        });
        assertThat(completion.getCause())
                .isInstanceOf(ConnectTimeoutException.class);
    }

    @Test
    public void testKeyMatch() throws Exception {
        server.config().setOption(EpollChannelOption.TCP_MD5SIG,
                Collections.singletonMap(NetUtil.LOCALHOST4, SERVER_KEY));

        EpollSocketChannel client = (EpollSocketChannel) new Bootstrap().group(GROUP)
                .channel(EpollSocketChannel.class)
                .handler(new ChannelHandler() { })
                .option(EpollChannelOption.TCP_MD5SIG,
                        Collections.singletonMap(NetUtil.LOCALHOST4, SERVER_KEY))
                .connect(server.localAddress()).get();
        client.close().syncUninterruptibly();
    }
}
