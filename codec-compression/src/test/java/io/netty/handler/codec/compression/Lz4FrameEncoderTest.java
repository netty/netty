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
package io.netty.handler.codec.compression;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.EncoderException;
import java.util.concurrent.TimeUnit;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.xxhash.XXHashFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Checksum;

import static io.netty.handler.codec.compression.Lz4Constants.DEFAULT_SEED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

public class Lz4FrameEncoderTest extends AbstractEncoderTest {
    /**
     * For the purposes of this test, if we pass this (very small) size of buffer into
     * {@link Lz4FrameEncoder#allocateBuffer(ChannelHandlerContext, ByteBuf, boolean)}, we should get back
     * an empty buffer.
     */
    private static final int NONALLOCATABLE_SIZE = 1;

    @Mock
    private ChannelHandlerContext ctx;

    /**
     * A {@link ByteBuf} for mocking purposes, largely because it's difficult to allocate to huge buffers.
     */
    @Mock
    private ByteBuf buffer;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(ctx.alloc()).thenReturn(ByteBufAllocator.DEFAULT);
    }

    @Override
    protected EmbeddedChannel createChannel() {
        return new EmbeddedChannel(new Lz4FrameEncoder());
    }

    @Override
    protected ByteBuf decompress(ByteBuf compressed, int originalLength) throws Exception {
        InputStream is = new ByteBufInputStream(compressed, true);
        LZ4BlockInputStream lz4Is = null;
        byte[] decompressed = new byte[originalLength];
        try {
            lz4Is = new LZ4BlockInputStream(is);
            int remaining = originalLength;
            while (remaining > 0) {
                int read = lz4Is.read(decompressed, originalLength - remaining, remaining);
                if (read > 0) {
                    remaining -= read;
                } else {
                    break;
                }
            }
            assertEquals(-1, lz4Is.read());
        } finally {
            if (lz4Is != null) {
                lz4Is.close();
            } else {
                is.close();
            }
        }

        return Unpooled.wrappedBuffer(decompressed);
    }

    @Test
    public void testAllocateDirectBuffer() {
        final int blockSize = 100;
        testAllocateBuffer(blockSize, blockSize - 13, true);
        testAllocateBuffer(blockSize, blockSize * 5, true);
        testAllocateBuffer(blockSize, NONALLOCATABLE_SIZE, true);
    }

    @Test
    public void testAllocateHeapBuffer() {
        final int blockSize = 100;
        testAllocateBuffer(blockSize, blockSize - 13, false);
        testAllocateBuffer(blockSize, blockSize * 5, false);
        testAllocateBuffer(blockSize, NONALLOCATABLE_SIZE, false);
    }

    private void testAllocateBuffer(int blockSize, int bufSize, boolean preferDirect) {
        // allocate the input buffer to an arbitrary size less than the blockSize
        ByteBuf in = ByteBufAllocator.DEFAULT.buffer(bufSize, bufSize);
        in.writerIndex(in.capacity());

        ByteBuf out = null;
        try {
            Lz4FrameEncoder encoder = newEncoder(blockSize, Lz4FrameEncoder.DEFAULT_MAX_ENCODE_SIZE);
            out = encoder.allocateBuffer(ctx, in, preferDirect);
            assertNotNull(out);
            if (NONALLOCATABLE_SIZE == bufSize) {
                assertFalse(out.isWritable());
            } else {
                assertTrue(out.writableBytes() > 0);
                if (!preferDirect) {
                    // Only check if preferDirect is not true as if a direct buffer is returned or not depends on
                    // if sun.misc.Unsafe is present.
                    assertFalse(out.isDirect());
                }
            }
        } finally {
            in.release();
            if (out != null) {
                out.release();
            }
        }
    }

    @Test
    public void testAllocateDirectBufferExceedMaxEncodeSize() {
        final int maxEncodeSize = 1024;
        final Lz4FrameEncoder encoder = newEncoder(Lz4Constants.DEFAULT_BLOCK_SIZE, maxEncodeSize);
        int inputBufferSize = maxEncodeSize * 10;
        final ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(inputBufferSize, inputBufferSize);
        try {
            buf.writerIndex(inputBufferSize);
            assertThrows(EncoderException.class, new Executable() {
                @Override
                public void execute() {
                    encoder.allocateBuffer(ctx, buf, false);
                }
            });
        } finally {
            buf.release();
        }
    }

    private Lz4FrameEncoder newEncoder(int blockSize, int maxEncodeSize) {
        Checksum checksum = XXHashFactory.fastestInstance().newStreamingHash32(DEFAULT_SEED).asChecksum();
        Lz4FrameEncoder encoder = new Lz4FrameEncoder(LZ4Factory.fastestInstance(), true,
                                                      blockSize,
                                                      checksum,
                                                      maxEncodeSize);
        encoder.handlerAdded(ctx);
        return encoder;
    }

    /**
     * This test might be a invasive in terms of knowing what happens inside
     * {@link Lz4FrameEncoder#allocateBuffer(ChannelHandlerContext, ByteBuf, boolean)}, but this is safest way
     * of testing the overflow conditions as allocating the huge buffers fails in many CI environments.
     */
    @Test
    public void testAllocateOnHeapBufferOverflowsOutputSize() {
        final int maxEncodeSize = Integer.MAX_VALUE;
        final Lz4FrameEncoder encoder = newEncoder(Lz4Constants.DEFAULT_BLOCK_SIZE, maxEncodeSize);
        when(buffer.readableBytes()).thenReturn(maxEncodeSize);
        buffer.writerIndex(maxEncodeSize);
        assertThrows(EncoderException.class, new Executable() {
            @Override
            public void execute() {
                encoder.allocateBuffer(ctx, buffer, false);
            }
        });
    }

    @Test
    public void testFlush() {
        Lz4FrameEncoder encoder = new Lz4FrameEncoder();
        EmbeddedChannel channel = new EmbeddedChannel(encoder);
        int size = 27;
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(size, size);
        buf.writerIndex(size);
        assertEquals(0, encoder.getBackingBuffer().readableBytes());
        channel.write(buf);
        assertTrue(channel.outboundMessages().isEmpty());
        assertEquals(size, encoder.getBackingBuffer().readableBytes());
        channel.flush();
        assertTrue(channel.finish());
        assertTrue(channel.releaseOutbound());
        assertFalse(channel.releaseInbound());
    }

    @Test
    public void testAllocatingAroundBlockSize() {
        int blockSize = 100;
        Lz4FrameEncoder encoder = newEncoder(blockSize, Lz4FrameEncoder.DEFAULT_MAX_ENCODE_SIZE);
        EmbeddedChannel channel = new EmbeddedChannel(encoder);

        int size = blockSize - 1;
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(size, size);
        buf.writerIndex(size);
        assertEquals(0, encoder.getBackingBuffer().readableBytes());
        channel.write(buf);
        assertEquals(size, encoder.getBackingBuffer().readableBytes());

        int nextSize = size - 1;
        buf = ByteBufAllocator.DEFAULT.buffer(nextSize, nextSize);
        buf.writerIndex(nextSize);
        channel.write(buf);
        assertEquals(size + nextSize - blockSize, encoder.getBackingBuffer().readableBytes());

        channel.flush();
        assertEquals(0, encoder.getBackingBuffer().readableBytes());
        assertTrue(channel.finish());
        assertTrue(channel.releaseOutbound());
        assertFalse(channel.releaseInbound());
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void writingAfterClosedChannelDoesNotNPE() throws InterruptedException {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(2, NioIoHandler.newFactory());
        Channel serverChannel = null;
        Channel clientChannel = null;
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> writeFailCauseRef = new AtomicReference<Throwable>();
        try {
            ServerBootstrap sb = new ServerBootstrap();
            sb.group(group);
            sb.channel(NioServerSocketChannel.class);
            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                }
            });

            Bootstrap bs = new Bootstrap();
            bs.group(group);
            bs.channel(NioSocketChannel.class);
            bs.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(new Lz4FrameEncoder());
                }
            });

            serverChannel = sb.bind(new InetSocketAddress(0)).syncUninterruptibly().channel();
            clientChannel = bs.connect(serverChannel.localAddress()).syncUninterruptibly().channel();

            final Channel finalClientChannel = clientChannel;
            clientChannel.eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    finalClientChannel.close();
                    final int size = 27;
                    ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(size, size);
                    finalClientChannel.writeAndFlush(buf.writerIndex(buf.writerIndex() + size))
                            .addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            try {
                                writeFailCauseRef.set(future.cause());
                            } finally {
                                latch.countDown();
                            }
                        }
                    });
                }
            });
            latch.await();
            Throwable writeFailCause = writeFailCauseRef.get();
            assertNotNull(writeFailCause);
            Throwable writeFailCauseCause = writeFailCause.getCause();
            if (writeFailCauseCause != null) {
                assertThat(writeFailCauseCause, is(not(instanceOf(NullPointerException.class))));
            }
        } finally {
            if (serverChannel != null) {
                serverChannel.close();
            }
            if (clientChannel != null) {
                clientChannel.close();
            }
            group.shutdownGracefully();
        }
    }
}
