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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.ChannelOutputShutdownException;
import io.netty.util.ReferenceCountUtil;

import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class QuicStreamChannelCloseTest extends AbstractQuicTest {

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testCloseFromServerWhileInActiveUnidirectional(Executor executor) throws Throwable {
        testCloseFromServerWhileInActive(executor, QuicStreamType.UNIDIRECTIONAL, false);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testCloseFromServerWhileInActiveBidirectional(Executor executor) throws Throwable {
        testCloseFromServerWhileInActive(executor, QuicStreamType.BIDIRECTIONAL, false);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testHalfCloseFromServerWhileInActiveUnidirectional(Executor executor) throws Throwable {
        testCloseFromServerWhileInActive(executor, QuicStreamType.UNIDIRECTIONAL, true);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testHalfCloseFromServerWhileInActiveBidirectional(Executor executor) throws Throwable {
        testCloseFromServerWhileInActive(executor, QuicStreamType.BIDIRECTIONAL, true);
    }

    private static void testCloseFromServerWhileInActive(Executor executor, QuicStreamType type,
                                                         boolean halfClose) throws Throwable {
        Channel server = null;
        Channel channel = null;
        try {
            final Promise<Channel> streamPromise = ImmediateEventExecutor.INSTANCE.newPromise();
            QuicChannelValidationHandler serverHandler = new StreamCreationHandler(type, halfClose, streamPromise);
            server = QuicTestUtils.newServer(executor, serverHandler,
                    new ChannelInboundHandlerAdapter());
            channel = QuicTestUtils.newClient(executor);

            QuicChannelValidationHandler clientHandler = new QuicChannelValidationHandler();

            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
                    .handler(clientHandler)
                    .streamHandler(new StreamHandler())
                    .remoteAddress(server.localAddress())
                    .connect()
                    .get();

            Channel streamChannel = streamPromise.get();

            // Wait for the steam to close. It needs to happen before the 5-second connection idle timeout.
            streamChannel.closeFuture().get(3000, TimeUnit.MILLISECONDS);

            streamChannel.parent().close();

            // Wait till the client was closed
            quicChannel.closeFuture().sync();

            serverHandler.assertState();
            clientHandler.assertState();
        } finally {
            QuicTestUtils.closeIfNotNull(channel);
            QuicTestUtils.closeIfNotNull(server);

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testCloseFromClientWhileInActiveUnidirectional(Executor executor) throws Throwable {
        testCloseFromClientWhileInActive(executor, QuicStreamType.UNIDIRECTIONAL, false);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testCloseFromClientWhileInActiveBidirectional(Executor executor) throws Throwable {
        testCloseFromClientWhileInActive(executor, QuicStreamType.BIDIRECTIONAL, false);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testHalfCloseFromClientWhileInActiveUnidirectional(Executor executor) throws Throwable {
        testCloseFromClientWhileInActive(executor, QuicStreamType.UNIDIRECTIONAL, true);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testHalfCloseFromClientWhileInActiveBidirectional(Executor executor) throws Throwable {
        testCloseFromClientWhileInActive(executor, QuicStreamType.BIDIRECTIONAL, true);
    }

    private static void testCloseFromClientWhileInActive(Executor executor, QuicStreamType type,
                                                         boolean halfClose) throws Throwable {
        Channel server = null;
        Channel channel = null;
        try {
            final Promise<Channel> streamPromise = ImmediateEventExecutor.INSTANCE.newPromise();
            QuicChannelValidationHandler serverHandler = new QuicChannelValidationHandler();
            server = QuicTestUtils.newServer(executor, serverHandler, new StreamHandler());
            channel = QuicTestUtils.newClient(executor);

            StreamCreationHandler creationHandler = new StreamCreationHandler(type, halfClose, streamPromise);
            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
                    .handler(creationHandler)
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(server.localAddress())
                    .connect()
                    .get();

            Channel streamChannel = streamPromise.get();

            // Wait for the steam to close. It needs to happen before the 5-second connection idle timeout.
            streamChannel.closeFuture().get(3000, TimeUnit.MILLISECONDS);

            streamChannel.parent().close();

            // Wait till the client was closed
            quicChannel.closeFuture().sync();

            serverHandler.assertState();
            creationHandler.assertState();
        } finally {
            QuicTestUtils.closeIfNotNull(channel);
            QuicTestUtils.closeIfNotNull(server);

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testWriteToUnidirectionalAfterShutdownOutput(Executor executor) throws Throwable {
        testWriteAfterClosedOrShutdown(executor, QuicStreamType.UNIDIRECTIONAL, true);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testWriteToBidirectionalAfterShutdownOutput(Executor executor) throws Throwable {
        testWriteAfterClosedOrShutdown(executor, QuicStreamType.BIDIRECTIONAL, true);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testWriteToUnidirectionalAfterClose(Executor executor) throws Throwable {
        testWriteAfterClosedOrShutdown(executor, QuicStreamType.UNIDIRECTIONAL, false);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testWriteToBidirectionalAfterClose(Executor executor) throws Throwable {
        testWriteAfterClosedOrShutdown(executor, QuicStreamType.BIDIRECTIONAL, false);
    }

    private static void testWriteAfterClosedOrShutdown(Executor executor, QuicStreamType type,
                                                         boolean halfClose) throws Throwable {
        Channel server = null;
        Channel channel = null;
        try {
            final Promise<Channel> streamPromise = ImmediateEventExecutor.INSTANCE.newPromise();
            server = QuicTestUtils.newServer(executor, new ChannelInboundHandlerAdapter(), new StreamHandler());
            channel = QuicTestUtils.newClient(executor);

            StreamCreationAndTearDownHandler creationHandler =
                    new StreamCreationAndTearDownHandler(type, halfClose, streamPromise);
            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
                    .handler(creationHandler)
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(server.localAddress())
                    .connect()
                    .get();

            // ChannelOutputShutdownException should only be used when its a BIDIRECTIONAL channel and half-closure
            // is used.
            Class<? extends Throwable> causeClass =
                    halfClose && type != QuicStreamType.UNIDIRECTIONAL ?
                            ChannelOutputShutdownException.class : ClosedChannelException.class;
            assertInstanceOf(causeClass, streamPromise.await().cause());
            quicChannel.close().sync();

            // Wait till the client was closed
            quicChannel.closeFuture().sync();
            creationHandler.assertState();
        } finally {
            QuicTestUtils.closeIfNotNull(channel);
            QuicTestUtils.closeIfNotNull(server);

            shutdown(executor);
        }
    }

    private static final class StreamCreationAndTearDownHandler extends QuicChannelValidationHandler {
        private final QuicStreamType type;
        private final boolean halfClose;
        private final Promise<Channel> streamPromise;

        StreamCreationAndTearDownHandler(QuicStreamType type, boolean halfClose, Promise<Channel> streamPromise) {
            this.type = type;
            this.halfClose = halfClose;
            this.streamPromise = streamPromise;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            super.channelActive(ctx);
            QuicChannel channel = (QuicChannel) ctx.channel();
            channel.createStream(type, new ChannelInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx)  {
                    final ChannelFuture future;
                    if (halfClose) {
                        future = ((QuicStreamChannel) ctx.channel()).shutdownOutput();
                    } else {
                        future = ctx.channel().close();
                    }
                    future.addListener(f -> {
                        ctx.channel().writeAndFlush("Unsupported message").addListener(wf -> {
                            streamPromise.setFailure(wf.cause());
                        });
                    });
                }
            });
        }
    }

    private static final class StreamCreationHandler extends QuicChannelValidationHandler {
        private final QuicStreamType type;
        private final boolean halfClose;
        private final Promise<Channel> streamPromise;

        StreamCreationHandler(QuicStreamType type, boolean halfClose, Promise<Channel> streamPromise) {
            this.type = type;
            this.halfClose = halfClose;
            this.streamPromise = streamPromise;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            super.channelActive(ctx);
            QuicChannel channel = (QuicChannel) ctx.channel();
            channel.createStream(type, new ChannelInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx)  {
                    streamPromise.trySuccess(ctx.channel());
                    // Do the write and close the channel
                    ctx.writeAndFlush(Unpooled.buffer().writeZero(8))
                            .addListener(halfClose
                                    ? QuicStreamChannel.SHUTDOWN_OUTPUT
                                    : ChannelFutureListener.CLOSE);
                }
            });
        }
    }

    private static final class StreamHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
                // Received a FIN
                ctx.close();
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ReferenceCountUtil.release(msg);
        }
    }
}
