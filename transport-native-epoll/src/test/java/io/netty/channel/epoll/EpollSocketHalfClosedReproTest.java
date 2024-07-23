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
package io.netty.channel.epoll;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.DuplexChannel;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.socket.AbstractSocketTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.channel.epoll.EpollSocketTestPermutation.EPOLL_BOSS_GROUP;
import static io.netty.channel.epoll.EpollSocketTestPermutation.EPOLL_WORKER_GROUP;

public class EpollSocketHalfClosedReproTest extends AbstractSocketTest {


    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> newFactories() {
        return Arrays.asList(new TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>() {
            @Override
            public ServerBootstrap newServerInstance() {
                return new ServerBootstrap().group(EPOLL_BOSS_GROUP, EPOLL_WORKER_GROUP)
                        .channel(EpollServerSocketChannel.class);
            }

            @Override
            public Bootstrap newClientInstance() {
                return new Bootstrap().group(EPOLL_WORKER_GROUP).channel(EpollSocketChannel.class);
            }
        });
    }

    @Timeout(10)
    @Test
    public void testAllDataReadAfterHalfClosureRepro(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                pipelineEmitsAllDataAndEventsOnHalfClosed(serverBootstrap, bootstrap);
            }
        });
    }

    private void pipelineEmitsAllDataAndEventsOnHalfClosed(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        final int totalServerBytesWritten = 1;
        final CountDownLatch clientReadAllDataLatch = new CountDownLatch(1);
        final CountDownLatch clientHalfClosedLatch = new CountDownLatch(1);
        final CountDownLatch clientHalfClosedAllBytesRead = new CountDownLatch(1);
        final AtomicInteger clientReadCompletes = new AtomicInteger();
        final AtomicInteger clientZeroDataReadCompletes = new AtomicInteger();
        Channel serverChannel = null;
        Channel clientChannel = null;
        AtomicReference<Channel> serverChildChannel = new AtomicReference<>();
        try {
            cb.option(ChannelOption.ALLOW_HALF_CLOSURE, true)
                    .option(ChannelOption.AUTO_CLOSE, false)
                    .option(ChannelOption.AUTO_READ, false);

            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    serverChildChannel.set(ch);
                    ch.config().setOption(ChannelOption.ALLOW_HALF_CLOSURE, true);
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            ctx.channel().config().setAutoClose(false);
                            ByteBuf buf = ctx.alloc().buffer(totalServerBytesWritten);
                            buf.writerIndex(buf.capacity());
                            ctx.writeAndFlush(buf);
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            ctx.close();
                        }
                    });
                }
            });

            // client.
            cb.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.config().setAutoClose(false);
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        private int bytesRead;
                        private int bytesSinceReadComplete;

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            ByteBuf buf = (ByteBuf) msg;
                            bytesRead += buf.readableBytes();
                            bytesSinceReadComplete += buf.readableBytes();
                            buf.release();
                        }

                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                            if (evt == ChannelInputShutdownEvent.INSTANCE) {
                                clientHalfClosedLatch.countDown();
                            } else if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
                                clientHalfClosedAllBytesRead.countDown();
                                ctx.close();
                            }
                        }

                        @Override
                        public void channelReadComplete(ChannelHandlerContext ctx) {
                            if (bytesSinceReadComplete == 0) {
                                clientZeroDataReadCompletes.incrementAndGet();
                            } else {
                                bytesSinceReadComplete = 0;
                            }
                            clientReadCompletes.incrementAndGet();
                            if (bytesRead == totalServerBytesWritten) {
                                // Bounce this through the event loop to make sure it happens after we're done
                                // with the read operation.
                                ch.eventLoop().execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        clientReadAllDataLatch.countDown();
                                    }
                                });
                            } else {
                                ctx.read();
                            }
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            ctx.close();
                        }
                    });
                    ch.read();
                }
            });

            serverChannel = sb.bind().sync().channel();
            clientChannel = cb.connect(serverChannel.localAddress()).sync().channel();
            clientChannel.read();

            clientReadAllDataLatch.await();

            // Now we need to trigger server half-close
            ((DuplexChannel) serverChildChannel.get()).shutdownOutput();

            clientHalfClosedLatch.await();
            clientHalfClosedAllBytesRead.await(); // failing here.
        } finally {
            if (clientChannel != null) {
                clientChannel.close().sync();
            }
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
        }
    }
}
