/*
 * Copyright 2025 The Netty Project
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
package io.netty.channel.uring;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.util.NetUtil;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IoUringSendZCTest {

    @Test
    public void testSendZC() throws Exception {
        String sampleString = "Hello Io_uring_send_zc";
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());
        try {
            AtomicInteger targetResult = new AtomicInteger();
            targetResult.set(sampleString.length());
            LinkedBlockingQueue<ByteBuf> zcResult = new LinkedBlockingQueue<>();
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            Channel serverChannel = serverBootstrap
                    .channel(IoUringServerSocketChannel.class)
                    .group(group)
                    .childHandler(new ChannelInboundHandlerAdapter() {
                        private CompositeByteBuf compositeByteBuf;

                        @Override
                        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                            compositeByteBuf = ctx.alloc().compositeBuffer();
                        }

                        @Override
                        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
                            if (compositeByteBuf != null) {
                                compositeByteBuf.release();
                            }
                        }

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                            ByteBuf buf = (ByteBuf) msg;
                            compositeByteBuf.addComponent(true, buf);
                            if (compositeByteBuf.readableBytes() == targetResult.get()) {
                                zcResult.put(compositeByteBuf);
                                compositeByteBuf = ctx.alloc().compositeBuffer();
                            }
                        }
                    })
                    .bind(NetUtil.LOCALHOST, 0)
                    .syncUninterruptibly().channel();

            Bootstrap clientBoostrap = new Bootstrap();
            clientBoostrap.group(group)
                    .channel(IoUringSocketChannel.class)
                    .option(IoUringChannelOption.IO_URING_SEND_ZC_THRESHOLD, 0)
                    .handler(new ChannelInboundHandlerAdapter());
            Channel clientChannel = clientBoostrap.connect(serverChannel.localAddress())
                    .syncUninterruptibly().channel();
            clientChannel.writeAndFlush(Unpooled.copiedBuffer(sampleString, StandardCharsets.US_ASCII));

            ByteBuf result = zcResult.take();
            ByteBuf expected = Unpooled.copiedBuffer(sampleString, StandardCharsets.US_ASCII);

            try {
                assertEquals(expected, result);
            } finally {
                result.release();
                expected.release();
            }

            clientChannel.config()
                    .setOption(IoUringChannelOption.IO_URING_SEND_ZC_THRESHOLD, sampleString.length() + 1);

            serverChannel.close().sync();
            clientChannel.close().sync();
        } finally {
            group.shutdownGracefully().syncUninterruptibly();
        }
    }

}
