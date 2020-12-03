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
package io.netty.incubator.codec.quic;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import org.junit.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class QuicStreamFrameTest {

    @Test
    public void testCloseHalfClosureUnidirectional() throws Exception {
        testCloseHalfClosure(QuicStreamType.UNIDIRECTIONAL);
    }

    @Test
    public void testCloseHalfClosureBidirectional() throws Exception {
        testCloseHalfClosure(QuicStreamType.BIDIRECTIONAL);
    }

    private static void testCloseHalfClosure(QuicStreamType type) throws Exception {
        Channel server = null;
        Channel channel = null;
        try {
            StreamHandler handler = new StreamHandler(type);
            server = QuicTestUtils.newServer(null, handler);
            channel = QuicTestUtils.newClient();
            QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                    .handler(new StreamCreationHandler(type))
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(server.localAddress())
                    .connect()
                    .get();

            handler.assertSequence();
            quicChannel.closeFuture().sync();
        } finally {
            QuicTestUtils.closeIfNotNull(channel);
            QuicTestUtils.closeIfNotNull(server);
        }
    }

    private static final class StreamCreationHandler extends ChannelInboundHandlerAdapter {
        private final QuicStreamType type;

        StreamCreationHandler(QuicStreamType type) {
            this.type = type;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
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
        private final QuicStreamType type;

        StreamHandler(QuicStreamType type) {
            this.type = type;
        }

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
                ctx.channel().parent().close();
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            QuicStreamFrame frame = (QuicStreamFrame) msg;
            if (frame.hasFin()) {
                queue.add(1);
                if (((QuicStreamChannel) ctx.channel()).type() == QuicStreamType.BIDIRECTIONAL) {
                    // Let's write back a fin which will also close the channel and so call channelInactive(...)
                    ctx.writeAndFlush(new DefaultQuicStreamFrame(Unpooled.EMPTY_BUFFER, true));
                }
            }
            frame.release();
        }

        void assertSequence() throws Exception {
            assertEquals(0, (int) queue.take());
            assertEquals(1, (int) queue.take());

            if (type == QuicStreamType.BIDIRECTIONAL) {
                // ChannelInputShutdownReadComplete is only triggered for BIDIRECTIONAL as UNIDIRECTIONAL outbound
                // is not readable by design.
                assertEquals(2, (int) queue.take());
            }
            assertEquals(3, (int) queue.take());
            assertTrue(queue.isEmpty());
        }
    }
}
