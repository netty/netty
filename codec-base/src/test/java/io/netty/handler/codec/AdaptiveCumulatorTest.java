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
package io.netty.handler.codec;

import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static io.netty.util.CharsetUtil.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.in;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AdaptiveCumulatorTest {

    @Nested
    public class CumulateTests {
        private static final String DATA_INITIAL = "0123";
        private static final String DATA_INCOMING = "456789";
        private static final String DATA_CUMULATED = "0123456789";

        private final ByteBufAllocator alloc = new UnpooledByteBufAllocator(false);
        private AdaptiveCumulator cumulator;
        private final UnsupportedOperationException throwingCumulatorError = new UnsupportedOperationException();

        private ByteBuf contiguous;
        private ByteBuf in;

        @BeforeEach
        public void setUp() {
            contiguous = ByteBufUtil.writeAscii(alloc, DATA_INITIAL);
            in = ByteBufUtil.writeAscii(alloc, DATA_INCOMING);

            cumulator = new AdaptiveCumulator(0);
        }

        @Test
        public void cumulate_notReadableCumulation_replacedWithInputAndReleased() {
            contiguous.readerIndex(contiguous.writerIndex());
            assertFalse(contiguous.isReadable());
            ByteBuf cumulation = cumulator.cumulate(alloc, contiguous, in);
            assertEquals(DATA_INCOMING, cumulation.toString(US_ASCII));
            assertEquals(0, contiguous.refCnt());
            assertEquals(1, in.refCnt());
            assertEquals(1, cumulation.refCnt());
            cumulation.release();
        }

        @Test
        public void cumulate_sameBuffer_identityHandled() {
            in.retain(); // simulate double retention when the same buffer is passed twice
            ByteBuf result = cumulator.cumulate(alloc, in, in);
            assertSame(in, result);
            assertEquals(1, in.refCnt(), "The cumulator should release the doubly-retained buffer once");
            in.release();
        }

        @Test
        public void cumulate_contiguousCumulation_newCompositeFromContiguousAndInput() {
            CompositeByteBuf cumulation = (CompositeByteBuf) cumulator.cumulate(alloc, contiguous, in);
            assertEquals(DATA_INITIAL, cumulation.component(0).toString(US_ASCII));
            assertEquals(DATA_INCOMING, cumulation.component(1).toString(US_ASCII));
            assertEquals(DATA_CUMULATED, cumulation.toString(US_ASCII));
            assertEquals(1, contiguous.refCnt());
            assertEquals(1, in.refCnt());
            assertEquals(1, cumulation.refCnt());
            cumulation.release();
        }

        @Test
        public void cumulate_compositeCumulation_inputAppendedAsANewComponent() {
            CompositeByteBuf composite = alloc.compositeBuffer().addComponent(true, contiguous);
            assertSame(composite, cumulator.cumulate(alloc, composite, in));
            assertEquals(DATA_INITIAL, composite.component(0).toString(US_ASCII));
            assertEquals(DATA_INCOMING, composite.component(1).toString(US_ASCII));
            assertEquals(DATA_CUMULATED, composite.toString(US_ASCII));
            assertEquals(1, contiguous.refCnt());
            assertEquals(1, in.refCnt());
            assertEquals(1, composite.refCnt());
            composite.release();
        }

        @Test
        public void cumulate_compositeCumulation_inputReleasedOnError() {
            CompositeByteBuf composite = new CompositeByteBuf(alloc, false, Integer.MAX_VALUE, contiguous) {
                @Override
                public CompositeByteBuf addFlattenedComponents(boolean increaseWriterIndex, ByteBuf buffer) {
                    buffer.release();
                    throw throwingCumulatorError;
                }
            };
            try {
                cumulator.cumulate(alloc, composite, in);
                fail("Cumulator didn't throw");
            } catch (UnsupportedOperationException actualError) {
                assertSame(throwingCumulatorError, actualError);
                assertEquals(0, in.refCnt());
                assertEquals(1, composite.refCnt());
                assertEquals(1, contiguous.refCnt());
            } finally {
                composite.release();
            }
        }

        @Test
        public void cumulate_contiguousCumulation_inputAndNewCompositeReleasedOnError() {
            CompositeByteBuf newComposite = new CompositeByteBuf(alloc, false, Integer.MAX_VALUE) {
                int count;

                @Override
                public CompositeByteBuf addFlattenedComponents(boolean increaseWriterIndex, ByteBuf buffer) {
                    if (++count == 2) {
                        buffer.release();
                        throw throwingCumulatorError;
                    }
                    return super.addFlattenedComponents(increaseWriterIndex, buffer);
                }
            };
            ByteBufAllocator mockAlloc = mock(ByteBufAllocator.class);
            when(mockAlloc.compositeBuffer(anyInt())).thenReturn(newComposite);

            try {
                cumulator.cumulate(mockAlloc, contiguous, in);
                fail("Cumulator didn't throw");
            } catch (UnsupportedOperationException actualError) {
                assertSame(throwingCumulatorError, actualError);
                assertEquals(0, in.refCnt());
                assertEquals(0, newComposite.refCnt());
                assertEquals(1, contiguous.refCnt());
            }
        }

        @Test
        public void mustNotReleaseNonOwnedCumulationIfAddInputFails() {
            final UnsupportedOperationException expectedError = new UnsupportedOperationException();
            ByteBufAllocator throwingAlloc = new AbstractByteBufAllocator(false) {
                @Override
                protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
                    throw expectedError;
                }

                @Override
                protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
                    throw expectedError;
                }

                @Override
                public boolean isDirectBufferPooled() {
                    return false;
                }
            };

            AdaptiveCumulator cumulator = new AdaptiveCumulator(1024);
            ByteBufAllocator bufAlloc = new UnpooledByteBufAllocator(false);
            ByteBuf cumulation = bufAlloc.buffer(4, 4).writeBytes("0123".getBytes(US_ASCII));
            ByteBuf in = bufAlloc.buffer().writeBytes("456".getBytes(US_ASCII));

            try {
                assertThatThrownBy(() -> cumulator.cumulate(throwingAlloc, cumulation, in)).isSameAs(expectedError);
                assertEquals(0, in.refCnt(), "Incoming buffer must be released on failure");
                assertEquals(1, cumulation.refCnt(),
                        "Caller-owned cumulation must NOT be released when the merge fails");
            } finally {
                if (cumulation.refCnt() > 0) {
                    cumulation.release();
                }
            }
        }
    }

    @Nested
    @TestInstance(Lifecycle.PER_CLASS)
    public class ShouldComposeTests {
        private final String DATA_INITIAL = "0123";
        private final String DATA_INCOMING = "456789";

        public Stream<Arguments> shouldComposeParams() {
            List<Integer> composeMinSizes = Arrays.asList(0, 9, 10, 11, Integer.MAX_VALUE);
            List<String> tailDatas = Arrays.asList("", DATA_INITIAL);
            List<String> inDatas = Arrays.asList("", DATA_INCOMING);

            return composeMinSizes.stream()
                    .flatMap(cms -> tailDatas.stream()
                            .flatMap(td -> inDatas.stream()
                                    .map(id -> Arguments.of(cms, td, id))));
        }

        private CompositeByteBuf composite;
        private ByteBuf tail;
        private ByteBuf in;
        private ByteBufAllocator alloc = new UnpooledByteBufAllocator(false);

        private void setUp(int composeMinSize, String tailData, String inData) {
            in = ByteBufUtil.writeAscii(alloc, inData);
            tail = ByteBufUtil.writeAscii(alloc, tailData);
            composite = alloc.compositeBuffer(Integer.MAX_VALUE);
            composite.addFlattenedComponents(true, tail);
        }

        private void tearDown() {
            if (composite != null && composite.refCnt() > 0) {
                composite.release();
            }
        }

        @ParameterizedTest(name = "composeMinSize={0}, tailData=\"{1}\", inData=\"{2}\"")
        @MethodSource("shouldComposeParams")
        public void shouldCompose(int composeMinSize, String tailData, String inData) {
            setUp(composeMinSize, tailData, inData);
            try {
                int initialComponents = composite.numComponents();
                AdaptiveCumulator cumulator = new AdaptiveCumulator(composeMinSize);
                ByteBuf result = cumulator.cumulate(alloc, composite, in);

                if (!composite.isReadable()) {
                    assertSame(in, result);
                    composite = null;
                    in.release();
                } else if (!in.isReadable()) {
                    assertSame(composite, result);
                    assertEquals(initialComponents, composite.numComponents());
                    assertEquals(tailData + inData, composite.toString(US_ASCII));
                } else if (tail.readableBytes() + in.readableBytes() >= composeMinSize) {
                    composite = (CompositeByteBuf) result;
                    assertEquals(initialComponents + 1, composite.numComponents());
                    assertEquals(tailData + inData, composite.toString(US_ASCII));
                } else {
                    composite = (CompositeByteBuf) result;
                    assertEquals(initialComponents, composite.numComponents());
                    assertEquals(tailData + inData, composite.toString(US_ASCII));
                }
            } finally {
                tearDown();
            }
        }
    }

    @Nested
    @TestInstance(Lifecycle.PER_CLASS)
    public class MergeWithCompositeTailTests {
        private final String INCOMING_DATA_READABLE = "+incoming";
        private final String INCOMING_DATA_DISCARDABLE = "discard";

        private final String TAIL_DATA_DISCARDABLE = "---";
        private final String TAIL_DATA_READABLE = "tail";
        private final String TAIL_DATA = TAIL_DATA_DISCARDABLE + TAIL_DATA_READABLE;
        private final int TAIL_READER_INDEX = TAIL_DATA_DISCARDABLE.length();
        private final int TAIL_MAX_CAPACITY = 128;

        private final String EXPECTED_TAIL_DATA = "tail+incoming";

        public Stream<Arguments> mergeParams() {
            List<String> headDatas = Arrays.asList("", "head");
            List<Integer> readerIndexes = Arrays.asList(0, 2, 6); // 0, head.length()-2, head.length()+2

            return headDatas.stream().flatMap(hd -> readerIndexes.stream().map(ri -> Arguments.of(hd, ri)));
        }

        private CompositeByteBuf composite;
        private ByteBuf tail;
        private ByteBuf in;
        private final ByteBufAllocator alloc = new io.netty.buffer.PooledByteBufAllocator();

        private void setUp(String compositeHeadData, int compositeReaderIndex) {
            composite = alloc.compositeBuffer();

            ByteBuf head = alloc.buffer().writeBytes(compositeHeadData.getBytes(US_ASCII));
            composite.addFlattenedComponents(true, head);

            tail = alloc.buffer(TAIL_DATA.length(), TAIL_MAX_CAPACITY)
                    .writeBytes(TAIL_DATA.getBytes(US_ASCII))
                    .readerIndex(TAIL_READER_INDEX);

            in = alloc.buffer()
                    .writeBytes(INCOMING_DATA_DISCARDABLE.getBytes(US_ASCII))
                    .writeBytes(INCOMING_DATA_READABLE.getBytes(US_ASCII))
                    .readerIndex(INCOMING_DATA_DISCARDABLE.length());
        }

        private void tearDown() {
            if (composite != null && composite.refCnt() > 0) {
                composite.release();
            }
        }

        private String repeat(String s, int count) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < count; i++) {
                sb.append(s);
            }
            return sb.toString();
        }

        private void assertTailExpanded(String expectedTailReadableData, int expectedNewTailCapacity,
                int compositeReaderIndex, String compositeHeadData) {
            int originalNumComponents = composite.numComponents();
            int compositeReaderIndexBounded = Math.min(compositeReaderIndex, composite.writerIndex());
            composite.readerIndex(compositeReaderIndexBounded);
            boolean wasReadable = composite.isReadable();

            AdaptiveCumulator cumulator = new AdaptiveCumulator(Integer.MAX_VALUE);
            ByteBuf result = cumulator.cumulate(alloc, composite, in);

            if (!wasReadable) {
                assertSame(in, result, "When composite is fully read, cumulate should return in");
                assertEquals(0, composite.refCnt(), "Old composite should be released");
                in.release(); // Free the returned buffer manually
                return;
            }

            composite = (CompositeByteBuf) result;

            assertEquals(originalNumComponents, composite.numComponents(),
                    "When tail is expanded, the number of components in the cumulation must not change");

            ByteBuf newTail = composite.component(composite.numComponents() - 1);

            assertEquals(expectedTailReadableData, newTail.toString(US_ASCII));
            assertEquals(expectedNewTailCapacity, newTail.capacity());

            String newTailDataDiscardable = newTail.toString(0, newTail.readerIndex(), US_ASCII);
            assertEquals("", newTailDataDiscardable,
                    "After tail expansion, its discardable bytes should be discarded");

            assertEquals(0, newTail.readerIndex());
            assertEquals(expectedTailReadableData.length(), newTail.writerIndex());

            assertExpectedCumulation(newTail, expectedTailReadableData, compositeReaderIndexBounded, compositeHeadData);

            assertFalse(in.isReadable(), "Incoming buffer is fully read");
            assertEquals(0, in.refCnt(), "Incoming buffer is released");
        }

        private void assertExpectedCumulation(ByteBuf newTail, String expectedTailReadable,
                int expectedReaderIndex, String compositeHeadData) {
            String expectedCumulationData = compositeHeadData.concat(expectedTailReadable)
                    .substring(expectedReaderIndex);
            assertEquals(expectedCumulationData, composite.toString(US_ASCII));

            int expectedCumulationCapacity = compositeHeadData.length() + expectedTailReadable.length();
            assertEquals(expectedCumulationCapacity, composite.capacity());
            assertEquals(expectedReaderIndex, composite.readerIndex());
            assertEquals(expectedCumulationCapacity, composite.writerIndex());

            assertEquals(1, composite.refCnt());
            assertEquals(1, newTail.refCnt());
        }

        private void assertTailReplaced(int compositeReaderIndex, String compositeHeadData) {
            int cumulationOriginalComponentsNum = composite.numComponents();
            int taiOriginalRefCount = tail.refCnt();
            String expectedTailReadable = tail.toString(US_ASCII) + in.toString(US_ASCII);
            int expectedReallocatedTailCapacity = alloc.calculateNewCapacity(
                    expectedTailReadable.length(), Integer.MAX_VALUE);

            int compositeReaderIndexBounded = Math.min(compositeReaderIndex, composite.writerIndex());
            composite.readerIndex(compositeReaderIndexBounded);
            boolean wasReadable = composite.isReadable();

            AdaptiveCumulator cumulator = new AdaptiveCumulator(Integer.MAX_VALUE);
            ByteBuf result = cumulator.cumulate(alloc, composite, in);

            if (!wasReadable) {
                assertSame(in, result, "When composite is fully read, cumulate should return in");
                assertEquals(0, composite.refCnt(), "Old composite should be released");
                in.release(); // Free the returned buffer manually
                return;
            }

            composite = (CompositeByteBuf) result;

            assertEquals(cumulationOriginalComponentsNum, composite.numComponents());
            ByteBuf replacedTail = composite.component(composite.numComponents() - 1);

            assertEquals(0, in.readableBytes());
            assertEquals(expectedTailReadable, replacedTail.toString(US_ASCII));

            assertEquals(0, replacedTail.readerIndex());
            assertEquals(expectedTailReadable.length(), replacedTail.writerIndex());
            assertEquals(expectedReallocatedTailCapacity, replacedTail.capacity());

            assertExpectedCumulation(replacedTail, expectedTailReadable,
                    compositeReaderIndexBounded, compositeHeadData);

            assertFalse(in.isReadable(), "Incoming buffer is fully read");
            assertEquals(0, in.refCnt(), "Incoming buffer is released");
            assertEquals(taiOriginalRefCount - 1, tail.refCnt(), "Replaced tail released once.");
        }

        @ParameterizedTest(name = "head=\"{0}\", readerIndex={1}")
        @MethodSource("mergeParams")
        public void mergeWithCompositeTail_tailExpandable_write(String compositeHeadData, int compositeReaderIndex) {
            setUp(compositeHeadData, compositeReaderIndex);
            try {
                int fitCapacity = tail.capacity() + INCOMING_DATA_READABLE.length();
                tail.capacity(fitCapacity);
                int expectedCapacity = alloc.calculateNewCapacity(fitCapacity, Integer.MAX_VALUE);
                assertTrue(in.readableBytes() <= tail.writableBytes());

                composite.addFlattenedComponents(true, tail);
                assertTailExpanded(EXPECTED_TAIL_DATA, expectedCapacity, compositeReaderIndex, compositeHeadData);
            } finally {
                tearDown();
            }
        }

        @ParameterizedTest(name = "head=\"{0}\", readerIndex={1}")
        @MethodSource("mergeParams")
        public void mergeWithCompositeTail_tailExpandable_fastWrite(
                String compositeHeadData, int compositeReaderIndex) {
            setUp(compositeHeadData, compositeReaderIndex);
            try {
                int tailFastCapacity = alloc.calculateNewCapacity(EXPECTED_TAIL_DATA.length(), Integer.MAX_VALUE);

                composite.addFlattenedComponents(true, tail);
                assertTailExpanded(EXPECTED_TAIL_DATA, tailFastCapacity, compositeReaderIndex, compositeHeadData);
            } finally {
                tearDown();
            }
        }

        @ParameterizedTest(name = "head=\"{0}\", readerIndex={1}")
        @MethodSource("mergeParams")
        public void mergeWithCompositeTail_tailExpandable_reallocateInMemory(
                String compositeHeadData, int compositeReaderIndex) {
            setUp(compositeHeadData, compositeReaderIndex);
            try {
                int tailFastCapacity = tail.writerIndex() + tail.maxFastWritableBytes();
                String inSuffixOverFastBytes = repeat("a", tailFastCapacity + 1);
                int newTailSize = tail.readableBytes() + inSuffixOverFastBytes.length();
                composite.addFlattenedComponents(true, tail);

                in.writeCharSequence(inSuffixOverFastBytes, US_ASCII);
                assertTrue(in.readableBytes() > tail.maxFastWritableBytes());
                assertTrue(in.readableBytes() <= tail.maxWritableBytes());

                int expectedTailCapacity = alloc.calculateNewCapacity(newTailSize, Integer.MAX_VALUE);
                assertTailExpanded(EXPECTED_TAIL_DATA.concat(inSuffixOverFastBytes),
                        expectedTailCapacity, compositeReaderIndex, compositeHeadData);
            } finally {
                tearDown();
            }
        }

        @ParameterizedTest(name = "head=\"{0}\", readerIndex={1}")
        @MethodSource("mergeParams")
        public void mergeWithCompositeTail_tailNotExpandable_maxCapacityReached(
                String compositeHeadData, int compositeReaderIndex) {
            setUp(compositeHeadData, compositeReaderIndex);
            try {
                String tailSuffixFullCapacity = repeat("a", tail.maxWritableBytes());
                tail.writeCharSequence(tailSuffixFullCapacity, US_ASCII);
                composite.addFlattenedComponents(true, tail);
                assertTailReplaced(compositeReaderIndex, compositeHeadData);
            } finally {
                tearDown();
            }
        }

        @ParameterizedTest(name = "head=\"{0}\", readerIndex={1}")
        @MethodSource("mergeParams")
        public void mergeWithCompositeTail_tailNotExpandable_shared(
                String compositeHeadData, int compositeReaderIndex) {
            setUp(compositeHeadData, compositeReaderIndex);
            try {
                tail.retain();
                composite.addFlattenedComponents(true, tail);
                assertTailReplaced(compositeReaderIndex, compositeHeadData);
                tail.release();
            } finally {
                tearDown();
            }
        }

        @ParameterizedTest(name = "head=\"{0}\", readerIndex={1}")
        @MethodSource("mergeParams")
        public void mergeWithCompositeTail_tailNotExpandable_readOnly(
                String compositeHeadData, int compositeReaderIndex) {
            setUp(compositeHeadData, compositeReaderIndex);
            try {
                composite.addFlattenedComponents(true, tail.asReadOnly());
                assertTailReplaced(compositeReaderIndex, compositeHeadData);
            } finally {
                tearDown();
            }
        }

        @ParameterizedTest(name = "head=\"{0}\", readerIndex={1}")
        @MethodSource("mergeParams")
        public void mergeWithCompositeTail_tailExpandable_mergedReleaseOnThrow(
                String compositeHeadData, int compositeReaderIndex) {
            setUp(compositeHeadData, compositeReaderIndex);
            final UnsupportedOperationException expectedError = new UnsupportedOperationException();
            CompositeByteBuf compositeThrows = new CompositeByteBuf(alloc, false, Integer.MAX_VALUE, tail) {
                @Override
                public CompositeByteBuf addFlattenedComponents(boolean increaseWriterIndex, ByteBuf buffer) {
                    buffer.release();
                    throw expectedError;
                }
            };

            try {
                AdaptiveCumulator cumulator = new AdaptiveCumulator(Integer.MAX_VALUE);
                compositeThrows = (CompositeByteBuf) cumulator.cumulate(alloc, compositeThrows, in);
                fail("Cumulator didn't throw");
            } catch (UnsupportedOperationException actualError) {
                assertSame(expectedError, actualError);
                assertEquals(0, tail.refCnt());
                assertEquals(1, compositeThrows.refCnt());
                assertEquals(0, compositeThrows.numComponents());
                assertEquals(0, in.refCnt());
            } finally {
                compositeThrows.release();
                tearDown();
            }
        }

        @ParameterizedTest(name = "head=\"{0}\", readerIndex={1}")
        @MethodSource("mergeParams")
        public void mergeWithCompositeTail_tailNotExpandable_mergedReleaseOnThrow(
                String compositeHeadData, int compositeReaderIndex) {
            setUp(compositeHeadData, compositeReaderIndex);
            final UnsupportedOperationException expectedError = new UnsupportedOperationException();
            CompositeByteBuf compositeRo = new CompositeByteBuf(alloc, false, Integer.MAX_VALUE, tail.asReadOnly()) {
                @Override
                public CompositeByteBuf addFlattenedComponents(boolean increaseWriterIndex, ByteBuf buffer) {
                    buffer.release();
                    throw expectedError;
                }
            };

            int newTailSize = tail.readableBytes() + in.readableBytes();
            ByteBuf newTail = alloc.buffer(alloc.calculateNewCapacity(newTailSize, Integer.MAX_VALUE));
            ByteBufAllocator mockAlloc = mock(ByteBufAllocator.class);
            when(mockAlloc.buffer(anyInt())).thenReturn(newTail);

            try {
                AdaptiveCumulator cumulator = new AdaptiveCumulator(Integer.MAX_VALUE);
                compositeRo = (CompositeByteBuf) cumulator.cumulate(mockAlloc, compositeRo, in);
                fail("Cumulator didn't throw");
            } catch (UnsupportedOperationException actualError) {
                assertSame(expectedError, actualError);
                assertEquals(1, compositeRo.refCnt());
                assertEquals(0, compositeRo.numComponents());
                assertEquals(0, newTail.refCnt());
                assertEquals(0, in.refCnt());
            } finally {
                compositeRo.release();
                if (newTail.refCnt() > 0) {
                    newTail.release();
                }
                tearDown();
            }
        }
    }

    @Nested
    public class MergeWithCompositeTailMiscTests {
        private final ByteBufAllocator alloc = new io.netty.buffer.PooledByteBufAllocator();

        @Test
        public void mergeWithCompositeTail_outOfSyncComposite() {
            AdaptiveCumulator cumulator = new AdaptiveCumulator(1024);

            ByteBuf buf = alloc.buffer(32).writeBytes("---01234".getBytes(US_ASCII));

            CompositeByteBuf composite1 = alloc.compositeBuffer(8).addFlattenedComponents(true, buf.retain());
            assertEquals("---", composite1.readCharSequence(3, US_ASCII).toString());

            CompositeByteBuf composite2 = alloc.compositeBuffer(8).addFlattenedComponents(true, composite1);
            assertEquals("01234", composite2.toString(US_ASCII));

            CompositeByteBuf cumulation = (CompositeByteBuf) cumulator.cumulate(alloc, composite2,
                    ByteBufUtil.writeAscii(alloc, "56789"));
            assertEquals("0123456789", cumulation.toString(US_ASCII));

            assertEquals(1, cumulation.numComponents());
            buf.setByte(5, '*').setByte(11, '$');
            assertEquals("0123456789", cumulation.toString(US_ASCII));
            cumulation.release();
            buf.release();
        }

        @Test
        public void mergeWithCompositeTail_detectsIndicesInNestedComposite() {
            // 1. Setup local allocator and a real cumulator that triggers merging.
            ByteBufAllocator alloc = new UnpooledByteBufAllocator(false);
            AdaptiveCumulator realCumulator = new AdaptiveCumulator(1024);

            // 2. Create a buffer with some "header" data we intend to skip.
            ByteBuf buf = alloc.buffer(32).writeBytes("SKIP01234".getBytes(US_ASCII));

            // 3. Wrap it in a composite buffer and read the "SKIP" part.
            CompositeByteBuf innerComposite = alloc.compositeBuffer()
                    .addFlattenedComponents(true, buf);
            innerComposite.readCharSequence(4, US_ASCII); // Discard "SKIP"

            // 4. Wrap that into a second composite (simulating cumulate() with refCnt > 1).
            // The readable content here is "01234".
            CompositeByteBuf outerComposite = alloc.compositeBuffer()
                    .addFlattenedComponents(true, innerComposite);

            try {
                // 5. Perform a cumulation that triggers mergeWithCompositeTail.
                // Use realCumulator to ensure the merge logic is executed.
                realCumulator.cumulate(alloc, outerComposite,
                        ByteBufUtil.writeAscii(alloc, "56789"));

                // Verify the "SKIP" bytes did not reappear.
                assertThat(outerComposite.toString(US_ASCII)).isEqualTo("0123456789");

                // Verify the merge occurred (resulting in a single component).
                assertThat(outerComposite.numComponents()).isEqualTo(1);
            } finally {
                // Clean up to prevent leaks.
                outerComposite.release();
            }
        }
    }
}
