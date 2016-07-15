/*
 * Copyright (c) 2008-2009 Bjoern Hoehrmann <bjoern@hoehrmann.de>
 * Copyright (c) 2016 Nicholas Schlabach <Techcable@techcable.net>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.netty.util.internal;

import java.io.DataInput;
import java.io.IOException;

import io.netty.util.ByteProcessor;

/**
 * A fast utf decoder implemented based on http://bjoern.hoehrmann.de/utf-8/decoder/dfa/
 */
public final class UTF8Decoder {

    private UTF8Decoder() { }

    /**
     * The state the decoder will be in if the decoder is done decoding the character.
     */
    /* default */ static final int UTF8_ACCEPT = 0;

    /**
     * The state the decoder will be in if the decoder detects an invalid character.
     */
    /* default */ static final int UTF8_REJECT = 12;

    /**
     * The state table that maps bytes to character classes.
     * <p>
     * <p>
     * Classes:
     * <ul>
     *     <li>00..7f -> 0</li>
     *     <li>80..8f -> 1</li>
     *     <li>90..9f -> 9</li>
     *     <li>a0..bf -> 7</li>
     *     <li>c0..c1 -> 8</li>
     *     <li>c2..df -> 2</li>
     *     <li>e0..e0 -> 10</li>
     *     <li>e1..ec -> 3</li>
     *     <li>ed..ed -> 4</li>
     *     <li>ee..ef -> 3</li>
     *     <li>f0..f0 -> 11</li>
     *     <li>f1..f3 -> 6</li>
     *     <li>f4..f4 -> 5</li>
     *     <li>f5..ff -> 8</li>
     * </ul>
     * </p>
     */
    /* default */  static final byte[] CHARACTER_CLASSES = new byte[] {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9,
            7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
            8, 8, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            10, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 3, 3, 11, 6, 6, 6, 5, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8,
    };

    /**
     * Maps the state and the character class to the next state
     */
    /* default */ static final byte[] TRANSLATION_TABLE = new byte[] {
            0, 12, 24, 36, 60, 96, 84, 12, 12, 12, 48, 72, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12,
            12, 0, 12, 12, 12, 12, 12, 0, 12, 0, 12, 12, 12, 24, 12, 12, 12, 12, 12, 24, 12, 24, 12, 12,
            12, 12, 12, 12, 12, 12, 12, 24, 12, 12, 12, 12, 12, 24, 12, 12, 12, 12, 12, 12, 12, 24, 12, 12,
            12, 12, 12, 12, 12, 12, 12, 36, 12, 36, 12, 12, 12, 36, 12, 12, 12, 12, 12, 36, 12, 36, 12, 12,
            12, 36, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12,
    };

    public static String decode(byte[] bytes) {
        return decode(bytes, 0, bytes.length);
    }

    public static String decode(byte[] bytes, int offset, int length) {
        int size = bytes.length;
        if (MathUtil.isOutOfBounds(offset, length, size)) {
            throw new IndexOutOfBoundsException(illegalIndex(offset, length, size));
        }
        UTF8Processor processor = new UTF8Processor(new char[length]);

        for (int i = 0; i < length; i++) {
            processor.process(bytes[offset + i]);
        }
        return processor.toString();
    }

    public static String decode(DataInput input, int length) throws IOException {
        UTF8Processor processor = new UTF8Processor(new char[length]);
        for (int i = 0; i < length; i++) {
            processor.process(input.readByte());
        }
        return processor.toString();
    }

    private static String illegalIndex(int offset, int length, int size) {
        return "Illegal offset " + offset + " and length " + length + " for only " + size + " bytes!";
    }

    public static final class UTF8Processor implements ByteProcessor {
        private int state = UTF8_ACCEPT; // The state we are currently in, we start out by wanting a new character
        private int codePoint; // Our current unicode code point
        private int destLength;
        private final char[] dest;

        public UTF8Processor(char[] dest) {
            this.dest = dest;
        }

        public boolean process(byte rawByte) {
            int b = rawByte & 0xFF; // Byte.toUnsignedInt
            if (b <= 127) {
                processASCII(b);
            } else {
                processUnicode(b);
            }
            return true;
        }

        private void processASCII(int b) {
            dest[destLength++] = (char) b;
        }

        private void processUnicode(int b) {
            final int characterClass = CHARACTER_CLASSES[b];
            if (state == UTF8_ACCEPT) {
                codePoint = (0xFF >> characterClass) & b;
            } else {
                codePoint = (b & 0x3f) | (codePoint << 6);
            }
            switch (state = TRANSLATION_TABLE[state + characterClass]) {
                case UTF8_REJECT:
                    throw new IllegalArgumentException(illegalByte(b));
                case UTF8_ACCEPT:
                    // We're finished
                    if (StringUtil.isBasicCodePoint(codePoint)) {
                        // It can be represented as a single char
                        dest[destLength++] = (char) codePoint;
                    } else if (StringUtil.isValidCodePoint(codePoint)) {
                        dest[destLength++] = StringUtil.highSurrogate(codePoint);
                        dest[destLength++] = StringUtil.lowSurrogate(codePoint);
                    } else {
                        throw new IllegalArgumentException(illegalCodePoint(codePoint));
                    }
            }
        }

        public String toString() {
            return new String(dest, 0, destLength);
        }
    }

    private static String illegalByte(int character) {
        return "Illegal UTF8 byte: 0x " + StringUtil.byteToHexStringPadded(character);
    }

    private static String illegalCodePoint(int codePoint) {
        return "Illegal UTF8 code point "
                + StringUtil.toHexStringPadded(new byte[] {
                                (byte) (codePoint >> 24),
                                (byte) (codePoint >> 16),
                                (byte) (codePoint >> 8),
                                (byte) codePoint
                });
    }
}
