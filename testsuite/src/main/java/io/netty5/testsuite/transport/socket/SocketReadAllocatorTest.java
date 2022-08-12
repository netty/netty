/*
 * Copyright 2022 The Netty Project
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
package io.netty5.testsuite.transport.socket;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.api.Buffer;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.FixedReadHandleFactory;
import io.netty5.channel.ReadBufferAllocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SocketReadAllocatorTest extends AbstractSocketTest {
    private static final int FIXED_CAPACITY = 1024;
    private static final int CUSTOM_CAPACITY = 8;

    @Test
    public void testCustomReadAllocator(TestInfo testInfo) throws Throwable {
        run(testInfo, (serverBootstrap, bootstrap) -> testReadAllocator(sb, cb, (allocator, estimatedCapacity) -> {
            assertEquals(FIXED_CAPACITY, estimatedCapacity);
            return allocator.allocate(CUSTOM_CAPACITY);
        }));
    }

    @Test
    public void testExactReadAllocator(TestInfo testInfo) throws Throwable {
        run(testInfo, (serverBootstrap, bootstrap) ->
                testReadAllocator(sb, cb, ReadBufferAllocator.exact(CUSTOM_CAPACITY)));
    }

    @Test
    public void testDefaultReadAllocator(TestInfo testInfo) throws Throwable {
        run(testInfo, (serverBootstrap, bootstrap) -> testReadAllocator(sb, cb, null));
    }

    private void testReadAllocator(ServerBootstrap sb, Bootstrap cb, ReadBufferAllocator allocator) throws Throwable {
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> causeRef = new AtomicReference<>();
            sb.option(ChannelOption.SO_BACKLOG, 1024)
                    .option(ChannelOption.AUTO_READ, true)
                    .childOption(ChannelOption.AUTO_READ, false)
                    .childOption(ChannelOption.READ_HANDLE_FACTORY, new FixedReadHandleFactory(FIXED_CAPACITY))
                    .childHandler(new ChannelHandler() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            try (Buffer buffer = (Buffer) msg) {
                                if (allocator == null) {
                                    assertEquals(FIXED_CAPACITY, buffer.capacity());
                                } else {
                                    assertEquals(CUSTOM_CAPACITY, buffer.capacity());
                                }
                                latch.countDown();
                            }
                        }

                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            if (allocator == null) {
                                ctx.read();
                            } else {
                                ctx.read(allocator);
                            }
                        }

                        @Override
                        public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            if (!(cause instanceof IOException)) {
                                causeRef.set(cause);
                                latch.countDown();
                            }
                        }
                    });

            serverChannel = sb.bind().asStage().get();

            cb.option(ChannelOption.AUTO_READ, true)
                    .handler(new ChannelHandler() { });

            clientChannel = cb.connect(serverChannel.localAddress()).asStage().get();
            clientChannel.writeAndFlush(clientChannel.bufferAllocator().copyOf(new byte[3])).asStage().sync();

            latch.await();
            Throwable cause = causeRef.get();
            if (cause != null) {
                throw cause;
            }
        } finally {
            if (clientChannel != null) {
                clientChannel.close().asStage().sync();
            }
            if (serverChannel != null) {
                serverChannel.close().asStage().sync();
            }
        }
    }
}
