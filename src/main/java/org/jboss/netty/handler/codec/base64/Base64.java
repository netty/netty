/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
/*
 * Written by Robert Harder and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */
package org.jboss.netty.handler.codec.base64;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.HeapChannelBufferFactory;

/**
 * Utility class for {@link ChannelBuffer} that encodes and decodes to and from
 * <a href="http://en.wikipedia.org/wiki/Base64">Base64</a> notation.
 * <p>
 * The encoding and decoding algorithm in this class has been derived from
 * <a href="http://iharder.sourceforge.net/current/java/base64/">Robert Harder's Public Domain Base64 Encoder/Decoder</a>.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Robert Harder (rob@iharder.net)
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2241 $, $Date: 2010-04-16 13:12:43 +0900 (Fri, 16 Apr 2010) $
 *
 * @apiviz.landmark
 * @apiviz.uses org.jboss.netty.handler.codec.base64.Base64Dialect
 */
public class Base64 {

    /** Maximum line length (76) of Base64 output. */
    private static final int MAX_LINE_LENGTH = 76;

    /** The equals sign (=) as a byte. */
    private static final byte EQUALS_SIGN = (byte) '=';

    /** The new line character (\n) as a byte. */
    private static final byte NEW_LINE = (byte) '\n';

    private static final byte WHITE_SPACE_ENC = -5; // Indicates white space in encoding

    private static final byte EQUALS_SIGN_ENC = -1; // Indicates equals sign in encoding

    private static final byte[] alphabet(Base64Dialect dialect) {
        if (dialect == null) {
            throw new NullPointerException("dialect");
        }
        return dialect.alphabet;
    }

    private static final byte[] decodabet(Base64Dialect dialect) {
        if (dialect == null) {
            throw new NullPointerException("dialect");
        }
        return dialect.decodabet;
    }

    private static final boolean breakLines(Base64Dialect dialect) {
        if (dialect == null) {
            throw new NullPointerException("dialect");
        }
        return dialect.breakLinesByDefault;
    }

    public static ChannelBuffer encode(ChannelBuffer src) {
        return encode(src, Base64Dialect.STANDARD);
    }

    public static ChannelBuffer encode(ChannelBuffer src, Base64Dialect dialect) {
        return encode(src, breakLines(dialect), dialect);
    }

    public static ChannelBuffer encode(
            ChannelBuffer src, ChannelBufferFactory bufferFactory) {
        return encode(src, Base64Dialect.STANDARD, bufferFactory);
    }

    public static ChannelBuffer encode(
            ChannelBuffer src, Base64Dialect dialect, ChannelBufferFactory bufferFactory) {
        return encode(src, breakLines(dialect), dialect, bufferFactory);
    }

    public static ChannelBuffer encode(ChannelBuffer src, boolean breakLines) {
        return encode(src, breakLines, Base64Dialect.STANDARD);
    }

    public static ChannelBuffer encode(
            ChannelBuffer src, boolean breakLines, Base64Dialect dialect) {
        return encode(src, breakLines, dialect, HeapChannelBufferFactory.getInstance());
    }

    public static ChannelBuffer encode(
            ChannelBuffer src, boolean breakLines, ChannelBufferFactory bufferFactory) {
        return encode(src, breakLines, Base64Dialect.STANDARD, bufferFactory);
    }

    public static ChannelBuffer encode(
            ChannelBuffer src, boolean breakLines, Base64Dialect dialect, ChannelBufferFactory bufferFactory) {

        if (src == null) {
            throw new NullPointerException("src");
        }

        ChannelBuffer dest = encode(
                src, src.readerIndex(), src.readableBytes(), breakLines, dialect, bufferFactory);
        src.readerIndex(src.writerIndex());
        return dest;
    }

    public static ChannelBuffer encode(ChannelBuffer src, int off, int len) {
        return encode(src, off, len, Base64Dialect.STANDARD);
    }

    public static ChannelBuffer encode(ChannelBuffer src, int off, int len, Base64Dialect dialect) {
        return encode(src, off, len, breakLines(dialect), dialect);
    }

    public static ChannelBuffer encode(ChannelBuffer src, int off, int len, ChannelBufferFactory bufferFactory) {
        return encode(src, off, len, Base64Dialect.STANDARD, bufferFactory);
    }

    public static ChannelBuffer encode(ChannelBuffer src, int off, int len, Base64Dialect dialect, ChannelBufferFactory bufferFactory) {
        return encode(src, off, len, breakLines(dialect), dialect, bufferFactory);
    }

    public static ChannelBuffer encode(
            ChannelBuffer src, int off, int len, boolean breakLines) {
        return encode(src, off, len, breakLines, Base64Dialect.STANDARD);
    }

    public static ChannelBuffer encode(
            ChannelBuffer src, int off, int len,
            boolean breakLines, Base64Dialect dialect) {
        return encode(src, off, len, breakLines, dialect, HeapChannelBufferFactory.getInstance());
    }

    public static ChannelBuffer encode(
            ChannelBuffer src, int off, int len,
            boolean breakLines, ChannelBufferFactory bufferFactory) {
        return encode(src, off, len, breakLines, Base64Dialect.STANDARD, bufferFactory);
    }

    public static ChannelBuffer encode(
            ChannelBuffer src, int off, int len,
            boolean breakLines, Base64Dialect dialect, ChannelBufferFactory bufferFactory) {

        if (src == null) {
            throw new NullPointerException("src");
        }
        if (dialect == null) {
            throw new NullPointerException("dialect");
        }
        if (bufferFactory == null) {
            throw new NullPointerException("bufferFactory");
        }

        int len43 = len * 4 / 3;
        ChannelBuffer dest = bufferFactory.getBuffer(
                src.order(),
                len43 +
                (len % 3 > 0? 4 : 0) + // Account for padding
                (breakLines? len43 / MAX_LINE_LENGTH : 0)); // New lines
        int d = 0;
        int e = 0;
        int len2 = len - 2;
        int lineLength = 0;
        for (; d < len2; d += 3, e += 4) {
            encode3to4(src, d + off, 3, dest, e, dialect);

            lineLength += 4;
            if (breakLines && lineLength == MAX_LINE_LENGTH) {
                dest.setByte(e + 4, NEW_LINE);
                e ++;
                lineLength = 0;
            } // end if: end of line
        } // end for: each piece of array

        if (d < len) {
            encode3to4(src, d + off, len - d, dest, e, dialect);
            e += 4;
        } // end if: some padding needed

        return dest.slice(0, e);
    }

    private static void encode3to4(
            ChannelBuffer src, int srcOffset, int numSigBytes,
            ChannelBuffer dest, int destOffset, Base64Dialect dialect) {

        byte[] ALPHABET = alphabet(dialect);

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
        int inBuff =
                (numSigBytes > 0? src.getByte(srcOffset    ) << 24 >>>  8 : 0) |
                (numSigBytes > 1? src.getByte(srcOffset + 1) << 24 >>> 16 : 0) |
                (numSigBytes > 2? src.getByte(srcOffset + 2) << 24 >>> 24 : 0);

        switch (numSigBytes) {
        case 3:
            dest.setByte(destOffset    , ALPHABET[inBuff >>> 18       ]);
            dest.setByte(destOffset + 1, ALPHABET[inBuff >>> 12 & 0x3f]);
            dest.setByte(destOffset + 2, ALPHABET[inBuff >>>  6 & 0x3f]);
            dest.setByte(destOffset + 3, ALPHABET[inBuff        & 0x3f]);
            break;
        case 2:
            dest.setByte(destOffset    , ALPHABET[inBuff >>> 18       ]);
            dest.setByte(destOffset + 1, ALPHABET[inBuff >>> 12 & 0x3f]);
            dest.setByte(destOffset + 2, ALPHABET[inBuff >>> 6  & 0x3f]);
            dest.setByte(destOffset + 3, EQUALS_SIGN);
            break;
        case 1:
            dest.setByte(destOffset    , ALPHABET[inBuff >>> 18       ]);
            dest.setByte(destOffset + 1, ALPHABET[inBuff >>> 12 & 0x3f]);
            dest.setByte(destOffset + 2, EQUALS_SIGN);
            dest.setByte(destOffset + 3, EQUALS_SIGN);
            break;
        }
    }

    public static ChannelBuffer decode(ChannelBuffer src) {
        return decode(src, Base64Dialect.STANDARD);
    }

    public static ChannelBuffer decode(ChannelBuffer src, Base64Dialect dialect) {
        return decode(src, dialect, HeapChannelBufferFactory.getInstance());
    }

    public static ChannelBuffer decode(ChannelBuffer src, ChannelBufferFactory bufferFactory) {
        return decode(src, Base64Dialect.STANDARD, bufferFactory);
    }

    public static ChannelBuffer decode(ChannelBuffer src, Base64Dialect dialect, ChannelBufferFactory bufferFactory) {

        if (src == null) {
            throw new NullPointerException("src");
        }

        ChannelBuffer dest = decode(src, src.readerIndex(), src.readableBytes(), dialect, bufferFactory);
        src.readerIndex(src.writerIndex());
        return dest;
    }

    public static ChannelBuffer decode(
            ChannelBuffer src, int off, int len) {
        return decode(src, off, len, Base64Dialect.STANDARD);
    }

    public static ChannelBuffer decode(
            ChannelBuffer src, int off, int len, Base64Dialect dialect) {
        return decode(src, off, len, dialect, HeapChannelBufferFactory.getInstance());
    }

    public static ChannelBuffer decode(
            ChannelBuffer src, int off, int len, ChannelBufferFactory bufferFactory) {
        return decode(src, off, len, Base64Dialect.STANDARD, bufferFactory);
    }

    public static ChannelBuffer decode(
            ChannelBuffer src, int off, int len, Base64Dialect dialect,
            ChannelBufferFactory bufferFactory) {

        if (src == null) {
            throw new NullPointerException("src");
        }
        if (dialect == null) {
            throw new NullPointerException("dialect");
        }
        if (bufferFactory == null) {
            throw new NullPointerException("bufferFactory");
        }

        byte[] DECODABET = decodabet(dialect);

        int len34 = len * 3 / 4;
        ChannelBuffer dest = bufferFactory.getBuffer(src.order(), len34); // Upper limit on size of output
        int outBuffPosn = 0;

        byte[] b4 = new byte[4];
        int b4Posn = 0;
        int i = 0;
        byte sbiCrop = 0;
        byte sbiDecode = 0;
        for (i = off; i < off + len; i ++) {
            sbiCrop = (byte) (src.getByte(i) & 0x7f); // Only the low seven bits
            sbiDecode = DECODABET[sbiCrop];

            if (sbiDecode >= WHITE_SPACE_ENC) { // White space, Equals sign or better
                if (sbiDecode >= EQUALS_SIGN_ENC) {
                    b4[b4Posn ++] = sbiCrop;
                    if (b4Posn > 3) {
                        outBuffPosn += decode4to3(
                                b4, 0, dest, outBuffPosn, dialect);
                        b4Posn = 0;

                        // If that was the equals sign, break out of 'for' loop
                        if (sbiCrop == EQUALS_SIGN) {
                            break;
                        }
                    } // end if: quartet built
                } // end if: equals sign or better
            } // end if: white space, equals sign or better
            else {
                throw new IllegalArgumentException(
                        "bad Base64 input character at " + i + ": " +
                        src.getUnsignedByte(i) + " (decimal)");
            }
        }

        return dest.slice(0, outBuffPosn);
    }

    private static int decode4to3(
            byte[] src, int srcOffset,
            ChannelBuffer dest, int destOffset, Base64Dialect dialect) {

        byte[] DECODABET = decodabet(dialect);

        // Example: Dk==
        if (src[srcOffset + 2] == EQUALS_SIGN) {
            int outBuff =
                    (DECODABET[src[srcOffset    ]] & 0xFF) << 18 |
                    (DECODABET[src[srcOffset + 1]] & 0xFF) << 12;

            dest.setByte(destOffset, (byte) (outBuff >>> 16));
            return 1;
        }

        // Example: DkL=
        else if (src[srcOffset + 3] == EQUALS_SIGN) {
            int outBuff =
                    (DECODABET[src[srcOffset    ]] & 0xFF) << 18 |
                    (DECODABET[src[srcOffset + 1]] & 0xFF) << 12 |
                    (DECODABET[src[srcOffset + 2]] & 0xFF) <<  6;

            dest.setByte(destOffset    , (byte) (outBuff >>> 16));
            dest.setByte(destOffset + 1, (byte) (outBuff >>>  8));
            return 2;
        }

        // Example: DkLE
        else {
            int outBuff;
            try {
                outBuff =
                        (DECODABET[src[srcOffset    ]] & 0xFF) << 18 |
                        (DECODABET[src[srcOffset + 1]] & 0xFF) << 12 |
                        (DECODABET[src[srcOffset + 2]] & 0xFF) <<  6 |
                         DECODABET[src[srcOffset + 3]] & 0xFF;
            } catch (IndexOutOfBoundsException e) {
                throw new IllegalArgumentException("not encoded in Base64");
            }

            dest.setByte(destOffset    , (byte) (outBuff >> 16));
            dest.setByte(destOffset + 1, (byte) (outBuff >>  8));
            dest.setByte(destOffset + 2, (byte)  outBuff);
            return 3;
        }
    }

    private Base64() {
        // Unused
    }
}
