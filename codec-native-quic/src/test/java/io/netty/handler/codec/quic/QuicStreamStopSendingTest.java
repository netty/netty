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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.ChannelOutputShutdownEvent;
import io.netty.channel.socket.ChannelOutputShutdownException;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that a {@link QuicStreamChannel} is notified when the remote peer sends a STOP_SENDING frame.
 */
public class QuicStreamStopSendingTest extends AbstractQuicTest {

    private static final long ERROR_CODE = 100;

    private static final int WINDOW = 16 * 1024;

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testStopSendingReceived(Executor executor) throws Throwable {
        Channel server = null;
        Channel channel = null;
        try {
            ServerStreams serverStreams = new ServerStreams();
            server = QuicTestUtils.newServer(executor, QuicTestUtils.NOOP_HANDLER, serverStreams);
            channel = QuicTestUtils.newClient(executor);
            QuicChannel quicChannel = connect(channel, server);

            QuicStreamChannel clientStream = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new ChannelInboundHandlerAdapter()).get(5, TimeUnit.SECONDS);
            QuicStreamChannel serverStream = serverStreams.open(clientStream);

            clientStream.shutdownInput((int) ERROR_CODE).sync();

            // The STOP_SENDING is reported although nothing was written. The output is shut down, but the stream
            // stays open.
            assertStopped(serverStreams.next());
            assertThat(serverStream.isOutputShutdown()).isTrue();
            assertThat(serverStream.isActive()).isTrue();
            assertThat(serverStreams.events).isEmpty();

            quicChannel.close().sync();
        } finally {
            QuicTestUtils.closeIfNotNull(channel);
            QuicTestUtils.closeIfNotNull(server);

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testStopSendingBeforeFirstDataIsReported(Executor executor) throws Throwable {
        Channel server = null;
        Channel channel = null;
        try {
            ServerStreams serverStreams = new ServerStreams();
            server = QuicTestUtils.newServer(executor, QuicTestUtils.NOOP_HANDLER, serverStreams);
            channel = QuicTestUtils.newClient(executor);
            QuicChannel quicChannel = connect(channel, server);

            QuicStreamChannel clientStream = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new ChannelInboundHandlerAdapter()).get(5, TimeUnit.SECONDS);
            clientStream.shutdownInput((int) ERROR_CODE).sync();
            // The data of a second stream is sent after the STOP_SENDING, so once the server has seen that stream, it
            // has also processed the STOP_SENDING, before any data of the first stream.
            QuicStreamChannel barrier = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new ChannelInboundHandlerAdapter()).get(5, TimeUnit.SECONDS);
            serverStreams.open(barrier);

            // The server stream is created already stopped. The STOP_SENDING is reported once its handler was added.
            QuicStreamChannel serverStream = serverStreams.open(clientStream);
            assertStopped(serverStreams.next());
            assertThat(serverStream.isOutputShutdown()).isTrue();
            assertThat(serverStreams.events).isEmpty();

            quicChannel.close().sync();
        } finally {
            QuicTestUtils.closeIfNotNull(channel);
            QuicTestUtils.closeIfNotNull(server);

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testStopSendingReceivedWithQueuedWrites(Executor executor) throws Throwable {
        Channel server = null;
        Channel channel = null;
        try {
            ServerStreams serverStreams = new ServerStreams();
            server = QuicTestUtils.newServer(executor, QuicTestUtils.NOOP_HANDLER, serverStreams);
            channel = QuicTestUtils.newClient(QuicTestUtils.newQuicClientBuilder(executor)
                    .initialMaxStreamDataBidirectionalLocal(WINDOW));
            QuicChannel quicChannel = connect(channel, server);

            // The client does not read, so what the server writes beyond the stream window is queued.
            QuicStreamChannel clientStream = quicChannel.newStreamBootstrap()
                    .type(QuicStreamType.BIDIRECTIONAL)
                    .option(ChannelOption.AUTO_READ, false)
                    .handler(new ChannelInboundHandlerAdapter())
                    .create().get(5, TimeUnit.SECONDS);
            QuicStreamChannel serverStream = serverStreams.open(clientStream);

            ByteBuf first = Unpooled.directBuffer(4 * WINDOW).writeZero(4 * WINDOW);
            ByteBuf second = Unpooled.directBuffer(8).writeZero(8);
            ChannelFuture firstWrite = serverStream.write(first);
            ChannelFuture secondWrite = serverStream.writeAndFlush(second);
            firstWrite.addListener(f -> serverStreams.events.add(f.isSuccess() ? "written" : f.cause()));
            assertThat(serverStream.eventLoop().submit(serverStream::isWritable).get(5, TimeUnit.SECONDS)).isFalse();

            clientStream.shutdownInput((int) ERROR_CODE).sync();

            // The STOP_SENDING is reported before the queued writes fail.
            assertStopped(serverStreams.next());
            assertThat(serverStreams.next()).isInstanceOf(ChannelOutputShutdownException.class);
            assertThat(secondWrite.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(secondWrite.cause()).isInstanceOf(ChannelOutputShutdownException.class);

            // The queued buffers are released.
            assertThat(first.refCnt()).isZero();
            assertThat(second.refCnt()).isZero();

            assertThat(serverStream.isOutputShutdown()).isTrue();
            assertThat(serverStream.isActive()).isTrue();

            // A write after the STOP_SENDING fails, and the STOP_SENDING is not reported again.
            ByteBuf third = Unpooled.directBuffer(8).writeZero(8);
            ChannelFuture thirdWrite = serverStream.writeAndFlush(third).await();
            assertThat(thirdWrite.cause()).isInstanceOf(ChannelOutputShutdownException.class);
            assertThat(third.refCnt()).isZero();

            // A write of a buffer with several components fails as well, instead of being queued.
            ByteBuf composite = Unpooled.wrappedBuffer(
                    Unpooled.directBuffer(8).writeZero(8), Unpooled.directBuffer(8).writeZero(8));
            ChannelFuture fourthWrite = serverStream.eventLoop()
                    .submit(() -> serverStream.writeAndFlush(composite)).get(5, TimeUnit.SECONDS);
            assertThat(fourthWrite.isDone()).isTrue();
            assertThat(fourthWrite.cause()).isInstanceOf(ChannelOutputShutdownException.class);
            assertThat(composite.refCnt()).isZero();

            // An empty write fails as well, instead of succeeding.
            ByteBuf empty = Unpooled.directBuffer(0);
            ChannelFuture fifthWrite = serverStream.eventLoop()
                    .submit(() -> serverStream.writeAndFlush(empty)).get(5, TimeUnit.SECONDS);
            assertThat(fifthWrite.isDone()).isTrue();
            assertThat(fifthWrite.cause()).isInstanceOf(ChannelOutputShutdownException.class);
            assertThat(empty.refCnt()).isZero();

            assertThat(serverStreams.events).isEmpty();

            quicChannel.close().sync();
        } finally {
            QuicTestUtils.closeIfNotNull(channel);
            QuicTestUtils.closeIfNotNull(server);

            shutdown(executor);
        }
    }

    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    public void testFinReceivedIsNotStopSending(Executor executor) throws Throwable {
        Channel server = null;
        Channel channel = null;
        try {
            ServerStreams serverStreams = new ServerStreams();
            server = QuicTestUtils.newServer(executor, QuicTestUtils.NOOP_HANDLER, serverStreams);
            channel = QuicTestUtils.newClient(executor);
            QuicChannel quicChannel = connect(channel, server);

            QuicStreamChannel clientStream = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new ChannelInboundHandlerAdapter()).get(5, TimeUnit.SECONDS);
            QuicStreamChannel serverStream = serverStreams.open(clientStream);

            clientStream.shutdownOutput().sync();

            // The end of the input of the server does not stop its output.
            assertThat(serverStreams.next()).isSameAs(ChannelInputShutdownReadComplete.INSTANCE);
            assertThat(serverStream.isOutputShutdown()).isFalse();
            serverStream.writeAndFlush(Unpooled.directBuffer().writeZero(8)).sync();
            assertThat(serverStreams.events).isEmpty();

            clientStream.shutdownInput((int) ERROR_CODE).sync();
            assertStopped(serverStreams.next());
            assertThat(serverStream.isOutputShutdown()).isTrue();

            quicChannel.close().sync();
        } finally {
            QuicTestUtils.closeIfNotNull(channel);
            QuicTestUtils.closeIfNotNull(server);

            shutdown(executor);
        }
    }

    /**
     * The server finished a response and closed its stream channel, but the data is not acknowledged yet when the
     * client stops the stream. quiche keeps a stopped stream until the stop is reported to the application, so if
     * netty never asks about a stream it has no channel for, the stream is never collected and the client never gets
     * its stream credit back.
     */
    @ParameterizedTest
    @MethodSource("newSslTaskExecutors")
    @Timeout(60)
    public void testStopSendingAfterChannelClosedReturnsStreamCredit(Executor executor) throws Throwable {
        Channel server = null;
        Channel channel = null;
        try (DroppingRelay relay = new DroppingRelay()) {
            BlockingQueue<QuicStreamChannel> closed = new LinkedBlockingQueue<>();
            BlockingQueue<QuicStreamChannel> barriers = new LinkedBlockingQueue<>();
            BlockingQueue<Object> limitChanged = new LinkedBlockingQueue<>();
            ChannelInboundHandlerAdapter responder = new ChannelInboundHandlerAdapter() {
                @Override
                public boolean isSharable() {
                    return true;
                }

                @Override
                public void handlerAdded(ChannelHandlerContext ctx) {
                    QuicStreamChannel stream = (QuicStreamChannel) ctx.channel();
                    if (stream.type() == QuicStreamType.UNIDIRECTIONAL) {
                        barriers.add(stream);
                    }
                }

                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    ReferenceCountUtil.release(msg);
                }

                @Override
                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                    if (evt == ChannelInputShutdownReadComplete.INSTANCE &&
                            ((QuicStreamChannel) ctx.channel()).type() == QuicStreamType.BIDIRECTIONAL) {
                        // The response never reaches the client, so it stays unacknowledged.
                        relay.dropToClient = true;
                        QuicStreamChannel stream = (QuicStreamChannel) ctx.channel();
                        stream.closeFuture().addListener(f -> closed.add(stream));
                        ctx.writeAndFlush(new DefaultQuicStreamFrame(
                                Unpooled.directBuffer(8).writeZero(8), true));
                    }
                    ctx.fireUserEventTriggered(evt);
                }
            };
            server = QuicTestUtils.newServer(QuicTestUtils.newQuicServerBuilder(executor)
                    .initialMaxStreamsBidirectional(2), InsecureQuicTokenHandler.INSTANCE,
                    QuicTestUtils.NOOP_HANDLER, responder);
            relay.start((InetSocketAddress) server.localAddress());
            channel = QuicTestUtils.newClient(executor);
            QuicChannel quicChannel = QuicTestUtils.newQuicChannelBootstrap(channel)
                    .handler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                            if (evt == QuicStreamLimitChangedEvent.INSTANCE) {
                                limitChanged.add(evt);
                            }
                            ctx.fireUserEventTriggered(evt);
                        }
                    })
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(relay.address())
                    .connect()
                    .get(5, TimeUnit.SECONDS);

            for (int i = 0; i < 3; i++) {
                QuicStreamChannel clientStream = createStreamWithin(quicChannel, limitChanged, 8, i);
                clientStream.writeAndFlush(new DefaultQuicStreamFrame(Unpooled.directBuffer(1).writeZero(1), true))
                        .sync();
                QuicStreamChannel serverStream = closed.poll(5, TimeUnit.SECONDS);
                assertThat(serverStream).as("server stream %d closed", i).isNotNull();

                clientStream.shutdownInput((int) ERROR_CODE).sync();
                // The STOP_SENDING must arrive while the response is still unacknowledged. The data of a
                // unidirectional stream is sent after it, so once the server has seen that stream, it has also
                // processed the STOP_SENDING.
                QuicStreamChannel barrier = quicChannel.createStream(QuicStreamType.UNIDIRECTIONAL,
                        new ChannelInboundHandlerAdapter()).get(5, TimeUnit.SECONDS);
                barrier.writeAndFlush(new DefaultQuicStreamFrame(Unpooled.directBuffer(1).writeZero(1), true))
                        .sync();
                assertThat(barriers.poll(5, TimeUnit.SECONDS)).as("barrier stream %d", i).isNotNull();
                relay.dropToClient = false;
            }

            quicChannel.close().sync();
        } finally {
            QuicTestUtils.closeIfNotNull(channel);
            QuicTestUtils.closeIfNotNull(server);
            shutdown(executor);
        }
    }

    /**
     * Creates a stream, waiting for the server to give stream credit back if there is none left.
     */
    private static QuicStreamChannel createStreamWithin(QuicChannel quicChannel, BlockingQueue<Object> limitChanged,
                                                        int seconds, int index) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        Throwable last;
        do {
            // Cleared before the attempt, so a change that comes in while it fails is not missed.
            limitChanged.clear();
            Future<QuicStreamChannel> f = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new ChannelInboundHandlerAdapter()).await();
            if (f.isSuccess()) {
                return f.getNow();
            }
            last = f.cause();
        } while (limitChanged.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS) != null);
        throw new AssertionError("could not open stream " + index + " within " + seconds +
                "s: the server did not give the stream credit back", last);
    }

    private static QuicChannel connect(Channel channel, Channel server) throws Exception {
        return QuicTestUtils.newQuicChannelBootstrap(channel)
                .handler(new ChannelInboundHandlerAdapter())
                .streamHandler(new ChannelInboundHandlerAdapter())
                .remoteAddress(server.localAddress())
                .connect()
                .get(5, TimeUnit.SECONDS);
    }

    private static void assertStopped(Object event) {
        assertThat(event).isInstanceOf(QuicStreamStoppedEvent.class);
        assertThat(((QuicStreamStoppedEvent) event).applicationProtocolCode()).isEqualTo(ERROR_CODE);
    }

    /**
     * Keeps the server side of the streams once they are opened, so the tests can act on them, and adds their events
     * to a queue: the exceptions, the {@link QuicStreamStoppedEvent}, the {@link ChannelOutputShutdownEvent} and the
     * {@link ChannelInputShutdownReadComplete}.
     */
    private static final class ServerStreams extends ChannelInboundHandlerAdapter {
        private final BlockingQueue<QuicStreamChannel> streams = new LinkedBlockingQueue<>();
        final BlockingQueue<Object> events = new LinkedBlockingQueue<>();

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

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof QuicStreamStoppedEvent || evt == ChannelOutputShutdownEvent.INSTANCE
                    || evt == ChannelInputShutdownReadComplete.INSTANCE) {
                events.add(evt);
            }
            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            events.add(cause);
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

        Object next() throws InterruptedException {
            return events.poll(5, TimeUnit.SECONDS);
        }
    }
}
