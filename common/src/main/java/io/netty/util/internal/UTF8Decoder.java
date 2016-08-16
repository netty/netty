/*
 * Copyright 2016 The Netty Project
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
 * Copyright (c) 2008-2009 Bjoern Hoehrmann <bjoern@hoehrmann.de>
 * Copyright (c) 2016 Nicholas Schlabach <Techcable@techcable.net>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so,subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.netty.util.internal;

import java.io.DataInput;
import java.io.IOException;

import io.netty.util.ByteProcessor;

import static io.netty.util.internal.StringUtil.*;

/**
 * A fast utf decoder implemented based on http://bjoern.hoehrmann.de/utf-8/decoder/dfa/
 */
public final class UTF8Decoder {

    private UTF8Decoder() {
    }

    /**
     * The state the decoder will be in if the decoder is done decoding the character.
     */
    /* default */ static final int UTF8_ACCEPT = 0;

    /**
     * The state the decoder will be in if the decoder detects an invalid character.
     */
    /* default */ static final int UTF8_REJECT = 12;

    /* default */ static final int ASCII_CLASS = 0;
    /* default */ static final int CONTINUATION_CLASS_1 = 1; // 80..8F
    /* default */ static final int CONTINUATION_CLASS_2 = 9; // 90..9F
    /* default */ static final int CONTINUATION_CLASS_3 = 7; // A0..BF
    /* default */ static final int TWO_BYTE_BEGIN_CLASS = 2; // C2..DF
    // Can potentially be used for an overlong encoding
    /* default */ static final int THREE_BYTE_BEGIN_CLASS_1 = 10; // E0
    /* default */ static final int THREE_BYTE_BEGIN_CLASS_2 = 4; // ED
    // These bytes take up 3 characters, but they can't be overlong
    /* default */ static final int THREE_BYTE_BEGIN_CLASS_PRIMARY = 3; // E1..EC && EE..EF
    // Can potentially be used for an overlong encoding
    /* default */ static final int FOUR_BYTE_BEGIN_CLASS_1 = 11; // F0
    // This is the primary set of four byte characters, which aren't overlong and represent valid codepoints
    /* default */ static final int FOUR_BYTE_BEGIN_CLASS_PRIMARY = 4; // F1..F3
    // This is possibly an invalid code point
    /* default */ static final int FOUR_BYTE_BEGIN_CLASS_2 = 5; // F0
    /**
     * C0..C1 can _only_ be used for an overlong encoding of ASCII
     * F4..F5 can _only_ be used to encode an invalid code point
     */
    /* default */ static final int INVALID_BYTE_CLASS = 8;

    /**
     * The state table that maps bytes to character classes.
     * <p>
     * <p>
     * Classes:
     * <ul>
     * <li>00..7f -> 0</li>
     * <li>80..8f -> 1</li>
     * <li>90..9f -> 9</li>
     * <li>a0..bf -> 7</li>
     * <li>c0..c1 -> 8</li>
     * <li>c2..df -> 2</li>
     * <li>e0..e0 -> 10</li>
     * <li>e1..ec -> 3</li>
     * <li>ed..ed -> 4</li>
     * <li>ee..ef -> 3</li>
     * <li>f0..f0 -> 11</li>
     * <li>f1..f3 -> 6</li>
     * <li>f4..f4 -> 5</li>
     * <li>f5..ff -> 8</li>
     * </ul>
     * </p>
     */
    /* default */  static final byte[] CHARACTER_CLASSES = new byte[]{
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
    /* default */ static final byte[] TRANSLATION_TABLE = new byte[]{
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
        UTF8Processor processor = new UTF8Processor(length);

        for (int i = 0; i < length; i++) {
            processor.process(bytes[offset + i]);
        }
        return processor.build();
    }

    public static String decode(DataInput input, int length) throws IOException {
        UTF8Processor processor = new UTF8Processor(length);
        for (int i = 0; i < length; i++) {
            processor.process(input.readByte());
        }
        return processor.build();
    }

    public static boolean isContinuationByte(byte b) {
        return isContinuationByte((int) b);
    }

    private static boolean isContinuationByte(int i) {
        return (i & 0xC0) == 0x80; // The top two bits are 0x
    }

    private static String illegalCodePointMsg(int codePoint) {
        return (codePoint < 0 ? "Negative code point: " : "Invalid code point: ") + codePoint;
    }

    private static String illegalIndex(int offset, int length, int size) {
        return "Illegal offset " + offset + " and length " + length + " for only " + size + " bytes!";
    }

    /* default */ static final InvalidReason[] INVALID_REASONS = ArrayUtils.join(
            InvalidReason.class,
            new InvalidReason[][] {
            {
                    // First byte
                    null,
                    InvalidReason.UNEXPECTED_CONTINUATION,
                    null,
                    null,
                    null,
                    null,
                    null,
                    InvalidReason.UNEXPECTED_CONTINUATION,
                    InvalidReason.INVALID_BYTE,
                    InvalidReason.UNEXPECTED_CONTINUATION,
                    null,
                    null
            },
            {
                    // UTF8_REJECT state
                    InvalidReason.ILLEGAL_STATE,
                    InvalidReason.ILLEGAL_STATE,
                    InvalidReason.ILLEGAL_STATE,
                    InvalidReason.ILLEGAL_STATE,
                    InvalidReason.ILLEGAL_STATE,
                    InvalidReason.ILLEGAL_STATE,
                    InvalidReason.ILLEGAL_STATE,
                    InvalidReason.ILLEGAL_STATE,
                    InvalidReason.ILLEGAL_STATE,
                    InvalidReason.ILLEGAL_STATE,
                    InvalidReason.ILLEGAL_STATE,
                    InvalidReason.ILLEGAL_STATE,
            },
            {
                    // state #2 (one more byte needed)
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    null,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    null,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    null,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES
            },
            {
                    // state #3 (two more bytes needed)
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    null,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    null,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    null,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES
            },
            {
                    // state #4 (two more bytes needed, potentially a surrogate char)
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.OVERLONG_ENCODING,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    null,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.OVERLONG_ENCODING,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES
            },
            {
                    // state #5 (two more bytes needed, potentially overlong)
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    null,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.SURROGATE_CODE_POINT,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    null,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES
            },
            {
                    // state #6 (three more bytes needed, potentially overlong)
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.OVERLONG_ENCODING,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    null,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    null,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES
            },
            {
                    // state #7 (three more bytes needed)
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    null,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    null,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    null,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES
            },
            {
                    // state #8 (three more bytes needed, potentially too big)
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    null,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.OVERFLOW_CODE_POINT,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.OVERFLOW_CODE_POINT,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES,
                    InvalidReason.INSUFFICIENT_CONTINUATION_BYTES
            }
    });

    public static final class UTF8Processor implements ByteProcessor {
        /* default */ int state = UTF8_ACCEPT; // The state we are currently in, we start out by wanting a new character
        private int codePoint; // Our current unicode code point
        /* default */ int destSize;
        public boolean replace = true;
        /* default */ char[] dest;

        public UTF8Processor(int length) {
            this.dest = new char[length];
        }

        /* default */ UTF8Processor(char[] dest, int destSize) {
            if (destSize < 0 || destSize >= dest.length) {
                throw new IndexOutOfBoundsException(Integer.toString(destSize));
            }
            this.dest = dest;
            this.destSize = destSize;
        }

        public boolean process(byte rawByte) {
            int b = rawByte & 0xFF; // Byte.toUnsignedInt
            if (dest == null) {
                throw new IllegalArgumentException("build() already called!");
            }
            if (state == UTF8_ACCEPT && b <= 127) {
                processASCII(b);
            } else {
                processUnicode(b);
            }
            return true;
        }

        private void processASCII(int b) {
            dest[destSize++] = (char) b;
        }

        /* default */ void processUnicode(int b) {
            final int characterClass = CHARACTER_CLASSES[b];
            if (state == UTF8_ACCEPT) {
                codePoint = (0xFF >> characterClass) & b;
            } else {
                codePoint = (b & 0x3f) | (codePoint << 6);
            }
            int newState = TRANSLATION_TABLE[state + characterClass];
            switch (newState) {
                case UTF8_REJECT:
                    newState = handleInvalid(b, characterClass);
                    break;
                case UTF8_ACCEPT:
                    // We're finished
                    destSize += Character.toChars(codePoint, dest, destSize);
            }
            this.state = newState;
        }

        public boolean isReady() {
            return state == UTF8_ACCEPT;
        }

        private int toIgnore;

        private int handleInvalid(int rawByte, int characterClass) {
            InvalidReason invalidReason = INVALID_REASONS[state + characterClass];
            if (--toIgnore > 0) {
                return UTF8_REJECT;
            }
            if (invalidReason == null) {
                throw new AssertionError(shouldBeValidMsg(state, rawByte));
            }
            return onInvalid(invalidReason, rawByte);
        }

        private static String illegalStateMsg(int state) {
            return "Illegal state: " + state;
        }

        private static String shouldBeValidMsg(int state, int rawByte) {
            return "State " + state + " for byte " + StringUtil.byteToHexStringPadded(rawByte) + " should be valid!";
        }

        protected int onInvalid(InvalidReason invalidReason, int rawByte) {
            if (rawByte < 0 && invalidReason != InvalidReason.INSUFFICIENT_CONTINUATION_BYTES) {
                throw new IllegalArgumentException(
                    "Byte can't be negative/absent unless the reason is INSUFFICIENT_CONTINUATION_BYTES!"
                );
            }
            int numExpected = state / 36 + 1;
            int toIgnore = 0;
            switch (invalidReason) {
                case ILLEGAL_STATE:
                    throw new IllegalStateException(illegalStateMsg(state));
                case OVERFLOW_CODE_POINT:
                    insertReplacements(numExpected);
                    // fall-through
                case SURROGATE_CODE_POINT:
                    toIgnore = numExpected;
                case UNEXPECTED_CONTINUATION:
                case INVALID_BYTE:
                    insertReplacement();
                    break;
                case OVERLONG_ENCODING:
                    insertReplacements(isContinuationByte(rawByte) ? 2 : 1);
                    break;
                case INSUFFICIENT_CONTINUATION_BYTES:
                    insertReplacement();
                    if (rawByte != -1) {
                        // Re-interpret this character as the start of a new sequence
                        state = UTF8_ACCEPT;
                        processUnicode(rawByte);
                    }
                    return state;
                default:
                    throw new AssertionError(invalidReason);
            }
            this.toIgnore = toIgnore;
            return toIgnore > 0 ? UTF8_REJECT : UTF8_ACCEPT;
        }

        private void insertReplacement() {
            insertReplacements(1);
        }

        private void insertReplacements(int amount) {
            if (replace) {
                for (int i = 0; i < amount; i++) {
                    dest[destSize++] = REPLACEMENT_CHARACTER;
                }
            }
        }

        public String build() {
            char[] dest = this.dest;
            if (state != UTF8_ACCEPT && toIgnore <= 0) {
                onInvalid(InvalidReason.INSUFFICIENT_CONTINUATION_BYTES, -1);
            }
            this.dest = null; // Sanity if anyone ever tries to use this again
            if (dest == null) {
                throw new IllegalArgumentException("build() already called!");
            }
            return PlatformDependent.createSharedString(dest, destSize);
        }

        @Deprecated // Use build() where possible for higher performance
        public String toString() {
            if (dest == null) {
                throw new IllegalStateException("build() already called!");
            }
            return new String(dest, 0, destSize);
        }
    }

    public enum InvalidReason {
        UNEXPECTED_CONTINUATION,
        INSUFFICIENT_CONTINUATION_BYTES,
        OVERLONG_ENCODING,
        SURROGATE_CODE_POINT,
        OVERFLOW_CODE_POINT,
        INVALID_BYTE,
        ILLEGAL_STATE;
    }

    private static final UnsafeAccess UNSAFE_INSTANCE = new UnsafeAccess() {

        @Override
        public UTF8Processor fromCharArray(char[] dest, int destSize) {
            return new UTF8Processor(dest, destSize);
        }

        @Override
        public void processUnicode(UTF8Processor processor, int rawByte) {
            processor.processUnicode(rawByte);
        }

        @Override
        public char[] getDestinationBuffer(UTF8Processor processor) {
            return processor.dest;
        }

        @Override
        public int getDestinationBufferSize(UTF8Processor processor) {
            return processor.destSize;
        }

        @Override
        public void setDestinationBufferSize(UTF8Processor processor, int newSize) {
            processor.destSize = newSize;
        }

        @Override
        public boolean isAcceptingNewChar(UTF8Processor processor) {
            return processor.state == UTF8_ACCEPT;
        }
    };

    public static UnsafeAccess unsafeAccess() {
        return UNSAFE_INSTANCE;
    }

    public interface UnsafeAccess {
        UTF8Processor fromCharArray(char[] dest, int destSize);

        void processUnicode(UTF8Processor processor, int rawByte);

        char[] getDestinationBuffer(UTF8Processor processor);

        int getDestinationBufferSize(UTF8Processor processor);

        void setDestinationBufferSize(UTF8Processor processor, int newSize);

        boolean isAcceptingNewChar(UTF8Processor processor);
    }
}
