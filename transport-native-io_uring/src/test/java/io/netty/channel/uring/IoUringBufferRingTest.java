/*
 * Copyright 2025 The Netty Project
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
package io.netty.channel.uring;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.WrappedByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.unix.Buffer;
import io.netty.util.NetUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class IoUringBufferRingTest {
    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
        assumeTrue(IoUring.isRegisterBufferRingSupported());
    }

    @Test
    public void testRegister() {
        // using cqeSize on purpose NOT a power of 2
        RingBuffer ringBuffer = Native.createRingBuffer(8, 15, 0);
        try {
            int ringFd = ringBuffer.fd();
            long ioUringBufRingAddr = Native.ioUringRegisterBufRing(ringFd, 4, (short) 1, 0);
            assertThat(ioUringBufRingAddr)
                    .as("ioUringSetupBufRing result must great than 0, but now result is %d", ioUringBufRingAddr)
                    .isGreaterThan(0);
            int freeRes = Native.ioUringUnRegisterBufRing(ringFd, ioUringBufRingAddr, 4, (short) 1);
            assertEquals(
                    0,
                    freeRes,
                    "ioUringFreeBufRing result must be 0, but now result is " + freeRes
            );
            // let io_uring to "fix" it
            assertEquals(16, ringBuffer.ioUringCompletionQueue().ringCapacity);
        } finally {
            ringBuffer.close();
        }
    }

    private static ByteBuf unwrapLeakAware(ByteBuf buf) {
        // If its a sub-type of WrappedByteBuf we know its because it was wrapped for leak-detection.
        if (buf instanceof WrappedByteBuf) {
            return buf.unwrap();
        }
        return buf;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testProviderBufferRead(boolean incremental) throws InterruptedException {
        if (incremental) {
            assumeTrue(IoUring.isRegisterBufferRingIncSupported());
        }
        final BlockingQueue<ByteBuf> bufferSyncer = new LinkedBlockingQueue<>();
        IoUringIoHandlerConfig ioUringIoHandlerConfiguration = new IoUringIoHandlerConfig();
        IoUringBufferRingConfig bufferRingConfig =
                IoUringBufferRingConfig.builder()
                        .bufferGroupId((short) 1)
                        .bufferRingSize((short) 2)
                        .batchSize(2).incremental(incremental)
                        .allocator(new IoUringFixedBufferRingAllocator(1024))
                        .batchAllocation(false)
                        .build();

        IoUringBufferRingConfig bufferRingConfig1 =
                IoUringBufferRingConfig.builder()
                        .bufferGroupId((short) 2)
                        .bufferRingSize((short) 16)
                        .batchSize(8)
                        .incremental(incremental)
                        .allocator(new IoUringFixedBufferRingAllocator(1024))
                        .batchAllocation(true)
                        .build();
        ioUringIoHandlerConfiguration.setBufferRingConfig(bufferRingConfig, bufferRingConfig1);

        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1,
                IoUringIoHandler.newFactory(ioUringIoHandlerConfiguration)
        );
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.channel(IoUringServerSocketChannel.class);

        String randomString = UUID.randomUUID().toString();
        int randomStringLength = randomString.length();

        ArrayBlockingQueue<IoUringBufferRingExhaustedEvent> eventSyncer = new ArrayBlockingQueue<>(1);

        Channel serverChannel = serverBootstrap.group(group)
                .childHandler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        bufferSyncer.offer((ByteBuf) msg);
                    }

                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                        if (evt instanceof IoUringBufferRingExhaustedEvent) {
                            eventSyncer.add((IoUringBufferRingExhaustedEvent) evt);
                        }
                    }
                })
                .childOption(IoUringChannelOption.IO_URING_BUFFER_GROUP_ID, bufferRingConfig.bufferGroupId())
                .bind(NetUtil.LOCALHOST, 0)
                .syncUninterruptibly().channel();

        Bootstrap clientBoostrap = new Bootstrap();
        clientBoostrap.group(group)
                .channel(IoUringSocketChannel.class)
                .handler(new ChannelInboundHandlerAdapter());
        ChannelFuture channelFuture = clientBoostrap.connect(serverChannel.localAddress()).syncUninterruptibly();
        assertTrue(channelFuture.isSuccess());
        Channel clientChannel = channelFuture.channel();

        //is provider buffer read?
        ByteBuf writeBuffer = Unpooled.directBuffer(randomStringLength);
        ByteBufUtil.writeAscii(writeBuffer, randomString);
        ByteBuf userspaceIoUringBufferElement1 = sendAndRecvMessage(clientChannel, writeBuffer, bufferSyncer);
        ByteBuf userspaceIoUringBufferElement2 = sendAndRecvMessage(clientChannel, writeBuffer, bufferSyncer);
        ByteBuf readBuffer = sendAndRecvMessage(clientChannel, writeBuffer, bufferSyncer);
        readBuffer.release();

        // Now we release the buffer and so put it back into the buffer ring.
        userspaceIoUringBufferElement1.release();
        userspaceIoUringBufferElement2.release();

        readBuffer = sendAndRecvMessage(clientChannel, writeBuffer, bufferSyncer);
        readBuffer.release();

        // The next buffer is expected to be provided out of the ring again.
        readBuffer = sendAndRecvMessage(clientChannel, writeBuffer, bufferSyncer);
        readBuffer.release();

        writeBuffer.release();

        serverChannel.close().syncUninterruptibly();
        clientChannel.close().syncUninterruptibly();
        group.shutdownGracefully();
    }

    static boolean recvsendBundleEnabled() {
        return IoUring.isRecvsendBundleEnabled();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @EnabledIf("recvsendBundleEnabled")
    public void testProviderBufferReadWithRecvsendBundle(boolean incremental) throws InterruptedException {
        // See https://lore.kernel.org/io-uring/184f9f92-a682-4205-a15d-89e18f664502@kernel.dk/T/#u
        assumeTrue(IoUring.isRecvMultishotEnabled());

        if (incremental) {
            assumeTrue(IoUring.isRegisterBufferRingIncSupported());
        }
        int bufferRingChunkSize = 8;
        IoUringIoHandlerConfig ioUringIoHandlerConfiguration = new IoUringIoHandlerConfig();
        IoUringBufferRingConfig bufferRingConfig = new IoUringBufferRingConfig(
                // let's use a small chunkSize so we are sure a recv will span multiple buffers.
                (short) 1, (short) 16, 8, 16 * 16,
                incremental, new IoUringFixedBufferRingAllocator(bufferRingChunkSize));

        ioUringIoHandlerConfiguration.setBufferRingConfig(bufferRingConfig);

        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1,
                IoUringIoHandler.newFactory(ioUringIoHandlerConfiguration)
        );
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.channel(IoUringServerSocketChannel.class);

        final BlockingQueue<ByteBuf> buffers = new LinkedBlockingQueue<>();
        Channel serverChannel = serverBootstrap.group(group)
                .childHandler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        buffers.offer((ByteBuf) msg);
                    }
                })
                .childOption(IoUringChannelOption.IO_URING_BUFFER_GROUP_ID, (short) 1)
                .bind(new InetSocketAddress(0))
                .syncUninterruptibly().channel();

        Bootstrap clientBoostrap = new Bootstrap();
        clientBoostrap.group(group)
                .channel(IoUringSocketChannel.class)
                .handler(new ChannelInboundHandlerAdapter());
        ChannelFuture channelFuture = clientBoostrap.connect(serverChannel.localAddress()).syncUninterruptibly();
        assertTrue(channelFuture.isSuccess());
        Channel clientChannel = channelFuture.channel();

        // Create a buffer that will span multiple buffers that are used out of the buffer ring.
        ByteBuf writeBuffer = Unpooled.directBuffer(bufferRingChunkSize * 16);
        CompositeByteBuf received = Unpooled.compositeBuffer();
        try {
            // Fill the buffer with something so we can assert if the received bytes are the same.
            for (int i = 0; i < writeBuffer.capacity(); i++) {
                writeBuffer.writeByte((byte) i);
            }
            clientChannel.writeAndFlush(writeBuffer.retainedDuplicate()).syncUninterruptibly();

            // Aggregate all received buffers until we received everything.
            do {
                ByteBuf buffer = buffers.take();
                received.addComponent(true, buffer);
            } while (received.readableBytes() != writeBuffer.readableBytes());

            assertEquals(writeBuffer, received);
            serverChannel.close().syncUninterruptibly();
            clientChannel.close().syncUninterruptibly();
            group.shutdownGracefully();
            assertTrue(buffers.isEmpty());
        } finally {
            writeBuffer.release();
            received.release();
        }
    }

    @Test
    public void testUseBufferClampsReadLargerThanEntryCapacity() throws Exception {
        // Regression test for a bundle completion (IORING_RECVSEND_BUNDLE) reporting a total byte count
        // that spans more than a single ring entry can hold: useBuffer(...) must clamp instead of throwing
        // an IndexOutOfBoundsException, leaving the remainder for the caller to fetch from the next bid.
        int entrySize = 8;
        short entries = 4;
        RingBuffer ringBuffer = Native.createRingBuffer(8, 0);
        try {
            int ringFd = ringBuffer.fd();
            long ioUringBufRingAddr = Native.ioUringRegisterBufRing(ringFd, entries, (short) 1, 0);
            assumeThat(ioUringBufRingAddr)
                    .as("ioUringSetupBufRing result must be greater than 0, but now result is %d", ioUringBufRingAddr)
                    .isGreaterThan(0);
            IoUringBufferRing bufferRing = new IoUringBufferRing(ringFd,
                    Buffer.wrapMemoryAddressWithNativeOrder(ioUringBufRingAddr, Native.ioUringBufRingSize(entries)),
                    entries, 2, (short) 1, false,
                    new IoUringFixedBufferRingAllocator(entrySize), false);
            bufferRing.initialize();
            // close() unregisters the ring and releases the buffers it still owns; unregistering by hand
            // leaks every entry that initialize() filled but the test did not use.
            try {
                int bundleTotal = entrySize * 2 + 1;
                ByteBuf buffer = bufferRing.useBuffer((short) 0, bundleTotal, true);
                try {
                    assertEquals(entrySize, buffer.readableBytes());
                } finally {
                    buffer.release();
                }
            } finally {
                bufferRing.close();
            }
        } finally {
            ringBuffer.close();
        }
    }

    @Test
    public void testNextBidWrapsCorrectlyWithNonPowerOfTwoAllocatedBuffers() throws Exception {
        // Regression test: allocatedBuffers grows by batchSize increments and so is not guaranteed to be a
        // power of two (e.g. ringSize=16, batchSize=4 goes 4 -> 8 -> 12 -> 16). nextBid(...) must wrap using
        // the real allocated buffer count and not a bitmask, which is only correct for power-of-two counts.
        short entries = 16;
        RingBuffer ringBuffer = Native.createRingBuffer(8, 0);
        try {
            int ringFd = ringBuffer.fd();
            long ioUringBufRingAddr = Native.ioUringRegisterBufRing(ringFd, entries, (short) 1, 0);
            assumeThat(ioUringBufRingAddr)
                    .as("ioUringSetupBufRing result must be greater than 0, but now result is %d", ioUringBufRingAddr)
                    .isGreaterThan(0);
            IoUringBufferRing bufferRing = new IoUringBufferRing(ringFd,
                    Buffer.wrapMemoryAddressWithNativeOrder(ioUringBufRingAddr, Native.ioUringBufRingSize(entries)),
                    entries, 4, (short) 1, false,
                    new IoUringFixedBufferRingAllocator(64), false);
            try {
                bufferRing.initialize();
                assertEquals(4, bufferRing.allocatedBuffers());

                assertEquals((short) 4, bufferRing.nextBid((short) 3, 12));
                assertEquals((short) 0, bufferRing.nextBid((short) 11, 12));
            } finally {
                // Releases the outstanding buffers and unregisters the ring.
                bufferRing.close();
            }
        } finally {
            ringBuffer.close();
        }
    }

    @Test
    public void testNextBidUsesAllocatedBuffersSnapshotBeforeRingGrowsMidBundle() throws Exception {
        // Regression test: useBuffer(...) can grow the ring (change allocatedBuffers) as a side effect once the
        // last currently-posted buffer is consumed. When walking a multi-entry RECVSEND_BUNDLE completion, the
        // wrap-around for the *next* bid must be computed using the ring size as it was before that growth (i.e.
        // as the kernel observed it when producing the bundle), not the grown size, or the caller will jump to
        // the wrong ring slot. See AbstractIoUringStreamChannel, which snapshots allocatedBuffers() before
        // calling useBuffer(...) and passes it to nextBid(...) afterwards.
        short entries = 16;
        int batchSize = 8;
        RingBuffer ringBuffer = Native.createRingBuffer(8, 0);
        try {
            int ringFd = ringBuffer.fd();
            long ioUringBufRingAddr = Native.ioUringRegisterBufRing(ringFd, entries, (short) 1, 0);
            assumeThat(ioUringBufRingAddr)
                    .as("ioUringSetupBufRing result must be greater than 0, but now result is %d", ioUringBufRingAddr)
                    .isGreaterThan(0);
            IoUringBufferRing bufferRing = new IoUringBufferRing(ringFd,
                    Buffer.wrapMemoryAddressWithNativeOrder(ioUringBufRingAddr, Native.ioUringBufRingSize(entries)),
                    entries, batchSize, (short) 1, false,
                    new IoUringFixedBufferRingAllocator(64), true);
            try {
                bufferRing.initialize();
                assertEquals(8, bufferRing.allocatedBuffers());

                // Simulate the ENOBUFS handling in AbstractIoUringStreamChannel: signal that we should expand
                // the ring the next time all currently posted buffers have been consumed.
                assertTrue(bufferRing.expand());

                // Consume bids 0..6, mimicking a bundle that walks through (almost) a whole lap of the ring.
                for (short bid = 0; bid < 7; bid++) {
                    bufferRing.useBuffer(bid, 1, false).release();
                    assertEquals(8, bufferRing.allocatedBuffers());
                }

                // This is exactly what AbstractIoUringStreamChannel does: snapshot the ring size *before*
                // calling useBuffer(...) for the last bid of the bundle.
                int allocatedBuffersBeforeUseBuffer = bufferRing.allocatedBuffers();
                assertEquals(8, allocatedBuffersBeforeUseBuffer);

                // Consuming the last (8th) posted buffer causes the ring to grow, since we called expand() above.
                bufferRing.useBuffer((short) 7, 1, false).release();
                assertEquals(16, bufferRing.allocatedBuffers());

                // Using the pre-growth snapshot gives the correct wrap-around: bid 7 was the last of the
                // original 8 posted buffers, so the bundle continues at bid 0.
                assertEquals((short) 0, bufferRing.nextBid((short) 7, allocatedBuffersBeforeUseBuffer));
                // Using the already-grown size (the bug this guards against) would incorrectly jump to bid 8,
                // a slot that was never part of this bundle and belongs to a buffer the kernel never touched.
                assertEquals((short) 8, bufferRing.nextBid((short) 7, bufferRing.allocatedBuffers()));
            } finally {
                bufferRing.close();
            }
        } finally {
            ringBuffer.close();
        }
    }

    private ByteBuf sendAndRecvMessage(Channel clientChannel, ByteBuf writeBuffer, BlockingQueue<ByteBuf> bufferSyncer)
            throws InterruptedException {
        //retain the buffer to assert
        clientChannel.writeAndFlush(writeBuffer.retainedDuplicate()).sync();
        ByteBuf readBuffer = bufferSyncer.take();
        assertEquals(writeBuffer.readableBytes(), readBuffer.readableBytes());
        assertTrue(ByteBufUtil.equals(writeBuffer, readBuffer));
        return readBuffer;
    }

    @Test
    public void testCloseEventLoopGroupWhileConnected() throws Exception {
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1,
                IoUringIoHandler.newFactory()
        );
        try {
            final BlockingQueue<Channel> acceptedChannels = new LinkedBlockingQueue<>();
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.channel(IoUringServerSocketChannel.class);
            Channel serverChannel = serverBootstrap.group(group)
                    .childHandler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            acceptedChannels.add(ctx.channel());
                        }
                    })
                    .bind(new InetSocketAddress(0))
                    .syncUninterruptibly().channel();

            Bootstrap clientBoostrap = new Bootstrap();
            clientBoostrap.group(group)
                    .channel(IoUringSocketChannel.class)
                    .handler(new ChannelInboundHandlerAdapter());
            ChannelFuture channelFuture = clientBoostrap.connect(serverChannel.localAddress());
            Channel clientChannel = channelFuture.sync().channel();

            group.shutdownGracefully().syncUninterruptibly();
            clientChannel.closeFuture().sync();
            serverChannel.closeFuture().sync();
            acceptedChannels.take().closeFuture().sync();
            assertTrue(acceptedChannels.isEmpty());
        } catch (Throwable t) {
            if (!group.isShutdown()) {
                group.shutdownGracefully().syncUninterruptibly();
            }
            throw t;
        }
    }
}
