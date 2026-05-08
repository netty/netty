/*
 * Copyright 2014 The Netty Project
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
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.FileRegion;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.socket.SocketFileRegionTest;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.WritableByteChannel;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class IoUringSocketFileRegionTest extends SocketFileRegionTest {

    // Configured chunk size for the io_uring generic FileRegion fallback. Reads and clamps the
    // same system property the transport does so the test stays accurate if an operator
    // overrides it (transport caps at 16 MiB and floors at 1).
    private static final int CONFIGURED_CHUNK_SIZE = Math.min(16 * 1024 * 1024,
            Math.max(1, Integer.getInteger("io.netty.iouring.fileRegionChunkSize", 64 * 1024)));
    // With the default 64 KiB chunk size this demands 8 chunks; with an override larger than
    // the payload chunking simply isn't exercised but the transfer is still validated.
    private static final int CHUNKING_REGION_SIZE = 512 * 1024;

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> newFactories() {
        return IoUringSocketTestPermutation.INSTANCE.socket();
    }

    @Override
    protected boolean supportsCustomFileRegion() {
        return true;
    }

    @Test
    public void testFileRegionCountLargerThenFile(TestInfo testInfo) throws Throwable {
        super.testFileRegionCountLargerThenFile(testInfo);
    }

    @Test
    public void testCustomFileRegionChunking(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testCustomFileRegionChunking(serverBootstrap, bootstrap);
            }
        });
    }

    @Test
    public void testTwoCustomFileRegionsWithChunking(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testTwoCustomFileRegionsWithChunking(serverBootstrap, bootstrap);
            }
        });
    }

    private static void testCustomFileRegionChunking(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        byte[] payload = randomBytes(CHUNKING_REGION_SIZE);
        File file = writeTempFile(payload);

        ReceivingHandler sh = new ReceivingHandler(payload);
        sb.childOption(ChannelOption.AUTO_READ, true);
        cb.option(ChannelOption.AUTO_READ, true);
        sb.childHandler(sh);
        cb.handler(new SimpleChannelInboundHandler<Object>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
                // drop
            }
        });

        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.connect(sc.localAddress()).sync().channel();
        boolean ioUringClient = cc instanceof IoUringSocketChannel;
        CountingFileRegion region = new CountingFileRegion(new DefaultFileRegion(
                new RandomAccessFile(file, "r").getChannel(), 0, payload.length));
        try {
            cc.writeAndFlush(region).sync();
            sh.awaitCompletion();
        } finally {
            cc.close().sync();
            sc.close().sync();
        }
        assertNull(sh.exception.get());
        assertEquals(payload.length, sh.counter);
        // Chunking is only exercised when the client uses io_uring -- other transports (e.g. NIO)
        // hand the FileRegion straight to the socket channel and the kernel's sendfile() transfers
        // the whole file in a single transferTo call. Skip the chunking assertion for those combos.
        assumeTrue(ioUringClient, "chunking only applies to io_uring client transport");
        // Skip the chunking assertion if an operator configured a chunk size larger than the
        // payload -- chunking is not exercised in that configuration, but the transfer itself is
        // still validated above.
        assumeTrue(CONFIGURED_CHUNK_SIZE < CHUNKING_REGION_SIZE, "chunk size >= payload; chunking not exercised");
        int minExpectedCalls = (payload.length + CONFIGURED_CHUNK_SIZE - 1) / CONFIGURED_CHUNK_SIZE;
        assertTrue(region.transferToCalls.get() >= minExpectedCalls,
                "Expected at least " + minExpectedCalls + " transferTo calls to demonstrate chunking, got "
                        + region.transferToCalls.get());
    }

    private static void testTwoCustomFileRegionsWithChunking(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        byte[] firstPayload = randomBytes(CHUNKING_REGION_SIZE);
        byte[] secondPayload = randomBytes(4 * 1024);
        byte[] combined = new byte[firstPayload.length + secondPayload.length];
        System.arraycopy(firstPayload, 0, combined, 0, firstPayload.length);
        System.arraycopy(secondPayload, 0, combined, firstPayload.length, secondPayload.length);

        File firstFile = writeTempFile(firstPayload);
        File secondFile = writeTempFile(secondPayload);

        ReceivingHandler sh = new ReceivingHandler(combined);
        sb.childOption(ChannelOption.AUTO_READ, true);
        cb.option(ChannelOption.AUTO_READ, true);
        sb.childHandler(sh);
        cb.handler(new SimpleChannelInboundHandler<Object>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
                // drop
            }
        });

        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.connect(sc.localAddress()).sync().channel();
        boolean ioUringClient = cc instanceof IoUringSocketChannel;
        CountingFileRegion firstRegion = new CountingFileRegion(new DefaultFileRegion(
                new RandomAccessFile(firstFile, "r").getChannel(), 0, firstPayload.length));
        CountingFileRegion secondRegion = new CountingFileRegion(new DefaultFileRegion(
                new RandomAccessFile(secondFile, "r").getChannel(), 0, secondPayload.length));
        try {
            // Surface any first-write failure immediately instead of waiting on the spin timeout.
            cc.write(firstRegion).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            cc.writeAndFlush(secondRegion).sync();
            sh.awaitCompletion();
        } finally {
            cc.close().sync();
            sc.close().sync();
        }
        assertNull(sh.exception.get());
        assertEquals(combined.length, sh.counter);
        assertTrue(secondRegion.transferToCalls.get() >= 1,
                "Second region must be transferred too");
        // Chunking is only exercised when the client uses io_uring -- see testCustomFileRegionChunking.
        assumeTrue(ioUringClient, "chunking only applies to io_uring client transport");
        assumeTrue(CONFIGURED_CHUNK_SIZE < CHUNKING_REGION_SIZE, "chunk size >= payload; chunking not exercised");
        int minFirstCalls = (firstPayload.length + CONFIGURED_CHUNK_SIZE - 1) / CONFIGURED_CHUNK_SIZE;
        assertTrue(firstRegion.transferToCalls.get() >= minFirstCalls,
                "Expected at least " + minFirstCalls + " transferTo calls for the first (chunked) region, got "
                        + firstRegion.transferToCalls.get());
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        ThreadLocalRandom.current().nextBytes(bytes);
        return bytes;
    }

    private static File writeTempFile(byte[] data) throws IOException {
        File file = PlatformDependent.createTempFile("netty-iouring-chunk-", ".tmp", null);
        file.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data);
        }
        return file;
    }

    private static final class ReceivingHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final byte[] expected;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        volatile int counter;

        ReceivingHandler(byte[] expected) {
            this.expected = expected;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf in) {
            int readable = in.readableBytes();
            byte[] actual = new byte[readable];
            in.readBytes(actual);
            int offset = counter;
            if (offset + readable > expected.length) {
                exception.compareAndSet(null, new AssertionError(
                        "Received more than " + expected.length + " bytes"));
                ctx.close();
                return;
            }
            for (int i = 0; i < actual.length; i++) {
                if (actual[i] != expected[offset + i]) {
                    exception.compareAndSet(null, new AssertionError(
                            "Byte mismatch at index " + (offset + i)));
                    ctx.close();
                    return;
                }
            }
            counter += readable;
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            exception.compareAndSet(null, cause);
            ctx.close();
        }

        void awaitCompletion() throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (counter < expected.length && exception.get() == null) {
                if (System.nanoTime() > deadline) {
                    throw new AssertionError("Timed out waiting for " + expected.length
                            + " bytes, received " + counter);
                }
                Thread.sleep(50);
            }
        }
    }

    /**
     * Wraps a {@link DefaultFileRegion} so it is routed through the io_uring generic
     * (non-splice) chunking path and counts {@link #transferTo} invocations so tests can assert
     * that chunking actually happened.
     */
    private static final class CountingFileRegion implements FileRegion {
        private final FileRegion delegate;
        final AtomicInteger transferToCalls = new AtomicInteger();

        CountingFileRegion(FileRegion delegate) {
            this.delegate = delegate;
        }

        @Override
        public long position() {
            return delegate.position();
        }

        @Override
        @Deprecated
        public long transfered() {
            return delegate.transferred();
        }

        @Override
        public long transferred() {
            return delegate.transferred();
        }

        @Override
        public long count() {
            return delegate.count();
        }

        @Override
        public long transferTo(WritableByteChannel target, long position) throws IOException {
            transferToCalls.incrementAndGet();
            return delegate.transferTo(target, position);
        }

        @Override
        public int refCnt() {
            return delegate.refCnt();
        }

        @Override
        public boolean release() {
            return delegate.release();
        }

        @Override
        public boolean release(int decrement) {
            return delegate.release(decrement);
        }

        @Override
        public FileRegion retain() {
            delegate.retain();
            return this;
        }

        @Override
        public FileRegion retain(int increment) {
            delegate.retain(increment);
            return this;
        }

        @Override
        public FileRegion touch() {
            delegate.touch();
            return this;
        }

        @Override
        public FileRegion touch(Object hint) {
            delegate.touch(hint);
            return this;
        }
    }
}
