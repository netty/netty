/*
 * Copyright 2012 The Netty Project
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
/*
 * Written by Robert Harder and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */
package io.netty.handler.codec.base64;

import static java.util.Objects.requireNonNull;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.ByteProcessor;
import io.netty.util.internal.PlatformDependent;

/**
 * Utility class for {@link ByteBuf} that encodes and decodes to and from
 * <a href="http://en.wikipedia.org/wiki/Base64">Base64</a> notation.
 * <p>
 * The encoding and decoding algorithm in this class has been derived from
 * <a href="http://iharder.sourceforge.net/current/java/base64/">Robert Harder's Public Domain
 * Base64 Encoder/Decoder</a>.
 */
public final class Base64 {

    /** Maximum line length (76) of Base64 output. */
    private static final int MAX_LINE_LENGTH = 76;

    /** The equals sign (=) as a byte. */
    private static final byte EQUALS_SIGN = (byte) '=';

    /** The new line character (\n) as a byte. */
    private static final byte NEW_LINE = (byte) '\n';

    private static final byte WHITE_SPACE_ENC = -5; // Indicates white space in encoding

    private static final byte EQUALS_SIGN_ENC = -1; // Indicates equals sign in encoding

    private static byte[] alphabet(Base64Dialect dialect) {
        requireNonNull(dialect, "dialect");
        return dialect.alphabet;
    }

    private static byte[] decodabet(Base64Dialect dialect) {
        requireNonNull(dialect, "dialect");
        return dialect.decodabet;
    }

    private static boolean breakLines(Base64Dialect dialect) {
        requireNonNull(dialect, "dialect");
        return dialect.breakLinesByDefault;
    }

    public static ByteBuf encode(ByteBuf src) {
        return encode(src, Base64Dialect.STANDARD);
    }

    public static ByteBuf encode(ByteBuf src, Base64Dialect dialect) {
        return encode(src, breakLines(dialect), dialect);
    }

    public static ByteBuf encode(ByteBuf src, boolean breakLines) {
        return encode(src, breakLines, Base64Dialect.STANDARD);
    }

    public static ByteBuf encode(ByteBuf src, boolean breakLines, Base64Dialect dialect) {
        requireNonNull(src, "src");

        ByteBuf dest = encode(src, src.readerIndex(), src.readableBytes(), breakLines, dialect);
        src.readerIndex(src.writerIndex());
        return dest;
    }

    public static ByteBuf encode(ByteBuf src, int off, int len) {
        return encode(src, off, len, Base64Dialect.STANDARD);
    }

    public static ByteBuf encode(ByteBuf src, int off, int len, Base64Dialect dialect) {
        return encode(src, off, len, breakLines(dialect), dialect);
    }

    public static ByteBuf encode(
            ByteBuf src, int off, int len, boolean breakLines) {
        return encode(src, off, len, breakLines, Base64Dialect.STANDARD);
    }

    public static ByteBuf encode(
            ByteBuf src, int off, int len, boolean breakLines, Base64Dialect dialect) {
        return encode(src, off, len, breakLines, dialect, src.alloc());
    }

    public static ByteBuf encode(
            ByteBuf src, int off, int len, boolean breakLines, Base64Dialect dialect, ByteBufAllocator allocator) {
        requireNonNull(src, "src");
        requireNonNull(dialect, "dialect");

        ByteBuf dest = allocator.buffer(encodedBufferSize(len, breakLines));
        byte[] alphabet = alphabet(dialect);
        int d = 0;
        int e = 0;
        int len2 = len - 2;
        int lineLength = 0;
        for (; d < len2; d += 3, e += 4) {
            encode3to4(src, d + off, 3, dest, e, alphabet);

            lineLength += 4;

            if (breakLines && lineLength == MAX_LINE_LENGTH) {
                dest.setByte(e + 4, NEW_LINE);
                e ++;
                lineLength = 0;
            } // end if: end of line
        } // end for: each piece of array

        if (d < len) {
            encode3to4(src, d + off, len - d, dest, e, alphabet);
            e += 4;
        } // end if: some padding needed

        // Remove last byte if it's a newline
        if (e > 1 && dest.getByte(e - 1) == NEW_LINE) {
            e--;
        }

        return dest.slice(0, e);
    }

    private static void encode3to4(
            ByteBuf src, int srcOffset, int numSigBytes, ByteBuf dest, int destOffset, byte[] alphabet) {
        //           1         2         3
        // 01234567890123456789012345678901 Bit position
        // --------000000001111111122222222 Array position from threeBytes
        // --------|    ||    ||    ||    | Six bit groups to index ALPHABET
        //          >>18  >>12  >> 6  >> 0  Right shift necessary
        //                0x3f  0x3f  0x3f  Additional AND

        // Create buffer with zero-padding if there are only one or two
        // significant bytes passed in the array.
        // We have to shift left 24 in order to flush out the 1's that appear
        // when Java treats a value as negative that is cast from a byte to an int.
        final int inBuff;
        switch (numSigBytes) {
            case 1:
                inBuff = toInt(src.getByte(srcOffset));
                break;
            case 2:
                inBuff = toIntBE(src.getShort(srcOffset));
                break;
            default:
                inBuff = numSigBytes <= 0 ? 0 : toIntBE(src.getMedium(srcOffset));
                break;
        }
        encode3to4BigEndian(inBuff, numSigBytes, dest, destOffset, alphabet);
    }

    // package-private for testing
    static int encodedBufferSize(int len, boolean breakLines) {
        // Cast len to long to prevent overflow
        long len43 = ((long) len << 2) / 3;

        // Account for padding
        long ret = (len43 + 3) & ~3;

        if (breakLines) {
            ret += len43 / MAX_LINE_LENGTH;
        }

        return ret < Integer.MAX_VALUE ? (int) ret : Integer.MAX_VALUE;
    }

    private static int toInt(byte value) {
        return (value & 0xff) << 16;
    }

    private static int toIntBE(short value) {
        return (value & 0xff00) << 8 | (value & 0xff) << 8;
    }

    private static int toIntBE(int mediumValue) {
        return (mediumValue & 0xff0000) | (mediumValue & 0xff00) | (mediumValue & 0xff);
    }

    private static void encode3to4BigEndian(
            int inBuff, int numSigBytes, ByteBuf dest, int destOffset, byte[] alphabet) {
        // Packing bytes into an int to reduce bound and reference count checking.
        switch (numSigBytes) {
            case 3:
                dest.setInt(destOffset, alphabet[inBuff >>> 18       ] << 24 |
                                        alphabet[inBuff >>> 12 & 0x3f] << 16 |
                                        alphabet[inBuff >>>  6 & 0x3f] << 8  |
                                        alphabet[inBuff        & 0x3f]);
                break;
            case 2:
                dest.setInt(destOffset, alphabet[inBuff >>> 18       ] << 24 |
                                        alphabet[inBuff >>> 12 & 0x3f] << 16 |
                                        alphabet[inBuff >>> 6  & 0x3f] << 8  |
                                        EQUALS_SIGN);
                break;
            case 1:
                dest.setInt(destOffset, alphabet[inBuff >>> 18       ] << 24 |
                                        alphabet[inBuff >>> 12 & 0x3f] << 16 |
                                        EQUALS_SIGN << 8                     |
                                        EQUALS_SIGN);
                break;
            default:
                // NOOP
                break;
        }
    }

    public static ByteBuf decode(ByteBuf src) {
        return decode(src, Base64Dialect.STANDARD);
    }

    public static ByteBuf decode(ByteBuf src, Base64Dialect dialect) {
        requireNonNull(src, "src");

        ByteBuf dest = decode(src, src.readerIndex(), src.readableBytes(), dialect);
        src.readerIndex(src.writerIndex());
        return dest;
    }

    public static ByteBuf decode(
            ByteBuf src, int off, int len) {
        return decode(src, off, len, Base64Dialect.STANDARD);
    }

    public static ByteBuf decode(
            ByteBuf src, int off, int len, Base64Dialect dialect) {
        return decode(src, off, len, dialect, src.alloc());
    }

    public static ByteBuf decode(
            ByteBuf src, int off, int len, Base64Dialect dialect, ByteBufAllocator allocator) {
        requireNonNull(src, "src");
        requireNonNull(dialect, "dialect");

        // Using a ByteProcessor to reduce bound and reference count checking.
        return new Decoder().decode(src, off, len, allocator, dialect);
    }

    // package-private for testing
    static int decodedBufferSize(int len) {
        return len - (len >>> 2);
    }

    private static final class Decoder implements ByteProcessor {
        private final byte[] b4 = new byte[4];
        private int b4Posn;
        private byte sbiCrop;
        private byte sbiDecode;
        private byte[] decodabet;
        private int outBuffPosn;
        private ByteBuf dest;

        ByteBuf decode(ByteBuf src, int off, int len, ByteBufAllocator allocator, Base64Dialect dialect) {
            dest = allocator.buffer(decodedBufferSize(len)); // Upper limit on size of output

            decodabet = decodabet(dialect);
            try {
                src.forEachByte(off, len, this);
                return dest.slice(0, outBuffPosn);
            } catch (Throwable cause) {
                dest.release();
                PlatformDependent.throwException(cause);
                return null;
            }
        }

        @Override
        public boolean process(byte value) throws Exception {
            sbiCrop = (byte) (value & 0x7f); // Only the low seven bits
            sbiDecode = decodabet[sbiCrop];

            if (sbiDecode >= WHITE_SPACE_ENC) { // White space, Equals sign or better
                if (sbiDecode >= EQUALS_SIGN_ENC) { // Equals sign or better
                    b4[b4Posn ++] = sbiCrop;
                    if (b4Posn > 3) { // Quartet built
                        outBuffPosn += decode4to3(b4, dest, outBuffPosn, decodabet);
                        b4Posn = 0;

                        // If that was the equals sign, break out of 'for' loop
                        if (sbiCrop == EQUALS_SIGN) {
                            return false;
                        }
                    }
                }
                return true;
            }
            throw new IllegalArgumentException(
                    "invalid bad Base64 input character: " + (short) (value & 0xFF) + " (decimal)");
        }

        private static int decode4to3(byte[] src, ByteBuf dest, int destOffset, byte[] decodabet) {
            final byte src0 = src[0];
            final byte src1 = src[1];
            final byte src2 = src[2];
            final int decodedValue;
            if (src2 == EQUALS_SIGN) {
                // Example: Dk==
                try {
                    decodedValue = (decodabet[src0] & 0xff) << 2 | (decodabet[src1] & 0xff) >>> 4;
                } catch (IndexOutOfBoundsException ignored) {
                    throw new IllegalArgumentException("not encoded in Base64");
                }
                dest.setByte(destOffset, decodedValue);
                return 1;
            }

            final byte src3 = src[3];
            if (src3 == EQUALS_SIGN) {
                // Example: DkL=
                final byte b1 = decodabet[src1];
                // Packing bytes into a short to reduce bound and reference count checking.
                try {
                    // The decodabet bytes are meant to straddle byte boundaries and so we must carefully mask out
                    // the bits we care about.
                    decodedValue = ((decodabet[src0] & 0x3f) << 2 | (b1 & 0xf0) >> 4) << 8 |
                            (b1 & 0xf) << 4 | (decodabet[src2] & 0xfc) >>> 2;
                } catch (IndexOutOfBoundsException ignored) {
                    throw new IllegalArgumentException("not encoded in Base64");
                }
                dest.setShort(destOffset, decodedValue);
                return 2;
            }

            // Example: DkLE
            try {
                decodedValue = (decodabet[src0] & 0x3f) << 18 |
                        (decodabet[src1] & 0xff) << 12 |
                        (decodabet[src2] & 0xff) << 6 |
                        decodabet[src3] & 0xff;
            } catch (IndexOutOfBoundsException ignored) {
                throw new IllegalArgumentException("not encoded in Base64");
            }
            dest.setMedium(destOffset, decodedValue);
            return 3;
        }
    }

    private Base64() {
        // Unused
    }
}
