/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.quic;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class QuicStreamFrameTest extends AbstractQuicTest {

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testCloseHalfClosureUnidirectional(Executor executor) throws Throwable {
        testCloseHalfClosure(executor, QuicStreamType.UNIDIRECTIONAL);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testCloseHalfClosureBidirectional(Executor executor) throws Throwable {
        testCloseHalfClosure(executor, QuicStreamType.BIDIRECTIONAL);
    }

    private static void testCloseHalfClosure(Executor executor, QuicStreamType type) throws Throwable {
        Channel server = null;
        Channel channel = null;
        QuicChannelValidationHandler serverHandler = new QuicChannelValidationHandler();
        QuicChannelValidationHandler clientHandler = new StreamCreationHandler(type);
        try {
            StreamHandler handler = new StreamHandler();
            server = QuicTestUtils.newServer(executor, serverHandler, handler);
            channel = QuicTestUtils.newClient(executor);
            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
                    .handler(clientHandler)
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(server.localAddress())
                    .connect()
                    .get();

            handler.assertSequence();
            quicChannel.closeFuture().sync();

            serverHandler.assertState();
            clientHandler.assertState();
        } finally {
            QuicTestUtils.closeIfNotNull(channel);
            QuicTestUtils.closeIfNotNull(server);

            shutdown(executor);
        }
    }

    private static final class StreamCreationHandler extends QuicChannelValidationHandler {
        private final QuicStreamType type;

        StreamCreationHandler(QuicStreamType type) {
            this.type = type;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            super.channelActive(ctx);
            QuicChannel channel = (QuicChannel) ctx.channel();
            channel.createStream(type, new ChannelInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx)  {
                    // Do the write and close the channel
                    ctx.writeAndFlush(Unpooled.buffer().writeZero(8))
                            .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
                }
            });
        }
    }

    private static final class StreamHandler extends ChannelInboundHandlerAdapter {
        private final BlockingQueue<Integer> queue = new LinkedBlockingQueue<>();

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) {
            ctx.channel().config().setOption(QuicChannelOption.READ_FRAMES, true);
            queue.add(0);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            queue.add(3);
            // Close the QUIC channel as well.
            ctx.channel().parent().close();
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
                queue.add(2);
                if (((QuicStreamChannel) ctx.channel()).type() == QuicStreamType.BIDIRECTIONAL) {
                    // Let's write back a fin which will also close the channel and so call channelInactive(...)
                    ctx.writeAndFlush(new DefaultQuicStreamFrame(Unpooled.EMPTY_BUFFER, true));
                }
                ctx.channel().parent().close();
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            QuicStreamFrame frame = (QuicStreamFrame) msg;
            if (frame.hasFin()) {
                queue.add(1);
            }
            frame.release();
        }

        void assertSequence() throws Exception {
            assertEquals(0, (int) queue.take());
            assertEquals(1, (int) queue.take());
            assertEquals(2, (int) queue.take());
            assertEquals(3, (int) queue.take());
            assertTrue(queue.isEmpty());
        }
    }
}
