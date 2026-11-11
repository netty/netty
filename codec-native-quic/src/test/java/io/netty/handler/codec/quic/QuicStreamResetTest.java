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
package io.netty.handler.codec.quic;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Tests the lifecycle of a {@link QuicStreamChannel} when the remote peer resets the stream (RESET_STREAM).
 */
public class QuicStreamResetTest extends AbstractQuicTest {

    private static final long ERROR_CODE = 100;

    // Well below the idle timeout of the connection (see QuicTestUtils), which would close the stream too.
    private static final long CLOSE_TIMEOUT_MILLIS = 2000;

    private static final int WINDOW = 16 * 1024;

    // Recorded when a QuicStreamFrame with the FIN flag is read.
    private static final Object FIN_FRAME = "FIN_FRAME";

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testResetReceivedThenShutdownOutputClosesStream(Executor executor) throws Throwable {
        testEndOfInputThenShutdownOutputClosesStream(executor, true, false, true);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testResetReceivedThenShutdownOutputClosesStreamReadFrames(Executor executor) throws Throwable {
        testEndOfInputThenShutdownOutputClosesStream(executor, true, true, true);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testResetReceivedThenShutdownOutputClosesStreamNoHalfClosure(Executor executor) throws Throwable {
        testEndOfInputThenShutdownOutputClosesStream(executor, true, false, false);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testResetReceivedThenShutdownOutputClosesStreamReadFramesNoHalfClosure(Executor executor)
            throws Throwable {
        testEndOfInputThenShutdownOutputClosesStream(executor, true, true, false);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testFinReceivedThenShutdownOutputClosesStream(Executor executor) throws Throwable {
        testEndOfInputThenShutdownOutputClosesStream(executor, false, false, true);
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testFinReceivedThenShutdownOutputClosesStreamReadFrames(Executor executor) throws Throwable {
        testEndOfInputThenShutdownOutputClosesStream(executor, false, true, true);
    }

    private static void testEndOfInputThenShutdownOutputClosesStream(Executor executor, boolean reset,
                                                                     boolean readFrames, boolean allowHalfClosure)
            throws Throwable {
        Channel server = null;
        Channel channel = null;
        try {
            ServerStreams serverStreams = new ServerStreams();
            server = QuicTestUtils.newServer(executor, QuicTestUtils.NOOP_HANDLER, serverStreams);
            channel = QuicTestUtils.newClient(executor);
            QuicChannel quicChannel = connect(channel, server);

            EventCollector collector = new EventCollector();
            QuicStreamChannel clientStream = quicChannel.newStreamBootstrap()
                    .type(QuicStreamType.BIDIRECTIONAL)
                    .option(QuicChannelOption.READ_FRAMES, readFrames)
                    .option(ChannelOption.ALLOW_HALF_CLOSURE, allowHalfClosure)
                    .handler(collector)
                    .create().get();
            QuicStreamChannel serverStream = serverStreams.open(clientStream);

            if (reset) {
                serverStream.shutdownOutput((int) ERROR_CODE).sync();
            } else {
                serverStream.shutdownOutput().sync();
            }

            if (reset) {
                // A reset is reported as an exception, and never as a graceful end of input.
                assertReset(collector);
            } else {
                if (readFrames) {
                    assertThat(collector.next()).isSameAs(FIN_FRAME);
                }
                assertThat(collector.next()).isSameAs(ChannelInputShutdownEvent.INSTANCE);
                assertThat(collector.next()).isSameAs(ChannelInputShutdownReadComplete.INSTANCE);
            }
            // Nothing can be read anymore, but the output is still open.
            assertThat(clientStream.isInputShutdown()).isTrue();
            assertThat(clientStream.isActive()).isTrue();
            assertThat(hasStream(clientStream)).isTrue();

            // Both directions are terminated once the local FIN is sent.
            clientStream.shutdownOutput().sync();
            assertThat(clientStream.closeFuture().await(CLOSE_TIMEOUT_MILLIS)).isTrue();
            assertThat(hasStream(clientStream)).isFalse();
            assertThat(collector.events).isEmpty();

            quicChannel.close().sync();
        } finally {
            QuicTestUtils.closeIfNotNull(channel);
            QuicTestUtils.closeIfNotNull(server);

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testResetReceivedAfterShutdownOutputClosesStream(Executor executor) throws Throwable {
        Channel server = null;
        Channel channel = null;
        try {
            ServerStreams serverStreams = new ServerStreams();
            server = QuicTestUtils.newServer(executor, QuicTestUtils.NOOP_HANDLER, serverStreams);
            channel = QuicTestUtils.newClient(executor);
            QuicChannel quicChannel = connect(channel, server);

            EventCollector collector = new EventCollector();
            QuicStreamChannel clientStream = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL, collector).get();
            QuicStreamChannel serverStream = serverStreams.open(clientStream);

            clientStream.shutdownOutput().sync();
            serverStream.shutdownOutput((int) ERROR_CODE).sync();

            // The output was ended already, the reset terminates the stream once it was reported.
            assertReset(collector);
            assertThat(clientStream.closeFuture().await(CLOSE_TIMEOUT_MILLIS)).isTrue();
            assertThat(hasStream(clientStream)).isFalse();
            assertThat(collector.events).isEmpty();

            quicChannel.close().sync();
        } finally {
            QuicTestUtils.closeIfNotNull(channel);
            QuicTestUtils.closeIfNotNull(server);

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testResetReceivedAfterFinReceived(Executor executor) throws Throwable {
        Channel server = null;
        Channel channel = null;
        try {
            ServerStreams serverStreams = new ServerStreams();
            server = QuicTestUtils.newServer(executor, QuicTestUtils.NOOP_HANDLER, serverStreams);
            channel = QuicTestUtils.newClient(executor);
            QuicChannel quicChannel = connect(channel, server);

            EventCollector collector = new EventCollector();
            QuicStreamChannel clientStream = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL, collector).get();
            QuicStreamChannel serverStream = serverStreams.open(clientStream);

            serverStream.shutdownOutput().sync();
            assertThat(collector.next()).isSameAs(ChannelInputShutdownEvent.INSTANCE);
            assertThat(collector.next()).isSameAs(ChannelInputShutdownReadComplete.INSTANCE);

            // The server resets its output, which it ended already. A RESET_STREAM is still sent (RFC 9000 allows it
            // in the "Data Sent" state), but quiche ignores it as the input was read completely: the stream is not
            // made readable again, so nothing should reach the pipeline.
            serverStream.shutdownOutput((int) ERROR_CODE).sync();

            // Round trip on another stream, so anything the server sent for the first stream was received.
            QuicStreamChannel barrier = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new ChannelInboundHandlerAdapter()).get();
            serverStreams.open(barrier).shutdownOutput().sync();
            barrier.shutdownOutput().sync();
            assertThat(barrier.closeFuture().await(CLOSE_TIMEOUT_MILLIS)).isTrue();

            // The input of the first stream ended with the FIN, nothing else is reported.
            clientStream.shutdownOutput().sync();
            assertThat(clientStream.closeFuture().await(CLOSE_TIMEOUT_MILLIS)).isTrue();
            assertThat(hasStream(clientStream)).isFalse();
            assertThat(collector.events).isEmpty();

            quicChannel.close().sync();
        } finally {
            QuicTestUtils.closeIfNotNull(channel);
            QuicTestUtils.closeIfNotNull(server);

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testResetReceivedAfterCloseRemovesStream(Executor executor) throws Throwable {
        Channel server = null;
        Channel channel = null;
        try {
            ServerStreams serverStreams = new ServerStreams();
            server = QuicTestUtils.newServer(executor, QuicTestUtils.NOOP_HANDLER, serverStreams);
            channel = QuicTestUtils.newClient(executor);
            QuicChannel quicChannel = connect(channel, server);

            QuicStreamChannel clientStream = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new ChannelInboundHandlerAdapter()).get();
            QuicStreamChannel serverStream = serverStreams.open(clientStream);

            // The stream is closed before its input is terminated, it stays tracked until the reset is received.
            clientStream.close().sync();
            serverStream.shutdownOutput((int) ERROR_CODE).sync();

            awaitStreamRemoved(clientStream);

            quicChannel.close().sync();
        } finally {
            QuicTestUtils.closeIfNotNull(channel);
            QuicTestUtils.closeIfNotNull(server);

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testResetReceivedWhileDrainingRemovesStream(Executor executor) throws Throwable {
        Channel server = null;
        Channel channel = null;
        try {
            ServerStreams serverStreams = new ServerStreams();
            server = QuicTestUtils.newServer(executor, QuicTestUtils.NOOP_HANDLER, serverStreams);
            channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder(executor)
                    .initialMaxStreamDataBidirectionalLocal(WINDOW));
            QuicChannel quicChannel = connect(channel, server);

            // Close the stream once the first bytes are received. The server never ends its output with a FIN, so
            // the draining of the input can only be ended by the reset.
            QuicStreamChannel clientStream = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            ReferenceCountUtil.release(msg);
                            ctx.close();
                        }
                    }).get();
            QuicStreamChannel serverStream = serverStreams.open(clientStream);

            // Write more than the stream window of the client, so the client closes the stream while the server is
            // still writing.
            serverStream.writeAndFlush(Unpooled.directBuffer(4 * WINDOW).writeZero(4 * WINDOW));
            clientStream.closeFuture().sync();
            serverStream.shutdownOutput((int) ERROR_CODE).sync();

            awaitStreamRemoved(clientStream);

            quicChannel.close().sync();
        } finally {
            QuicTestUtils.closeIfNotNull(channel);
            QuicTestUtils.closeIfNotNull(server);

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testResetReceivedOnUnidirectionalStream(Executor executor) throws Throwable {
        Channel server = null;
        Channel channel = null;
        try {
            EventCollector serverCollector = new EventCollector();
            server = QuicTestUtils.newServer(executor, QuicTestUtils.NOOP_HANDLER, serverCollector);
            channel = QuicTestUtils.newClient(executor);
            QuicChannel quicChannel = connect(channel, server);

            QuicStreamChannel clientStream = quicChannel.createStream(QuicStreamType.UNIDIRECTIONAL,
                    new ChannelInboundHandlerAdapter()).get();
            clientStream.writeAndFlush(Unpooled.buffer().writeByte(0)).sync();
            clientStream.shutdownOutput((int) ERROR_CODE).sync();
            assertReset(serverCollector);

            // The remote created stream has no output, the application closes it once the input is terminated.
            QuicStreamChannel serverStream = serverCollector.channel;
            assertThat(hasStream(serverStream)).isTrue();
            serverStream.close().sync();
            assertThat(hasStream(serverStream)).isFalse();
            assertThat(serverCollector.events).isEmpty();

            quicChannel.close().sync();
        } finally {
            QuicTestUtils.closeIfNotNull(channel);
            QuicTestUtils.closeIfNotNull(server);

            shutdown(executor);
        }
    }

    private static QuicChannel connect(Channel channel, Channel server) throws Exception {
        return QuicTestUtils.newQuicChannelBootstrap(channel)
                .handler(new ChannelInboundHandlerAdapter())
                .streamHandler(new ChannelInboundHandlerAdapter())
                .remoteAddress(server.localAddress())
                .connect()
                .get();
    }

    // The reset is reported with the application error code, while the stream is still active and its input is
    // already shut down.
    private static void assertReset(EventCollector collector) throws InterruptedException {
        Object event = collector.next();
        assertThat(event).isInstanceOf(QuicStreamResetException.class);
        assertThat(((QuicStreamResetException) event).applicationProtocolCode()).isEqualTo(ERROR_CODE);
        assertThat(collector.next()).isEqualTo("active=true, inputShutdown=true");
    }

    private static boolean hasStream(QuicStreamChannel stream) throws Exception {
        QuicheQuicChannel parent = (QuicheQuicChannel) stream.parent();
        return parent.eventLoop().submit(() -> parent.hasStream(stream.streamId())).get();
    }

    private static void awaitStreamRemoved(QuicStreamChannel stream) throws Exception {
        // Nothing is fired when a closed stream is removed, poll.
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(CLOSE_TIMEOUT_MILLIS);
        while (hasStream(stream)) {
            if (System.nanoTime() - deadline > 0) {
                fail("Stream was not removed from its parent");
            }
            Thread.sleep(10);
        }
    }

    /**
     * Keeps the server side of the streams once they are opened, so the tests can act on them.
     */
    private static final class ServerStreams extends ChannelInboundHandlerAdapter {
        private final BlockingQueue<QuicStreamChannel> streams = new LinkedBlockingQueue<>();

        @Override
        public boolean isSharable() {
            return true;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            streams.add((QuicStreamChannel) ctx.channel());
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ReferenceCountUtil.release(msg);
        }

        /**
         * Opens the stream on the server by writing to it from the client, and returns its server side.
         */
        QuicStreamChannel open(QuicStreamChannel clientStream) throws InterruptedException {
            clientStream.writeAndFlush(Unpooled.buffer().writeByte(0)).sync();
            QuicStreamChannel stream = streams.poll(5, TimeUnit.SECONDS);
            assertThat(stream).isNotNull();
            return stream;
        }
    }

    /**
     * Adds the events of a stream to a queue: the exceptions, each followed by the state of the stream when it was
     * reported, and the graceful end of input signals, that is the input shutdown events and the FIN frames.
     */
    private static final class EventCollector extends ChannelInboundHandlerAdapter {
        final BlockingQueue<Object> events = new LinkedBlockingQueue<>();
        volatile QuicStreamChannel channel;

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            channel = (QuicStreamChannel) ctx.channel();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof QuicStreamFrame && ((QuicStreamFrame) msg).hasFin()) {
                events.add(FIN_FRAME);
            }
            ReferenceCountUtil.release(msg);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt == ChannelInputShutdownEvent.INSTANCE || evt == ChannelInputShutdownReadComplete.INSTANCE) {
                events.add(evt);
            }
            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            QuicStreamChannel channel = (QuicStreamChannel) ctx.channel();
            events.add(cause);
            events.add("active=" + channel.isActive() + ", inputShutdown=" + channel.isInputShutdown());
        }

        Object next() throws InterruptedException {
            return events.poll(5, TimeUnit.SECONDS);
        }
    }
}
