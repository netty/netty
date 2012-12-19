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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;

/**
 * Uncompresses an input {@link ByteBuf} encoded with Snappy compression into an
 * output {@link ByteBuf}.
 *
 * See http://code.google.com/p/snappy/source/browse/trunk/format_description.txt
 */
public class Snappy {
    private static final int MAX_HT_SIZE = 1 << 14;
    private static final int MIN_COMPRESSIBLE_BYTES = 15;

    // constants for the tag types
    private static final int LITERAL = 0;
    private static final int COPY_1_BYTE_OFFSET = 1;
    private static final int COPY_2_BYTE_OFFSET = 2;
    private static final int COPY_4_BYTE_OFFSET = 3;

    private int inputLength;

    public void reset() {
        inputLength = 0;
    }

    public void encode(ByteBuf in, ByteBuf out, int length) {
        // Write the preamble length to the output buffer
        int bytesToEncode = 1 + bitsToEncode(length - 1) / 7;
        for (int i = 0; i < bytesToEncode; i++) {
            if (i == bytesToEncode - 1) {
                out.writeByte(length >> i * 7);
            } else {
                out.writeByte(0x80 | length >> i * 7);
            }
        }

        int inIndex = in.readerIndex();
        final int baseIndex = in.readerIndex();
        final int maxIndex = length;

        final short[] table = getHashTable(maxIndex);
        final int shift = 32 - (int) Math.floor(Math.log(table.length) / Math.log(2));

        int nextEmit = inIndex;

        if (maxIndex - inIndex >= MIN_COMPRESSIBLE_BYTES) {
            int nextHash = hash(in, ++inIndex, shift);
            outer: while (true) {
                int skip = 32;

                int candidate;
                int nextIndex = inIndex;
                do {
                    inIndex = nextIndex;
                    int hash = nextHash;
                    int bytesBetweenHashLookups = skip++ >> 5;
                    nextIndex = inIndex + bytesBetweenHashLookups;

                    if (nextIndex > maxIndex) {
                        break outer;
                    }

                    nextHash = hash(in, nextIndex, shift);

                    candidate = baseIndex + table[hash];

                    table[hash] = (short) (inIndex - baseIndex);
                }
                while (in.getInt(inIndex) != in.getInt(candidate));

                encodeLiteral(in, out, inIndex - nextEmit);

                int insertTail;
                do {
                    int base = inIndex;
                    int matched = 4 + findMatchingLength(in, candidate + 4, inIndex + 4, maxIndex);
                    inIndex += matched;
                    int offset = base - candidate;
                    encodeCopy(out, offset, matched);
                    in.readerIndex(in.readerIndex() + matched);
                    insertTail = inIndex - 1;
                    nextEmit = inIndex;
                    if (inIndex >= maxIndex) {
                        break outer;
                    }

                    int prevHash = hash(in, insertTail, shift);
                    table[prevHash] = (short) (inIndex - baseIndex - 1);
                    int currentHash = hash(in, insertTail + 1, shift);
                    candidate = baseIndex + table[currentHash];
                    table[currentHash] = (short) (inIndex - baseIndex);
                }
                while (in.getInt(insertTail + 1) == in.getInt(candidate));

                nextHash = hash(in, insertTail + 2, shift);
                ++inIndex;
            }
        }

        // If there are any remaining characters, write them out as a literal
        if (nextEmit < maxIndex) {
            encodeLiteral(in, out, maxIndex - nextEmit);
        }
    }

    /**
     * Hashes the 4 bytes located at index, shifting the resulting hash into
     * the appropriate range for our hash table.
     *
     * @param in The input buffer to read 4 bytes from
     * @param index The index to read at
     * @param shift The shift value, for ensuring that the resulting value is
     *     withing the range of our hash table size
     * @return A 32-bit hash of 4 bytes located at index
     */
    private static int hash(ByteBuf in, int index, int shift) {
        return in.getInt(index) + 0x1e35a7bd >>> shift;
    }

    /**
     * Creates an appropriately sized hashtable for the given input size
     *
     * @param inputSize The size of our input, ie. the number of bytes we need to encode
     * @return An appropriately sized empty hashtable
     */
    private static short[] getHashTable(int inputSize) {
        int htSize = 256;
        while (htSize < MAX_HT_SIZE && htSize < inputSize) {
            htSize <<= 1;
        }

        short[] table;
        if (htSize <= 256) {
            table = new short[256];
        } else {
            table = new short[MAX_HT_SIZE];
        }

        return table;
    }

    /**
     * Iterates over the supplied input buffer between the supplied minIndex and
     * maxIndex to find how long our matched copy overlaps with an already-written
     * literal value.
     *
     * @param in The input buffer to scan over
     * @param minIndex The index in the input buffer to start scanning from
     * @param inIndex The index of the start of our copy
     * @param maxIndex The length of our input buffer
     * @return The number of bytes for which our candidate copy is a repeat of
     */
    private static int findMatchingLength(ByteBuf in, int minIndex, int inIndex, int maxIndex) {
        int matched = 0;

        while (inIndex <= maxIndex - 4 &&
                in.getInt(inIndex) == in.getInt(minIndex + matched)) {
            inIndex += 4;
            matched += 4;
        }

        while (inIndex < maxIndex && in.getByte(minIndex + matched) == in.getByte(inIndex)) {
            ++inIndex;
            ++matched;
        }

        return matched;
    }

    /**
     * Calculates the minimum number of bits required to encode a value.  This can
     * then in turn be used to calculate the number of septets or octets (as
     * appropriate) to use to encode a length parameter.
     *
     * @param value The value to calculate the minimum number of bits required to encode
     * @return The minimum number of bits required to encode the supplied value
     */
    private static int bitsToEncode(int value) {
        int highestOneBit = Integer.highestOneBit(value);
        int bitLength = 0;
        while ((highestOneBit >>= 1) != 0) {
            bitLength++;
        }

        return bitLength;
    }

    /**
     * Writes a literal to the supplied output buffer by directly copying from
     * the input buffer.  The literal is taken from the current readerIndex
     * up to the supplied length.
     *
     * @param in The input buffer to copy from
     * @param out The output buffer to copy to
     * @param length The length of the literal to copy
     */
    private static void encodeLiteral(ByteBuf in, ByteBuf out, int length) {
        if (length < 61) {
            out.writeByte(length - 1 << 2);
        } else {
            int bitLength = bitsToEncode(length - 1);
            int bytesToEncode = 1 + bitLength / 8;
            out.writeByte(59 + bytesToEncode << 2);
            for (int i = 0; i < bytesToEncode; i++) {
                out.writeByte(length - 1 >> i * 8 & 0x0ff);
            }
        }

        out.writeBytes(in, length);
    }

    private static void encodeCopyWithOffset(ByteBuf out, int offset, int length) {
        if (length < 12 && offset < 2048) {
            out.writeByte(COPY_1_BYTE_OFFSET | length - 4 << 2 | offset >> 8 << 5);
            out.writeByte(offset & 0x0ff);
        } else {
            out.writeByte(COPY_2_BYTE_OFFSET | length - 1 << 2);
            out.writeByte(offset & 0x0ff);
            out.writeByte(offset >> 8 & 0x0ff);
        }
    }

    /**
     * Encodes a series of copies, each at most 64 bytes in length.
     *
     * @param out The output buffer to write the copy pointer to
     * @param offset The offset at which the original instance lies
     * @param length The length of the original instance
     */
    private static void encodeCopy(ByteBuf out, int offset, int length) {
        while (length >= 68) {
            encodeCopyWithOffset(out, offset, 64);
            length -= 64;
        }

        if (length > 64) {
            encodeCopyWithOffset(out, offset, 60);
            length -= 60;
        }

        encodeCopyWithOffset(out, offset, length);
    }

    public void decode(ByteBuf in, ByteBuf out, int maxLength) {
        int inIndex = in.readerIndex();
        if (inputLength == 0) {
            inputLength = readPreamble(in);
        }

        if (inputLength == 0  || in.readerIndex() - inIndex + in.readableBytes() < maxLength) {
            // Wait until we've got the entire chunk before continuing
            return;
        }

        out.ensureWritableBytes(inputLength);

        while (in.readable() && in.readerIndex() - inIndex < maxLength) {
            byte tag = in.readByte();
            switch (tag & 0x03) {
            case LITERAL:
                decodeLiteral(tag, in, out);
                break;
            case COPY_1_BYTE_OFFSET:
                decodeCopyWith1ByteOffset(tag, in, out);
                break;
            case COPY_2_BYTE_OFFSET:
                decodeCopyWith2ByteOffset(tag, in, out);
                break;
            case COPY_4_BYTE_OFFSET:
                decodeCopyWith4ByteOffset(tag, in, out);
                break;
            }
        }
    }

    /**
     * Reads the length varint (a series of bytes, where the lower 7 bits
     * are data and the upper bit is a flag to indicate more bytes to be
     * read).
     *
     * @param in The input buffer to read the preamble from
     * @return The calculated length based on the input buffer, or 0 if
     *   no preamble is able to be calculated
     */
    private static int readPreamble(ByteBuf in) {
        int length = 0;
        int byteIndex = 0;
        while (in.readableBytes() > 0) {
            int current = in.readByte() & 0x0ff;
            length += current << byteIndex++ * 7;
            if ((current & 0x80) != 0x80) {
                return length;
            }

            if (byteIndex >= 4) {
                throw new CompressionException("Preamble is greater than 4 bytes");
            }
        }

        return 0;
    }

    /**
     * Reads a literal from the input buffer directly to the output buffer.
     * A "literal" is an uncompressed segment of data stored directly in the
     * byte stream.
     *
     * @param tag The tag that identified this segment as a literal is also
     *     used to encode part of the length of the data
     * @param in The input buffer to read the literal from
     * @param out The output buffer to write the literal to
     */
    private static void decodeLiteral(byte tag, ByteBuf in, ByteBuf out) {
        int length;
        switch(tag >> 2 & 0x3F) {
        case 60:
            length = in.readByte() & 0x0ff;
            break;
        case 61:
            length = (in.readByte() & 0x0ff)
                   + ((in.readByte() & 0x0ff) << 8);
            break;
        case 62:
            length = (in.readByte() & 0x0ff)
                   + ((in.readByte() & 0x0ff) << 8)
                   + ((in.readByte() & 0x0ff) << 16);
            break;
        case 64:
            length = (in.readByte() & 0x0ff)
                    + ((in.readByte() & 0x0ff) << 8)
                    + ((in.readByte() & 0x0ff) << 16)
                    + ((in.readByte() & 0x0ff) << 24);
            break;
        default:
            length = tag >> 2 & 0x3F;
        }
        length += 1;

        out.writeBytes(in, length);
    }

    /**
     * Reads a compressed reference offset and length from the supplied input
     * buffer, seeks back to the appropriate place in the input buffer and
     * writes the found data to the supplied output stream.
     *
     * @param tag The tag used to identify this as a copy is also used to encode
     *     the length and part of the offset
     * @param in The input buffer to read from
     * @param out The output buffer to write to
     * @throws CompressionException If the read offset is invalid
     */
    private static void decodeCopyWith1ByteOffset(byte tag, ByteBuf in, ByteBuf out) {
        int initialIndex = in.readerIndex();
        int length = 4 + ((tag & 0x0c) >> 2);
        int offset = 1 + ((tag & 0x0e0) << 8)
                   + (in.readByte() & 0x0ff);

        validateOffset(offset, initialIndex);

        in.markReaderIndex();
        in.readerIndex(initialIndex - offset);
        in.readBytes(out, length);
        in.resetReaderIndex();
    }

    /**
     * Reads a compressed reference offset and length from the supplied input
     * buffer, seeks back to the appropriate place in the input buffer and
     * writes the found data to the supplied output stream.
     *
     * @param tag The tag used to identify this as a copy is also used to encode
     *     the length and part of the offset
     * @param in The input buffer to read from
     * @param out The output buffer to write to
     * @throws CompressionException If the read offset is invalid
     */
    private static void decodeCopyWith2ByteOffset(byte tag, ByteBuf in, ByteBuf out) {
        int initialIndex = in.readerIndex();
        int length = 1 + (tag >> 2 & 0x03f);
        int offset = 1 + (in.readByte() & 0x0ff)
                   + (in.readByte() & 0x0ff) << 8;

        validateOffset(offset, initialIndex);

        in.markReaderIndex();
        in.readerIndex(initialIndex - offset);
        in.readBytes(out, length);
        in.resetReaderIndex();
    }

    /**
     * Reads a compressed reference offset and length from the supplied input
     * buffer, seeks back to the appropriate place in the input buffer and
     * writes the found data to the supplied output stream.
     *
     * @param tag The tag used to identify this as a copy is also used to encode
     *     the length and part of the offset
     * @param in The input buffer to read from
     * @param out The output buffer to write to
     * @throws CompressionException If the read offset is invalid
     */
    private static void decodeCopyWith4ByteOffset(byte tag, ByteBuf in, ByteBuf out) {
        int initialIndex = in.readerIndex();
        int length = 1 + (tag >> 2 & 0x03F);
        int offset = 1 + (in.readByte() & 0x0ff)
                   + ((in.readByte() & 0x0ff) << 8)
                   + ((in.readByte() & 0x0ff) << 16)
                   + ((in.readByte() & 0x0ff) << 24);

        validateOffset(offset, initialIndex);

        in.markReaderIndex();
        in.readerIndex(initialIndex - offset);
        in.readBytes(out, length);
        in.resetReaderIndex();
    }

    /**
     * Validates that the offset extracted from a compressed reference is within
     * the permissible bounds of an offset (4 <= offset <= 32768), and does not
     * exceed the length of the chunk currently read so far.
     *
     * @param offset The offset extracted from the compressed reference
     * @param chunkSizeSoFar The number of bytes read so far from this chunk
     * @throws CompressionException if the offset is invalid
     */
    private static void validateOffset(int offset, int chunkSizeSoFar) {
        if (offset > Short.MAX_VALUE) {
            throw new CompressionException("Offset exceeds maximum permissible value");
        }

        if (offset <= 4) {
            throw new CompressionException("Offset is less than minimum permissible value");
        }

        if (offset > chunkSizeSoFar) {
            throw new CompressionException("Offset exceeds size of chunk");
        }
    }
}
