/*
 * Copyright 2012 The Netty Project
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
package io.netty5.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty5.buffer.BufferUtil;
import io.netty5.buffer.api.Buffer;

/**
 * Uncompresses an input {@link ByteBuf} encoded with Snappy compression into an
 * output {@link ByteBuf}.
 *
 * See <a href="https://github.com/google/snappy/blob/master/format_description.txt">snappy format</a>.
 */
public final class Snappy {

    private static final int MAX_HT_SIZE = 1 << 14;
    private static final int MIN_COMPRESSIBLE_BYTES = 15;

    // used as a return value to indicate that we haven't yet read our full preamble
    private static final int PREAMBLE_NOT_FULL = -1;
    private static final int NOT_ENOUGH_INPUT = -1;

    // constants for the tag types
    private static final int LITERAL = 0;
    private static final int COPY_1_BYTE_OFFSET = 1;
    private static final int COPY_2_BYTE_OFFSET = 2;
    private static final int COPY_4_BYTE_OFFSET = 3;

    private State state = State.READING_PREAMBLE;
    private byte tag;
    private int written;

    private enum State {
        READING_PREAMBLE,
        READING_TAG,
        READING_LITERAL,
        READING_COPY
    }

    public void reset() {
        state = State.READING_PREAMBLE;
        tag = 0;
        written = 0;
    }

    public void encode(final Buffer in, final Buffer out, final int length) {
        // Write the preamble length to the output buffer
        for (int i = 0;; i ++) {
            int b = length >>> i * 7;
            if ((b & 0xFFFFFF80) != 0) {
                out.writeByte((byte) (b & 0x7f | 0x80));
            } else {
                out.writeByte((byte) b);
                break;
            }
        }

        int inIndex = in.readerOffset();
        final int baseIndex = inIndex;

        final short[] table = getHashTable(length);
        final int shift = Integer.numberOfLeadingZeros(table.length) + 1;

        int nextEmit = inIndex;

        if (length - inIndex >= MIN_COMPRESSIBLE_BYTES) {
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

                    // We need at least 4 remaining bytes to read the hash
                    if (nextIndex > length - 4) {
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
                    int matched = 4 + findMatchingLength(in, candidate + 4, inIndex + 4, length);
                    inIndex += matched;
                    int offset = base - candidate;
                    encodeCopy(out, offset, matched);
                    in.readerOffset(in.readerOffset() + matched);
                    insertTail = inIndex - 1;
                    nextEmit = inIndex;
                    if (inIndex >= length - 4) {
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
        if (nextEmit < length) {
            encodeLiteral(in, out, length - nextEmit);
        }
    }

    /**
     * Hashes the 4 bytes located at index, shifting the resulting hash into
     * the appropriate range for our hash table.
     *
     * @param in The input buffer to read 4 bytes from
     * @param index The index to read at
     * @param shift The shift value, for ensuring that the resulting value is
     *     within the range of our hash table size
     * @return A 32-bit hash of 4 bytes located at index
     */
    private static int hash(Buffer in, int index, int shift) {
        return in.getInt(index) * 0x1e35a7bd >>> shift;
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
        return new short[htSize];
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
    private static int findMatchingLength(Buffer in, int minIndex, int inIndex, int maxIndex) {
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
    static void encodeLiteral(Buffer in, Buffer out, int length) {
        if (length < 61) {
            out.writeByte((byte) (length - 1 << 2));
        } else {
            int bitLength = bitsToEncode(length - 1);
            int bytesToEncode = 1 + bitLength / 8;
            out.writeByte((byte) (59 + bytesToEncode << 2));
            for (int i = 0; i < bytesToEncode; i++) {
                out.writeByte((byte) (length - 1 >> i * 8 & 0x0ff));
            }
        }

        out.ensureWritable(length);
        in.copyInto(in.readerOffset(), out, out.writerOffset(), length);
        in.skipReadableBytes(length);
        out.skipWritableBytes(length);
    }

    private static void encodeCopyWithOffset(Buffer out, int offset, int length) {
        if (length < 12 && offset < 2048) {
            out.writeByte((byte) (COPY_1_BYTE_OFFSET | length - 4 << 2 | offset >> 8 << 5));
            out.writeByte((byte) (offset & 0x0ff));
        } else {
            out.writeByte((byte) (COPY_2_BYTE_OFFSET | length - 1 << 2));
            out.writeByte((byte) (offset & 0x0ff));
            out.writeByte((byte) (offset >> 8 & 0x0ff));
        }
    }

    /**
     * Encodes a series of copies, each at most 64 bytes in length.
     *
     * @param out The output buffer to write the copy pointer to
     * @param offset The offset at which the original instance lies
     * @param length The length of the original instance
     */
    private static void encodeCopy(Buffer out, int offset, int length) {
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

    public void decode(Buffer in, Buffer out) {
        while (in.readableBytes() > 0) {
            switch (state) {
            case READING_PREAMBLE:
                int uncompressedLength = readPreamble(in);
                if (uncompressedLength == PREAMBLE_NOT_FULL) {
                    // We've not yet read all of the preamble, so wait until we can
                    return;
                }
                if (uncompressedLength == 0) {
                    // Should never happen, but it does mean we have nothing further to do
                    return;
                }
                out.ensureWritable(uncompressedLength);
                state = State.READING_TAG;
                // fall through
            case READING_TAG:
                if (in.readableBytes() == 0) {
                    return;
                }
                tag = in.readByte();
                switch (tag & 0x03) {
                case LITERAL:
                    state = State.READING_LITERAL;
                    break;
                case COPY_1_BYTE_OFFSET:
                case COPY_2_BYTE_OFFSET:
                case COPY_4_BYTE_OFFSET:
                    state = State.READING_COPY;
                    break;
                }
                break;
            case READING_LITERAL:
                int literalWritten = decodeLiteral(tag, in, out);
                if (literalWritten != NOT_ENOUGH_INPUT) {
                    state = State.READING_TAG;
                    written += literalWritten;
                } else {
                    // Need to wait for more data
                    return;
                }
                break;
            case READING_COPY:
                int decodeWritten;
                switch (tag & 0x03) {
                case COPY_1_BYTE_OFFSET:
                    decodeWritten = decodeCopyWith1ByteOffset(tag, in, out, written);
                    if (decodeWritten != NOT_ENOUGH_INPUT) {
                        state = State.READING_TAG;
                        written += decodeWritten;
                    } else {
                        // Need to wait for more data
                        return;
                    }
                    break;
                case COPY_2_BYTE_OFFSET:
                    decodeWritten = decodeCopyWith2ByteOffset(tag, in, out, written);
                    if (decodeWritten != NOT_ENOUGH_INPUT) {
                        state = State.READING_TAG;
                        written += decodeWritten;
                    } else {
                        // Need to wait for more data
                        return;
                    }
                    break;
                case COPY_4_BYTE_OFFSET:
                    decodeWritten = decodeCopyWith4ByteOffset(tag, in, out, written);
                    if (decodeWritten != NOT_ENOUGH_INPUT) {
                        state = State.READING_TAG;
                        written += decodeWritten;
                    } else {
                        // Need to wait for more data
                        return;
                    }
                    break;
                }
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
    private static int readPreamble(Buffer in) {
        int length = 0;
        int byteIndex = 0;
        while (in.readableBytes() > 0) {
            int current = in.readUnsignedByte();
            length |= (current & 0x7f) << byteIndex++ * 7;
            if ((current & 0x80) == 0) {
                return length;
            }

            if (byteIndex >= 4) {
                throw new DecompressionException("Preamble is greater than 4 bytes");
            }
        }

        return 0;
    }

    /**
     * Get the length varint (a series of bytes, where the lower 7 bits
     * are data and the upper bit is a flag to indicate more bytes to be
     * read).
     *
     * @param in The input buffer to get the preamble from
     * @return The calculated length based on the input buffer, or 0 if
     *   no preamble is able to be calculated
     */
    int getPreamble(Buffer in) {
        if (state == State.READING_PREAMBLE) {
            int readerIndex = in.readerOffset();
            try {
                return readPreamble(in);
            } finally {
                in.readerOffset(readerIndex);
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
     *            used to encode part of the length of the data
     * @param in The input buffer to read the literal from
     * @param out The output buffer to write the literal to
     * @return The number of bytes appended to the output buffer, or -1 to indicate "try again later"
     */
    static int decodeLiteral(byte tag, Buffer in, Buffer out) {
        int readerIndex = in.readerOffset();
        int length;
        switch(tag >> 2 & 0x3F) {
        case 60:
            if (in.readableBytes() == 0) {
                return NOT_ENOUGH_INPUT;
            }
            length = in.readUnsignedByte();
            break;
        case 61:
            if (in.readableBytes() < 2) {
                return NOT_ENOUGH_INPUT;
            }
            length = BufferUtil.reverseUnsignedShort(in.readUnsignedShort());
            break;
        case 62:
            if (in.readableBytes() < 3) {
                return NOT_ENOUGH_INPUT;
            }
            length = BufferUtil.reverseUnsignedMedium(in.readUnsignedMedium());
            break;
        case 63:
            if (in.readableBytes() < 4) {
                return NOT_ENOUGH_INPUT;
            }
            length = Integer.reverseBytes(in.readInt());
            break;
        default:
            length = tag >> 2 & 0x3F;
        }
        length += 1;

        if (in.readableBytes() < length) {
            in.readerOffset(readerIndex);
            return NOT_ENOUGH_INPUT;
        }

        out.ensureWritable(length);
        in.copyInto(in.readerOffset(), out, out.writerOffset(), length);
        in.skipReadableBytes(length);
        out.skipWritableBytes(length);
        return length;
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
     * @return The number of bytes appended to the output buffer, or -1 to indicate
     *     "try again later"
     * @throws DecompressionException If the read offset is invalid
     */
    private static int decodeCopyWith1ByteOffset(byte tag, Buffer in, Buffer out, int writtenSoFar) {
        if (in.readableBytes() == 0) {
            return NOT_ENOUGH_INPUT;
        }

        int initialIndex = out.writerOffset();
        int length = 4 + ((tag & 0x01c) >> 2);
        int offset = (tag & 0x0e0) << 8 >> 5 | in.readUnsignedByte();

        validateOffset(offset, writtenSoFar);

        int readerIndex = out.readerOffset();
        if (offset < length) {
            int copies = length / offset;
            for (; copies > 0; copies--) {
                copyRegion(out, initialIndex, offset, offset);
            }
            if (length % offset != 0) {
                copyRegion(out, initialIndex, offset, length % offset);
            }
        } else {
            copyRegion(out, initialIndex, offset, length);
        }
        out.readerOffset(readerIndex);

        return length;
    }

    private static void copyRegion(Buffer out, int initialIndex, int offset, int length) {
        out.readerOffset(initialIndex - offset);
        out.copyInto(out.readerOffset(), out, out.writerOffset(), length);
        out.skipWritableBytes(length).skipReadableBytes(length);
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
     * @throws DecompressionException If the read offset is invalid
     * @return The number of bytes appended to the output buffer, or -1 to indicate
     *     "try again later"
     */
    private static int decodeCopyWith2ByteOffset(byte tag, Buffer in, Buffer out, int writtenSoFar) {
        if (in.readableBytes() < 2) {
            return NOT_ENOUGH_INPUT;
        }

        int initialIndex = out.writerOffset();
        int length = 1 + (tag >> 2 & 0x03f);
        int offset = BufferUtil.reverseUnsignedShort(in.readUnsignedShort());

        validateOffset(offset, writtenSoFar);

        int readerIndex = out.readerOffset();
        if (offset < length) {
            int copies = length / offset;
            for (; copies > 0; copies--) {
                copyRegion(out, initialIndex, offset, offset);
            }
            if (length % offset != 0) {
                copyRegion(out, initialIndex, offset, length % offset);
            }
        } else {
            copyRegion(out, initialIndex, offset, length);
        }
        out.readerOffset(readerIndex);

        return length;
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
     * @return The number of bytes appended to the output buffer, or -1 to indicate
     *     "try again later"
     * @throws DecompressionException If the read offset is invalid
     */
    private static int decodeCopyWith4ByteOffset(byte tag, Buffer in, Buffer out, int writtenSoFar) {
        if (in.readableBytes() < 4) {
            return NOT_ENOUGH_INPUT;
        }

        int initialIndex = out.writerOffset();
        int length = 1 + (tag >> 2 & 0x03F);
        int offset = Integer.reverseBytes(in.readInt());

        validateOffset(offset, writtenSoFar);

        int readerIndex = out.readerOffset();
        if (offset < length) {
            int copies = length / offset;
            for (; copies > 0; copies--) {
                copyRegion(out, initialIndex, offset, offset);
            }
            if (length % offset != 0) {
                copyRegion(out, initialIndex, offset, length % offset);
            }
        } else {
            copyRegion(out, initialIndex, offset, length);
        }
        out.readerOffset(readerIndex);

        return length;
    }

    /**
     * Validates that the offset extracted from a compressed reference is within
     * the permissible bounds of an offset (0 < offset < Integer.MAX_VALUE), and does not
     * exceed the length of the chunk currently read so far.
     *
     * @param offset The offset extracted from the compressed reference
     * @param chunkSizeSoFar The number of bytes read so far from this chunk
     * @throws DecompressionException if the offset is invalid
     */
    private static void validateOffset(int offset, int chunkSizeSoFar) {
        if (offset == 0) {
            throw new DecompressionException("Offset is less than minimum permissible value");
        }

        if (offset < 0) {
            // Due to arithmetic overflow
            throw new DecompressionException("Offset is greater than maximum value supported by this implementation");
        }

        if (offset > chunkSizeSoFar) {
            throw new DecompressionException("Offset exceeds size of chunk");
        }
    }

    /**
     * Computes the CRC32C checksum of the supplied data and performs the "mask" operation
     * on the computed checksum
     *
     * @param data The input data to calculate the CRC32C checksum of
     */
    static int calculateChecksum(Buffer data) {
        return calculateChecksum(data, data.readerOffset(), data.readableBytes());
    }

    /**
     * Computes the CRC32C checksum of the supplied data and performs the "mask" operation
     * on the computed checksum
     *
     * @param data The input data to calculate the CRC32C checksum of
     */
    static int calculateChecksum(Buffer data, int offset, int length) {
        Crc32c crc32 = new Crc32c();
        try {
            crc32.update(data, offset, length);
            return maskChecksum(crc32.getValue());
        } finally {
            crc32.reset();
        }
    }

    /**
     * Computes the CRC32C checksum of the supplied data, performs the "mask" operation
     * on the computed checksum, and then compares the resulting masked checksum to the
     * supplied checksum.
     *
     * @param expectedChecksum The checksum decoded from the stream to compare against
     * @param data The input data to calculate the CRC32C checksum of
     * @throws DecompressionException If the calculated and supplied checksums do not match
     */
    static void validateChecksum(int expectedChecksum, Buffer data) {
        validateChecksum(expectedChecksum, data, data.readerOffset(), data.readableBytes());
    }

    /**
     * Computes the CRC32C checksum of the supplied data, performs the "mask" operation
     * on the computed checksum, and then compares the resulting masked checksum to the
     * supplied checksum.
     *
     * @param expectedChecksum The checksum decoded from the stream to compare against
     * @param data The input data to calculate the CRC32C checksum of
     * @throws DecompressionException If the calculated and supplied checksums do not match
     */
    static void validateChecksum(int expectedChecksum, Buffer data, int offset, int length) {
        final int actualChecksum = calculateChecksum(data, offset, length);
        if (actualChecksum != expectedChecksum) {
            throw new DecompressionException(
                    "mismatching checksum: " + Integer.toHexString(actualChecksum) +
                            " (expected: " + Integer.toHexString(expectedChecksum) + ')');
        }
    }

    /**
     * From the spec:
     *
     * "Checksums are not stored directly, but masked, as checksumming data and
     * then its own checksum can be problematic. The masking is the same as used
     * in Apache Hadoop: Rotate the checksum by 15 bits, then add the constant
     * 0xa282ead8 (using wraparound as normal for unsigned integers)."
     *
     * @param checksum The actual checksum of the data
     * @return The masked checksum
     */
    static int maskChecksum(long checksum) {
        return (int) ((checksum >> 15 | checksum << 17) + 0xa282ead8);
    }
}
