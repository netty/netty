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
package io.netty.buffer;

import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.buffer.Unpooled.unreleasableBuffer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class ByteBufUtilTest {
    private static final String PARAMETERIZED_NAME = "bufferType = {0}";

    private enum BufferType {
        DIRECT_UNPOOLED, DIRECT_POOLED, HEAP_POOLED, HEAP_UNPOOLED
    }

    private ByteBuf buffer(BufferType bufferType, int capacity) {
        switch (bufferType) {

        case DIRECT_UNPOOLED:
            return Unpooled.directBuffer(capacity);
        case HEAP_UNPOOLED:
            return Unpooled.buffer(capacity);
        case DIRECT_POOLED:
            return PooledByteBufAllocator.DEFAULT.directBuffer(capacity);
        case HEAP_POOLED:
            return PooledByteBufAllocator.DEFAULT.buffer(capacity);
        default:
            throw new AssertionError("unexpected buffer type: " + bufferType);
        }
    }

    public static Collection<Object[]> noUnsafe() {
        return Arrays.asList(new Object[][] {
                { BufferType.DIRECT_POOLED },
                { BufferType.DIRECT_UNPOOLED },
                { BufferType.HEAP_POOLED },
                { BufferType.HEAP_UNPOOLED }
        });
    }

    @Test
    public void decodeRandomHexBytesWithEvenLength() {
        decodeRandomHexBytes(256);
    }

    @Test
    public void decodeRandomHexBytesWithOddLength() {
        decodeRandomHexBytes(257);
    }

    private static void decodeRandomHexBytes(int len) {
        byte[] b = new byte[len];
        Random rand = new Random();
        rand.nextBytes(b);
        String hexDump = ByteBufUtil.hexDump(b);
        for (int i = 0; i <= len; i++) {  // going over sub-strings of various lengths including empty byte[].
            byte[] b2 = Arrays.copyOfRange(b, i, b.length);
            byte[] decodedBytes = ByteBufUtil.decodeHexDump(hexDump, i * 2, (len - i) * 2);
            assertArrayEquals(b2, decodedBytes);
        }
    }

    @Test
    public void decodeHexDumpWithOddLength() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                ByteBufUtil.decodeHexDump("abc");
            }
        });
    }

    @Test
    public void decodeHexDumpWithInvalidChar() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                ByteBufUtil.decodeHexDump("fg");
            }
        });
    }

    @Test
    public void testIndexOf() {
        ByteBuf haystack = Unpooled.copiedBuffer("abc123", CharsetUtil.UTF_8);
        assertEquals(0, ByteBufUtil.indexOf(Unpooled.copiedBuffer("a", CharsetUtil.UTF_8), haystack));
        assertEquals(1, ByteBufUtil.indexOf(Unpooled.copiedBuffer("bc".getBytes(CharsetUtil.UTF_8)), haystack));
        assertEquals(2, ByteBufUtil.indexOf(Unpooled.copiedBuffer("c".getBytes(CharsetUtil.UTF_8)), haystack));
        assertEquals(0, ByteBufUtil.indexOf(Unpooled.copiedBuffer("abc12".getBytes(CharsetUtil.UTF_8)), haystack));
        assertEquals(-1, ByteBufUtil.indexOf(Unpooled.copiedBuffer("abcdef".getBytes(CharsetUtil.UTF_8)), haystack));
        assertEquals(-1, ByteBufUtil.indexOf(Unpooled.copiedBuffer("abc12x".getBytes(CharsetUtil.UTF_8)), haystack));
        assertEquals(-1, ByteBufUtil.indexOf(Unpooled.copiedBuffer("abc123def".getBytes(CharsetUtil.UTF_8)), haystack));

        final ByteBuf needle = Unpooled.copiedBuffer("abc12", CharsetUtil.UTF_8);
        haystack.readerIndex(1);
        needle.readerIndex(1);
        assertEquals(0, ByteBufUtil.indexOf(needle, haystack));
        haystack.readerIndex(2);
        needle.readerIndex(3);
        assertEquals(1, ByteBufUtil.indexOf(needle, haystack));
        haystack.readerIndex(1);
        needle.readerIndex(2);
        assertEquals(1, ByteBufUtil.indexOf(needle, haystack));
        haystack.release();

        haystack = Unpooled.copiedBuffer("123aab123", CharsetUtil.UTF_8);
        assertEquals(3, ByteBufUtil.indexOf(Unpooled.copiedBuffer("aab", CharsetUtil.UTF_8), haystack));
        haystack.release();
        needle.release();
    }

    @Test
    public void equalsBufferSubsections() {
        byte[] b1 = new byte[128];
        byte[] b2 = new byte[256];
        Random rand = new Random();
        rand.nextBytes(b1);
        rand.nextBytes(b2);
        final int iB1 = b1.length / 2;
        final int iB2 = iB1 + b1.length;
        final int length = b1.length - iB1;
        System.arraycopy(b1, iB1, b2, iB2, length);
        assertTrue(ByteBufUtil.equals(Unpooled.wrappedBuffer(b1), iB1, Unpooled.wrappedBuffer(b2), iB2, length));
    }

    private static int random(Random r, int min, int max) {
        return r.nextInt((max - min) + 1) + min;
    }

    @Test
    public void notEqualsBufferSubsections() {
        byte[] b1 = new byte[50];
        byte[] b2 = new byte[256];
        Random rand = new Random();
        rand.nextBytes(b1);
        rand.nextBytes(b2);
        final int iB1 = b1.length / 2;
        final int iB2 = iB1 + b1.length;
        final int length = b1.length - iB1;
        System.arraycopy(b1, iB1, b2, iB2, length);
        // Randomly pick an index in the range that will be compared and make the value at that index differ between
        // the 2 arrays.
        int diffIndex = random(rand, iB1, iB1 + length - 1);
        ++b1[diffIndex];
        assertFalse(ByteBufUtil.equals(Unpooled.wrappedBuffer(b1), iB1, Unpooled.wrappedBuffer(b2), iB2, length));
    }

    @Test
    public void notEqualsBufferOverflow() {
        byte[] b1 = new byte[8];
        byte[] b2 = new byte[16];
        Random rand = new Random();
        rand.nextBytes(b1);
        rand.nextBytes(b2);
        final int iB1 = b1.length / 2;
        final int iB2 = iB1 + b1.length;
        final int length = b1.length - iB1;
        System.arraycopy(b1, iB1, b2, iB2, length - 1);
        assertFalse(ByteBufUtil.equals(Unpooled.wrappedBuffer(b1), iB1, Unpooled.wrappedBuffer(b2), iB2,
                Math.max(b1.length, b2.length) * 2));
    }

    @Test
    public void notEqualsBufferUnderflow() {
        final byte[] b1 = new byte[8];
        final byte[] b2 = new byte[16];
        Random rand = new Random();
        rand.nextBytes(b1);
        rand.nextBytes(b2);
        final int iB1 = b1.length / 2;
        final int iB2 = iB1 + b1.length;
        final int length = b1.length - iB1;
        System.arraycopy(b1, iB1, b2, iB2, length - 1);
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                ByteBufUtil.equals(Unpooled.wrappedBuffer(b1), iB1, Unpooled.wrappedBuffer(b2), iB2, -1);
            }
        });
    }

    @SuppressWarnings("deprecation")
    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void writeShortBE(BufferType bufferType) {
        int expected = 0x1234;

        ByteBuf buf = buffer(bufferType, 2).order(ByteOrder.BIG_ENDIAN);
        ByteBufUtil.writeShortBE(buf, expected);
        assertEquals(expected, buf.readShort());
        buf.resetReaderIndex();
        assertEquals(ByteBufUtil.swapShort((short) expected), buf.readShortLE());
        buf.release();

        buf = buffer(bufferType, 2).order(ByteOrder.LITTLE_ENDIAN);
        ByteBufUtil.writeShortBE(buf, expected);
        assertEquals(ByteBufUtil.swapShort((short) expected), buf.readShortLE());
        buf.resetReaderIndex();
        assertEquals(ByteBufUtil.swapShort((short) expected), buf.readShort());
        buf.release();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void setShortBE() {
        int shortValue = 0x1234;

        ByteBuf buf = Unpooled.wrappedBuffer(new byte[2]).order(ByteOrder.BIG_ENDIAN);
        ByteBufUtil.setShortBE(buf, 0, shortValue);
        assertEquals(shortValue, buf.readShort());
        buf.resetReaderIndex();
        assertEquals(ByteBufUtil.swapShort((short) shortValue), buf.readShortLE());
        buf.release();

        buf = Unpooled.wrappedBuffer(new byte[2]).order(ByteOrder.LITTLE_ENDIAN);
        ByteBufUtil.setShortBE(buf, 0, shortValue);
        assertEquals(ByteBufUtil.swapShort((short) shortValue), buf.readShortLE());
        buf.resetReaderIndex();
        assertEquals(ByteBufUtil.swapShort((short) shortValue), buf.readShort());
        buf.release();
    }

    @SuppressWarnings("deprecation")
    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void writeMediumBE(BufferType bufferType) {
        int mediumValue = 0x123456;

        ByteBuf buf = buffer(bufferType, 4).order(ByteOrder.BIG_ENDIAN);
        ByteBufUtil.writeMediumBE(buf, mediumValue);
        assertEquals(mediumValue, buf.readMedium());
        buf.resetReaderIndex();
        assertEquals(ByteBufUtil.swapMedium(mediumValue), buf.readMediumLE());
        buf.release();

        buf = buffer(bufferType, 4).order(ByteOrder.LITTLE_ENDIAN);
        ByteBufUtil.writeMediumBE(buf, mediumValue);
        assertEquals(ByteBufUtil.swapMedium(mediumValue), buf.readMediumLE());
        buf.resetReaderIndex();
        assertEquals(ByteBufUtil.swapMedium(mediumValue), buf.readMedium());
        buf.release();
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testWriteUsAscii(BufferType bufferType) {
        String usAscii = "NettyRocks";
        ByteBuf buf = buffer(bufferType, 16);
        buf.writeBytes(usAscii.getBytes(CharsetUtil.US_ASCII));
        ByteBuf buf2 = buffer(bufferType, 16);
        ByteBufUtil.writeAscii(buf2, usAscii);

        assertEquals(buf, buf2);

        buf.release();
        buf2.release();
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testWriteUsAsciiSwapped(BufferType bufferType) {
        String usAscii = "NettyRocks";
        ByteBuf buf = buffer(bufferType, 16);
        buf.writeBytes(usAscii.getBytes(CharsetUtil.US_ASCII));
        SwappedByteBuf buf2 = new SwappedByteBuf(buffer(bufferType, 16));
        ByteBufUtil.writeAscii(buf2, usAscii);

        assertEquals(buf, buf2);

        buf.release();
        buf2.release();
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testWriteUsAsciiWrapped(BufferType bufferType) {
        String usAscii = "NettyRocks";
        ByteBuf buf = unreleasableBuffer(buffer(bufferType, 16));
        assertWrapped(buf);
        buf.writeBytes(usAscii.getBytes(CharsetUtil.US_ASCII));
        ByteBuf buf2 = unreleasableBuffer(buffer(bufferType, 16));
        assertWrapped(buf2);
        ByteBufUtil.writeAscii(buf2, usAscii);

        assertEquals(buf, buf2);

        buf.unwrap().release();
        buf2.unwrap().release();
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testWriteUsAsciiComposite(BufferType bufferType) {
        String usAscii = "NettyRocks";
        ByteBuf buf = buffer(bufferType, 16);
        buf.writeBytes(usAscii.getBytes(CharsetUtil.US_ASCII));
        ByteBuf buf2 = Unpooled.compositeBuffer().addComponent(
                buffer(bufferType, 8)).addComponent(buffer(bufferType, 24));
        // write some byte so we start writing with an offset.
        buf2.writeByte(1);
        ByteBufUtil.writeAscii(buf2, usAscii);

        // Skip the previously written byte.
        assertEquals(buf, buf2.skipBytes(1));

        buf.release();
        buf2.release();
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testWriteUsAsciiCompositeWrapped(BufferType bufferType) {
        String usAscii = "NettyRocks";
        ByteBuf buf = buffer(bufferType, 16);
        buf.writeBytes(usAscii.getBytes(CharsetUtil.US_ASCII));
        ByteBuf buf2 = new WrappedCompositeByteBuf(Unpooled.compositeBuffer().addComponent(
                buffer(bufferType, 8)).addComponent(buffer(bufferType, 24)));
        // write some byte so we start writing with an offset.
        buf2.writeByte(1);
        ByteBufUtil.writeAscii(buf2, usAscii);

        // Skip the previously written byte.
        assertEquals(buf, buf2.skipBytes(1));

        buf.release();
        buf2.release();
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testWriteUtf8(BufferType bufferType) {
        String usAscii = "Some UTF-8 like äÄ∏ŒŒ";
        ByteBuf buf = buffer(bufferType, 16);
        buf.writeBytes(usAscii.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = buffer(bufferType, 16);
        ByteBufUtil.writeUtf8(buf2, usAscii);

        assertEquals(buf, buf2);

        buf.release();
        buf2.release();
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testWriteUtf8Composite(BufferType bufferType) {
        String utf8 = "Some UTF-8 like äÄ∏ŒŒ";
        ByteBuf buf = buffer(bufferType, 16);
        buf.writeBytes(utf8.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = Unpooled.compositeBuffer().addComponent(
                buffer(bufferType, 8)).addComponent(buffer(bufferType, 24));
        // write some byte so we start writing with an offset.
        buf2.writeByte(1);
        ByteBufUtil.writeUtf8(buf2, utf8);

        // Skip the previously written byte.
        assertEquals(buf, buf2.skipBytes(1));

        buf.release();
        buf2.release();
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testWriteUtf8CompositeWrapped(BufferType bufferType) {
        String utf8 = "Some UTF-8 like äÄ∏ŒŒ";
        ByteBuf buf = buffer(bufferType, 16);
        buf.writeBytes(utf8.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = new WrappedCompositeByteBuf(Unpooled.compositeBuffer().addComponent(
                buffer(bufferType, 8)).addComponent(buffer(bufferType, 24)));
        // write some byte so we start writing with an offset.
        buf2.writeByte(1);
        ByteBufUtil.writeUtf8(buf2, utf8);

        // Skip the previously written byte.
        assertEquals(buf, buf2.skipBytes(1));

        buf.release();
        buf2.release();
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testWriteUtf8Surrogates(BufferType bufferType) {
        // leading surrogate + trailing surrogate
        String surrogateString = new StringBuilder(2)
                                .append('a')
                                .append('\uD800')
                                .append('\uDC00')
                                .append('b')
                                .toString();
        ByteBuf buf = buffer(bufferType, 16);
        buf.writeBytes(surrogateString.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = buffer(bufferType, 16);
        ByteBufUtil.writeUtf8(buf2, surrogateString);

        assertEquals(buf, buf2);
        assertEquals(buf.readableBytes(), ByteBufUtil.utf8Bytes(surrogateString));

        buf.release();
        buf2.release();
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testWriteUtf8InvalidOnlyTrailingSurrogate(BufferType bufferType) {
        String surrogateString = new StringBuilder(2)
                                .append('a')
                                .append('\uDC00')
                                .append('b')
                                .toString();
        ByteBuf buf = buffer(bufferType, 16);
        buf.writeBytes(surrogateString.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = buffer(bufferType, 16);
        ByteBufUtil.writeUtf8(buf2, surrogateString);

        assertEquals(buf, buf2);
        assertEquals(buf.readableBytes(), ByteBufUtil.utf8Bytes(surrogateString));

        buf.release();
        buf2.release();
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testWriteUtf8InvalidOnlyLeadingSurrogate(BufferType bufferType) {
        String surrogateString = new StringBuilder(2)
                                .append('a')
                                .append('\uD800')
                                .append('b')
                                .toString();
        ByteBuf buf = buffer(bufferType, 16);
        buf.writeBytes(surrogateString.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = buffer(bufferType, 16);
        ByteBufUtil.writeUtf8(buf2, surrogateString);

        assertEquals(buf, buf2);
        assertEquals(buf.readableBytes(), ByteBufUtil.utf8Bytes(surrogateString));

        buf.release();
        buf2.release();
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testWriteUtf8InvalidSurrogatesSwitched(BufferType bufferType) {
        String surrogateString = new StringBuilder(2)
                                .append('a')
                                .append('\uDC00')
                                .append('\uD800')
                                .append('b')
                                .toString();
        ByteBuf buf = buffer(bufferType, 16);
        buf.writeBytes(surrogateString.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = buffer(bufferType, 16);
        ByteBufUtil.writeUtf8(buf2, surrogateString);

        assertEquals(buf, buf2);
        assertEquals(buf.readableBytes(), ByteBufUtil.utf8Bytes(surrogateString));

        buf.release();
        buf2.release();
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testWriteUtf8InvalidTwoLeadingSurrogates(BufferType bufferType) {
        String surrogateString = new StringBuilder(2)
                                .append('a')
                                .append('\uD800')
                                .append('\uD800')
                                .append('b')
                                .toString();
        ByteBuf buf = buffer(bufferType, 16);
        buf.writeBytes(surrogateString.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = buffer(bufferType, 16);
        ByteBufUtil.writeUtf8(buf2, surrogateString);

        assertEquals(buf, buf2);
        assertEquals(buf.readableBytes(), ByteBufUtil.utf8Bytes(surrogateString));
        buf.release();
        buf2.release();
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testWriteUtf8InvalidTwoTrailingSurrogates(BufferType bufferType) {
        String surrogateString = new StringBuilder(2)
                                .append('a')
                                .append('\uDC00')
                                .append('\uDC00')
                                .append('b')
                                .toString();
        ByteBuf buf = buffer(bufferType, 16);
        buf.writeBytes(surrogateString.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = buffer(bufferType, 16);
        ByteBufUtil.writeUtf8(buf2, surrogateString);

        assertEquals(buf, buf2);
        assertEquals(buf.readableBytes(), ByteBufUtil.utf8Bytes(surrogateString));

        buf.release();
        buf2.release();
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testWriteUtf8InvalidEndOnLeadingSurrogate(BufferType bufferType) {
        String surrogateString = new StringBuilder(2)
                                .append('\uD800')
                                .toString();
        ByteBuf buf = buffer(bufferType, 16);
        buf.writeBytes(surrogateString.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = buffer(bufferType, 16);
        ByteBufUtil.writeUtf8(buf2, surrogateString);

        assertEquals(buf, buf2);
        assertEquals(buf.readableBytes(), ByteBufUtil.utf8Bytes(surrogateString));

        buf.release();
        buf2.release();
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testWriteUtf8InvalidEndOnTrailingSurrogate(BufferType bufferType) {
        String surrogateString = new StringBuilder(2)
                                .append('\uDC00')
                                .toString();
        ByteBuf buf = buffer(bufferType, 16);
        buf.writeBytes(surrogateString.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = buffer(bufferType, 16);
        ByteBufUtil.writeUtf8(buf2, surrogateString);

        assertEquals(buf, buf2);
        assertEquals(buf.readableBytes(), ByteBufUtil.utf8Bytes(surrogateString));

        buf.release();
        buf2.release();
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testWriteUsAsciiString(BufferType bufferType) {
        AsciiString usAscii = new AsciiString("NettyRocks");
        int expectedCapacity = usAscii.length();
        ByteBuf buf = buffer(bufferType, expectedCapacity);
        buf.writeBytes(usAscii.toString().getBytes(CharsetUtil.US_ASCII));
        ByteBuf buf2 = buffer(bufferType, expectedCapacity);
        ByteBufUtil.writeAscii(buf2, usAscii);

        assertEquals(buf, buf2);

        buf.release();
        buf2.release();
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testWriteUtf8Wrapped(BufferType bufferType) {
        String usAscii = "Some UTF-8 like äÄ∏ŒŒ";
        ByteBuf buf = unreleasableBuffer(buffer(bufferType, 16));
        assertWrapped(buf);
        buf.writeBytes(usAscii.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = unreleasableBuffer(buffer(bufferType, 16));
        assertWrapped(buf2);
        ByteBufUtil.writeUtf8(buf2, usAscii);

        assertEquals(buf, buf2);

        buf.unwrap().release();
        buf2.unwrap().release();
    }

    private static void assertWrapped(ByteBuf buf) {
        assertTrue(buf instanceof WrappedByteBuf);
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testWriteUtf8Subsequence(BufferType bufferType) {
        String usAscii = "Some UTF-8 like äÄ∏ŒŒ";
        ByteBuf buf = buffer(bufferType, 16);
        buf.writeBytes(usAscii.substring(5, 18).getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = buffer(bufferType, 16);
        ByteBufUtil.writeUtf8(buf2, usAscii, 5, 18);

        assertEquals(buf, buf2);

        buf.release();
        buf2.release();
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testWriteUtf8SubsequenceSplitSurrogate(BufferType bufferType) {
        String usAscii = "\uD800\uDC00"; // surrogate pair: one code point, two chars
        ByteBuf buf = buffer(bufferType, 16);
        buf.writeBytes(usAscii.substring(0, 1).getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = buffer(bufferType, 16);
        ByteBufUtil.writeUtf8(buf2, usAscii, 0, 1);

        assertEquals(buf, buf2);

        buf.release();
        buf2.release();
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testReserveAndWriteUtf8Subsequence(BufferType bufferType) {
        String usAscii = "Some UTF-8 like äÄ∏ŒŒ";
        ByteBuf buf = buffer(bufferType, 16);
        buf.writeBytes(usAscii.substring(5, 18).getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = buffer(bufferType, 16);
        int count = ByteBufUtil.reserveAndWriteUtf8(buf2, usAscii, 5, 18, 16);

        assertEquals(buf, buf2);
        assertEquals(buf.readableBytes(), count);

        buf.release();
        buf2.release();
    }

    @Test
    public void testUtf8BytesSubsequence() {
        String usAscii = "Some UTF-8 like äÄ∏ŒŒ";
        assertEquals(usAscii.substring(5, 18).getBytes(CharsetUtil.UTF_8).length,
                ByteBufUtil.utf8Bytes(usAscii, 5, 18));
    }

    private static final int[][] INVALID_RANGES = new int[][] {
        { -1, 5 }, { 5, 30 }, { 10, 5 }
    };

    interface TestMethod {
        int invoke(Object... args);
    }

    private void testInvalidSubsequences(BufferType bufferType, TestMethod method) {
        for (int [] range : INVALID_RANGES) {
            ByteBuf buf = buffer(bufferType, 16);
            try {
                method.invoke(buf, "Some UTF-8 like äÄ∏ŒŒ", range[0], range[1]);
                fail("Did not throw IndexOutOfBoundsException for range (" + range[0] + ", " + range[1] + ")");
            } catch (IndexOutOfBoundsException iiobe) {
                // expected
            } finally {
                assertFalse(buf.isReadable());
                buf.release();
            }
        }
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testWriteUtf8InvalidSubsequences(BufferType bufferType) {
        testInvalidSubsequences(bufferType, new TestMethod() {
            @Override
            public int invoke(Object... args) {
                return ByteBufUtil.writeUtf8((ByteBuf) args[0], (String) args[1],
                        (Integer) args[2], (Integer) args[3]);
            }
        });
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testReserveAndWriteUtf8InvalidSubsequences(BufferType bufferType) {
        testInvalidSubsequences(bufferType, new TestMethod() {
            @Override
            public int invoke(Object... args) {
                return ByteBufUtil.reserveAndWriteUtf8((ByteBuf) args[0], (String) args[1],
                        (Integer) args[2], (Integer) args[3], 32);
            }
        });
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testUtf8BytesInvalidSubsequences(BufferType bufferType) {
        testInvalidSubsequences(bufferType,
                new TestMethod() {
                    @Override
                    public int invoke(Object... args) {
                        return ByteBufUtil.utf8Bytes((String) args[1], (Integer) args[2], (Integer) args[3]);
                    }
                });
    }

    @Test
    public void testDecodeUsAscii() {
        testDecodeString("This is a test", CharsetUtil.US_ASCII);
    }

    @Test
    public void testDecodeUtf8() {
        testDecodeString("Some UTF-8 like äÄ∏ŒŒ", CharsetUtil.UTF_8);
    }

    private static void testDecodeString(String text, Charset charset) {
        ByteBuf buffer = Unpooled.copiedBuffer(text, charset);
        assertEquals(text, ByteBufUtil.decodeString(buffer, 0, buffer.readableBytes(), charset));
        buffer.release();
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testToStringDoesNotThrowIndexOutOfBounds(BufferType bufferType) {
        CompositeByteBuf buffer = Unpooled.compositeBuffer();
        try {
            byte[] bytes = "1234".getBytes(CharsetUtil.UTF_8);
            buffer.addComponent(buffer(bufferType, bytes.length).writeBytes(bytes));
            buffer.addComponent(buffer(bufferType, bytes.length).writeBytes(bytes));
            assertEquals("1234", buffer.toString(bytes.length, bytes.length, CharsetUtil.UTF_8));
        } finally {
            buffer.release();
        }
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testIsTextWithUtf8(BufferType bufferType) {
        byte[][] validUtf8Bytes = {
                "netty".getBytes(CharsetUtil.UTF_8),
                {(byte) 0x24},
                {(byte) 0xC2, (byte) 0xA2},
                {(byte) 0xE2, (byte) 0x82, (byte) 0xAC},
                {(byte) 0xF0, (byte) 0x90, (byte) 0x8D, (byte) 0x88},
                {(byte) 0x24,
                        (byte) 0xC2, (byte) 0xA2,
                        (byte) 0xE2, (byte) 0x82, (byte) 0xAC,
                        (byte) 0xF0, (byte) 0x90, (byte) 0x8D, (byte) 0x88} // multiple characters
        };
        for (byte[] bytes : validUtf8Bytes) {
            assertIsText(bufferType, bytes, true, CharsetUtil.UTF_8);
        }
        byte[][] invalidUtf8Bytes = {
                {(byte) 0x80},
                {(byte) 0xF0, (byte) 0x82, (byte) 0x82, (byte) 0xAC}, // Overlong encodings
                {(byte) 0xC2},                                        // not enough bytes
                {(byte) 0xE2, (byte) 0x82},                           // not enough bytes
                {(byte) 0xF0, (byte) 0x90, (byte) 0x8D},              // not enough bytes
                {(byte) 0xC2, (byte) 0xC0},                           // not correct bytes
                {(byte) 0xE2, (byte) 0x82, (byte) 0xC0},              // not correct bytes
                {(byte) 0xF0, (byte) 0x90, (byte) 0x8D, (byte) 0xC0}, // not correct bytes
                {(byte) 0xC1, (byte) 0x80},                           // out of lower bound
                {(byte) 0xE0, (byte) 0x80, (byte) 0x80},              // out of lower bound
                {(byte) 0xED, (byte) 0xAF, (byte) 0x80}               // out of upper bound
        };
        for (byte[] bytes : invalidUtf8Bytes) {
            assertIsText(bufferType, bytes, false, CharsetUtil.UTF_8);
        }
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testIsTextWithoutOptimization(BufferType bufferType) {
        byte[] validBytes = {(byte) 0x01, (byte) 0xD8, (byte) 0x37, (byte) 0xDC};
        byte[] invalidBytes = {(byte) 0x01, (byte) 0xD8};

        assertIsText(bufferType, validBytes, true, CharsetUtil.UTF_16LE);
        assertIsText(bufferType, invalidBytes, false, CharsetUtil.UTF_16LE);
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testIsTextWithAscii(BufferType bufferType) {
        byte[] validBytes = {(byte) 0x00, (byte) 0x01, (byte) 0x37, (byte) 0x7F};
        byte[] invalidBytes = {(byte) 0x80, (byte) 0xFF};

        assertIsText(bufferType, validBytes, true, CharsetUtil.US_ASCII);
        assertIsText(bufferType, invalidBytes, false, CharsetUtil.US_ASCII);
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testIsTextWithInvalidIndexAndLength(BufferType bufferType) {
        ByteBuf buffer = buffer(bufferType, 4);
        try {
            buffer.writeBytes(new byte[4]);
            int[][] validIndexLengthPairs = {
                    {4, 0},
                    {0, 4},
                    {1, 3},
            };
            for (int[] pair : validIndexLengthPairs) {
                assertTrue(ByteBufUtil.isText(buffer, pair[0], pair[1], CharsetUtil.US_ASCII));
            }
            int[][] invalidIndexLengthPairs = {
                    {4, 1},
                    {-1, 2},
                    {3, -1},
                    {3, -2},
                    {5, 0},
                    {1, 5},
            };
            for (int[] pair : invalidIndexLengthPairs) {
                try {
                    ByteBufUtil.isText(buffer, pair[0], pair[1], CharsetUtil.US_ASCII);
                    fail("Expected IndexOutOfBoundsException");
                } catch (IndexOutOfBoundsException e) {
                    // expected
                }
            }
        } finally {
            buffer.release();
        }
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testUtf8Bytes(BufferType bufferType) {
        final String s = "Some UTF-8 like äÄ∏ŒŒ";
        checkUtf8Bytes(bufferType, s);
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testUtf8BytesWithSurrogates(BufferType bufferType) {
        final String s = "a\uD800\uDC00b";
        checkUtf8Bytes(bufferType, s);
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testUtf8BytesWithNonSurrogates3Bytes(BufferType bufferType) {
        final String s = "a\uE000b";
        checkUtf8Bytes(bufferType, s);
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testUtf8BytesWithNonSurrogatesNonAscii(BufferType bufferType) {
        final char nonAscii = (char) 0x81;
        final String s = "a" + nonAscii + "b";
        checkUtf8Bytes(bufferType, s);
    }

    private void checkUtf8Bytes(BufferType bufferType, final CharSequence charSequence) {
        final ByteBuf buf = buffer(bufferType, ByteBufUtil.utf8MaxBytes(charSequence));
        try {
            final int writtenBytes = ByteBufUtil.writeUtf8(buf, charSequence);
            final int utf8Bytes = ByteBufUtil.utf8Bytes(charSequence);
            assertEquals(writtenBytes, utf8Bytes);
        } finally {
            buf.release();
        }
    }

    private void assertIsText(BufferType bufferType, byte[] bytes, boolean expected, Charset charset) {
        ByteBuf buffer = buffer(bufferType, bytes.length);
        try {
            buffer.writeBytes(bytes);
            assertEquals(expected, ByteBufUtil.isText(buffer, charset));
        } finally {
            buffer.release();
        }
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testIsTextMultiThreaded(BufferType bufferType) throws Throwable {
        assumeTrue(bufferType == BufferType.HEAP_UNPOOLED);
        final ByteBuf buffer = Unpooled.copiedBuffer("Hello, World!", CharsetUtil.ISO_8859_1);

        try {
            final AtomicInteger counter = new AtomicInteger(60000);
            final AtomicReference<Throwable> errorRef = new AtomicReference<Throwable>();
            List<Thread> threads = new ArrayList<Thread>();
            for (int i = 0; i < 10; i++) {
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            while (errorRef.get() == null && counter.decrementAndGet() > 0) {
                                assertTrue(ByteBufUtil.isText(buffer, CharsetUtil.ISO_8859_1));
                            }
                        } catch (Throwable cause) {
                            errorRef.compareAndSet(null, cause);
                        }
                    }
                });
                threads.add(thread);
            }
            for (Thread thread : threads) {
                thread.start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            Throwable error = errorRef.get();
            if (error != null) {
                throw error;
            }
        } finally {
            buffer.release();
        }
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testGetBytes(BufferType bufferType) {
        final ByteBuf buf = buffer(bufferType, 4);
        try {
            checkGetBytes(buf);
        } finally {
            buf.release();
        }
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testGetBytesHeapWithNonZeroArrayOffset(BufferType bufferType) {
        assumeTrue(bufferType == BufferType.HEAP_UNPOOLED);
        final ByteBuf buf = buffer(bufferType, 5);
        try {
            buf.setByte(0, 0x05);

            final ByteBuf slice = buf.slice(1, 4);
            slice.writerIndex(0);

            assertTrue(slice.hasArray());
            assertThat(slice.arrayOffset(), is(1));
            assertThat(slice.array().length, is(buf.capacity()));

            checkGetBytes(slice);
        } finally {
            buf.release();
        }
    }

    @ParameterizedTest(name = PARAMETERIZED_NAME)
    @MethodSource("noUnsafe")
    public void testGetBytesHeapWithArrayLengthGreaterThanCapacity(BufferType bufferType) {
        assumeTrue(bufferType == BufferType.HEAP_UNPOOLED);
        final ByteBuf buf = buffer(bufferType, 5);
        try {
            buf.setByte(4, 0x05);

            final ByteBuf slice = buf.slice(0, 4);
            slice.writerIndex(0);

            assertTrue(slice.hasArray());
            assertThat(slice.arrayOffset(), is(0));
            assertThat(slice.array().length, greaterThan(slice.capacity()));

            checkGetBytes(slice);
        } finally {
            buf.release();
        }
    }

    private static void checkGetBytes(final ByteBuf buf) {
        buf.writeInt(0x01020304);

        byte[] expected = { 0x01, 0x02, 0x03, 0x04 };
        assertArrayEquals(expected, ByteBufUtil.getBytes(buf));
        assertArrayEquals(expected, ByteBufUtil.getBytes(buf, 0, buf.readableBytes(), false));

        expected = new byte[] { 0x01, 0x02, 0x03 };
        assertArrayEquals(expected, ByteBufUtil.getBytes(buf, 0, 3));
        assertArrayEquals(expected, ByteBufUtil.getBytes(buf, 0, 3, false));

        expected = new byte[] { 0x02, 0x03, 0x04 };
        assertArrayEquals(expected, ByteBufUtil.getBytes(buf, 1, 3));
        assertArrayEquals(expected, ByteBufUtil.getBytes(buf, 1, 3, false));

        expected = new byte[] { 0x02, 0x03 };
        assertArrayEquals(expected, ByteBufUtil.getBytes(buf, 1, 2));
        assertArrayEquals(expected, ByteBufUtil.getBytes(buf, 1, 2, false));
    }
}
