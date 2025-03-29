/*
 * Copyright 2024 The Netty Project
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
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.util.NetUtil;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class IoUringFileRegionTest {

    @Test
    public void testSendFile() throws IOException, InterruptedException {
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());
        String sampleString = "hello netty io_uring sendFile!";
        File inFile = File.createTempFile(UUID.randomUUID().toString(), ".tmp");
        inFile.deleteOnExit();
        Files.write(inFile.toPath(), sampleString.getBytes());
        BlockingQueue<ByteBuf> sendFileResult = new LinkedBlockingQueue<>();

        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.channel(IoUringServerSocketChannel.class);
        Channel serverChannel = serverBootstrap.group(group)
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
                        if (compositeByteBuf.readableBytes() == inFile.length()) {
                            sendFileResult.put(compositeByteBuf);
                            compositeByteBuf = null;
                        }
                    }
                })
                .bind(NetUtil.LOCALHOST, 0)
                .syncUninterruptibly().channel();

        Bootstrap clientBoostrap = new Bootstrap();
        clientBoostrap.group(group)
                .channel(IoUringSocketChannel.class)
                .handler(new ChannelInboundHandlerAdapter());
        Channel clientChannel = clientBoostrap.connect(serverChannel.localAddress()).syncUninterruptibly().channel();
        clientChannel.writeAndFlush(new DefaultFileRegion(inFile, 0, Files.size(inFile.toPath()))).sync();
        ByteBuf result = sendFileResult.take();
        ByteBuf expected = Unpooled.copiedBuffer(sampleString, StandardCharsets.US_ASCII);
        try {
            assertEquals(expected, result);
        } finally {
            result.release();
            expected.release();
        }

        serverChannel.close().syncUninterruptibly();
        clientChannel.close().syncUninterruptibly();
        group.shutdownGracefully();
    }
}

