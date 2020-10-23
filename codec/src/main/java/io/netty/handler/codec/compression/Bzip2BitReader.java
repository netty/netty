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
 * An bit reader that allows the reading of single bit booleans, bit strings of
 * arbitrary length (up to 32 bits), and bit aligned 32-bit integers. A single byte
 * at a time is read from the {@link ByteBuf} when more bits are required.
 */
class Bzip2BitReader {
    /**
     * Maximum count of possible readable bytes to check.
     */
    private static final int MAX_COUNT_OF_READABLE_BYTES = Integer.MAX_VALUE >>> 3;

    /**
     * The {@link ByteBuf} from which to read data.
     */
    private ByteBuf in;

    /**
     * A buffer of bits read from the input stream that have not yet been returned.
     */
    private long bitBuffer;

    /**
     * The number of bits currently buffered in {@link #bitBuffer}.
     */
    private int bitCount;

    /**
     * Set the {@link ByteBuf} from which to read data.
     */
    void setByteBuf(ByteBuf in) {
        this.in = in;
    }

    /**
     * Reads up to 32 bits from the {@link ByteBuf}.
     * @param count The number of bits to read (maximum {@code 32} as a size of {@code int})
     * @return The bits requested, right-aligned within the integer
     */
    int readBits(final int count) {
        if (count < 0 || count > 32) {
            throw new IllegalArgumentException("count: " + count + " (expected: 0-32 )");
        }
        int bitCount = this.bitCount;
        long bitBuffer = this.bitBuffer;

        if (bitCount < count) {
            long readData;
            int offset;
            switch (in.readableBytes()) {
                case 1: {
                    readData = in.readUnsignedByte();
                    offset = 8;
                    break;
                }
                case 2: {
                    readData = in.readUnsignedShort();
                    offset = 16;
                    break;
                }
                case 3: {
                    readData = in.readUnsignedMedium();
                    offset = 24;
                    break;
                }
                default: {
                    readData = in.readUnsignedInt();
                    offset = 32;
                    break;
                }
            }

            bitBuffer = bitBuffer << offset | readData;
            bitCount += offset;
            this.bitBuffer = bitBuffer;
        }

        this.bitCount = bitCount -= count;
        return (int) (bitBuffer >>> bitCount & (count != 32 ? (1 << count) - 1 : 0xFFFFFFFFL));
    }

    /**
     * Reads a single bit from the {@link ByteBuf}.
     * @return {@code true} if the bit read was {@code 1}, otherwise {@code false}
     */
    boolean readBoolean() {
        return readBits(1) != 0;
    }

    /**
     * Reads 32 bits of input as an integer.
     * @return The integer read
     */
    int readInt() {
        return readBits(32);
    }

    /**
     * Refill the {@link ByteBuf} by one byte.
     */
    void refill() {
        int readData = in.readUnsignedByte();
        bitBuffer = bitBuffer << 8 | readData;
        bitCount += 8;
    }

    /**
     * Checks that at least one bit is available for reading.
     * @return {@code true} if one bit is available for reading, otherwise {@code false}
     */
    boolean isReadable() {
        return bitCount > 0 || in.isReadable();
    }

    /**
     * Checks that the specified number of bits available for reading.
     * @param count The number of bits to check
     * @return {@code true} if {@code count} bits are available for reading, otherwise {@code false}
     */
    boolean hasReadableBits(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count: " + count + " (expected value greater than 0)");
        }
        return bitCount >= count || (in.readableBytes() << 3 & Integer.MAX_VALUE) >= count - bitCount;
    }

    /**
     * Checks that the specified number of bytes available for reading.
     * @param count The number of bytes to check
     * @return {@code true} if {@code count} bytes are available for reading, otherwise {@code false}
     */
    boolean hasReadableBytes(int count) {
        if (count < 0 || count > MAX_COUNT_OF_READABLE_BYTES) {
            throw new IllegalArgumentException("count: " + count
                    + " (expected: 0-" + MAX_COUNT_OF_READABLE_BYTES + ')');
        }
        return hasReadableBits(count << 3);
    }
}
