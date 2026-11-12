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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
                    new ChannelInboundHandlerAdapter()).get();
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
                    .create().get();
            QuicStreamChannel serverStream = serverStreams.open(clientStream);

            ByteBuf first = Unpooled.directBuffer(4 * WINDOW).writeZero(4 * WINDOW);
            ByteBuf second = Unpooled.directBuffer(8).writeZero(8);
            ChannelFuture firstWrite = serverStream.write(first);
            ChannelFuture secondWrite = serverStream.writeAndFlush(second);
            firstWrite.addListener(f -> serverStreams.events.add(f.isSuccess() ? "written" : f.cause()));
            assertThat(serverStream.eventLoop().submit(serverStream::isWritable).get()).isFalse();

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
                    new ChannelInboundHandlerAdapter()).get();
            QuicStreamChannel serverStream = serverStreams.open(clientStream);

            clientStream.shutdownOutput().sync();

            // The end of the input of the server does not stop its output.
            assertThat(serverStreams.next()).isSameAs(ChannelInputShutdownReadComplete.INSTANCE);
            assertThat(serverStream.isOutputShutdown()).isFalse();
            serverStream.writeAndFlush(Unpooled.directBuffer().writeZero(8)).sync();
            assertThat(serverStreams.events).isEmpty();

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
