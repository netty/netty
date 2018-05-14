/*
 * Copyright 2014 The Netty Project
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
package io.netty.buffer;

import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.buffer.Unpooled.unreleasableBuffer;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ByteBufUtilTest {
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

    @Test(expected = IllegalArgumentException.class)
    public void decodeHexDumpWithOddLength() {
        ByteBufUtil.decodeHexDump("abc");
    }

    @Test(expected = IllegalArgumentException.class)
    public void decodeHexDumpWithInvalidChar() {
        ByteBufUtil.decodeHexDump("fg");
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

    @Test (expected = IllegalArgumentException.class)
    public void notEqualsBufferUnderflow() {
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
                -1));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void writeShortBE() {
        int expected = 0x1234;

        ByteBuf buf = Unpooled.buffer(2).order(ByteOrder.BIG_ENDIAN);
        ByteBufUtil.writeShortBE(buf, expected);
        assertEquals(expected, buf.readShort());
        buf.resetReaderIndex();
        assertEquals(ByteBufUtil.swapShort((short) expected), buf.readShortLE());
        buf.release();

        buf = Unpooled.buffer(2).order(ByteOrder.LITTLE_ENDIAN);
        ByteBufUtil.writeShortBE(buf, expected);
        assertEquals((short) expected, buf.readShortLE());
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
        assertEquals((short) shortValue, buf.readShortLE());
        buf.resetReaderIndex();
        assertEquals(ByteBufUtil.swapShort((short) shortValue), buf.readShort());
        buf.release();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void writeMediumBE() {
        int mediumValue = 0x123456;

        ByteBuf buf = Unpooled.buffer(4).order(ByteOrder.BIG_ENDIAN);
        ByteBufUtil.writeMediumBE(buf, mediumValue);
        assertEquals(mediumValue, buf.readMedium());
        buf.resetReaderIndex();
        assertEquals(ByteBufUtil.swapMedium(mediumValue), buf.readMediumLE());
        buf.release();

        buf = Unpooled.buffer(4).order(ByteOrder.LITTLE_ENDIAN);
        ByteBufUtil.writeMediumBE(buf, mediumValue);
        assertEquals(mediumValue, buf.readMediumLE());
        buf.resetReaderIndex();
        assertEquals(ByteBufUtil.swapMedium(mediumValue), buf.readMedium());
        buf.release();
    }

    @Test
    public void testWriteUsAscii() {
        String usAscii = "NettyRocks";
        ByteBuf buf = Unpooled.buffer(16);
        buf.writeBytes(usAscii.getBytes(CharsetUtil.US_ASCII));
        ByteBuf buf2 = Unpooled.buffer(16);
        ByteBufUtil.writeAscii(buf2, usAscii);

        assertEquals(buf, buf2);

        buf.release();
        buf2.release();
    }

    @Test
    public void testWriteUsAsciiSwapped() {
        String usAscii = "NettyRocks";
        ByteBuf buf = Unpooled.buffer(16);
        buf.writeBytes(usAscii.getBytes(CharsetUtil.US_ASCII));
        SwappedByteBuf buf2 = new SwappedByteBuf(Unpooled.buffer(16));
        ByteBufUtil.writeAscii(buf2, usAscii);

        assertEquals(buf, buf2);

        buf.release();
        buf2.release();
    }

    @Test
    public void testWriteUsAsciiWrapped() {
        String usAscii = "NettyRocks";
        ByteBuf buf = unreleasableBuffer(Unpooled.buffer(16));
        assertWrapped(buf);
        buf.writeBytes(usAscii.getBytes(CharsetUtil.US_ASCII));
        ByteBuf buf2 = unreleasableBuffer(Unpooled.buffer(16));
        assertWrapped(buf2);
        ByteBufUtil.writeAscii(buf2, usAscii);

        assertEquals(buf, buf2);

        buf.unwrap().release();
        buf2.unwrap().release();
    }

    @Test
    public void testWriteUtf8() {
        String usAscii = "Some UTF-8 like äÄ∏ŒŒ";
        ByteBuf buf = Unpooled.buffer(16);
        buf.writeBytes(usAscii.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = Unpooled.buffer(16);
        ByteBufUtil.writeUtf8(buf2, usAscii);

        assertEquals(buf, buf2);

        buf.release();
        buf2.release();
    }

    @Test
    public void testWriteUtf8Surrogates() {
        // leading surrogate + trailing surrogate
        String surrogateString = new StringBuilder(2)
                                .append('a')
                                .append('\uD800')
                                .append('\uDC00')
                                .append('b')
                                .toString();
        ByteBuf buf = Unpooled.buffer(16);
        buf.writeBytes(surrogateString.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = Unpooled.buffer(16);
        ByteBufUtil.writeUtf8(buf2, surrogateString);

        assertEquals(buf, buf2);
        assertEquals(buf.readableBytes(), ByteBufUtil.utf8Bytes(surrogateString));

        buf.release();
        buf2.release();
    }

    @Test
    public void testWriteUtf8InvalidOnlyTrailingSurrogate() {
        String surrogateString = new StringBuilder(2)
                                .append('a')
                                .append('\uDC00')
                                .append('b')
                                .toString();
        ByteBuf buf = Unpooled.buffer(16);
        buf.writeBytes(surrogateString.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = Unpooled.buffer(16);
        ByteBufUtil.writeUtf8(buf2, surrogateString);

        assertEquals(buf, buf2);
        assertEquals(buf.readableBytes(), ByteBufUtil.utf8Bytes(surrogateString));

        buf.release();
        buf2.release();
    }

    @Test
    public void testWriteUtf8InvalidOnlyLeadingSurrogate() {
        String surrogateString = new StringBuilder(2)
                                .append('a')
                                .append('\uD800')
                                .append('b')
                                .toString();
        ByteBuf buf = Unpooled.buffer(16);
        buf.writeBytes(surrogateString.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = Unpooled.buffer(16);
        ByteBufUtil.writeUtf8(buf2, surrogateString);

        assertEquals(buf, buf2);
        assertEquals(buf.readableBytes(), ByteBufUtil.utf8Bytes(surrogateString));

        buf.release();
        buf2.release();
    }

    @Test
    public void testWriteUtf8InvalidSurrogatesSwitched() {
        String surrogateString = new StringBuilder(2)
                                .append('a')
                                .append('\uDC00')
                                .append('\uD800')
                                .append('b')
                                .toString();
        ByteBuf buf = Unpooled.buffer(16);
        buf.writeBytes(surrogateString.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = Unpooled.buffer(16);
        ByteBufUtil.writeUtf8(buf2, surrogateString);

        assertEquals(buf, buf2);
        assertEquals(buf.readableBytes(), ByteBufUtil.utf8Bytes(surrogateString));

        buf.release();
        buf2.release();
    }

    @Test
    public void testWriteUtf8InvalidTwoLeadingSurrogates() {
        String surrogateString = new StringBuilder(2)
                                .append('a')
                                .append('\uD800')
                                .append('\uD800')
                                .append('b')
                                .toString();
        ByteBuf buf = Unpooled.buffer(16);
        buf.writeBytes(surrogateString.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = Unpooled.buffer(16);
        ByteBufUtil.writeUtf8(buf2, surrogateString);

        assertEquals(buf, buf2);
        assertEquals(buf.readableBytes(), ByteBufUtil.utf8Bytes(surrogateString));
        buf.release();
        buf2.release();
    }

    @Test
    public void testWriteUtf8InvalidTwoTrailingSurrogates() {
        String surrogateString = new StringBuilder(2)
                                .append('a')
                                .append('\uDC00')
                                .append('\uDC00')
                                .append('b')
                                .toString();
        ByteBuf buf = Unpooled.buffer(16);
        buf.writeBytes(surrogateString.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = Unpooled.buffer(16);
        ByteBufUtil.writeUtf8(buf2, surrogateString);

        assertEquals(buf, buf2);
        assertEquals(buf.readableBytes(), ByteBufUtil.utf8Bytes(surrogateString));

        buf.release();
        buf2.release();
    }

    @Test
    public void testWriteUtf8InvalidEndOnLeadingSurrogate() {
        String surrogateString = new StringBuilder(2)
                                .append('\uD800')
                                .toString();
        ByteBuf buf = Unpooled.buffer(16);
        buf.writeBytes(surrogateString.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = Unpooled.buffer(16);
        ByteBufUtil.writeUtf8(buf2, surrogateString);

        assertEquals(buf, buf2);
        assertEquals(buf.readableBytes(), ByteBufUtil.utf8Bytes(surrogateString));

        buf.release();
        buf2.release();
    }

    @Test
    public void testWriteUtf8InvalidEndOnTrailingSurrogate() {
        String surrogateString = new StringBuilder(2)
                                .append('\uDC00')
                                .toString();
        ByteBuf buf = Unpooled.buffer(16);
        buf.writeBytes(surrogateString.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = Unpooled.buffer(16);
        ByteBufUtil.writeUtf8(buf2, surrogateString);

        assertEquals(buf, buf2);
        assertEquals(buf.readableBytes(), ByteBufUtil.utf8Bytes(surrogateString));

        buf.release();
        buf2.release();
    }

    @Test
    public void testWriteUsAsciiString() {
        AsciiString usAscii = new AsciiString("NettyRocks");
        ByteBuf buf = Unpooled.buffer(16);
        buf.writeBytes(usAscii.toString().getBytes(CharsetUtil.US_ASCII));
        ByteBuf buf2 = Unpooled.buffer(16);
        ByteBufUtil.writeAscii(buf2, usAscii);

        assertEquals(buf, buf2);

        buf.release();
        buf2.release();
    }

    @Test
    public void testWriteUtf8Wrapped() {
        String usAscii = "Some UTF-8 like äÄ∏ŒŒ";
        ByteBuf buf = unreleasableBuffer(Unpooled.buffer(16));
        assertWrapped(buf);
        buf.writeBytes(usAscii.getBytes(CharsetUtil.UTF_8));
        ByteBuf buf2 = unreleasableBuffer(Unpooled.buffer(16));
        assertWrapped(buf2);
        ByteBufUtil.writeUtf8(buf2, usAscii);

        assertEquals(buf, buf2);

        buf.release();
        buf2.release();
    }

    private static void assertWrapped(ByteBuf buf) {
        assertTrue(buf instanceof WrappedByteBuf);
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

    @Test
    public void testToStringDoesNotThrowIndexOutOfBounds() {
        CompositeByteBuf buffer = Unpooled.compositeBuffer();
        try {
            byte[] bytes = "1234".getBytes(CharsetUtil.UTF_8);
            buffer.addComponent(Unpooled.buffer(bytes.length).writeBytes(bytes));
            buffer.addComponent(Unpooled.buffer(bytes.length).writeBytes(bytes));
            assertEquals("1234", buffer.toString(bytes.length, bytes.length, CharsetUtil.UTF_8));
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testIsTextWithUtf8() {
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
            assertIsText(bytes, true, CharsetUtil.UTF_8);
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
            assertIsText(bytes, false, CharsetUtil.UTF_8);
        }
    }

    @Test
    public void testIsTextWithoutOptimization() {
        byte[] validBytes = {(byte) 0x01, (byte) 0xD8, (byte) 0x37, (byte) 0xDC};
        byte[] invalidBytes = {(byte) 0x01, (byte) 0xD8};

        assertIsText(validBytes, true, CharsetUtil.UTF_16LE);
        assertIsText(invalidBytes, false, CharsetUtil.UTF_16LE);
    }

    @Test
    public void testIsTextWithAscii() {
        byte[] validBytes = {(byte) 0x00, (byte) 0x01, (byte) 0x37, (byte) 0x7F};
        byte[] invalidBytes = {(byte) 0x80, (byte) 0xFF};

        assertIsText(validBytes, true, CharsetUtil.US_ASCII);
        assertIsText(invalidBytes, false, CharsetUtil.US_ASCII);
    }

    @Test
    public void testIsTextWithInvalidIndexAndLength() {
        ByteBuf buffer = Unpooled.buffer();
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

    @Test
    public void testUtf8Bytes() {
        final String s = "Some UTF-8 like äÄ∏ŒŒ";
        checkUtf8Bytes(s);
    }

    @Test
    public void testUtf8BytesWithSurrogates() {
        final String s = "a\uD800\uDC00b";
        checkUtf8Bytes(s);
    }

    @Test
    public void testUtf8BytesWithNonSurrogates3Bytes() {
        final String s = "a\uE000b";
        checkUtf8Bytes(s);
    }

    @Test
    public void testUtf8BytesWithNonSurrogatesNonAscii() {
        final char nonAscii = (char) 0x81;
        final String s = "a" + nonAscii + "b";
        checkUtf8Bytes(s);
    }

    private static void checkUtf8Bytes(final CharSequence charSequence) {
        final ByteBuf buf = Unpooled.buffer(ByteBufUtil.utf8MaxBytes(charSequence));
        try {
            final int writtenBytes = ByteBufUtil.writeUtf8(buf, charSequence);
            final int utf8Bytes = ByteBufUtil.utf8Bytes(charSequence);
            assertEquals(writtenBytes, utf8Bytes);
        } finally {
            buf.release();
        }
    }

    private static void assertIsText(byte[] bytes, boolean expected, Charset charset) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeBytes(bytes);
            assertEquals(expected, ByteBufUtil.isText(buffer, charset));
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testIsTextMultiThreaded() throws Throwable {
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
}
