/*
 * Copyright 2024 The Netty Project
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
package io.netty.testcert.der;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.internal.EmptyArrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DerWriterTest {
    private final DerWriter writer = new DerWriter();
    private Object fromValue;

    @AfterEach
    void releaseWriter() {
        writer.release();
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) (values[i] & 0xFF);
        }
        return bytes;
    }

    private static byte[] tagLenBytes(int tag, int len, byte[] bytes) {
        byte[] result = new byte[bytes.length + 2];
        result[0] = (byte) (tag & 0xFF);
        result[1] = (byte) (len & 0xFF);
        System.arraycopy(bytes, 0, result, 2, bytes.length);
        return result;
    }

    private void assertDer(DerWriter writer, int... bytes) {
        assertDer(writer, bytes(bytes));
    }

    private void assertDer(DerWriter writer, byte... expected) {
        try {
            byte[] actual = writer.getBytes();
            if (!Arrays.equals(expected, actual)) {
                String message = "Incorrect DER generated";
                if (fromValue != null) {
                    message += ": " + fromValue;
                }
                throw new AssertionFailedError(message, formatHex(expected), formatHex(actual));
            }
        } finally {
            writer.reset();
            fromValue = null;
        }
    }

    private static String formatHex(byte[] bytes) {
        String digits = "0123456789ABCDEF";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int v = Byte.toUnsignedInt(b);
            sb.append(digits.charAt(v >>> 4));
            sb.append(digits.charAt(v & 0xF));
        }
        return sb.toString();
    }

    @Test
    void encodeBoolean() throws Exception {
        assertDer(writer.writeBoolean(true), 0x01, 0x01, 0xFF);
        assertDer(writer.writeBoolean(false), 0x01, 0x01, 0x00);
    }

    @Test
    void encodeInteger() throws Exception {
        // Zero
        assertDer(writer.writeInteger(0), 0x02, 0x01, 0x00);
        // One byte ints
        for (int i = -128; i < 128; i++) {
            assertDer(writer.writeInteger(i), 0x02, 0x01, i);
        }
        // Two byte ints
        for (int i = 256; i < Short.MAX_VALUE; i++) {
            assertDer(writer.writeInteger(i), 0x02, 0x02, i >>> 8, i & 0xFF);
        }
        // Three byte, because of sign bit.
        for (int i = 32768; i < 65534; i++) {
            assertDer(writer.writeInteger(i), 0x02, 0x03, 0, i >>> 8, i & 0xFF);
        }
        // Random ints up to 64-bit, using BigInteger encoding as a test oracle
        Random rng = ThreadLocalRandom.current();
        for (int i = 0; i < 1000000; i++) {
            long value = rng.nextLong();
            String valueStr = String.valueOf(value);
            byte[] expected = writer.writeInteger(new BigInteger(valueStr)).getBytes();
            writer.reset();
            fromValue = valueStr;
            assertDer(writer.writeInteger(value), expected);
        }
    }

    @Test
    void encodeBigInteger() throws Exception {
        assertDer(writer.writeInteger(BigInteger.ZERO), 0x02, 0x01, 0x00);
        // One byte ints
        for (int i = -128; i < 128; i++) {
            assertDer(writer.writeInteger(new BigInteger(String.valueOf(i))), 0x02, 0x01, i);
        }
        // Two byte ints
        Random rng = ThreadLocalRandom.current();
        for (int i = 0; i < 1000; i++) {
            assertBigIntegerEncoding(16, rng);
        }
        // Three byte ints
        for (int i = 0; i < 10000; i++) {
            assertBigIntegerEncoding(24, rng);
        }
        // Four byte ints
        for (int i = 0; i < 100000; i++) {
            assertBigIntegerEncoding(32, rng);
        }
        // Five byte ints
        for (int i = 0; i < 1000000; i++) {
            assertBigIntegerEncoding(40, rng);
        }
        // Six byte ints
        for (int i = 0; i < 1000000; i++) {
            assertBigIntegerEncoding(48, rng);
        }
        // Seven byte ints
        for (int i = 0; i < 1000000; i++) {
            assertBigIntegerEncoding(56, rng);
        }
        // Eight byte ints
        for (int i = 0; i < 1000000; i++) {
            assertBigIntegerEncoding(64, rng);
        }
    }

    private void assertBigIntegerEncoding(int numBits, Random rng) {
        BigInteger value = new BigInteger(numBits, rng);
        byte[] array = value.toByteArray();
        assertDer(writer.writeInteger(value), tagLenBytes(0x02, array.length, array));
    }

    @Test
    void encodingEnumerated() throws Exception {
        Random rng = ThreadLocalRandom.current();
        for (int i = 0; i < 1000; i++) {
            int value = rng.nextInt();
            byte[] expected = writer.writeInteger(value).getBytes();
            expected[0] = 10; // Change the tag to ENUMERATED; the rest of the encoding is like INTEGER
            writer.reset();
            assertDer(writer.writeEnumerated(value), expected);
        }
    }

    @Test
    void encodingOctetString() throws Exception {
        assertDer(writer.writeOctetString(EmptyArrays.EMPTY_BYTES), 0x04, 0x00);
        assertDer(writer.writeOctetString(new byte[] { (byte) 0xA5 }), 0x04, 0x01, 0xA5);
        assertDer(writer.writeOctetString(new byte[] { (byte) 0xA5, 0x00, 0x7B }), 0x04, 0x03, 0xA5, 0x00, 0x7B);

        // Two byte length
        byte[] expected = new byte[260];
        expected[0] = 0x04;
        expected[1] = (byte) 0x82;
        expected[2] = 0x01;
        expected[3] = 0x00;
        assertDer(writer.writeOctetString(new byte[256]), expected);

        // Max two byte length
        int len = 0xFFFF;
        expected = new byte[len + 4];
        expected[0] = 0x04;
        expected[1] = (byte) 0x82;
        expected[2] = (byte) 0xFF;
        expected[3] = (byte) 0xFF;
        assertDer(writer.writeOctetString(new byte[len]), expected);

        // Three byte length
        len = 0x01_0000;
        expected = new byte[len + 5];
        expected[0] = 0x04;
        expected[1] = (byte) 0x83;
        expected[2] = 0x01;
        expected[3] = 0x00;
        expected[4] = 0x00;
        assertDer(writer.writeOctetString(new byte[len]), expected);
    }

    @Test
    void encodingByteBufOctetString() throws Exception {
        assertDer(writer.writeOctetString(Unpooled.buffer()), 0x04, 0x00);
        assertDer(writer.writeOctetString(Unpooled.wrappedBuffer(new byte[] { (byte) 0xA5 })), 0x04, 0x01, 0xA5);
        assertDer(writer.writeOctetString(Unpooled.wrappedBuffer(new byte[] { (byte) 0xA5, 0x00, 0x7B })),
                0x04, 0x03, 0xA5, 0x00, 0x7B);

        // Two byte length
        byte[] expected = new byte[260];
        expected[0] = 0x04;
        expected[1] = (byte) 0x82;
        expected[2] = 0x01;
        expected[3] = 0x00;
        ByteBuf buffer = Unpooled.wrappedBuffer(new byte[256]);
        assertDer(writer.writeOctetString(buffer), expected);
        assertEquals(0, buffer.readableBytes());

        // Max two byte length
        int len = 0xFFFF;
        expected = new byte[len + 4];
        expected[0] = 0x04;
        expected[1] = (byte) 0x82;
        expected[2] = (byte) 0xFF;
        expected[3] = (byte) 0xFF;
        buffer = Unpooled.wrappedBuffer(new byte[len]);
        assertDer(writer.writeOctetString(buffer), expected);
        assertEquals(0, buffer.readableBytes());

        // Three byte length
        len = 0x01_0000;
        expected = new byte[len + 5];
        expected[0] = 0x04;
        expected[1] = (byte) 0x83;
        expected[2] = 0x01;
        expected[3] = 0x00;
        expected[4] = 0x00;
        buffer = Unpooled.wrappedBuffer(new byte[len]);
        assertDer(writer.writeOctetString(buffer), expected);
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void encodingIA5String() throws Exception {
        String str = "a-zA-Z\næøå";
        byte[] ascii = str.getBytes(StandardCharsets.US_ASCII);
        byte[] expected = new byte[ascii.length + 2];
        expected[0] = 0x16;
        expected[1] = (byte) ascii.length;
        System.arraycopy(ascii, 0, expected, 2, ascii.length);
        assertDer(writer.writeIA5String(str), expected);
    }

    @Test
    void encodingUTF8String() throws Exception {
        String str = "a-zA-Z\næøå";
        byte[] utf8 = str.getBytes(StandardCharsets.UTF_8);
        byte[] expected = new byte[utf8.length + 2];
        expected[0] = 0x0C;
        expected[1] = (byte) utf8.length;
        System.arraycopy(utf8, 0, expected, 2, utf8.length);
        assertDer(writer.writeUTF8String(str), expected);
    }

    @Test
    void encodeBitString() throws Exception {
        assertDer(writer.writeBitString(new byte[] {0x5A, 0x42}, 0), 0x03, 0x03, 0x00, 0x5A, 0x42);
        assertDer(writer.writeBitString(new byte[] {0x5A, (byte) 0xFF}, 4), 0x03, 0x03, 0x04, 0x5A, 0xF0);
        assertDer(writer.writeBitString(new boolean[] {true}), 0x03, 0x02, 0x07, 0x80);
        assertDer(writer.writeBitString(new boolean[] {false, false, false, false, false, false, false, false, true}),
                0x03, 0x03, 0x07, 0x00, 0x80);
        assertDer(writer.writeBitString(new boolean[] {false, false, false, false, false, false, false, true}),
                0x03, 0x02, 0x00, 0x01);
        assertThrows(IllegalArgumentException.class, () -> writer.writeBitString(EmptyArrays.EMPTY_BYTES, 1));
        assertThrows(IllegalArgumentException.class, () -> writer.writeBitString(new byte[] {1}, -1));
        assertThrows(IllegalArgumentException.class, () -> writer.writeBitString(new byte[] {1}, 8));
    }

    @Test
    void encodeObjectIdentifier() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> writer.writeObjectIdentifier(""));
        assertThrows(IllegalArgumentException.class, () -> writer.writeObjectIdentifier("0"));
        assertDer(writer.writeObjectIdentifier("0.0"), 0x06, 0x01, 0x00);
        assertDer(writer.writeObjectIdentifier("0.0.0"), 0x06, 0x02, 0x00, 0x00);
        assertDer(writer.writeObjectIdentifier("0.0.1"), 0x06, 0x02, 0x00, 0x01);
        assertDer(writer.writeObjectIdentifier("0.0.127"), 0x06, 0x02, 0x00, 0x7F);
        assertDer(writer.writeObjectIdentifier("0.0.128"), 0x06, 0x03, 0x00, 0x81, 0x00);
        assertDer(writer.writeObjectIdentifier("0.0.255"), 0x06, 0x03, 0x00, 0x81, 0x7F);
        assertDer(writer.writeObjectIdentifier("0.1"), 0x06, 0x01, 0x01);
        assertDer(writer.writeObjectIdentifier("0.39"), 0x06, 0x01, 0x27);
        assertThrows(IllegalArgumentException.class, () -> writer.writeObjectIdentifier("0.40"));
        assertDer(writer.writeObjectIdentifier("1.0"), 0x06, 0x01, 0x28);
        assertDer(writer.writeObjectIdentifier("1.1"), 0x06, 0x01, 0x29);
        assertDer(writer.writeObjectIdentifier("1.39"), 0x06, 0x01, 0x4F);
        assertThrows(IllegalArgumentException.class, () -> writer.writeObjectIdentifier("1.40"));
        assertDer(writer.writeObjectIdentifier("2.0"), 0x06, 0x01, 0x50);
        assertDer(writer.writeObjectIdentifier("2.1"), 0x06, 0x01, 0x51);
        assertDer(writer.writeObjectIdentifier("2.39"), 0x06, 0x01, 0x77);
        assertDer(writer.writeObjectIdentifier("2.40"), 0x06, 0x01, 0x78);
        assertDer(writer.writeObjectIdentifier("2.999"), 0x06, 0x02, 0x88, 0x37);
        assertDer(writer.writeObjectIdentifier("2.5.29.14"), 0x06, 0x03, 0x55, 0x1D, 0x0E);
        assertDer(writer.writeObjectIdentifier("1.3.6.1.4.1.311.20.2.2"),
                0x06, 0x0A, 0x2B, 0x06, 0x01, 0x04, 0x01, 0x82, 0x37, 0x14, 0x02, 0x02);

        // Largest supported child-identifier
        assertDer(writer.writeObjectIdentifier("2." + (0x00FFFFFF_FFFFFFFFL - 80)),
                0x06, 0x08, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x7F);
        // Largest supported sub-identifier
        assertDer(writer.writeObjectIdentifier("0.0." + 0x00FFFFFF_FFFFFFFFL),
                0x06, 0x09, 0x00, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x7F);

        // Encoding with multibyte lengths
        byte[] expectedDer;
        expectedDer = new byte[128];
        expectedDer[0] = 0x06;
        expectedDer[1] = 0x7E;
        assertDer(writer.writeObjectIdentifier(new long[127]), expectedDer);

        expectedDer = new byte[129];
        expectedDer[0] = 0x06;
        expectedDer[1] = 0x7F;
        assertDer(writer.writeObjectIdentifier(new long[128]), expectedDer);

        expectedDer = new byte[131];
        expectedDer[0] = 0x06;
        expectedDer[1] = (byte) 0x81;
        expectedDer[2] = (byte) 0x80;
        assertDer(writer.writeObjectIdentifier(new long[129]), expectedDer);

        // Rejecting 64-bit integer overflow.
        Class<UnsupportedOperationException> expectedType = UnsupportedOperationException.class;
        assertThrows(expectedType, () -> writer.writeObjectIdentifier("2." + 0x000FFFFFFFFFFFFB0L));
        assertThrows(expectedType, () -> writer.writeObjectIdentifier("0.0." + 0x01FFFFFF_FFFFFFFFL));
        assertThrows(expectedType, () -> writer.writeObjectIdentifier("2." + 0x01FFFFFF_FFFFFFFFL));
        assertThrows(expectedType, () -> writer.writeObjectIdentifier(
                "2." + Long.toUnsignedString(0x80000000_00000000L)));
        assertThrows(expectedType, () -> writer.writeObjectIdentifier(
                "2." + Long.toUnsignedString(0xFFFFFFFF_FFFFFFFFL)));
    }

    @Test
    void encodeSequence() throws Exception {
        DerWriter.WritableSequence sequenceWriter = writer -> {
            writer.writeOctetString(new byte[] { 0x54 }); // 0x04, 0x01, 0x54
            writer.writeInteger(0x42); // 0x02, 0x01, 0x42
        };
        assertDer(writer.writeSequence(sequenceWriter),
                0x30, 0x06,
                0x04, 0x01, 0x54,
                0x02, 0x01, 0x42);
        assertDer(writer.writeSequence(writer -> { }), 0x30, 0x00);
    }

    @Test
    void encodeExplicit() throws Exception {
        assertDer(writer.writeExplicit(0x21, writer -> writer.writeInteger(0x42)), 0x21, 0x03, 0x02, 0x01, 0x42);
        assertThrows(IllegalArgumentException.class,
                () -> writer.writeExplicit(0x01, writer -> writer.writeInteger(0)));
        assertThrows(IllegalStateException.class,
                () -> writer.writeExplicit(0x21, writer -> writer.writeInteger(0).writeInteger(0)));
    }

    @Test
    void encodeUTCTime() throws Exception {
        assertDer(writer.writeUTCTime(ZonedDateTime.parse("2007-12-03T10:15:30.000Z")),
                0x17, 0x0D, '0', '7', '1', '2', '0', '3', '1', '0', '1', '5', '3', '0', 'Z');
        assertDer(writer.writeUTCTime(ZonedDateTime.parse("2007-12-03T10:15:30-05:00")),
                0x17, 0x11, '0', '7', '1', '2', '0', '3', '1', '0', '1', '5', '3', '0', '-', '0', '5', '0', '0');
        assertDer(writer.writeUTCTime(ZonedDateTime.parse("1982-01-02T10:15:30+08:00")),
                0x17, 0x11, '8', '2', '0', '1', '0', '2', '1', '0', '1', '5', '3', '0', '+', '0', '8', '0', '0');

        assertDer(writer.writeUTCTime(Instant.parse("2007-12-03T10:15:30.000Z")),
                0x17, 0x0D, '0', '7', '1', '2', '0', '3', '1', '0', '1', '5', '3', '0', 'Z');
    }

    @Test
    void encodeGeneralizedTime() throws Exception {
        assertDer(writer.writeGeneralizedTime(ZonedDateTime.parse("2007-12-03T10:15:30.00Z")),
                0x18, 0x0F, '2', '0', '0', '7', '1', '2', '0', '3', '1', '0', '1', '5', '3', '0', 'Z');
        assertDer(writer.writeGeneralizedTime(ZonedDateTime.parse("2007-12-03T10:15:30-05:00")),
                0x18, 0x13, '2', '0', '0', '7', '1', '2', '0', '3', '1', '0', '1', '5', '3', '0',
                '-', '0', '5', '0', '0');
        assertDer(writer.writeGeneralizedTime(ZonedDateTime.parse("1982-01-02T10:15:30+08:00")),
                0x18, 0x13, '1', '9', '8', '2', '0', '1', '0', '2', '1', '0', '1', '5', '3', '0',
                '+', '0', '8', '0', '0');

        assertDer(writer.writeGeneralizedTime(LocalDateTime.parse("2007-12-03T10:15:30")),
                0x18, 0x0E, '2', '0', '0', '7', '1', '2', '0', '3', '1', '0', '1', '5', '3', '0');
        assertDer(writer.writeGeneralizedTime(LocalDateTime.parse("1982-01-02T10:15:30")),
                0x18, 0x0E, '1', '9', '8', '2', '0', '1', '0', '2', '1', '0', '1', '5', '3', '0');

        assertDer(writer.writeGeneralizedTime(Instant.parse("2007-12-03T10:15:30.000Z")),
                0x18, 0x0F, '2', '0', '0', '7', '1', '2', '0', '3', '1', '0', '1', '5', '3', '0', 'Z');
        assertDer(writer.writeGeneralizedTime(Instant.parse("1982-01-02T10:15:30.000Z")),
                0x18, 0x0F, '1', '9', '8', '2', '0', '1', '0', '2', '1', '0', '1', '5', '3', '0', 'Z');
        assertDer(writer.writeGeneralizedTime(Instant.parse("1982-01-02T10:15:30.123Z")),
                0x18, 0x0F, '1', '9', '8', '2', '0', '1', '0', '2', '1', '0', '1', '5', '3', '0', 'Z');
        assertDer(writer.writeGeneralizedTime(Instant.parse("1982-01-02T10:15:30.001Z")),
                0x18, 0x0F, '1', '9', '8', '2', '0', '1', '0', '2', '1', '0', '1', '5', '3', '0', 'Z');

        assertDer(writer.writeGeneralizedTime(LocalDate.parse("2007-12-03")),
                0x18, 0x08, '2', '0', '0', '7', '1', '2', '0', '3');
        assertDer(writer.writeGeneralizedTime(LocalDate.parse("1982-01-02")),
                0x18, 0x08, '1', '9', '8', '2', '0', '1', '0', '2');
    }
}
