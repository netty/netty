/*
 * Copyright 2018 The Netty Project
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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

public class SocketConditionalWritabilityTest extends AbstractSocketTest {
    @Test(timeout = 30000)
    public void testConditionalWritability() throws Throwable {
        run();
    }

    public void testConditionalWritability(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            final int expectedBytes = 100 * 1024 * 1024;
            final int maxWriteChunkSize = 16 * 1024;
            final CountDownLatch latch = new CountDownLatch(1);
            sb.childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 16 * 1024));
            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new ChannelDuplexHandler() {
                        private int bytesWritten;

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            ReferenceCountUtil.release(msg);
                            writeRemainingBytes(ctx);
                        }

                        @Override
                        public void flush(ChannelHandlerContext ctx) {
                            if (ctx.channel().isWritable()) {
                                writeRemainingBytes(ctx);
                            } else {
                                ctx.flush();
                            }
                        }

                        @Override
                        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
                            if (ctx.channel().isWritable()) {
                                writeRemainingBytes(ctx);
                            }
                            ctx.fireChannelWritabilityChanged();
                        }

                        private void writeRemainingBytes(ChannelHandlerContext ctx) {
                            while (ctx.channel().isWritable() && bytesWritten < expectedBytes) {
                                int chunkSize = Math.min(expectedBytes - bytesWritten, maxWriteChunkSize);
                                bytesWritten += chunkSize;
                                ctx.write(ctx.alloc().buffer(chunkSize).writeZero(chunkSize));
                            }
                            ctx.flush();
                        }
                    });
                }
            });

            serverChannel = sb.bind().syncUninterruptibly().channel();

            cb.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        private int totalRead;
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            ctx.writeAndFlush(ctx.alloc().buffer(1).writeByte(0));
                        }

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            if (msg instanceof ByteBuf) {
                                totalRead += ((ByteBuf) msg).readableBytes();
                                if (totalRead == expectedBytes) {
                                    latch.countDown();
                                }
                            }
                            ReferenceCountUtil.release(msg);
                        }
                    });
                }
            });
            clientChannel = cb.connect(serverChannel.localAddress()).syncUninterruptibly().channel();
            latch.await();
        } finally {
            if (serverChannel != null) {
                serverChannel.close();
            }
            if (clientChannel != null) {
                clientChannel.close();
            }
        }
    }
}
