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

import io.netty5.bootstrap.Bootstrap;
import io.netty5.channel.ChannelException;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.fail;

public class KQueueChannelConfigTest {
    @BeforeEach
    public void before() {
        KQueue.ensureAvailability();
    }

    @Test
    public void testOptionGetThrowsChannelException() throws Exception {
        EventLoopGroup group = new MultithreadEventLoopGroup(1, KQueueHandler.newFactory());
        try {
            KQueueSocketChannel channel = new KQueueSocketChannel(group.next());
            channel.config().getSoLinger();
            channel.fd().close();
            try {
                channel.config().getSoLinger();
                fail();
            } catch (ChannelException e) {
                // expected
            }
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testOptionSetThrowsChannelException() throws Exception {
        EventLoopGroup group = new MultithreadEventLoopGroup(1, KQueueHandler.newFactory());
        try {
            KQueueSocketChannel channel = new KQueueSocketChannel(group.next());
            channel.config().setKeepAlive(true);
            channel.fd().close();
            try {
                channel.config().setKeepAlive(true);
                fail();
            } catch (ChannelException e) {
                // expected
            }
        } finally {
            group.shutdownGracefully();
        }
    }

    // See https://github.com/netty/netty/issues/7159
    @Test
    public void testSoLingerNoAssertError() throws Exception {
        EventLoopGroup group = new MultithreadEventLoopGroup(1, KQueueHandler.newFactory());

        try {
            Bootstrap bootstrap = new Bootstrap();
            KQueueSocketChannel ch = (KQueueSocketChannel) bootstrap.group(group)
                    .channel(KQueueSocketChannel.class)
                    .option(ChannelOption.SO_LINGER, 10)
                    .handler(new ChannelHandler() { })
                    .bind(new InetSocketAddress(0)).get();
            ch.close().syncUninterruptibly();
        } finally {
            group.shutdownGracefully();
        }
    }
}
