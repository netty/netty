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
package io.netty.channel.uring;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.IoHandle;
import io.netty.channel.IoHandler;
import io.netty.channel.IoHandlerContext;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.IoOps;
import io.netty.channel.IoRegistration;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.unix.Buffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.channel.uring.IoUringRefCntZeroAwaiter.awaitRefCntZero;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
public class IoUringSendmsgZCIovTest {

    private static final int SENDMSG_ZC_COUNT = 2 * Native.MAX_SKB_FRAGS;
    private static final int THRESHOLD = 1024;

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
        assumeTrue(IoUring.isSendmsgZcSupported());
    }

    @Test
    public void testSendmsgZcPollutesNextWritev() throws Exception {
        ArrayList<IoUringIoOps> ioOps = new ArrayList<>();
        IoHandlerFactory factory = executor ->
            new RecordingIoUringIoHandler(IoUringIoHandler.newFactory().newHandler(executor), ioOps);
        MultiThreadIoEventLoopGroup ioEventLoopGroup = new MultiThreadIoEventLoopGroup(1, factory);
        try {
            prepChannels(ioEventLoopGroup).join();
            assertEquals(2, ioOps.size());
            IoUringIoOps sendmsgzcOps = ioOps.get(0);
            IoUringIoOps writevOps = ioOps.get(1);
            int sendmsgIovLength = sendmsgIovLength(sendmsgzcOps);
            int writevIovLength = writevIovLength(writevOps);
            assertEquals(SENDMSG_ZC_COUNT, sendmsgIovLength);
            assertEquals(2, writevIovLength);
        } finally {
            ioEventLoopGroup.shutdownGracefully();
        }
    }

    @Test
    public void testSmallBuffersBetweenLargeOnesAreMerged() throws Exception {
        Recorded recorded = writeAndVerify(THRESHOLD, messages(16, 32, 2048, 64, 4096, 8));

        assertEquals(1, recorded.ops.size());
        assertEquals(Native.IORING_OP_SENDMSG_ZC, recorded.ops.get(0).opcode());
        assertArrayEquals(new long[] {48, 2048, 64, 4096, 8}, recorded.iovLengths.get(0));
    }

    @Test
    public void testCompositeIsSplitIntoSmallAndLargeComponents() throws Exception {
        ByteBuf composite = Unpooled.compositeBuffer().addComponents(true, messages(16, 2048, 32, 4096));
        Recorded recorded = writeAndVerify(THRESHOLD, new ByteBuf[] {composite});

        assertEquals(1, recorded.ops.size());
        assertEquals(Native.IORING_OP_SENDMSG_ZC, recorded.ops.get(0).opcode());
        assertArrayEquals(new long[] {16, 2048, 32, 4096}, recorded.iovLengths.get(0));
    }

    @Test
    public void testAllSmallBuffersUseWritev() throws Exception {
        Recorded recorded = writeAndVerify(THRESHOLD, messages(16, 32, 64));

        assertEquals(1, recorded.ops.size());
        assertEquals(Native.IORING_OP_WRITEV, recorded.ops.get(0).opcode());
        assertArrayEquals(new long[] {16, 32, 64}, recorded.iovLengths.get(0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testSmallBuffersBeforeLargeBufferExceedMergedCapacityUseWritev(boolean composite) throws Exception {
        // 80KiB of small buffers do not fit into the merged buffer, which is limited to 64KiB by default.
        int small = 16 * 1024;
        FailingDirectByteBufAllocator allocator = new FailingDirectByteBufAllocator();
        Recorded recorded = writeAndVerify(32 * 1024, allocator,
                messages(composite, small, small, small, small, small, 64 * 1024));

        assertThat(recorded.ops.get(0).opcode()).isEqualTo(Native.IORING_OP_WRITEV);
        assertThat(recorded.iovLengths.get(0)).containsExactly(small, small, small, small, small, 64 * 1024);
        assertThat(allocator.directAllocations.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testSmallBufferBeforeLargeBufferOnlyPartiallyFitsUseWritev(boolean composite) throws Exception {
        int small = 24 * 1024;
        FailingDirectByteBufAllocator allocator = new FailingDirectByteBufAllocator();
        Recorded recorded = writeAndVerify(32 * 1024, allocator,
                messages(composite, small, small, small, 64 * 1024));

        assertThat(recorded.ops.get(0).opcode()).isEqualTo(Native.IORING_OP_WRITEV);
        assertThat(recorded.iovLengths.get(0)).containsExactly(small, small, small, 64 * 1024);
        assertThat(allocator.directAllocations.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testSmallBuffersBeforeLargeBufferExactlyFitMergedCapacity(boolean composite) throws Exception {
        int small = 16 * 1024;
        Recorded recorded = writeAndVerify(32 * 1024,
                messages(composite, small, small, small, small, 64 * 1024));

        assertThat(recorded.ops.get(0).opcode()).isEqualTo(Native.IORING_OP_SENDMSG_ZC);
        assertThat(recorded.iovLengths.get(0)).containsExactly(64 * 1024, 64 * 1024);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testSmallBuffersAfterLargeBufferExceedMergedCapacityKeepZeroCopy(boolean composite) throws Exception {
        int small = 16 * 1024;
        Recorded recorded = writeAndVerify(32 * 1024,
                messages(composite, 64 * 1024, small, small, small, small, small));

        assertThat(recorded.ops.get(0).opcode()).isEqualTo(Native.IORING_OP_SENDMSG_ZC);
        assertThat(recorded.iovLengths.get(0)).containsExactly(64 * 1024, 64 * 1024);
    }

    @Test
    public void testMergedAllocationFailureUsesWritev() throws Exception {
        FailingDirectByteBufAllocator allocator = new FailingDirectByteBufAllocator();
        Recorded recorded = writeAndVerify(THRESHOLD, allocator, messages(16, 2048, 32));

        assertThat(recorded.ops.get(0).opcode()).isEqualTo(Native.IORING_OP_WRITEV);
        assertThat(recorded.iovLengths.get(0)).containsExactly(16, 2048, 32);
        assertThat(allocator.directAllocations.get()).isEqualTo(1);
    }

    /**
     * Flushes every batch, and verifies that the peer receives all the bytes in order and that the messages are
     * released.
     */
    private static Recorded writeAndVerify(int threshold, ByteBuf[]... batches) throws Exception {
        return writeAndVerify(threshold, null, batches);
    }

    private static Recorded writeAndVerify(int threshold, ByteBufAllocator allocator, ByteBuf[]... batches)
            throws Exception {
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        for (ByteBuf[] batch : batches) {
            for (ByteBuf message : batch) {
                expected.write(ByteBufUtil.getBytes(message));
            }
        }
        CompletableFuture<byte[]> received = new CompletableFuture<>();
        Recorded recorded = new Recorded();
        IoHandlerFactory factory = executor -> new RecordingIoUringIoHandler(
                IoUringIoHandler.newFactory().newHandler(executor), recorded.ops, recorded.iovLengths);
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, factory);
        try {
            ServerChannel server = receivingServer(group, expected.size(), received);
            Channel client = connect(group, server);
            client.setOption(IoUringChannelOption.IO_URING_WRITE_ZERO_COPY_THRESHOLD, threshold);
            if (allocator != null) {
                client.config().setAllocator(allocator);
            }
            client.eventLoop().execute(() -> {
                for (ByteBuf[] batch : batches) {
                    for (ByteBuf message : batch) {
                        client.write(message);
                    }
                    client.flush();
                }
            });

            assertArrayEquals(expected.toByteArray(), received.get(20, TimeUnit.SECONDS));
            for (ByteBuf[] batch : batches) {
                for (ByteBuf message : batch) {
                    assertTrue(awaitRefCntZero(client, message, 5, TimeUnit.SECONDS), "message was not released");
                }
            }
            return recorded;
        } finally {
            group.shutdownGracefully();
        }
    }

    private static ByteBuf[] messages(boolean composite, int... sizes) {
        ByteBuf[] messages = messages(sizes);
        return composite ? new ByteBuf[] {Unpooled.compositeBuffer().addComponents(true, messages)} : messages;
    }

    private static ByteBuf[] messages(int... sizes) {
        ByteBuf[] messages = new ByteBuf[sizes.length];
        for (int i = 0; i < sizes.length; i++) {
            messages[i] = Unpooled.directBuffer(sizes[i]);
            // Use a different pattern for each message, so that a wrong order is detected.
            for (int j = 0; j < sizes[i]; j++) {
                messages[i].writeByte(i + 1 + j);
            }
        }
        return messages;
    }

    private static ServerChannel receivingServer(MultiThreadIoEventLoopGroup group, int expectedBytes,
                                                 CompletableFuture<byte[]> received) throws InterruptedException {
        return (ServerChannel) new ServerBootstrap()
            .channel(IoUringServerSocketChannel.class)
            .group(group, group)
            .childHandler(new SimpleChannelInboundHandler<ByteBuf>() {
                private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

                @Override
                protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
                    msg.readBytes(bytes, msg.readableBytes());
                    if (bytes.size() >= expectedBytes) {
                        received.complete(bytes.toByteArray());
                    }
                }
            })
            .bind(0)
            .sync().channel();
    }

    public CompletableFuture<Void> prepChannels(MultiThreadIoEventLoopGroup ioEventLoopGroup)
        throws InterruptedException {
        ServerChannel server = server(ioEventLoopGroup);
        Channel sendmsgzcChannel = connect(ioEventLoopGroup, server);
        sendmsgzcChannel.setOption(
            IoUringChannelOption.IO_URING_WRITE_ZERO_COPY_THRESHOLD, 0
        );
        Channel writvChannel = connect(ioEventLoopGroup, server);
        ByteBuf byteBuf = Unpooled.directBuffer(1024);
        byteBuf.writerIndex(1024);
        byteBuf.retain(SENDMSG_ZC_COUNT + 2 - 1);
        CompletableFuture<Void> prepFuture = new CompletableFuture<>();
        ioEventLoopGroup.execute(() -> {
            for (int i = 0; i < SENDMSG_ZC_COUNT; i++) {
                sendmsgzcChannel.write(byteBuf);
            }
            for (int i = 0; i < 2; i++) {
                writvChannel.write(byteBuf);
            }
            sendmsgzcChannel.flush();
            writvChannel.flush();
            prepFuture.complete(null);
        });
        return prepFuture;
    }

    private static Channel connect(MultiThreadIoEventLoopGroup group, ServerChannel server)
        throws InterruptedException {
        return new Bootstrap()
                .group(group)
                .channel(IoUringSocketChannel.class)
                .option(ChannelOption.AUTO_READ, false)
                .handler(new ChannelInboundHandlerAdapter())
                .connect(server.localAddress())
                .sync().channel();
    }

    private static ServerChannel server(MultiThreadIoEventLoopGroup group) throws InterruptedException {
        return (ServerChannel) new ServerBootstrap()
            .channel(IoUringServerSocketChannel.class)
            .group(group, group)
            .childHandler(new ChannelInitializer<Channel>() {
                @Override
                public void initChannel(Channel ch) throws Exception {
                }
            })
            .bind(0)
            .sync().channel();
    }

    private static int sendmsgIovLength(IoUringIoOps ops) {
        ByteBuffer msgHdr = Buffer.wrapMemoryAddressWithNativeOrder(ops.union2(), Native.SIZEOF_MSGHDR);
        if (Native.SIZEOF_SIZE_T == 4) {
            return msgHdr.getInt(Native.MSGHDR_OFFSETOF_MSG_IOVLEN);
        } else {
            return (int) msgHdr.getLong(Native.MSGHDR_OFFSETOF_MSG_IOVLEN);
        }
    }

    private static int writevIovLength(IoUringIoOps ops) {
        return ops.len();
    }

    // The iovec memory is reused after the submit, so it needs to be read while submitting.
    private static long[] iovLengths(IoUringIoOps ops) {
        final long iovAddress;
        final int count;
        if (ops.opcode() == Native.IORING_OP_SENDMSG_ZC) {
            ByteBuffer msgHdr = Buffer.wrapMemoryAddressWithNativeOrder(ops.union2(), Native.SIZEOF_MSGHDR);
            iovAddress = Native.SIZEOF_SIZE_T == 4 ? msgHdr.getInt(Native.MSGHDR_OFFSETOF_MSG_IOV) & 0xFFFFFFFFL :
                    msgHdr.getLong(Native.MSGHDR_OFFSETOF_MSG_IOV);
            count = sendmsgIovLength(ops);
        } else {
            iovAddress = ops.union2();
            count = writevIovLength(ops);
        }
        ByteBuffer iovs = Buffer.wrapMemoryAddressWithNativeOrder(iovAddress, count * Native.SIZEOF_IOVEC);
        long[] lengths = new long[count];
        for (int i = 0; i < count; i++) {
            int index = i * Native.SIZEOF_IOVEC + Native.SIZEOF_SIZE_T;
            lengths[i] = Native.SIZEOF_SIZE_T == 4 ? iovs.getInt(index) : iovs.getLong(index);
        }
        return lengths;
    }

    private static final class Recorded {
        final List<IoUringIoOps> ops = new CopyOnWriteArrayList<>();
        final List<long[]> iovLengths = new CopyOnWriteArrayList<>();
    }

    private static final class FailingDirectByteBufAllocator extends AbstractByteBufAllocator {
        final AtomicInteger directAllocations = new AtomicInteger();

        @Override
        protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
            return Unpooled.buffer(initialCapacity, maxCapacity);
        }

        @Override
        protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
            directAllocations.incrementAndGet();
            throw new OutOfMemoryError("merged allocation failure");
        }

        @Override
        public boolean isDirectBufferPooled() {
            return false;
        }
    }

    private static final class RecordingIoUringIoHandler implements IoHandler {
        private final IoHandler delegate;
        private final List<IoUringIoOps> writes;
        private final List<long[]> iovLengths;

        RecordingIoUringIoHandler(IoHandler delegate, List<IoUringIoOps> ioUringIoOps) {
            this(delegate, ioUringIoOps, new ArrayList<>());
        }

        RecordingIoUringIoHandler(IoHandler delegate, List<IoUringIoOps> ioUringIoOps, List<long[]> iovLengths) {
            this.delegate = delegate;
            this.writes = ioUringIoOps;
            this.iovLengths = iovLengths;
        }

        @Override
        public void initialize() {
            delegate.initialize();
        }

        @Override
        public int run(IoHandlerContext context) {
            return delegate.run(context);
        }

        @Override
        public void prepareToDestroy() {
            delegate.prepareToDestroy();
        }

        @Override
        public void destroy() {
            delegate.destroy();
        }

        @Override
        public IoRegistration register(IoHandle handle) throws Exception {
            IoRegistration registration = delegate.register(handle);
            return new IoRegistration() {
                @Override
                public <T> T attachment() {
                    return registration.attachment();
                }

                @Override
                public long submit(IoOps ops) {
                    IoUringIoOps operation = (IoUringIoOps) ops;
                    if (operation.opcode() == Native.IORING_OP_SENDMSG_ZC ||
                            operation.opcode() == Native.IORING_OP_WRITEV) {
                        writes.add(operation);
                        iovLengths.add(iovLengths(operation));
                    }
                    return registration.submit(ops);
                }

                @Override
                public boolean isValid() {
                    return registration.isValid();
                }

                @Override
                public boolean cancel() {
                    return registration.cancel();
                }
            };
        }

        @Override
        public void wakeup() {
            delegate.wakeup();
        }

        @Override
        public boolean isCompatible(Class<? extends IoHandle> handleType) {
            return delegate.isCompatible(handleType);
        }
    }
}
