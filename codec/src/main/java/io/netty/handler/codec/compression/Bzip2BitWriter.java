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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;

/**
 * A bit writer that allows the writing of single bit booleans, unary numbers, bit strings
 * of arbitrary length (up to 32 bits), and bit aligned 32-bit integers. A single byte at a
 * time is written to the {@link ByteBuf} when sufficient bits have been accumulated.
 */
final class Bzip2BitWriter {
    /**
     * A buffer of bits waiting to be written to the output stream.
     */
    private long bitBuffer;

    /**
     * The number of bits currently buffered in {@link #bitBuffer}.
     */
    private int bitCount;

    /**
     * Writes up to 32 bits to the output {@link ByteBuf}.
     * @param count The number of bits to write (maximum {@code 32} as a size of {@code int})
     * @param value The bits to write
     */
    void writeBits(ByteBuf out, final int count, final long value) {
        if (count < 0 || count > 32) {
            throw new IllegalArgumentException("count: " + count + " (expected: 0-32)");
        }
        int bitCount = this.bitCount;
        long bitBuffer = this.bitBuffer | value << 64 - count >>> bitCount;
        bitCount += count;

        if (bitCount >= 32) {
            out.writeInt((int) (bitBuffer >>> 32));
            bitBuffer <<= 32;
            bitCount -= 32;
        }
        this.bitBuffer = bitBuffer;
        this.bitCount = bitCount;
    }

    /**
     * Writes a single bit to the output {@link ByteBuf}.
     * @param value The bit to write
     */
    void writeBoolean(ByteBuf out, final boolean value) {
        int bitCount = this.bitCount + 1;
        long bitBuffer = this.bitBuffer | (value ? 1L << 64 - bitCount : 0L);

        if (bitCount == 32) {
            out.writeInt((int) (bitBuffer >>> 32));
            bitBuffer = 0;
            bitCount = 0;
        }
        this.bitBuffer = bitBuffer;
        this.bitCount = bitCount;
    }

    /**
     * Writes a zero-terminated unary number to the output {@link ByteBuf}.
     * Example of the output for value = 6: {@code 1111110}
     * @param value The number of {@code 1} to write
     */
    void writeUnary(ByteBuf out, int value) {
        if (value < 0) {
            throw new IllegalArgumentException("value: " + value + " (expected 0 or more)");
        }
        while (value-- > 0) {
            writeBoolean(out, true);
        }
        writeBoolean(out, false);
    }

    /**
     * Writes an integer as 32 bits to the output {@link ByteBuf}.
     * @param value The integer to write
     */
    void writeInt(ByteBuf out, final int value) {
        writeBits(out, 32, value);
    }

    /**
     * Writes any remaining bits to the output {@link ByteBuf},
     * zero padding to a whole byte as required.
     */
    void flush(ByteBuf out) {
        final int bitCount = this.bitCount;

        if (bitCount > 0) {
            final long bitBuffer = this.bitBuffer;
            final int shiftToRight = 64 - bitCount;

            if (bitCount <= 8) {
                out.writeByte((int) (bitBuffer >>> shiftToRight << 8 - bitCount));
            } else if (bitCount <= 16) {
                out.writeShort((int) (bitBuffer >>> shiftToRight << 16 - bitCount));
            } else if (bitCount <= 24) {
                out.writeMedium((int) (bitBuffer >>> shiftToRight << 24 - bitCount));
            } else {
                out.writeInt((int) (bitBuffer >>> shiftToRight << 32 - bitCount));
            }
        }
    }
}
