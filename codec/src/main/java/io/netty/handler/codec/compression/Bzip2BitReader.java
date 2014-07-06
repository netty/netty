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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;

/**
 * An bit reader that allows the reading of single bit booleans, bit strings of
 * arbitrary length (up to 24 bits), and bit aligned 32-bit integers. A single byte
 * at a time is read from the {@link ByteBuf} when more bits are required.
 */
class Bzip2BitReader {
    /**
     * A buffer of bits read from the input stream that have not yet been returned.
     */
    private int bitBuffer;

    /**
     * The number of bits currently buffered in {@link #bitBuffer}.
     */
    private int bitCount;

    /**
     * Reads up to 24 bits from the {@link ByteBuf}.
     * @param count The number of bits to read (maximum {@code 24}, because the {@link #bitBuffer}
     *              is {@code int} and it can store up to {@code 8} bits before calling)
     * @return The bits requested, right-aligned within the integer
     */
    int readBits(ByteBuf in, final int count) {
        if (count > 24) {
            throw new IllegalArgumentException("count");
        }
        int bitCount = this.bitCount;
        int bitBuffer = this.bitBuffer;

        if (bitCount < count) {
            do {
                int uByte = in.readUnsignedByte();
                bitBuffer = bitBuffer << 8 | uByte;
                bitCount += 8;
            } while (bitCount < count);

            this.bitBuffer = bitBuffer;
        }

        this.bitCount = bitCount -= count;
        return (bitBuffer >>> bitCount) & ((1 << count) - 1);
    }

    /**
     * Reads a single bit from the {@link ByteBuf}.
     * @return {@code true} if the bit read was {@code 1}, otherwise {@code false}
     */
    boolean readBoolean(ByteBuf in) {
        return readBits(in, 1) != 0;
    }

    /**
     * Reads 32 bits of input as an integer.
     * @return The integer read
     */
    int readInt(ByteBuf in) {
        return readBits(in, 16) << 16 | readBits(in, 16);
    }

    /**
     * Checks that at least one bit is available for reading.
     * @return {@code true} if one bit is available for reading, otherwise {@code false}
     */
    boolean hasBit(ByteBuf in) {
        return bitCount > 0 || in.isReadable();
    }
}
