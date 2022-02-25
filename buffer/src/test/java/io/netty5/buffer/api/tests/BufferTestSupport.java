/*
 * Copyright 2021 The Netty Project
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
package io.netty5.buffer.api.tests;

import io.netty5.buffer.api.AllocationType;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.BufferClosedException;
import io.netty5.buffer.api.CompositeBuffer;
import io.netty5.buffer.api.MemoryManager;
import io.netty5.buffer.api.internal.ResourceSupport;
import io.netty5.buffer.api.pool.PooledBufferAllocator;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;

import static io.netty5.buffer.api.internal.Statics.acquire;
import static io.netty5.buffer.api.tests.Fixture.Properties.DIRECT;
import static io.netty5.buffer.api.tests.Fixture.Properties.HEAP;
import static io.netty5.buffer.api.tests.Fixture.Properties.POOLED;
import static java.nio.ByteOrder.BIG_ENDIAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public abstract class BufferTestSupport {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(BufferTestSupport.class);
    public static ExecutorService executor;

    private static final Memoize<Fixture[]> INITIAL_COMBINATIONS = new Memoize<>(
            () -> initialFixturesForEachImplementation().toArray(Fixture[]::new));
    private static final Memoize<Fixture[]> ALL_COMBINATIONS = new Memoize<>(
            () -> fixtureCombinations(initialFixturesForEachImplementation()).toArray(Fixture[]::new));
    private static final Memoize<Fixture[]> ALL_ALLOCATORS = new Memoize<>(
            () -> Arrays.stream(ALL_COMBINATIONS.get())
                    .toArray(Fixture[]::new));
    private static final Memoize<Fixture[]> NON_COMPOSITE = new Memoize<>(
            () -> Arrays.stream(ALL_COMBINATIONS.get())
                    .filter(f -> !f.isComposite())
                    .toArray(Fixture[]::new));
    private static final Memoize<Fixture[]> HEAP_ALLOCS = new Memoize<>(
            () -> Arrays.stream(ALL_COMBINATIONS.get())
                    .filter(f -> f.isHeap())
                    .toArray(Fixture[]::new));
    private static final Memoize<Fixture[]> DIRECT_ALLOCS = new Memoize<>(
            () -> Arrays.stream(ALL_COMBINATIONS.get())
                    .filter(f -> f.isDirect())
                    .toArray(Fixture[]::new));
    private static final Memoize<Fixture[]> POOLED_ALLOCS = new Memoize<>(
            () -> Arrays.stream(ALL_COMBINATIONS.get())
                    .filter(f -> f.isPooled())
                    .toArray(Fixture[]::new));

    protected static Predicate<Fixture> filterOfTheDay(int percentage) {
        Instant today = Instant.now().truncatedTo(ChronoUnit.DAYS); // New seed every day.
        SplittableRandom rng = new SplittableRandom(today.hashCode());
        AtomicInteger counter = new AtomicInteger();
        return fixture -> counter.getAndIncrement() < 1 || rng.nextInt(0, 100) < percentage;
    }

    static Fixture[] allocators() {
        return ALL_ALLOCATORS.get();
    }

    static Fixture[] nonCompositeAllocators() {
        return NON_COMPOSITE.get();
    }

    static Fixture[] heapAllocators() {
        return HEAP_ALLOCS.get();
    }

    static Fixture[] directAllocators() {
        return DIRECT_ALLOCS.get();
    }

    static Fixture[] pooledAllocators() {
        return POOLED_ALLOCS.get();
    }

    static Fixture[] initialCombinations() {
        return INITIAL_COMBINATIONS.get();
    }

    static List<Fixture> initialAllocators() {
        return List.of(
                new Fixture("heap", BufferAllocator::onHeapUnpooled, HEAP),
                new Fixture("direct", BufferAllocator::offHeapUnpooled, DIRECT),
                new Fixture("pooledHeap", BufferAllocator::onHeapPooled, POOLED, HEAP),
                new Fixture("pooledDirect", BufferAllocator::offHeapPooled, POOLED, DIRECT),
                new Fixture("pooledDirect", () ->
                        new PooledBufferAllocator(MemoryManager.instance(), true,
                                PooledBufferAllocator.defaultNumDirectArena(), PooledBufferAllocator.defaultPageSize(),
                                PooledBufferAllocator.defaultMaxOrder(), PooledBufferAllocator.defaultSmallCacheSize(),
                                PooledBufferAllocator.defaultNormalCacheSize(), true, 64),
                        POOLED, DIRECT)
        );
    }

    static List<Fixture> initialFixturesForEachImplementation() {
        List<Fixture> initFixtures = initialAllocators();

        // Multiply by all MemoryManagers.
        List<Throwable> failedManagers = new ArrayList<>();
        List<MemoryManager> loadableManagers = new ArrayList<>();
        MemoryManager.availableManagers().forEach(provider -> {
            try {
                loadableManagers.add(provider.get());
            } catch (ServiceConfigurationError | Exception e) {
                logger.debug("Could not load implementation for testing", e);
                failedManagers.add(e);
            }
        });
        if (loadableManagers.isEmpty()) {
            AssertionError error = new AssertionError("Failed to load any memory managers implementations.");
            for (Throwable failure : failedManagers) {
                error.addSuppressed(failure);
            }
            throw error;
        }
        initFixtures = initFixtures.stream().flatMap(f -> {
            Builder<Fixture> builder = Stream.builder();
            for (MemoryManager managers : loadableManagers) {
                char[] chars = managers.implementationName().toCharArray();
                for (int i = 1, j = 1; i < chars.length; i++) {
                    if (Character.isUpperCase(chars[i])) {
                        chars[j++] = chars[i];
                    }
                }
                String managersName = String.valueOf(chars, 0, 2);
                builder.add(new Fixture(f + "/" + managersName,
                                        () -> MemoryManager.using(managers, f), f.getProperties()));
            }
            return builder.build();
        }).collect(Collectors.toList());
        return initFixtures;
    }

    private abstract static class TestAllocator implements BufferAllocator {
        @Override
        public Supplier<Buffer> constBufferSupplier(byte[] bytes) {
            Buffer base = allocate(bytes.length).writeBytes(bytes).makeReadOnly();
            return () -> base; // Technically off-spec.
        }
    }

    static Stream<Fixture> fixtureCombinations(List<Fixture> initFixtures) {
        Builder<Fixture> builder = Stream.builder();
        initFixtures.forEach(builder);

        // Add 2-way composite buffers of all combinations.
        for (Fixture first : initFixtures) {
            for (Fixture second : initFixtures) {
                builder.add(new Fixture("compose(" + first + ", " + second + ')', () -> {
                    return new TestAllocator() {
                        final BufferAllocator a = first.get();
                        final BufferAllocator b = second.get();
                        @Override
                        public Buffer allocate(int size) {
                            int half = size / 2;
                            try (Buffer firstHalf = a.allocate(half);
                                 Buffer secondHalf = b.allocate(size - half)) {
                                return CompositeBuffer.compose(a, firstHalf.send(), secondHalf.send());
                            }
                        }

                        @Override
                        public boolean isPooling() {
                            return a.isPooling();
                        }

                        @Override
                        public AllocationType getAllocationType() {
                            return a.getAllocationType();
                        }

                        @Override
                        public void close() {
                            a.close();
                            b.close();
                        }
                    };
                }, Fixture.Properties.COMPOSITE));
            }
        }

        // Also add a 3-way composite buffer.
        builder.add(new Fixture("compose(heap,heap,heap)", () -> {
            return new TestAllocator() {
                final BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
                @Override
                public Buffer allocate(int size) {
                    int part = size / 3;
                    try (Buffer a = allocator.allocate(part);
                         Buffer b = allocator.allocate(part);
                         Buffer c = allocator.allocate(size - part * 2)) {
                        return CompositeBuffer.compose(allocator, a.send(), b.send(), c.send());
                    }
                }

                @Override
                public boolean isPooling() {
                    return allocator.isPooling();
                }

                @Override
                public AllocationType getAllocationType() {
                    return allocator.getAllocationType();
                }

                @Override
                public void close() {
                    allocator.close();
                }
            };
        }, Fixture.Properties.COMPOSITE));

        for (Fixture fixture : initFixtures) {
            builder.add(new Fixture(fixture + ".ensureWritable", () -> {
                return new TestAllocator() {
                    final BufferAllocator allocator = fixture.createAllocator();

                    @Override
                    public Buffer allocate(int size) {
                        if (size < 2) {
                            return allocator.allocate(size);
                        }
                        var buf = allocator.allocate(size - 1);
                        buf.ensureWritable(size);
                        return buf;
                    }

                    @Override
                    public boolean isPooling() {
                        return allocator.isPooling();
                    }

                    @Override
                    public AllocationType getAllocationType() {
                        return allocator.getAllocationType();
                    }

                    @Override
                    public void close() {
                        allocator.close();
                    }
                };
            }, fixture.getProperties()));
            builder.add(new Fixture(fixture + ".compose.ensureWritable", () -> {
                return new TestAllocator() {
                    final BufferAllocator allocator = fixture.createAllocator();

                    @Override
                    public Buffer allocate(int size) {
                        if (size < 2) {
                            return allocator.allocate(size);
                        }
                        var buf = CompositeBuffer.compose(allocator);
                        buf.ensureWritable(size);
                        return buf;
                    }

                    @Override
                    public boolean isPooling() {
                        return allocator.isPooling();
                    }

                    @Override
                    public AllocationType getAllocationType() {
                        return allocator.getAllocationType();
                    }

                    @Override
                    public void close() {
                        allocator.close();
                    }
                };
            }, Fixture.Properties.COMPOSITE));
        }

        var stream = builder.build();
        return stream.flatMap(BufferTestSupport::injectSplits);
    }

    private static Stream<Fixture> injectSplits(Fixture f) {
        Builder<Fixture> builder = Stream.builder();
        builder.add(f);
        builder.add(new Fixture(f + ".split", () -> {
            return new TestAllocator() {
                final BufferAllocator allocatorBase = f.get();

                @Override
                public Buffer allocate(int size) {
                    try (Buffer buf = allocatorBase.allocate(size + 1)) {
                        buf.writerOffset(size);
                        return buf.split().writerOffset(0);
                    }
                }

                @Override
                public boolean isPooling() {
                    return allocatorBase.isPooling();
                }

                @Override
                public AllocationType getAllocationType() {
                    return allocatorBase.getAllocationType();
                }

                @Override
                public void close() {
                    allocatorBase.close();
                }
            };
        }, f.getProperties()));
        return builder.build();
    }

    @BeforeAll
    static void startExecutor() throws IOException, ParseException {
        executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = Executors.defaultThreadFactory().newThread(r);
                thread.setName("BufferTest-" + thread.getName());
                thread.setDaemon(true); // Do not prevent shut down of test runner.
                return thread;
            }
        });
    }

    @AfterAll
    static void stopExecutor() throws IOException {
        executor.shutdown();
    }

    static void writeRandomBytes(Buffer buf, int length) {
        byte[] data = new byte[length];
        ThreadLocalRandom.current().nextBytes(data);
        buf.writeBytes(data);
    }

    public static void verifyInaccessible(Buffer buf) {
        verifyReadInaccessible(buf);

        verifyWriteInaccessible(buf, BufferClosedException.class);

        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
             Buffer target = allocator.allocate(24)) {
            assertThrows(BufferClosedException.class, () -> buf.copyInto(0, target, 0, 1));
            assertThrows(BufferClosedException.class, () -> buf.copyInto(0, target, 0, 0));
            assertThrows(BufferClosedException.class, () -> buf.copyInto(0, new byte[1], 0, 1));
            assertThrows(BufferClosedException.class, () -> buf.copyInto(0, new byte[1], 0, 0));
            assertThrows(BufferClosedException.class, () -> buf.copyInto(0, ByteBuffer.allocate(1), 0, 1));
            assertThrows(BufferClosedException.class, () -> buf.copyInto(0, ByteBuffer.allocate(1), 0, 0));
            if (CompositeBuffer.isComposite(buf)) {
                assertThrows(BufferClosedException.class, () -> ((CompositeBuffer) buf).extendWith(target.send()));
            }
        }

        assertThrows(BufferClosedException.class, () -> buf.split());
        assertThrows(BufferClosedException.class, () -> buf.send());
        assertThrows(BufferClosedException.class, () -> acquire((ResourceSupport<?, ?>) buf));
        assertThrows(BufferClosedException.class, () -> buf.copy());
        assertThrows(BufferClosedException.class, () -> buf.openCursor());
        assertThrows(BufferClosedException.class, () -> buf.openCursor(0, 0));
        assertThrows(BufferClosedException.class, () -> buf.openReverseCursor());
        assertThrows(BufferClosedException.class, () -> buf.openReverseCursor(0, 0));
    }

    public static void verifyReadInaccessible(Buffer buf) {
        assertThrows(BufferClosedException.class, () -> buf.readByte());
        assertThrows(BufferClosedException.class, () -> buf.readUnsignedByte());
        assertThrows(BufferClosedException.class, () -> buf.readChar());
        assertThrows(BufferClosedException.class, () -> buf.readShort());
        assertThrows(BufferClosedException.class, () -> buf.readUnsignedShort());
        assertThrows(BufferClosedException.class, () -> buf.readMedium());
        assertThrows(BufferClosedException.class, () -> buf.readUnsignedMedium());
        assertThrows(BufferClosedException.class, () -> buf.readInt());
        assertThrows(BufferClosedException.class, () -> buf.readUnsignedInt());
        assertThrows(BufferClosedException.class, () -> buf.readFloat());
        assertThrows(BufferClosedException.class, () -> buf.readLong());
        assertThrows(BufferClosedException.class, () -> buf.readDouble());

        assertThrows(BufferClosedException.class, () -> buf.getByte(0));
        assertThrows(BufferClosedException.class, () -> buf.getUnsignedByte(0));
        assertThrows(BufferClosedException.class, () -> buf.getChar(0));
        assertThrows(BufferClosedException.class, () -> buf.getShort(0));
        assertThrows(BufferClosedException.class, () -> buf.getUnsignedShort(0));
        assertThrows(BufferClosedException.class, () -> buf.getMedium(0));
        assertThrows(BufferClosedException.class, () -> buf.getUnsignedMedium(0));
        assertThrows(BufferClosedException.class, () -> buf.getInt(0));
        assertThrows(BufferClosedException.class, () -> buf.getUnsignedInt(0));
        assertThrows(BufferClosedException.class, () -> buf.getFloat(0));
        assertThrows(BufferClosedException.class, () -> buf.getLong(0));
        assertThrows(BufferClosedException.class, () -> buf.getDouble(0));
    }

    public static void verifyWriteInaccessible(Buffer buf, Class<? extends RuntimeException> expected) {
        assertThrows(expected, () -> buf.writeByte((byte) 32));
        assertThrows(expected, () -> buf.writeUnsignedByte(32));
        assertThrows(expected, () -> buf.writeChar('3'));
        assertThrows(expected, () -> buf.writeShort((short) 32));
        assertThrows(expected, () -> buf.writeUnsignedShort(32));
        assertThrows(expected, () -> buf.writeMedium(32));
        assertThrows(expected, () -> buf.writeUnsignedMedium(32));
        assertThrows(expected, () -> buf.writeInt(32));
        assertThrows(expected, () -> buf.writeUnsignedInt(32));
        assertThrows(expected, () -> buf.writeFloat(3.2f));
        assertThrows(expected, () -> buf.writeLong(32));
        assertThrows(expected, () -> buf.writeDouble(32));

        assertThrows(expected, () -> buf.setByte(0, (byte) 32));
        assertThrows(expected, () -> buf.setUnsignedByte(0, 32));
        assertThrows(expected, () -> buf.setChar(0, '3'));
        assertThrows(expected, () -> buf.setShort(0, (short) 32));
        assertThrows(expected, () -> buf.setUnsignedShort(0, 32));
        assertThrows(expected, () -> buf.setMedium(0, 32));
        assertThrows(expected, () -> buf.setUnsignedMedium(0, 32));
        assertThrows(expected, () -> buf.setInt(0, 32));
        assertThrows(expected, () -> buf.setUnsignedInt(0, 32));
        assertThrows(expected, () -> buf.setFloat(0, 3.2f));
        assertThrows(expected, () -> buf.setLong(0, 32));
        assertThrows(expected, () -> buf.setDouble(0, 32));

        assertThrows(expected, () -> buf.ensureWritable(1));
        assertThrows(expected, () -> buf.fill((byte) 0));
        assertThrows(expected, () -> buf.compact());
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
             Buffer source = allocator.allocate(8)) {
            assertThrows(expected, () -> source.copyInto(0, buf, 0, 1));
            if (expected == BufferClosedException.class) {
                assertThrows(expected, () -> buf.copyInto(0, source, 0, 1));
            }
        }
    }

    public static void testCopyIntoByteBuffer(Fixture fixture, Function<Integer, ByteBuffer> bbAlloc) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeLong(0x0102030405060708L);
            ByteBuffer buffer = bbAlloc.apply(8);
            buf.copyInto(0, buffer, 0, buffer.capacity());
            assertEquals((byte) 0x01, buffer.get());
            assertEquals((byte) 0x02, buffer.get());
            assertEquals((byte) 0x03, buffer.get());
            assertEquals((byte) 0x04, buffer.get());
            assertEquals((byte) 0x05, buffer.get());
            assertEquals((byte) 0x06, buffer.get());
            assertEquals((byte) 0x07, buffer.get());
            assertEquals((byte) 0x08, buffer.get());
            buffer.clear();

            buffer = bbAlloc.apply(6);
            buf.copyInto(1, buffer, 1, 3);
            assertEquals((byte) 0x00, buffer.get());
            assertEquals((byte) 0x02, buffer.get());
            assertEquals((byte) 0x03, buffer.get());
            assertEquals((byte) 0x04, buffer.get());
            assertEquals((byte) 0x00, buffer.get());
            assertEquals((byte) 0x00, buffer.get());
            buffer.clear();

            buffer = bbAlloc.apply(6);
            buffer.position(3).limit(3);
            buf.copyInto(1, buffer, 1, 3);
            assertEquals(3, buffer.position());
            assertEquals(3, buffer.limit());
            buffer.clear();
            assertEquals((byte) 0x00, buffer.get());
            assertEquals((byte) 0x02, buffer.get());
            assertEquals((byte) 0x03, buffer.get());
            assertEquals((byte) 0x04, buffer.get());
            assertEquals((byte) 0x00, buffer.get());
            assertEquals((byte) 0x00, buffer.get());

            var roBuffer = bbAlloc.apply(6).asReadOnlyBuffer();
            assertThrows(ReadOnlyBufferException.class, () -> buf.copyInto(0, roBuffer, 0, 1));
            assertThrows(ReadOnlyBufferException.class, () -> buf.copyInto(0, roBuffer, 0, 0));
        }
    }

    public static void testCopyIntoBuf(Fixture fixture, Function<Integer, Buffer> bbAlloc) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeLong(0x0102030405060708L);
            try (Buffer buffer = bbAlloc.apply(8)) {
                buffer.writerOffset(8);
                buf.copyInto(0, buffer, 0, buffer.capacity());
                assertEquals((byte) 0x01, buffer.readByte());
                assertEquals((byte) 0x02, buffer.readByte());
                assertEquals((byte) 0x03, buffer.readByte());
                assertEquals((byte) 0x04, buffer.readByte());
                assertEquals((byte) 0x05, buffer.readByte());
                assertEquals((byte) 0x06, buffer.readByte());
                assertEquals((byte) 0x07, buffer.readByte());
                assertEquals((byte) 0x08, buffer.readByte());
                buffer.resetOffsets();
            }

            try (Buffer buffer = bbAlloc.apply(6)) {
                buf.copyInto(1, buffer, 1, 3);
                buffer.writerOffset(6);
                assertEquals((byte) 0x00, buffer.readByte());
                assertEquals((byte) 0x02, buffer.readByte());
                assertEquals((byte) 0x03, buffer.readByte());
                assertEquals((byte) 0x04, buffer.readByte());
                assertEquals((byte) 0x00, buffer.readByte());
                assertEquals((byte) 0x00, buffer.readByte());
            }

            try (Buffer buffer = bbAlloc.apply(6)) {
                buffer.writerOffset(3).readerOffset(3);
                buf.copyInto(1, buffer, 1, 3);
                assertEquals(3, buffer.readerOffset());
                assertEquals(3, buffer.writerOffset());
                buffer.resetOffsets();
                buffer.writerOffset(6);
                assertEquals((byte) 0x00, buffer.readByte());
                assertEquals((byte) 0x02, buffer.readByte());
                assertEquals((byte) 0x03, buffer.readByte());
                assertEquals((byte) 0x04, buffer.readByte());
                assertEquals((byte) 0x00, buffer.readByte());
                assertEquals((byte) 0x00, buffer.readByte());
            }

            buf.resetOffsets();
            buf.writeLong(0x0102030405060708L);
            // Testing copyInto for overlapping writes:
            //
            //          0x0102030405060708
            //            └──┬──┬──┘     │
            //               └─▶└┬───────┘
            //                   ▼
            //          0x0102030102030405
            buf.copyInto(0, buf, 3, 5);
            assertThat(toByteArray(buf)).containsExactly(0x01, 0x02, 0x03, 0x01, 0x02, 0x03, 0x04, 0x05);
        }
    }

    public static void checkByteIteration(Buffer buf) {
        var cursor = buf.openCursor();
        assertFalse(cursor.readByte());
        assertEquals(0, cursor.bytesLeft());
        assertEquals((byte) -1, cursor.getByte());

        buf.writeBytes(new byte[] {1, 2, 3, 4});
        int roff = buf.readerOffset();
        int woff = buf.writerOffset();
        cursor = buf.openCursor();
        assertEquals(4, cursor.bytesLeft());
        assertTrue(cursor.readByte());
        assertEquals((byte) 0x01, cursor.getByte());
        assertEquals((byte) 0x01, cursor.getByte());
        assertEquals(3, cursor.bytesLeft());
        assertTrue(cursor.readByte());
        assertEquals((byte) 0x02, cursor.getByte());
        assertEquals((byte) 0x02, cursor.getByte());
        assertEquals(2, cursor.bytesLeft());
        assertTrue(cursor.readByte());
        assertEquals((byte) 0x03, cursor.getByte());
        assertEquals(1, cursor.bytesLeft());
        assertTrue(cursor.readByte());
        assertEquals((byte) 0x04, cursor.getByte());
        assertEquals(0, cursor.bytesLeft());
        assertFalse(cursor.readByte());
        assertEquals((byte) 0x04, cursor.getByte());
        assertEquals((byte) 0x04, cursor.getByte());
        assertEquals(roff, buf.readerOffset());
        assertEquals(woff, buf.writerOffset());
    }

    public static void checkByteIterationOfRegion(Buffer buf) {
        assertThrows(IllegalArgumentException.class, () -> buf.openCursor(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> buf.openCursor(1, -1));
        assertThrows(IllegalArgumentException.class, () -> buf.openCursor(buf.capacity(), 1));
        assertThrows(IllegalArgumentException.class, () -> buf.openCursor(buf.capacity() - 1, 2));
        assertThrows(IllegalArgumentException.class, () -> buf.openCursor(buf.capacity() - 2, 3));

        var cursor = buf.openCursor(1, 0);
        assertFalse(cursor.readByte());
        assertEquals(0, cursor.bytesLeft());
        assertEquals((byte) -1, cursor.getByte());

        buf.writeBytes(new byte[] {1, 2, 3, 4, 5, 6});
        int roff = buf.readerOffset();
        int woff = buf.writerOffset();
        cursor = buf.openCursor(buf.readerOffset() + 1, buf.readableBytes() - 2);
        assertEquals(4, cursor.bytesLeft());
        assertTrue(cursor.readByte());
        assertEquals((byte) 2, cursor.getByte());
        assertEquals((byte) 2, cursor.getByte());
        assertEquals(3, cursor.bytesLeft());
        assertTrue(cursor.readByte());
        assertEquals((byte) 3, cursor.getByte());
        assertEquals(2, cursor.bytesLeft());
        assertTrue(cursor.readByte());
        assertEquals((byte) 4, cursor.getByte());
        assertEquals(1, cursor.bytesLeft());
        assertTrue(cursor.readByte());
        assertEquals((byte) 5, cursor.getByte());
        assertEquals(0, cursor.bytesLeft());
        assertEquals(0, cursor.bytesLeft());
        assertFalse(cursor.readByte());
        assertEquals(0, cursor.bytesLeft());
        assertEquals((byte) 5, cursor.getByte());

        cursor = buf.openCursor(buf.readerOffset() + 1, 2);
        assertEquals(2, cursor.bytesLeft());
        assertTrue(cursor.readByte());
        assertEquals((byte) 2, cursor.getByte());
        assertEquals(1, cursor.bytesLeft());
        assertTrue(cursor.readByte());
        assertEquals((byte) 3, cursor.getByte());
        assertEquals(0, cursor.bytesLeft());
        assertFalse(cursor.readByte());
        assertEquals(roff, buf.readerOffset());
        assertEquals(woff, buf.writerOffset());
    }

    public static void checkReverseByteIteration(Buffer buf) {
        var cursor = buf.openReverseCursor();
        assertFalse(cursor.readByte());
        assertEquals(0, cursor.bytesLeft());
        assertEquals((byte) -1, cursor.getByte());

        buf.writeBytes(new byte[] {1, 2, 3, 4});
        int roff = buf.readerOffset();
        int woff = buf.writerOffset();
        cursor = buf.openReverseCursor();
        assertEquals(4, cursor.bytesLeft());
        assertTrue(cursor.readByte());
        assertEquals((byte) 4, cursor.getByte());
        assertEquals((byte) 4, cursor.getByte());
        assertEquals(3, cursor.bytesLeft());
        assertTrue(cursor.readByte());
        assertEquals((byte) 3, cursor.getByte());
        assertEquals(2, cursor.bytesLeft());
        assertTrue(cursor.readByte());
        assertEquals((byte) 2, cursor.getByte());
        assertEquals(1, cursor.bytesLeft());
        assertTrue(cursor.readByte());
        assertEquals((byte) 1, cursor.getByte());
        assertEquals(0, cursor.bytesLeft());
        assertFalse(cursor.readByte());
        assertEquals((byte) 1, cursor.getByte());
        assertFalse(cursor.readByte());
        assertEquals(0, cursor.bytesLeft());
        assertEquals(roff, buf.readerOffset());
        assertEquals(woff, buf.writerOffset());
    }

    public static void checkReverseByteIterationOfRegion(Buffer buf) {
        assertThrows(IllegalArgumentException.class, () -> buf.openReverseCursor(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> buf.openReverseCursor(0, -1));
        assertThrows(IllegalArgumentException.class, () -> buf.openReverseCursor(0, 2));
        assertThrows(IllegalArgumentException.class, () -> buf.openReverseCursor(1, 3));
        assertThrows(IllegalArgumentException.class, () -> buf.openReverseCursor(buf.capacity(), 0));

        var cursor = buf.openReverseCursor(1, 0);
        assertFalse(cursor.readByte());
        assertEquals(0, cursor.bytesLeft());
        assertEquals((byte) -1, cursor.getByte());

        buf.writeBytes(new byte[] {1, 2, 3, 4, 5, 6, 7});
        int roff = buf.readerOffset();
        int woff = buf.writerOffset();
        cursor = buf.openReverseCursor(buf.writerOffset() - 2, buf.readableBytes() - 2);
        assertEquals(5, cursor.bytesLeft());
        assertTrue(cursor.readByte());
        assertEquals((byte) 6, cursor.getByte());
        assertEquals((byte) 6, cursor.getByte());
        assertEquals(4, cursor.bytesLeft());
        assertTrue(cursor.readByte());
        assertEquals((byte) 5, cursor.getByte());
        assertEquals(3, cursor.bytesLeft());
        assertTrue(cursor.readByte());
        assertEquals((byte) 4, cursor.getByte());
        assertEquals(2, cursor.bytesLeft());
        assertTrue(cursor.readByte());
        assertEquals((byte) 3, cursor.getByte());
        assertEquals(1, cursor.bytesLeft());
        assertTrue(cursor.readByte());
        assertEquals((byte) 2, cursor.getByte());
        assertEquals(0, cursor.bytesLeft());
        assertFalse(cursor.readByte());
        assertEquals((byte) 2, cursor.getByte());
        assertFalse(cursor.readByte());
        assertEquals(0, cursor.bytesLeft());

        cursor = buf.openReverseCursor(buf.readerOffset() + 2, 2);
        assertEquals(2, cursor.bytesLeft());
        assertTrue(cursor.readByte());
        assertEquals((byte) 3, cursor.getByte());
        assertEquals(1, cursor.bytesLeft());
        assertTrue(cursor.readByte());
        assertEquals((byte) 2, cursor.getByte());
        assertEquals(0, cursor.bytesLeft());
        assertFalse(cursor.readByte());
        assertEquals(roff, buf.readerOffset());
        assertEquals(woff, buf.writerOffset());
    }

    public static void verifySplitEmptyCompositeBuffer(Buffer buf) {
        try (Buffer a = buf.split()) {
            a.ensureWritable(4);
            buf.ensureWritable(4);
            a.writeInt(1);
            buf.writeInt(2);
            assertEquals(1, a.readInt());
            assertEquals(2, buf.readInt());
        }
    }

    public static void verifyForEachReadableSingleComponent(Fixture fixture, Buffer buf) {
        buf.forEachReadable(0, (index, component) -> {
            var buffer = component.readableBuffer();
            assertThat(buffer.position()).isZero();
            assertThat(buffer.limit()).isEqualTo(8);
            assertThat(buffer.capacity()).isEqualTo(8);
            assertEquals(0x0102030405060708L, buffer.getLong());

            if (fixture.isDirect()) {
                assertThat(component.readableNativeAddress()).isNotZero();
            } else {
                assertThat(component.readableNativeAddress()).isZero();
            }

            if (component.hasReadableArray()) {
                byte[] array = component.readableArray();
                byte[] arrayCopy = new byte[component.readableArrayLength()];
                System.arraycopy(array, component.readableArrayOffset(), arrayCopy, 0, arrayCopy.length);
                if (buffer.order() == BIG_ENDIAN) {
                    assertThat(arrayCopy).containsExactly(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08);
                } else {
                    assertThat(arrayCopy).containsExactly(0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01);
                }
            }

            assertThrows(ReadOnlyBufferException.class, () -> buffer.put(0, (byte) 0xFF));
            return true;
        });
    }

    public static void verifyForEachWritableSingleComponent(Fixture fixture, Buffer buf) {
        buf.forEachWritable(0, (index, component) -> {
            var buffer = component.writableBuffer();
            assertThat(buffer.position()).isZero();
            assertThat(buffer.limit()).isEqualTo(8);
            assertThat(buffer.capacity()).isEqualTo(8);
            buffer.putLong(0x0102030405060708L);
            buffer.flip();
            assertEquals(0x0102030405060708L, buffer.getLong());
            buf.writerOffset(8);
            assertEquals(0x0102030405060708L, buf.getLong(0));

            if (fixture.isDirect()) {
                assertThat(component.writableNativeAddress()).isNotZero();
            } else {
                assertThat(component.writableNativeAddress()).isZero();
            }

            buf.writerOffset(0);
            if (component.hasWritableArray()) {
                byte[] array = component.writableArray();
                int offset = component.writableArrayOffset();
                byte[] arrayCopy = new byte[component.writableArrayLength()];
                System.arraycopy(array, offset, arrayCopy, 0, arrayCopy.length);
                if (buffer.order() == BIG_ENDIAN) {
                    assertThat(arrayCopy).containsExactly(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08);
                } else {
                    assertThat(arrayCopy).containsExactly(0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01);
                }
            }

            buffer.put(0, (byte) 0xFF);
            assertEquals((byte) 0xFF, buffer.get(0));
            assertEquals((byte) 0xFF, buf.getByte(0));
            return true;
        });
    }

    public static byte[] toByteArray(Buffer buf) {
        byte[] bs = new byte[buf.capacity()];
        buf.copyInto(0, bs, 0, bs.length);
        return bs;
    }

    public static byte[] readByteArray(Buffer buf) {
        byte[] bs = new byte[buf.readableBytes()];
        buf.copyInto(buf.readerOffset(), bs, 0, bs.length);
        buf.readerOffset(buf.writerOffset());
        return bs;
    }

    public static void assertEquals(byte expected, byte actual) {
        if (expected != actual) {
            fail(String.format("expected: %1$s (0x%1$X) but was: %2$s (0x%2$X)", expected, actual));
        }
    }

    public static void assertEquals(char expected, char actual) {
        if (expected != actual) {
            fail(String.format("expected: %s (0x%X) but was: %s (0x%X)",
                               expected, (int) expected, actual, (int) actual));
        }
    }

    public static void assertEquals(short expected, short actual) {
        if (expected != actual) {
            fail(String.format("expected: %1$s (0x%1$X) but was: %2$s (0x%2$X)", expected, actual));
        }
    }

    public static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            fail(String.format("expected: %1$s (0x%1$X) but was: %2$s (0x%2$X)", expected, actual));
        }
    }

    public static void assertEquals(long expected, long actual) {
        if (expected != actual) {
            fail(String.format("expected: %1$s (0x%1$X) but was: %2$s (0x%2$X)", expected, actual));
        }
    }

    public static void assertEquals(float expected, float actual) {
        //noinspection FloatingPointEquality
        if (expected != actual) {
            fail(String.format("expected: %s (0x%X) but was: %s (0x%X)",
                               expected, Float.floatToRawIntBits(expected),
                               actual, Float.floatToRawIntBits(actual)));
        }
    }

    public static void assertEquals(double expected, double actual) {
        //noinspection FloatingPointEquality
        if (expected != actual) {
            fail(String.format("expected: %s (0x%X) but was: %s (0x%X)",
                               expected, Double.doubleToRawLongBits(expected),
                               actual, Double.doubleToRawLongBits(actual)));
        }
    }
}
