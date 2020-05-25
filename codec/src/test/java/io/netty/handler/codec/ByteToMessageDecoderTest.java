/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec;

import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.buffer.UnpooledHeapByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.internal.PlatformDependent;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ByteToMessageDecoderTest {

    @Test
    public void testRemoveItself() {
        EmbeddedChannel channel = new EmbeddedChannel(new ByteToMessageDecoder() {
            private boolean removed;

            @Override
            protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
                assertFalse(removed);
                in.readByte();
                ctx.pipeline().remove(this);
                removed = true;
            }
        });

        ByteBuf buf = Unpooled.wrappedBuffer(new byte[] {'a', 'b', 'c'});
        channel.writeInbound(buf.copy());
        ByteBuf b = channel.readInbound();
        assertEquals(b, buf.skipBytes(1));
        b.release();
        buf.release();
    }

    @Test
    public void testRemoveItselfWriteBuffer() {
        final ByteBuf buf = Unpooled.buffer().writeBytes(new byte[] {'a', 'b', 'c'});
        EmbeddedChannel channel = new EmbeddedChannel(new ByteToMessageDecoder() {
            private boolean removed;

            @Override
            protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
                assertFalse(removed);
                in.readByte();
                ctx.pipeline().remove(this);

                // This should not let it keep call decode
                buf.writeByte('d');
                removed = true;
            }
        });

        channel.writeInbound(buf.copy());
        ByteBuf expected = Unpooled.wrappedBuffer(new byte[] {'b', 'c'});
        ByteBuf b = channel.readInbound();
        assertEquals(expected, b);
        expected.release();
        buf.release();
        b.release();
    }

    /**
     * Verifies that internal buffer of the ByteToMessageDecoder is released once decoder is removed from pipeline. In
     * this case input is read fully.
     */
    @Test
    public void testInternalBufferClearReadAll() {
        final ByteBuf buf = Unpooled.buffer().writeBytes(new byte[] {'a'});
        EmbeddedChannel channel = newInternalBufferTestChannel();
        assertFalse(channel.writeInbound(buf));
        assertFalse(channel.finish());
    }

    /**
     * Verifies that internal buffer of the ByteToMessageDecoder is released once decoder is removed from pipeline. In
     * this case input was not fully read.
     */
    @Test
    public void testInternalBufferClearReadPartly() {
        final ByteBuf buf = Unpooled.buffer().writeBytes(new byte[] {'a', 'b'});
        EmbeddedChannel channel = newInternalBufferTestChannel();
        assertTrue(channel.writeInbound(buf));
        assertTrue(channel.finish());
        ByteBuf expected = Unpooled.wrappedBuffer(new byte[] {'b'});
        ByteBuf b = channel.readInbound();
        assertEquals(expected, b);
        assertNull(channel.readInbound());
        expected.release();
        b.release();
    }

    private EmbeddedChannel newInternalBufferTestChannel() {
        return new EmbeddedChannel(new ByteToMessageDecoder() {
            @Override
            protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
                ByteBuf byteBuf = internalBuffer();
                assertEquals(1, byteBuf.refCnt());
                in.readByte();
                // Removal from pipeline should clear internal buffer
                ctx.pipeline().remove(this);
            }

            @Override
            protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
                assertCumulationReleased(internalBuffer());
            }
        });
    }

    @Test
    public void handlerRemovedWillNotReleaseBufferIfDecodeInProgress() {
        EmbeddedChannel channel = new EmbeddedChannel(new ByteToMessageDecoder() {
            @Override
            protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
                ctx.pipeline().remove(this);
                assertTrue(in.refCnt() != 0);
            }

            @Override
            protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
                assertCumulationReleased(internalBuffer());
            }
        });
        byte[] bytes = new byte[1024];
        PlatformDependent.threadLocalRandom().nextBytes(bytes);

        assertTrue(channel.writeInbound(Unpooled.wrappedBuffer(bytes)));
        assertTrue(channel.finishAndReleaseAll());
    }

    private static void assertCumulationReleased(ByteBuf byteBuf) {
        assertTrue("unexpected value: " + byteBuf,
                byteBuf == null || byteBuf == Unpooled.EMPTY_BUFFER || byteBuf.refCnt() == 0);
    }

    @Test
    public void testFireChannelReadCompleteOnInactive() throws InterruptedException {
        final BlockingQueue<Integer> queue = new LinkedBlockingDeque<Integer>();
        final ByteBuf buf = Unpooled.buffer().writeBytes(new byte[] {'a', 'b'});
        EmbeddedChannel channel = new EmbeddedChannel(new ByteToMessageDecoder() {
            @Override
            protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
                int readable = in.readableBytes();
                assertTrue(readable > 0);
                in.skipBytes(readable);
            }

            @Override
            protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
                assertFalse(in.isReadable());
                out.add("data");
            }
        }, new ChannelInboundHandlerAdapter() {
            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                queue.add(3);
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                queue.add(1);
            }

            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                if (!ctx.channel().isActive()) {
                    queue.add(2);
                }
            }
        });
        assertFalse(channel.writeInbound(buf));
        channel.finish();
        assertEquals(1, (int) queue.take());
        assertEquals(2, (int) queue.take());
        assertEquals(3, (int) queue.take());
        assertTrue(queue.isEmpty());
    }

    // See https://github.com/netty/netty/issues/4635
    @Test
    public void testRemoveWhileInCallDecode() {
        final Object upgradeMessage = new Object();
        final ByteToMessageDecoder decoder = new ByteToMessageDecoder() {
            @Override
            protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
                assertEquals('a', in.readByte());
                out.add(upgradeMessage);
            }
        };

        EmbeddedChannel channel = new EmbeddedChannel(decoder, new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg == upgradeMessage) {
                    ctx.pipeline().remove(decoder);
                    return;
                }
                ctx.fireChannelRead(msg);
            }
        });

        ByteBuf buf = Unpooled.wrappedBuffer(new byte[] { 'a', 'b', 'c' });
        assertTrue(channel.writeInbound(buf.copy()));
        ByteBuf b = channel.readInbound();
        assertEquals(b, buf.skipBytes(1));
        assertFalse(channel.finish());
        buf.release();
        b.release();
    }

    @Test
    public void testDecodeLastEmptyBuffer() {
        EmbeddedChannel channel = new EmbeddedChannel(new ByteToMessageDecoder() {
            @Override
            protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
                int readable = in.readableBytes();
                assertTrue(readable > 0);
                out.add(in.readBytes(readable));
            }
        });
        byte[] bytes = new byte[1024];
        PlatformDependent.threadLocalRandom().nextBytes(bytes);

        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(bytes)));
        assertBuffer(Unpooled.wrappedBuffer(bytes), (ByteBuf) channel.readInbound());
        assertNull(channel.readInbound());
        assertFalse(channel.finish());
        assertNull(channel.readInbound());
    }

    @Test
    public void testDecodeLastNonEmptyBuffer() {
        EmbeddedChannel channel = new EmbeddedChannel(new ByteToMessageDecoder() {
            private boolean decodeLast;

            @Override
            protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
                int readable = in.readableBytes();
                assertTrue(readable > 0);
                if (!decodeLast && readable == 1) {
                    return;
                }
                out.add(in.readBytes(decodeLast ? readable : readable - 1));
            }

            @Override
            protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
                assertFalse(decodeLast);
                decodeLast = true;
                super.decodeLast(ctx, in, out);
            }
        });
        byte[] bytes = new byte[1024];
        PlatformDependent.threadLocalRandom().nextBytes(bytes);

        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(bytes)));
        assertBuffer(Unpooled.wrappedBuffer(bytes, 0, bytes.length - 1), (ByteBuf) channel.readInbound());
        assertNull(channel.readInbound());
        assertTrue(channel.finish());
        assertBuffer(Unpooled.wrappedBuffer(bytes, bytes.length - 1, 1), (ByteBuf) channel.readInbound());
        assertNull(channel.readInbound());
    }

    private static void assertBuffer(ByteBuf expected, ByteBuf buffer) {
        try {
            assertEquals(expected, buffer);
        } finally {
            buffer.release();
            expected.release();
        }
    }

    @Test
    public void testReadOnlyBuffer() {
        EmbeddedChannel channel = new EmbeddedChannel(new ByteToMessageDecoder() {
            @Override
            protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            }
        });
        assertFalse(channel.writeInbound(Unpooled.buffer(8).writeByte(1).asReadOnly()));
        assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(new byte[] { (byte) 2 })));
        assertFalse(channel.finish());
    }

    static class WriteFailingByteBuf extends UnpooledHeapByteBuf {
        private final Error error = new Error();
        private int untilFailure;

        WriteFailingByteBuf(int untilFailure, int capacity) {
            super(UnpooledByteBufAllocator.DEFAULT, capacity, capacity);
            this.untilFailure = untilFailure;
        }

        @Override
        public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
            if (--untilFailure <= 0) {
                throw error;
            }
            return super.setBytes(index, src, srcIndex, length);
        }

        Error writeError() {
            return error;
        }
    }

    @Test
    public void releaseWhenMergeCumulateThrows() {
        WriteFailingByteBuf oldCumulation = new WriteFailingByteBuf(1, 64);
        oldCumulation.writeZero(1);
        ByteBuf in = Unpooled.buffer().writeZero(12);

        Throwable thrown = null;
        try {
            ByteToMessageDecoder.MERGE_CUMULATOR.cumulate(UnpooledByteBufAllocator.DEFAULT, oldCumulation, in);
        } catch (Throwable t) {
            thrown = t;
        }

        assertSame(oldCumulation.writeError(), thrown);
        assertEquals(0, in.refCnt());
        assertEquals(1, oldCumulation.refCnt());
        oldCumulation.release();
    }

    @Test
    public void releaseWhenMergeCumulateThrowsInExpand() {
        releaseWhenMergeCumulateThrowsInExpand(1, true);
        releaseWhenMergeCumulateThrowsInExpand(2, true);
        releaseWhenMergeCumulateThrowsInExpand(3, false); // sentinel test case
    }

    private void releaseWhenMergeCumulateThrowsInExpand(int untilFailure, boolean shouldFail) {
        ByteBuf oldCumulation = UnpooledByteBufAllocator.DEFAULT.heapBuffer(8, 8).writeZero(1);
        final WriteFailingByteBuf newCumulation = new WriteFailingByteBuf(untilFailure, 16);

        ByteBufAllocator allocator = new AbstractByteBufAllocator(false) {
            @Override
            public boolean isDirectBufferPooled() {
                return false;
            }

            @Override
            protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
                return newCumulation;
            }

            @Override
            protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
                throw new UnsupportedOperationException();
            }
        };

        ByteBuf in = Unpooled.buffer().writeZero(12);
        Throwable thrown = null;
        try {
            ByteToMessageDecoder.MERGE_CUMULATOR.cumulate(allocator, oldCumulation, in);
        } catch (Throwable t) {
            thrown = t;
        }

        assertEquals(0, in.refCnt());

        if (shouldFail) {
            assertSame(newCumulation.writeError(), thrown);
            assertEquals(1, oldCumulation.refCnt());
            oldCumulation.release();
            assertEquals(0, newCumulation.refCnt());
        } else {
            assertNull(thrown);
            assertEquals(0, oldCumulation.refCnt());
            assertEquals(1, newCumulation.refCnt());
            newCumulation.release();
        }
    }

    @Test
    public void releaseWhenCompositeCumulateThrows() {
        final Error error = new Error();

        ByteBuf cumulation = new CompositeByteBuf(UnpooledByteBufAllocator.DEFAULT, false, 64) {
            @Override
            public CompositeByteBuf addComponent(boolean increaseWriterIndex, ByteBuf buffer) {
                throw error;
            }
            @Override
            public CompositeByteBuf addFlattenedComponents(boolean increaseWriterIndex, ByteBuf buffer) {
                throw error;
            }
        }.writeZero(1);
        ByteBuf in = Unpooled.buffer().writeZero(12);
        try {
            ByteToMessageDecoder.COMPOSITE_CUMULATOR.cumulate(UnpooledByteBufAllocator.DEFAULT, cumulation, in);
            fail();
        } catch (Error expected) {
            assertSame(error, expected);
            assertEquals(0, in.refCnt());
            cumulation.release();
        }
    }

    @Test
    public void testDoesNotOverRead() {
        class ReadInterceptingHandler extends ChannelOutboundHandlerAdapter {
            private int readsTriggered;

            @Override
            public void read(ChannelHandlerContext ctx) throws Exception {
                readsTriggered++;
                super.read(ctx);
            }
        }
        ReadInterceptingHandler interceptor = new ReadInterceptingHandler();

        EmbeddedChannel channel = new EmbeddedChannel();
        channel.config().setAutoRead(false);
        channel.pipeline().addLast(interceptor, new FixedLengthFrameDecoder(3));
        assertEquals(0, interceptor.readsTriggered);

        // 0 complete frames, 1 partial frame: SHOULD trigger a read
        channel.writeInbound(wrappedBuffer(new byte[] { 0, 1 }));
        assertEquals(1, interceptor.readsTriggered);

        // 2 complete frames, 0 partial frames: should NOT trigger a read
        channel.writeInbound(wrappedBuffer(new byte[] { 2 }), wrappedBuffer(new byte[] { 3, 4, 5 }));
        assertEquals(1, interceptor.readsTriggered);

        // 1 complete frame, 1 partial frame: should NOT trigger a read
        channel.writeInbound(wrappedBuffer(new byte[] { 6, 7, 8 }), wrappedBuffer(new byte[] { 9 }));
        assertEquals(1, interceptor.readsTriggered);

        // 1 complete frame, 1 partial frame: should NOT trigger a read
        channel.writeInbound(wrappedBuffer(new byte[] { 10, 11 }), wrappedBuffer(new byte[] { 12 }));
        assertEquals(1, interceptor.readsTriggered);

        // 0 complete frames, 1 partial frame: SHOULD trigger a read
        channel.writeInbound(wrappedBuffer(new byte[] { 13 }));
        assertEquals(2, interceptor.readsTriggered);

        // 1 complete frame, 0 partial frames: should NOT trigger a read
        channel.writeInbound(wrappedBuffer(new byte[] { 14 }));
        assertEquals(2, interceptor.readsTriggered);

        for (int i = 0; i < 5; i++) {
            ByteBuf read = channel.readInbound();
            assertEquals(i * 3 + 0, read.getByte(0));
            assertEquals(i * 3 + 1, read.getByte(1));
            assertEquals(i * 3 + 2, read.getByte(2));
            read.release();
        }
        assertFalse(channel.finish());
    }

    @Test
    public void testDisorder() {
        ByteToMessageDecoder decoder = new ByteToMessageDecoder() {
            int count;

            //read 4 byte then remove this decoder
            @Override
            protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
                out.add(in.readByte());
                if (++count >= 4) {
                    ctx.pipeline().remove(this);
                }
            }
        };
        EmbeddedChannel channel = new EmbeddedChannel(decoder);
        assertTrue(channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{1, 2, 3, 4, 5})));
        assertEquals((byte) 1,  channel.readInbound());
        assertEquals((byte) 2,  channel.readInbound());
        assertEquals((byte) 3,  channel.readInbound());
        assertEquals((byte) 4,  channel.readInbound());
        ByteBuf buffer5 = channel.readInbound();
        assertEquals((byte) 5, buffer5.readByte());
        assertFalse(buffer5.isReadable());
        assertTrue(buffer5.release());
        assertFalse(channel.finish());
    }
}
