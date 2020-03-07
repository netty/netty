/*
 * Copyright 2020 The Netty Project
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
// Protocol Buffers - Google's data interchange format
// Copyright 2008 Google Inc.  All rights reserved.
// https://developers.google.com/protocol-buffers/
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
//     * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following disclaimer
// in the documentation and/or other materials provided with the
// distribution.
//     * Neither the name of Google Inc. nor the names of its
// contributors may be used to endorse or promote products derived from
// this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
package io.netty.handler.codec.http;

import io.netty.util.internal.PlatformDependent;

import static java.lang.Character.MIN_HIGH_SURROGATE;
import static java.lang.Character.MIN_LOW_SURROGATE;
import static java.lang.Character.MIN_SUPPLEMENTARY_CODE_POINT;

/**
 * See original <a
 * href="https://github.com/protocolbuffers/protobuf/blob/master/java/core/src/main/java/com/google/protobuf/Utf8.java">
 * Utf8.java</a>
 */
public final class Utf8Utils {

    private Utf8Utils() {
        //empty
    }

    public static void decodeUtf8(byte[] srcBytes, int srcIndex, int size, StringBuilder destStrBuf) {
        // Bitwise OR combines the sign bits so any negative value fails the check.
        if ((srcIndex | size | srcBytes.length - srcIndex - size) < 0) {
            throw new ArrayIndexOutOfBoundsException(
                    String.format("buffer length=%d, index=%d, size=%d", srcBytes.length, srcIndex, size));
        }

        if (PlatformDependent.hasUnsafe()) {
            decodeUtf8Unsafe(srcBytes, srcIndex, size, destStrBuf);
        } else {
            decodeUtf8Safe(srcBytes, srcIndex, size, destStrBuf);
        }
    }

    public static void decodeUtf8Safe(byte[] srcBytes, int srcIndex, int size, StringBuilder outStrBuf) {
        int offset = srcIndex;
        final int limit = offset + size;

        // Optimize for 100% ASCII (Hotspot loves small simple top-level loops like this).
        // This simple loop stops when we encounter a byte >= 0x80 (i.e. non-ASCII).
        while (offset < limit) {
            byte b = srcBytes[offset];
            if (!DecodeUtil.isOneByte(b)) {
                break;
            }
            offset++;
            DecodeUtil.handleOneByte(b, outStrBuf);
        }

        while (offset < limit) {
            byte byte1 = srcBytes[offset++];
            if (DecodeUtil.isOneByte(byte1)) {
                DecodeUtil.handleOneByte(byte1, outStrBuf);
                // It's common for there to be multiple ASCII characters in a run mixed in, so add an
                // extra optimized loop to take care of these runs.
                while (offset < limit) {
                    byte b = srcBytes[offset];
                    if (!DecodeUtil.isOneByte(b)) {
                        break;
                    }
                    offset++;
                    DecodeUtil.handleOneByte(b, outStrBuf);
                }
            } else if (DecodeUtil.isTwoBytes(byte1)) {
                if (offset >= limit) {
                    throw new IllegalArgumentException("invalid UTF-8.");
                }
                DecodeUtil.handleTwoBytes(byte1, /* byte2 */ srcBytes[offset++], outStrBuf);
            } else if (DecodeUtil.isThreeBytes(byte1)) {
                if (offset >= limit - 1) {
                    throw new IllegalArgumentException("invalid UTF-8.");
                }
                DecodeUtil.handleThreeBytes(
                        byte1,
                        /* byte2 */ srcBytes[offset++],
                        /* byte3 */ srcBytes[offset++],
                        outStrBuf);
            } else {
                if (offset >= limit - 2) {
                    throw new IllegalArgumentException("invalid UTF-8.");
                }
                DecodeUtil.handleFourBytes(
                        byte1,
                        /* byte2 */ srcBytes[offset++],
                        /* byte3 */ srcBytes[offset++],
                        /* byte4 */ srcBytes[offset++],
                        outStrBuf);
            }
        }
    }

    public static void decodeUtf8Unsafe(byte[] srcBytes, int srcIndex, int size, StringBuilder outStrBuf) {
        int offset = srcIndex;
        final int limit = offset + size;

        // Optimize for 100% ASCII (Hotspot loves small simple top-level loops like this).
        // This simple loop stops when we encounter a byte >= 0x80 (i.e. non-ASCII).
        while (offset < limit) {
            byte b = PlatformDependent.getByte(srcBytes, offset);
            if (!DecodeUtil.isOneByte(b)) {
                break;
            }
            offset++;
            DecodeUtil.handleOneByte(b, outStrBuf);
        }

        while (offset < limit) {
            byte byte1 = PlatformDependent.getByte(srcBytes, offset++);
            if (DecodeUtil.isOneByte(byte1)) {
                DecodeUtil.handleOneByte(byte1, outStrBuf);
                // It's common for there to be multiple ASCII characters in a run mixed in, so add an
                // extra optimized loop to take care of these runs.
                while (offset < limit) {
                    byte b = PlatformDependent.getByte(srcBytes, offset);
                    if (!DecodeUtil.isOneByte(b)) {
                        break;
                    }
                    offset++;
                    DecodeUtil.handleOneByte(b, outStrBuf);
                }
            } else if (DecodeUtil.isTwoBytes(byte1)) {
                if (offset >= limit) {
                    throw new IllegalArgumentException("invalid UTF-8.");
                }
                DecodeUtil.handleTwoBytes(byte1, /* byte2 */ PlatformDependent.getByte(srcBytes, offset++), outStrBuf);
            } else if (DecodeUtil.isThreeBytes(byte1)) {
                if (offset >= limit - 1) {
                    throw new IllegalArgumentException("invalid UTF-8.");
                }
                DecodeUtil.handleThreeBytes(
                        byte1,
                        /* byte2 */ PlatformDependent.getByte(srcBytes, offset++),
                        /* byte3 */ PlatformDependent.getByte(srcBytes, offset++),
                        outStrBuf);
            } else {
                if (offset >= limit - 2) {
                    throw new IllegalArgumentException("invalid UTF-8.");
                }
                DecodeUtil.handleFourBytes(
                        byte1,
                        /* byte2 */ PlatformDependent.getByte(srcBytes, offset++),
                        /* byte3 */ PlatformDependent.getByte(srcBytes, offset++),
                        /* byte4 */ PlatformDependent.getByte(srcBytes, offset++),
                        outStrBuf);
            }
        }
    }

    private static class DecodeUtil {

        /**
         * Returns whether this is a single-byte codepoint (i.e., ASCII) with the form '0XXXXXXX'.
         */
        private static boolean isOneByte(byte b) {
            return b >= 0;
        }

        /**
         * Returns whether this is a two-byte codepoint with the form '10XXXXXX'.
         */
        private static boolean isTwoBytes(byte b) {
            return b < (byte) 0xE0;
        }

        /**
         * Returns whether this is a three-byte codepoint with the form '110XXXXX'.
         */
        private static boolean isThreeBytes(byte b) {
            return b < (byte) 0xF0;
        }

        private static void handleOneByte(byte byte1, StringBuilder strBuf) {
            strBuf.append((char) byte1);
        }

        private static void handleTwoBytes(byte byte1, byte byte2, StringBuilder strBuf) {
            // Simultaneously checks for illegal trailing-byte in leading position (<= '11000000') and
            // overlong 2-byte, '11000001'.
            if (byte1 < (byte) 0xC2 || isNotTrailingByte(byte2)) {
                throw new IllegalArgumentException("invalid UTF-8.");
            }
            strBuf.append((char) (((byte1 & 0x1F) << 6) | trailingByteValue(byte2)));
        }

        private static void handleThreeBytes(byte byte1, byte byte2, byte byte3, StringBuilder strBuf) {
            if (isNotTrailingByte(byte2)
                    // overlong? 5 most significant bits must not all be zero
                    || (byte1 == (byte) 0xE0 && byte2 < (byte) 0xA0)
                    // check for illegal surrogate codepoints
                    || (byte1 == (byte) 0xED && byte2 >= (byte) 0xA0)
                    || isNotTrailingByte(byte3)) {
                throw new IllegalArgumentException("invalid UTF-8.");
            }

            strBuf.append((char) (((byte1 & 0x0F) << 12) | (trailingByteValue(byte2) << 6) | trailingByteValue(byte3)));
        }

        private static void handleFourBytes(byte byte1, byte byte2, byte byte3, byte byte4, StringBuilder strBuf) {
            if (isNotTrailingByte(byte2)
                    // Check that 1 <= plane <= 16.  Tricky optimized form of:
                    //   valid 4-byte leading byte?
                    // if (byte1 > (byte) 0xF4 ||
                    //   overlong? 4 most significant bits must not all be zero
                    //     byte1 == (byte) 0xF0 && byte2 < (byte) 0x90 ||
                    //   codepoint larger than the highest code point (U+10FFFF)?
                    //     byte1 == (byte) 0xF4 && byte2 > (byte) 0x8F)
                    || (((byte1 << 28) + (byte2 - (byte) 0x90)) >> 30) != 0
                    || isNotTrailingByte(byte3)
                    || isNotTrailingByte(byte4)) {
                throw new IllegalArgumentException("invalid UTF-8.");
            }
            int codepoint =
                    ((byte1 & 0x07) << 18)
                            | (trailingByteValue(byte2) << 12)
                            | (trailingByteValue(byte3) << 6)
                            | trailingByteValue(byte4);
            strBuf.append(DecodeUtil.highSurrogate(codepoint));
            strBuf.append(DecodeUtil.lowSurrogate(codepoint));
        }

        /**
         * Returns whether the byte is not a valid continuation of the form '10XXXXXX'.
         */
        private static boolean isNotTrailingByte(byte b) {
            return b > (byte) 0xBF;
        }

        /**
         * Returns the actual value of the trailing byte (removes the prefix '10') for composition.
         */
        private static int trailingByteValue(byte b) {
            return b & 0x3F;
        }

        private static char highSurrogate(int codePoint) {
            return (char)
                    ((MIN_HIGH_SURROGATE - (MIN_SUPPLEMENTARY_CODE_POINT >>> 10)) + (codePoint >>> 10));
        }

        private static char lowSurrogate(int codePoint) {
            return (char) (MIN_LOW_SURROGATE + (codePoint & 0x3ff));
        }
    }

}
