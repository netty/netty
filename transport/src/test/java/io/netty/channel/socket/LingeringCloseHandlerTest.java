/*
 * Copyright 2026 The Netty Project
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
package io.netty.channel.socket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.junit.jupiter.api.Test;

import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class LingeringCloseHandlerTest {
    @Test
    void peerCanReadFinalWriteAndFinishSendingBeforeFullClose() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        BlockingQueue<Channel> accepted = new LinkedBlockingQueue<>();
        try {
            Channel server = new ServerBootstrap().group(group).channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.AUTO_READ, false)
                    .childOption(ChannelOption.ALLOW_HALF_CLOSURE, true)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(new LingeringCloseHandler(30, TimeUnit.SECONDS));
                            accepted.add(ch);
                        }
                    }).bind("127.0.0.1", 0).sync().channel();
            try (Socket peer = new Socket()) {
                peer.connect(server.localAddress());
                peer.setSoTimeout(5000);
                Channel child = accepted.poll(5, TimeUnit.SECONDS);
                assertThat(child).isNotNull();
                child.writeAndFlush(Unpooled.wrappedBuffer(new byte[] { 42 })).sync();
                ChannelFuture closed = child.close();
                assertThat(peer.getInputStream().read()).isEqualTo(42);
                assertThat(peer.getInputStream().read()).isEqualTo(-1);
                assertThat(closed.isDone()).isFalse();
                peer.getOutputStream().write(42);
                peer.shutdownOutput();
                assertThat(closed.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(closed.isSuccess()).isTrue();
            }
        } finally {
            group.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync();
        }
    }
}
