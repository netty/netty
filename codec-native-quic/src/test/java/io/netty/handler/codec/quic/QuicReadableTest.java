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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class QuicReadableTest extends AbstractQuicTest {

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testCorrectlyHandleReadableStreams(Executor executor) throws Throwable  {
        int numOfStreams = 256;
        int readStreams = numOfStreams / 2;
        // We do write longs.
        int expectedDataRead = readStreams * Long.BYTES;
        final CountDownLatch latch = new CountDownLatch(numOfStreams);
        final AtomicInteger bytesRead = new AtomicInteger();
        final AtomicReference<Throwable> serverErrorRef = new AtomicReference<>();
        final AtomicReference<Throwable> clientErrorRef = new AtomicReference<>();

        QuicChannelValidationHandler serverHandler = new QuicChannelValidationHandler();
        Channel server = QuicTestUtils.newServer(
                QuicTestUtils.newQuicServerBuilder(executor).initialMaxStreamsBidirectional(5000),
                InsecureQuicTokenHandler.INSTANCE,
                serverHandler, new ChannelInboundHandlerAdapter() {
                    private int counter;
                    @Override
                    public void channelRegistered(ChannelHandlerContext ctx) {
                        // Ensure we dont read from the streams so all of these will be reported as readable
                        ctx.channel().config().setAutoRead(false);
                    }

                    @Override
                    public void channelActive(ChannelHandlerContext ctx) {
                        counter++;
                        latch.countDown();
                        if (counter > readStreams) {
                            // Now set it to readable again for some channels
                            ctx.channel().config().setAutoRead(true);
                        }
                    }

                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        ByteBuf buffer = (ByteBuf) msg;
                        bytesRead.addAndGet(buffer.readableBytes());
                        buffer.release();
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        serverErrorRef.set(cause);
                    }

                    @Override
                    public boolean isSharable() {
                        return true;
                    }
                });
        Channel channel = QuicTestUtils.newClient(executor);
        QuicChannelValidationHandler clientHandler = new QuicChannelValidationHandler();
        ByteBuf data = Unpooled.directBuffer().writeLong(8);
        try {
            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
                    .handler(clientHandler)
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(server.localAddress())
                    .connect()
                    .get();

            List<Channel> streams = new ArrayList<>();
            for (int i = 0; i < numOfStreams; i++) {
                QuicStreamChannel stream = quicChannel.createStream(
                        QuicStreamType.BIDIRECTIONAL, new ChannelInboundHandlerAdapter() {
                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                clientErrorRef.set(cause);
                            }
                        }).get();
                streams.add(stream.writeAndFlush(data.retainedSlice()).sync().channel());
            }
            latch.await();
            while (bytesRead.get() < expectedDataRead) {
                Thread.sleep(50);
            }
            for (Channel stream: streams) {
                stream.close().sync();
            }
            quicChannel.close().sync();

            throwIfNotNull(serverErrorRef);
            throwIfNotNull(clientErrorRef);

            serverHandler.assertState();
            clientHandler.assertState();
        } finally {
            data.release();
            server.close().sync();
            // Close the parent Datagram channel as well.
            channel.close().sync();

            shutdown(executor);
        }
    }

    private static void throwIfNotNull(AtomicReference<Throwable> errorRef) throws Throwable {
        Throwable cause = errorRef.get();
        if (cause != null) {
            throw cause;
        }
    }
}
