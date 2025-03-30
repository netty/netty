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
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.MultiThreadIoEventLoopGroup;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
            assumeTrue(
                    ioUringBufRingAddr > 0,
                    "ioUringSetupBufRing result must great than 0, but now result is " + ioUringBufRingAddr);
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testProviderBufferRead(boolean incremental) throws InterruptedException {
        if (incremental) {
            assumeTrue(IoUring.isRegisterBufferRingIncSupported());
        }
        final BlockingQueue<ByteBuf> bufferSyncer = new LinkedBlockingQueue<>();
        IoUringIoHandlerConfig ioUringIoHandlerConfiguration = new IoUringIoHandlerConfig();
        IoUringBufferRingConfig bufferRingConfig = new IoUringBufferRingConfig(
                (short) 1, (short) 2, 2, 2 * 16, incremental, new IoUringFixedBufferRingAllocator(1024));

        IoUringBufferRingConfig bufferRingConfig1 = new IoUringBufferRingConfig(
                (short) 2, (short) 16, 8, 16 * 16, incremental, new IoUringFixedBufferRingAllocator(1024)
        );
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
        assumeTrue(channelFuture.isSuccess());
        Channel clientChannel = channelFuture.channel();

        //is provider buffer read?
        ByteBuf writeBuffer = Unpooled.directBuffer(randomStringLength);
        ByteBufUtil.writeAscii(writeBuffer, randomString);
        ByteBuf userspaceIoUringBufferElement1 = sendAndRecvMessage(clientChannel, writeBuffer, bufferSyncer);
        if (incremental) {
            // Need to unwrap as its a slice.
            assertInstanceOf(IoUringBufferRing.IoUringBufferRingByteBuf.class, userspaceIoUringBufferElement1.unwrap());
        } else {
            assertInstanceOf(IoUringBufferRing.IoUringBufferRingByteBuf.class, userspaceIoUringBufferElement1);
        }
        ByteBuf userspaceIoUringBufferElement2 = sendAndRecvMessage(clientChannel, writeBuffer, bufferSyncer);
        if (incremental) {
            // Need to unwrap as its a slice.
            assertInstanceOf(IoUringBufferRing.IoUringBufferRingByteBuf.class, userspaceIoUringBufferElement2.unwrap());
        } else {
            assertInstanceOf(IoUringBufferRing.IoUringBufferRingByteBuf.class, userspaceIoUringBufferElement2);
        }
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
        assumeTrue(IoUring.isRecvMultishotEnabled(),
                "Only yields expected test results when using multishot atm");
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
        assumeTrue(channelFuture.isSuccess());
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
