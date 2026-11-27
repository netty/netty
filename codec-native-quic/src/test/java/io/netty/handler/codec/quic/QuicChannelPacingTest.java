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

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.ImmediateExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class QuicChannelPacingTest extends AbstractQuicTest {
    private static final int DATAGRAM_SIZE = 2048;
    // More than the stream flow control window, so the transfer also depends on flow control updates.
    // We don't limit the pacing rate to force longer gaps between packets, as with BBR that also limits the
    // congestion window based on the RTT, which over loopback would leave less than a single packet.
    private static final int TRANSFER_SIZE = 1024 * 1024;

    private static final InetSocketAddress SERVER_ADDRESS = new InetSocketAddress(NetUtil.LOCALHOST4, 4433);
    private static final InetSocketAddress CLIENT_ADDRESS = new InetSocketAddress(NetUtil.LOCALHOST4, 5555);
    // Simulated one-way delay, so the RTT is large enough for the max pacing rate to not limit the congestion
    // window to less than a single packet.
    private static final long ONE_WAY_DELAY_NANOS = TimeUnit.MILLISECONDS.toNanos(10);
    private static final int DATAGRAMS = 2000;
    private static final int DATAGRAM_LENGTH = 1000;

    enum Transport {
        NIO,
        EPOLL,
        EPOLL_SEGMENTED
    }

    @ParameterizedTest
    @EnumSource(Transport.class)
    public void testEchoWithPacing(Transport transport) throws Throwable {
        assumeTrue(transport == Transport.NIO || Epoll.isAvailable());

        // Prepare the data up front and only use bulk operations on the buffers, as with paranoid leak detection
        // each operation on a buffer records a stack trace. Doing this byte by byte would take long enough for the
        // connection to hit its idle timeout.
        byte[] data = new byte[TRANSFER_SIZE];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }

        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, transport == Transport.NIO ?
                NioIoHandler.newFactory() : EpollIoHandler.newFactory());
        SegmentedDatagramPacketAllocator allocator = transport == Transport.EPOLL_SEGMENTED ?
                EpollQuicUtils.newSegmentedAllocator(10) : SegmentedDatagramPacketAllocator.NONE;
        AtomicReference<Throwable> serverErrorRef = new AtomicReference<>();
        AtomicReference<Throwable> clientErrorRef = new AtomicReference<>();
        Promise<Void> receivedAllPromise = ImmediateEventExecutor.INSTANCE.newPromise();
        QuicChannelValidationHandler serverHandler = new QuicChannelValidationHandler();
        QuicChannelValidationHandler clientHandler = new QuicChannelValidationHandler();
        Channel server = null;
        Channel client = null;
        try {
            ChannelHandler serverCodec = QuicTestUtils.newQuicServerBuilder(ImmediateExecutor.INSTANCE)
                    .congestionControlAlgorithm(QuicCongestionControlAlgorithm.BBR)
                    .pacing(true)
                    .option(QuicChannelOption.SEGMENTED_DATAGRAM_PACKET_ALLOCATOR, allocator)
                    .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                    .handler(serverHandler)
                    .streamHandler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            // Echo back everything.
                            ctx.write(msg);
                        }

                        @Override
                        public void channelReadComplete(ChannelHandlerContext ctx) {
                            ctx.flush();
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            serverErrorRef.compareAndSet(null, cause);
                        }

                        @Override
                        public boolean isSharable() {
                            return true;
                        }
                    }).build();
            server = newBootstrap(group, transport).handler(serverCodec)
                    .bind(new InetSocketAddress(NetUtil.LOCALHOST4, 0)).sync().channel();

            ChannelHandler clientCodec = QuicTestUtils.newQuicClientBuilder(ImmediateExecutor.INSTANCE)
                    .congestionControlAlgorithm(QuicCongestionControlAlgorithm.BBR)
                    .pacing(true)
                    .build();
            client = newBootstrap(group, transport).handler(clientCodec)
                    .bind(new InetSocketAddress(NetUtil.LOCALHOST4, 0)).sync().channel();

            QuicChannel quicChannel = QuicChannel.newBootstrap(client)
                    .option(QuicChannelOption.SEGMENTED_DATAGRAM_PACKET_ALLOCATOR, allocator)
                    .handler(clientHandler)
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(server.localAddress())
                    .connect()
                    .get();

            QuicStreamChannel stream = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new ChannelInboundHandlerAdapter() {
                        private int received;

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            ByteBuf buffer = (ByteBuf) msg;
                            byte[] bytes = new byte[buffer.readableBytes()];
                            buffer.readBytes(bytes);
                            buffer.release();
                            if (received + bytes.length > data.length) {
                                receivedAllPromise.tryFailure(new AssertionError(
                                        "Received more than expected: " + (received + bytes.length)));
                                return;
                            }
                            for (int i = 0; i < bytes.length; i++, received++) {
                                if (bytes[i] != data[received]) {
                                    receivedAllPromise.tryFailure(new AssertionError(
                                            "Unexpected byte at index " + received + ": " + bytes[i] + " != " +
                                                    data[received]));
                                    return;
                                }
                            }
                            if (received == TRANSFER_SIZE) {
                                receivedAllPromise.trySuccess(null);
                            }
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            clientErrorRef.compareAndSet(null, cause);
                            receivedAllPromise.tryFailure(cause);
                        }
                    }).get();

            stream.writeAndFlush(stream.alloc().directBuffer(TRANSFER_SIZE).writeBytes(data)).sync();
            receivedAllPromise.sync();

            stream.close().sync();
            // Closing must not be held back by pacing.
            quicChannel.close().sync();

            throwIfNotNull(serverErrorRef);
            throwIfNotNull(clientErrorRef);
            serverHandler.assertState();
            clientHandler.assertState();
            assertThat(quicChannel.isOpen()).isFalse();
        } finally {
            QuicTestUtils.closeIfNotNull(server);
            QuicTestUtils.closeIfNotNull(client);
            group.shutdownGracefully();
        }
    }

    @Test
    public void testDatagramsAreNotDroppedWhilePaced() throws Throwable {
        // quiche only provides send times on these platforms, and without pacing the max pacing rate limits the
        // congestion window so that datagrams are dropped once it is used up.
        assumeTrue(!PlatformDependent.isOsx() && !PlatformDependent.isWindows());

        AtomicInteger received = new AtomicInteger();
        ChannelHandler serverCodec = QuicTestUtils.newQuicServerBuilder(ImmediateExecutor.INSTANCE)
                .congestionControlAlgorithm(QuicCongestionControlAlgorithm.BBR)
                .pacing(true)
                // Low enough to regularly have to wait more than a millisecond before the next packet can be sent.
                .maxPacingRate(20)
                // A small send queue, so it is full most of the time while we have to wait.
                .datagram(DATAGRAMS, 16)
                .option(QuicChannelOption.SEGMENTED_DATAGRAM_PACKET_ALLOCATOR, SegmentedDatagramPacketAllocator.NONE)
                .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                .handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        if (msg instanceof ByteBuf) {
                            received.incrementAndGet();
                        }
                        ReferenceCountUtil.release(msg);
                    }

                    @Override
                    public boolean isSharable() {
                        return true;
                    }
                })
                .streamHandler(new ChannelInboundHandlerAdapter())
                .build();
        ChannelHandler clientCodec = QuicTestUtils.newQuicClientBuilder(ImmediateExecutor.INSTANCE)
                .congestionControlAlgorithm(QuicCongestionControlAlgorithm.BBR)
                .pacing(true)
                .maxPacingRate(20)
                // Datagrams are also dropped if the congestion window is used up, so ensure this does not happen.
                .initialCongestionWindowPackets(1000)
                .datagram(DATAGRAMS, 16)
                .build();

        // Use EmbeddedChannels instead of sockets, so we can simulate a delay between both peers.
        EmbeddedChannel server = new EmbeddedChannel(serverCodec);
        EmbeddedChannel client = new EmbeddedChannel(clientCodec);
        Queue<InFlight> inFlight = new ArrayDeque<>();
        try {
            Future<QuicChannel> connectFuture = QuicChannel.newBootstrap(client)
                    .handler(new ChannelInboundHandlerAdapter())
                    .streamHandler(new ChannelInboundHandlerAdapter())
                    .localAddress(CLIENT_ADDRESS)
                    .remoteAddress(SERVER_ADDRESS)
                    .connect();
            pumpUntil(client, server, inFlight, connectFuture::isDone, () -> "connect");
            QuicChannel quicChannel = connectFuture.getNow();
            assertThat(quicChannel).as("connect failed: %s", connectFuture.cause()).isNotNull();

            for (int i = 0; i < DATAGRAMS; i++) {
                quicChannel.write(quicChannel.alloc().directBuffer(DATAGRAM_LENGTH).writeZero(DATAGRAM_LENGTH));
            }
            quicChannel.flush();
            // Before, all datagrams that did not fit into the send queue while we had to wait were dropped.
            pumpUntil(client, server, inFlight, () -> received.get() == DATAGRAMS,
                    () -> "all datagrams received, got " + received.get() + " of " + DATAGRAMS);

            Future<Void> closeFuture = quicChannel.close();
            pumpUntil(client, server, inFlight, closeFuture::isDone, () -> "close");
        } finally {
            for (InFlight f : inFlight) {
                f.packet.release();
            }
            client.finishAndReleaseAll();
            server.finishAndReleaseAll();
        }
        assertThat(received.get()).isEqualTo(DATAGRAMS);
    }

    private static final class InFlight {
        final long deliverAt;
        final EmbeddedChannel to;
        final DatagramPacket packet;

        InFlight(long deliverAt, EmbeddedChannel to, DatagramPacket packet) {
            this.deliverAt = deliverAt;
            this.to = to;
            this.packet = packet;
        }
    }

    /**
     * Runs both channels and moves the packets that they write to each other, each after a delay, until the
     * condition is met.
     */
    private static void pumpUntil(EmbeddedChannel client, EmbeddedChannel server, Queue<InFlight> inFlight,
                                  BooleanSupplier condition, Supplier<String> description) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() - deadline > 0) {
                throw new AssertionError("Timed out waiting for: " + description.get());
            }
            client.runPendingTasks();
            client.runScheduledPendingTasks();
            server.runPendingTasks();
            server.runScheduledPendingTasks();
            long now = System.nanoTime();
            for (Object msg; (msg = client.readOutbound()) != null;) {
                inFlight.add(new InFlight(now + ONE_WAY_DELAY_NANOS, server,
                        new DatagramPacket(((DatagramPacket) msg).content(), SERVER_ADDRESS, CLIENT_ADDRESS)));
            }
            for (Object msg; (msg = server.readOutbound()) != null;) {
                inFlight.add(new InFlight(now + ONE_WAY_DELAY_NANOS, client,
                        new DatagramPacket(((DatagramPacket) msg).content(), CLIENT_ADDRESS, SERVER_ADDRESS)));
            }
            // All packets have the same delay, so they are ordered by delivery time.
            while (!inFlight.isEmpty() && inFlight.peek().deliverAt - now <= 0) {
                InFlight f = inFlight.poll();
                f.to.writeInbound(f.packet);
            }
        }
    }

    private static Bootstrap newBootstrap(EventLoopGroup group, Transport transport) {
        Bootstrap bs = new Bootstrap().group(group);
        if (transport == Transport.NIO) {
            bs.channel(NioDatagramChannel.class)
                    .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(DATAGRAM_SIZE));
        } else {
            bs.channel(EpollDatagramChannel.class)
                    // Use recvmmsg when possible.
                    .option(EpollChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE, DATAGRAM_SIZE)
                    .option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(DATAGRAM_SIZE * 8));
        }
        return bs;
    }

    private static void throwIfNotNull(AtomicReference<Throwable> errorRef) throws Throwable {
        Throwable cause = errorRef.get();
        if (cause != null) {
            throw cause;
        }
    }
}
