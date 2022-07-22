/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.handler.codec;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.BufferStub;
import io.netty5.buffer.api.CompositeBuffer;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelShutdownDirection;
import io.netty5.channel.ReadBufferAllocator;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.ByteToMessageDecoder.Cumulator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.SplittableRandom;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static io.netty5.buffer.api.BufferAllocator.offHeapPooled;
import static io.netty5.buffer.api.BufferAllocator.offHeapUnpooled;
import static io.netty5.buffer.api.BufferAllocator.onHeapPooled;
import static io.netty5.buffer.api.BufferAllocator.onHeapUnpooled;
import static io.netty5.handler.codec.ByteToMessageDecoder.COMPOSITE_CUMULATOR;
import static io.netty5.handler.codec.ByteToMessageDecoder.MERGE_CUMULATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ByteToMessageDecoderTest {
    private static final String PARAMETERIZED_NAME = "allocator = {0}, cumulator = {1}";

    private BufferAllocator allocator;

    public static Stream<Arguments> allocators() {
        return Stream.of(
                arguments(onHeapUnpooled(), MERGE_CUMULATOR),
                arguments(onHeapUnpooled(), COMPOSITE_CUMULATOR),
                arguments(offHeapUnpooled(), MERGE_CUMULATOR),
                arguments(offHeapUnpooled(), COMPOSITE_CUMULATOR),
                arguments(onHeapPooled(), MERGE_CUMULATOR),
                arguments(onHeapPooled(), COMPOSITE_CUMULATOR),
                arguments(offHeapPooled(), MERGE_CUMULATOR),
                arguments(offHeapPooled(), COMPOSITE_CUMULATOR)
        );
    }

    @BeforeEach
    public void closeAllocator() {
        if (allocator != null) {
            allocator.close();
        }
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("allocators")
    public void removeSelf(BufferAllocator allocator, Cumulator cumulator) {
        this.allocator = allocator;
        EmbeddedChannel channel = new EmbeddedChannel(new ByteToMessageDecoder(cumulator) {
            private boolean removed;

            @Override
            protected void decode(ChannelHandlerContext ctx, Buffer in) {
                assertFalse(removed);
                in.readByte();
                removed = true;
                ctx.pipeline().remove(this);
            }
        });

        try (Buffer buf = newBufferWithData(allocator, 'a', 'b', 'c')) {
            channel.writeInbound(buf.copy());
            try (Buffer b = channel.readInbound()) {
                buf.readByte();
                assertContentEquals(b, buf);
            }
        }
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("allocators")
    public void removeSelfThenWriteToBuffer(BufferAllocator allocator, Cumulator cumulator) {
        this.allocator = allocator;
        try (Buffer buf = newBufferWithData(allocator, 4, 'a', 'b', 'c')) {
            EmbeddedChannel channel = new EmbeddedChannel(new ByteToMessageDecoder(cumulator) {
                private boolean removed;

                @Override
                protected void decode(ChannelHandlerContext ctx, Buffer in) {
                    assertFalse(removed);
                    in.readByte();
                    removed = true;
                    ctx.pipeline().remove(this);

                    // This should not let it keep calling decode
                    buf.writeByte((byte) 'd');
                }
            });

            channel.writeInbound(buf.copy());
            try (Buffer expected = allocator.allocate(8); Buffer b = channel.readInbound()) {
                expected.writeBytes(new byte[]{'b', 'c'});
                assertContentEquals(expected, b);
            }
        }
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("allocators")
    public void internalBufferClearPostReadFully(BufferAllocator allocator, Cumulator cumulator) {
        this.allocator = allocator;
        Buffer buf = newBufferWithData(allocator, 'a');
        EmbeddedChannel channel = newInternalBufferTestChannel(cumulator, Buffer::readByte);
        assertFalse(channel.writeInbound(buf));
        assertFalse(channel.finish());
        assertFalse(buf.isAccessible());
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("allocators")
    public void internalBufferClearReadPartly(BufferAllocator allocator, Cumulator cumulator) {
        this.allocator = allocator;
        Buffer buf = newBufferWithData(allocator, 'a', 'b');
        EmbeddedChannel channel = newInternalBufferTestChannel(cumulator, Buffer::readByte);
        assertTrue(channel.writeInbound(buf));
        try (Buffer expected = newBufferWithData(allocator, 'b'); Buffer b = channel.readInbound()) {
            assertContentEquals(b, expected);
            assertNull(channel.readInbound());
            assertFalse(channel.finish());
        }
        assertFalse(buf.isAccessible());
    }

    private static EmbeddedChannel newInternalBufferTestChannel(
            Cumulator cumulator, Consumer<Buffer> readBeforeRemove) {
        return new EmbeddedChannel(new ByteToMessageDecoder(cumulator) {
            @Override
            protected void decode(ChannelHandlerContext ctx, Buffer in) {
                Buffer buffer = internalBuffer();
                assertNotNull(buffer);
                assertTrue(buffer.isAccessible());
                readBeforeRemove.accept(in);
                // Removal from pipeline should clear internal buffer
                ctx.pipeline().remove(this);
            }

            @Override
            protected void handlerRemoved0(ChannelHandlerContext ctx) {
                assertCumulationReleased(internalBuffer());
            }
        });
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("allocators")
    public void handlerRemovedWillNotReleaseBufferIfDecodeInProgress(BufferAllocator allocator, Cumulator cumulator) {
        this.allocator = allocator;
        EmbeddedChannel channel = new EmbeddedChannel(new ByteToMessageDecoder(cumulator) {
            @Override
            protected void decode(ChannelHandlerContext ctx, Buffer in) {
                ctx.pipeline().remove(this);
                assertTrue(in.isAccessible());
            }

            @Override
            protected void handlerRemoved0(ChannelHandlerContext ctx) {
                assertCumulationReleased(internalBuffer());
            }
        });

        Buffer buf = newBufferWithRandomBytes(allocator);
        assertTrue(channel.writeInbound(buf));
        assertTrue(channel.finishAndReleaseAll());
        assertFalse(buf.isAccessible());
    }

    private static void assertCumulationReleased(Buffer buffer) {
        assertTrue(buffer == null || !buffer.isAccessible(), "unexpected value: " + buffer);
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("allocators")
    public void fireChannelReadCompleteOnInactive(BufferAllocator allocator, Cumulator cumulator) throws Exception {
        this.allocator = allocator;
        final BlockingQueue<Integer> queue = new LinkedBlockingDeque<>();
        final Buffer buf = newBufferWithData(allocator, 'a', 'b');
        EmbeddedChannel channel = new EmbeddedChannel(new ByteToMessageDecoder(cumulator) {
            @Override
            protected void decode(ChannelHandlerContext ctx, Buffer in) {
                int readable = in.readableBytes();
                assertTrue(readable > 0);
                in.readerOffset(in.readerOffset() + readable);
            }

            @Override
            protected void decodeLast(ChannelHandlerContext ctx, Buffer in) {
                assertFalse(in.readableBytes() > 0);
                ctx.fireChannelRead("data");
            }
        }, new ChannelHandler() {
            @Override
            public void channelInactive(ChannelHandlerContext ctx) {
                queue.add(3);
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                queue.add(1);
            }

            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) {
                if (!ctx.channel().isActive()) {
                    queue.add(2);
                }
            }
        });
        assertFalse(channel.writeInbound(buf));
        assertFalse(channel.finish());
        assertEquals(1, (int) queue.take());
        assertEquals(2, (int) queue.take());
        assertEquals(3, (int) queue.take());
        assertTrue(queue.isEmpty());
        assertFalse(buf.isAccessible());
    }

    // See https://github.com/netty/netty/issues/4635
    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("allocators")
    public void removeWhileInCallDecode(BufferAllocator allocator, Cumulator cumulator) {
        this.allocator = allocator;
        final Object upgradeMessage = new Object();
        final ByteToMessageDecoder decoder = new ByteToMessageDecoder(cumulator) {
            @Override
            protected void decode(ChannelHandlerContext ctx, Buffer in) {
                assertEquals('a', in.readByte());
                ctx.fireChannelRead(upgradeMessage);
            }
        };

        EmbeddedChannel channel = new EmbeddedChannel(decoder, new ChannelHandler() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                if (msg == upgradeMessage) {
                    ctx.pipeline().remove(decoder);
                    return;
                }
                ctx.fireChannelRead(msg);
            }
        });

        try (Buffer buf = newBufferWithData(allocator, 'a', 'b', 'c')) {
            assertTrue(channel.writeInbound(buf.copy()));
            try (Buffer b = channel.readInbound()) {
                buf.readByte();
                assertContentEquals(b, buf);
                assertFalse(channel.finish());
            }
        }
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("allocators")
    public void decodeLastEmptyBuffer(BufferAllocator allocator, Cumulator cumulator) {
        this.allocator = allocator;
        EmbeddedChannel channel = new EmbeddedChannel(new ByteToMessageDecoder(cumulator) {
            @Override
            protected void decode(ChannelHandlerContext ctx, Buffer in) {
                int readable = in.readableBytes();
                assertTrue(readable > 0);
                ctx.fireChannelRead(transferBytes(ctx.bufferAllocator(), in, readable));
            }
        });

        try (Buffer buf = newBufferWithRandomBytes(allocator)) {
            assertTrue(channel.writeInbound(buf.copy()));
            try (Buffer b = channel.readInbound()) {
                assertContentEquals(b, buf);
            }
            assertNull(channel.readInbound());
            assertFalse(channel.finish());
            assertNull(channel.readInbound());
        }
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("allocators")
    public void decodeLastNonEmptyBuffer(BufferAllocator allocator, Cumulator cumulator) {
        this.allocator = allocator;
        EmbeddedChannel channel = new EmbeddedChannel(new ByteToMessageDecoder(cumulator) {
            private boolean decodeLast;

            @Override
            protected void decode(ChannelHandlerContext ctx, Buffer in) {
                int readable = in.readableBytes();
                assertTrue(readable > 0);
                if (!decodeLast && readable == 1) {
                    return;
                }
                final int length = decodeLast ? readable : readable - 1;
                ctx.fireChannelRead(transferBytes(ctx.bufferAllocator(), in, length));
            }

            @Override
            protected void decodeLast(ChannelHandlerContext ctx, Buffer in) throws Exception {
                assertFalse(decodeLast);
                decodeLast = true;
                super.decodeLast(ctx, in);
            }
        });
        try (Buffer buf = newBufferWithRandomBytes(allocator)) {
            assertTrue(channel.writeInbound(buf.copy()));
            try (Buffer b = channel.readInbound()) {
                assertContentEquals(b, buf.copy(0, buf.readableBytes() - 1));
            }

            assertNull(channel.readInbound());
            assertTrue(channel.finish());

            try (Buffer b1 = channel.readInbound()) {
                assertContentEquals(b1, buf.copy(buf.readableBytes() - 1, 1));
            }

            assertNull(channel.readInbound());
        }
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("allocators")
    public void readOnlyBuffer(BufferAllocator allocator, Cumulator cumulator) {
        this.allocator = allocator;
        EmbeddedChannel channel = new EmbeddedChannel(new ByteToMessageDecoder(cumulator) {
            @Override
            protected void decode(ChannelHandlerContext ctx, Buffer in) { }
        });
        assertFalse(channel.writeInbound(newBufferWithData(allocator, 8, 'a').makeReadOnly()));
        assertFalse(channel.writeInbound(newBufferWithData(allocator, 'b')));
        assertFalse(channel.writeInbound(newBufferWithData(allocator, 'c').makeReadOnly()));
        assertFalse(channel.finish());
    }

    static class WriteFailingBuffer extends BufferStub {
        private final Error error = new Error();
        private int untilFailure;

        WriteFailingBuffer(BufferAllocator allocator, int untilFailure, int capacity) {
            super(allocator.allocate(capacity));
            this.untilFailure = untilFailure;
        }

        @Override
        public Buffer writeBytes(Buffer source) {
            if (--untilFailure <= 0) {
                throw error;
            }
            return super.writeBytes(source);
        }

        Error writeError() {
            return error;
        }
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("allocators")
    public void releaseWhenMergeCumulateThrows(BufferAllocator allocator) {
        this.allocator = allocator;
        try (WriteFailingBuffer oldCumulation = new WriteFailingBuffer(allocator, 1, 64)) {
            oldCumulation.writeByte((byte) 0);
            Buffer in = newBufferWithRandomBytes(allocator, 12);
            final Error err = assertThrows(Error.class, () -> MERGE_CUMULATOR.cumulate(allocator, oldCumulation, in));
            assertSame(oldCumulation.writeError(), err);
            assertFalse(in.isAccessible());
            assertTrue(oldCumulation.isAccessible());
        }
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("allocators")
    public void releaseWhenMergeCumulateThrowsInExpand(BufferAllocator allocator) {
        this.allocator = allocator;
        final WriteFailingBuffer cumulation = new WriteFailingBuffer(allocator, 1, 16) {
            @Override
            public int readableBytes() {
                return 1;
            }
        };

        Buffer in = newBufferWithRandomBytes(allocator, 12);
        Throwable thrown = null;
        try {
            BufferAllocator mockAlloc = mock(BufferAllocator.class);
            MERGE_CUMULATOR.cumulate(mockAlloc, cumulation, in);
        } catch (Throwable t) {
            thrown = t;
        }

        assertFalse(in.isAccessible());

        assertSame(cumulation.writeError(), thrown);
        assertTrue(cumulation.isAccessible());
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("allocators")
    public void releaseWhenMergeCumulateThrowsInExpandAndCumulatorIsReadOnly(BufferAllocator allocator) {
        this.allocator = allocator;
        Buffer oldCumulation = newBufferWithData(allocator, 8, (char) 1).makeReadOnly();
        final WriteFailingBuffer newCumulation = new WriteFailingBuffer(allocator, 1, 16) ;

        Buffer in = newBufferWithRandomBytes(allocator, 12);
        Throwable thrown = null;
        try {
            BufferAllocator mockAlloc = mock(BufferAllocator.class);
            when(mockAlloc.allocate(anyInt())).thenReturn(newCumulation);
            MERGE_CUMULATOR.cumulate(mockAlloc, oldCumulation, in);
        } catch (Throwable t) {
            thrown = t;
        }

        assertFalse(in.isAccessible());

        assertSame(newCumulation.writeError(), thrown);
        assertFalse(oldCumulation.isAccessible());
        assertTrue(newCumulation.isAccessible());
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("allocators")
    public void releaseWhenCompositeCumulateThrows(BufferAllocator allocator) {
        this.allocator = allocator;
        Buffer buffer = newBufferWithRandomBytes(allocator);
        try (CompositeBuffer cumulation = allocator.compose(buffer.send())) {
            Buffer in = allocator.allocate(0);
            in.close(); // Cause the cumulator to throw.

            assertThrows(Exception.class, () -> COMPOSITE_CUMULATOR.cumulate(allocator, cumulation, in));
            assertFalse(in.isAccessible());
        }
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("allocators")
    public void doesNotOverRead(BufferAllocator allocator, Cumulator cumulator) {
        this.allocator = allocator;
        class ReadInterceptingHandler implements ChannelHandler {
            private int readsTriggered;

            @Override
            public void read(ChannelHandlerContext ctx, ReadBufferAllocator readBufferAllocator) {
                readsTriggered++;
                ctx.read();
            }
        }
        ReadInterceptingHandler interceptor = new ReadInterceptingHandler();

        EmbeddedChannel channel = new EmbeddedChannel();
        channel.setOption(ChannelOption.AUTO_READ, false);
        channel.pipeline().addLast(interceptor, new FixedLengthFrameDecoder(3, cumulator));
        assertEquals(0, interceptor.readsTriggered);

        // 0 complete frames, 1 partial frame: SHOULD trigger a read
        channel.writeInbound(newBufferWithData(allocator, new byte[] { 0, 1 }));
        assertEquals(1, interceptor.readsTriggered);

        // 2 complete frames, 0 partial frames: should NOT trigger a read
        channel.writeInbound(newBufferWithData(allocator, new byte[]{2}),
                newBufferWithData(allocator, new byte[]{3, 4, 5}));
        assertEquals(1, interceptor.readsTriggered);

        // 1 complete frame, 1 partial frame: should NOT trigger a read
        channel.writeInbound(newBufferWithData(allocator, new byte[] { 6, 7, 8 }),
                newBufferWithData(allocator, new byte[] { 9 }));
        assertEquals(1, interceptor.readsTriggered);

        // 1 complete frame, 1 partial frame: should NOT trigger a read
        channel.writeInbound(newBufferWithData(allocator, new byte[] { 10, 11 }),
                newBufferWithData(allocator, new byte[] { 12 }));
        assertEquals(1, interceptor.readsTriggered);

        // 0 complete frames, 1 partial frame: SHOULD trigger a read
        channel.writeInbound(newBufferWithData(allocator, new byte[] { 13 }));
        assertEquals(2, interceptor.readsTriggered);

        // 1 complete frame, 0 partial frames: should NOT trigger a read
        channel.writeInbound(newBufferWithData(allocator, new byte[] { 14 }));
        assertEquals(2, interceptor.readsTriggered);

        for (int i = 0; i < 5; i++) {
            try (Buffer read = channel.readInbound()) {
                assertEquals(i * 3, read.getByte(0));
                assertEquals(i * 3 + 1, read.getByte(1));
                assertEquals(i * 3 + 2, read.getByte(2));
            }
        }
        assertFalse(channel.finish());
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("allocators")
    public void testDisorder(BufferAllocator allocator, Cumulator cumulator) {
        this.allocator = allocator;
        ByteToMessageDecoder decoder = new ByteToMessageDecoder(cumulator) {
            int count;

            //read 4 byte then remove this decoder
            @Override
            protected void decode(ChannelHandlerContext ctx, Buffer in) {
                ctx.fireChannelRead(in.readByte());
                if (++count >= 4) {
                    ctx.pipeline().remove(this);
                }
            }
        };
        EmbeddedChannel channel = new EmbeddedChannel(decoder);
        assertTrue(channel.writeInbound(newBufferWithData(allocator, new byte[]{1, 2, 3, 4, 5})));
        assertEquals((byte) 1, (Byte) channel.readInbound());
        assertEquals((byte) 2, (Byte) channel.readInbound());
        assertEquals((byte) 3, (Byte) channel.readInbound());
        assertEquals((byte) 4, (Byte) channel.readInbound());
        Buffer buffer5 = channel.readInbound();
        assertNotNull(buffer5);
        assertEquals((byte) 5, buffer5.readByte());
        assertFalse(buffer5.readableBytes() > 0);
        assertTrue(buffer5.isAccessible());
        assertFalse(channel.finish());
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("allocators")
    public void testDecodeLast(BufferAllocator allocator, Cumulator cumulator) {
        this.allocator = allocator;
        final AtomicBoolean removeHandler = new AtomicBoolean();
        EmbeddedChannel channel = new EmbeddedChannel(new ByteToMessageDecoder(cumulator) {

            @Override
            protected void decode(ChannelHandlerContext ctx, Buffer in) {
                if (removeHandler.get()) {
                    ctx.pipeline().remove(this);
                }
            }
        });

        try (Buffer buf = newBufferWithRandomBytes(allocator)) {
            assertFalse(channel.writeInbound(buf.copy()));
            assertNull(channel.readInbound());
            removeHandler.set(true);
            // This should trigger channelInputClosed(...)
            channel.pipeline().fireChannelShutdown(ChannelShutdownDirection.Inbound);

            assertTrue(channel.finish());
            try (Buffer b = channel.readInbound()) {
                assertContentEquals(buf, b);
            }
            assertNull(channel.readInbound());
        }
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("allocators")
    public void testHighVolume(BufferAllocator allocator, Cumulator cumulator) {
        this.allocator = allocator;
        SplittableRandom rng = new SplittableRandom();
        AtomicInteger receiveCounter = new AtomicInteger();
        AtomicBoolean lastMessage = new AtomicBoolean();

        EmbeddedChannel channel = new EmbeddedChannel(new ByteToMessageDecoder(cumulator) {
            @Override
            protected void decode(ChannelHandlerContext ctx, Buffer in) {
                // Don't split off the input buffer like what would normally happen.
                // Force the decoder to discard read bytes.
                // Also, don't always read unless this is the last message.
                // This will force the decoder to buffer data across packets.
                int valuesAvailable = in.readableBytes() / Integer.BYTES;
                int valuesToRead = lastMessage.get() ? valuesAvailable : rng.nextInt(0, valuesAvailable + 1);
                for (int i = 0; i < valuesToRead; i++) {
                    int value = in.readInt();
                    assertThat(value).isEqualTo(1 + receiveCounter.get());
                    receiveCounter.set(value);
                }
            }
        });
        int sendCounter = 0;
        do {
            int valueCapacity = rng.nextInt(10, 1000);
            int valuesSkipped = rng.nextInt(0, valueCapacity / 4);
            int valuesWritten = rng.nextInt(0, valueCapacity - valuesSkipped);
            Buffer buf = allocator.allocate(valueCapacity * Integer.BYTES);
            buf.skipWritableBytes(valuesSkipped * Integer.BYTES);
            buf.skipReadableBytes(valuesSkipped * Integer.BYTES);
            for (int i = 0; i < valuesWritten; i++) {
                buf.writeInt(++sendCounter);
            }
            channel.writeInbound(buf);
        } while (sendCounter < 1_000_000);
        lastMessage.set(true);
        channel.flushInbound();
        channel.finishAndReleaseAll();

        assertThat(receiveCounter.get()).isEqualTo(sendCounter);
    }

    private static Buffer newBufferWithRandomBytes(BufferAllocator allocator) {
        return newBufferWithRandomBytes(allocator, 1024);
    }

    private static Buffer newBufferWithRandomBytes(BufferAllocator allocator, int length) {
        final Buffer buf = allocator.allocate(length);
        byte[] bytes = new byte[length];
        ThreadLocalRandom.current().nextBytes(bytes);
        buf.writeBytes(bytes);
        return buf;
    }

    private static Buffer newBufferWithData(BufferAllocator allocator, int capacity, char... data) {
        final Buffer buf = allocator.allocate(capacity);
        for (char datum : data) {
            buf.writeByte((byte) datum);
        }
        return buf;
    }

    private static Buffer newBufferWithData(BufferAllocator allocator, char... data) {
        return newBufferWithData(allocator, data.length, data);
    }

    private static Buffer newBufferWithData(BufferAllocator allocator, byte... data) {
        return allocator.allocate(data.length).writeBytes(data);
    }

    private static void assertContentEquals(Buffer actual, Buffer expected) {
        assertArrayEquals(readByteArray(expected), readByteArray(actual));
    }

    private static byte[] readByteArray(Buffer buf) {
        byte[] bs = new byte[buf.readableBytes()];
        buf.copyInto(buf.readerOffset(), bs, 0, bs.length);
        buf.readerOffset(buf.writerOffset());
        return bs;
    }

    private static Buffer transferBytes(BufferAllocator allocator, Buffer src, int length) {
        final Buffer msg = allocator.allocate(length);
        src.copyInto(src.readerOffset(), msg, 0, length);
        msg.writerOffset(length);
        src.readerOffset(length);
        return msg;
    }
}
