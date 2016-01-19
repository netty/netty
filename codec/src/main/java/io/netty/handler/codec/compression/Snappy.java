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
import io.netty.buffer.ByteBufUtil;

/**
 * Uncompresses an input {@link ByteBuf} encoded with Snappy compression into an
 * output {@link ByteBuf}.
 *
 * See http://code.google.com/p/snappy/source/browse/trunk/format_description.txt
 */
class Snappy {

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

    private State state = State.READY;
    private byte tag;
    private int written;

    private enum State {
        READY,
        READING_PREAMBLE,
        READING_TAG,
        READING_LITERAL,
        READING_COPY
    }

    public void reset() {
        state = State.READY;
        tag = 0;
        written = 0;
    }

    public void encode(final ByteBuf in, final ByteBuf out, final int length) {
        // Write the preamble length to the output buffer
        for (int i = 0;; i ++) {
            int b = length >>> i * 7;
            if ((b & 0xFFFFFF80) != 0) {
                out.writeByte(b & 0x7f | 0x80);
            } else {
                out.writeByte(b);
                break;
            }
        }

        int inIndex = in.readerIndex();
        final int baseIndex = inIndex;

        final short[] table = getHashTable(length);
        final int shift = 32 - (int) Math.floor(Math.log(table.length) / Math.log(2));

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
                    in.readerIndex(in.readerIndex() + matched);
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
    static void encodeLiteral(ByteBuf in, ByteBuf out, int length) {
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

    public void decode(ByteBuf in, ByteBuf out) {
        while (in.isReadable()) {
            switch (state) {
            case READY:
                state = State.READING_PREAMBLE;
            case READING_PREAMBLE:
                int uncompressedLength = readPreamble(in);
                if (uncompressedLength == PREAMBLE_NOT_FULL) {
                    // We've not yet read all of the preamble, so wait until we can
                    return;
                }
                if (uncompressedLength == 0) {
                    // Should never happen, but it does mean we have nothing further to do
                    state = State.READY;
                    return;
                }
                out.ensureWritable(uncompressedLength);
                state = State.READING_TAG;
            case READING_TAG:
                if (!in.isReadable()) {
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
    private static int readPreamble(ByteBuf in) {
        int length = 0;
        int byteIndex = 0;
        while (in.isReadable()) {
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
    static int decodeLiteral(byte tag, ByteBuf in, ByteBuf out) {
        in.markReaderIndex();
        int length;
        switch(tag >> 2 & 0x3F) {
        case 60:
            if (!in.isReadable()) {
                return NOT_ENOUGH_INPUT;
            }
            length = in.readUnsignedByte();
            break;
        case 61:
            if (in.readableBytes() < 2) {
                return NOT_ENOUGH_INPUT;
            }
            length = ByteBufUtil.swapShort(in.readShort());
            break;
        case 62:
            if (in.readableBytes() < 3) {
                return NOT_ENOUGH_INPUT;
            }
            length = ByteBufUtil.swapMedium(in.readUnsignedMedium());
            break;
        case 63:
            if (in.readableBytes() < 4) {
                return NOT_ENOUGH_INPUT;
            }
            length = ByteBufUtil.swapInt(in.readInt());
            break;
        default:
            length = tag >> 2 & 0x3F;
        }
        length += 1;

        if (in.readableBytes() < length) {
            in.resetReaderIndex();
            return NOT_ENOUGH_INPUT;
        }

        out.writeBytes(in, length);
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
    private static int decodeCopyWith1ByteOffset(byte tag, ByteBuf in, ByteBuf out, int writtenSoFar) {
        if (!in.isReadable()) {
            return NOT_ENOUGH_INPUT;
        }

        int initialIndex = out.writerIndex();
        int length = 4 + ((tag & 0x01c) >> 2);
        int offset = (tag & 0x0e0) << 8 >> 5 | in.readUnsignedByte();

        validateOffset(offset, writtenSoFar);

        out.markReaderIndex();
        if (offset < length) {
            int copies = length / offset;
            for (; copies > 0; copies--) {
                out.readerIndex(initialIndex - offset);
                out.readBytes(out, offset);
            }
            if (length % offset != 0) {
                out.readerIndex(initialIndex - offset);
                out.readBytes(out, length % offset);
            }
        } else {
            out.readerIndex(initialIndex - offset);
            out.readBytes(out, length);
        }
        out.resetReaderIndex();

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
     * @throws DecompressionException If the read offset is invalid
     * @return The number of bytes appended to the output buffer, or -1 to indicate
     *     "try again later"
     */
    private static int decodeCopyWith2ByteOffset(byte tag, ByteBuf in, ByteBuf out, int writtenSoFar) {
        if (in.readableBytes() < 2) {
            return NOT_ENOUGH_INPUT;
        }

        int initialIndex = out.writerIndex();
        int length = 1 + (tag >> 2 & 0x03f);
        int offset = ByteBufUtil.swapShort(in.readShort());

        validateOffset(offset, writtenSoFar);

        out.markReaderIndex();
        if (offset < length) {
            int copies = length / offset;
            for (; copies > 0; copies--) {
                out.readerIndex(initialIndex - offset);
                out.readBytes(out, offset);
            }
            if (length % offset != 0) {
                out.readerIndex(initialIndex - offset);
                out.readBytes(out, length % offset);
            }
        } else {
            out.readerIndex(initialIndex - offset);
            out.readBytes(out, length);
        }
        out.resetReaderIndex();

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
    private static int decodeCopyWith4ByteOffset(byte tag, ByteBuf in, ByteBuf out, int writtenSoFar) {
        if (in.readableBytes() < 4) {
            return NOT_ENOUGH_INPUT;
        }

        int initialIndex = out.writerIndex();
        int length = 1 + (tag >> 2 & 0x03F);
        int offset = ByteBufUtil.swapInt(in.readInt());

        validateOffset(offset, writtenSoFar);

        out.markReaderIndex();
        if (offset < length) {
            int copies = length / offset;
            for (; copies > 0; copies--) {
                out.readerIndex(initialIndex - offset);
                out.readBytes(out, offset);
            }
            if (length % offset != 0) {
                out.readerIndex(initialIndex - offset);
                out.readBytes(out, length % offset);
            }
        } else {
            out.readerIndex(initialIndex - offset);
            out.readBytes(out, length);
        }
        out.resetReaderIndex();

        return length;
    }

    /**
     * Validates that the offset extracted from a compressed reference is within
     * the permissible bounds of an offset (4 <= offset <= 32768), and does not
     * exceed the length of the chunk currently read so far.
     *
     * @param offset The offset extracted from the compressed reference
     * @param chunkSizeSoFar The number of bytes read so far from this chunk
     * @throws DecompressionException if the offset is invalid
     */
    private static void validateOffset(int offset, int chunkSizeSoFar) {
        if (offset > Short.MAX_VALUE) {
            throw new DecompressionException("Offset exceeds maximum permissible value");
        }

        if (offset <= 0) {
            throw new DecompressionException("Offset is less than minimum permissible value");
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
    public static int calculateChecksum(ByteBuf data) {
        return calculateChecksum(data, data.readerIndex(), data.readableBytes());
    }

    /**
     * Computes the CRC32C checksum of the supplied data and performs the "mask" operation
     * on the computed checksum
     *
     * @param data The input data to calculate the CRC32C checksum of
     */
    public static int calculateChecksum(ByteBuf data, int offset, int length) {
        Crc32c crc32 = new Crc32c();
        try {
            if (data.hasArray()) {
                crc32.update(data.array(), data.arrayOffset() + offset, length);
            } else {
                byte[] array = new byte[length];
                data.getBytes(offset, array);
                crc32.update(array, 0, length);
            }

            return maskChecksum((int) crc32.getValue());
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
    static void validateChecksum(int expectedChecksum, ByteBuf data) {
        validateChecksum(expectedChecksum, data, data.readerIndex(), data.readableBytes());
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
    static void validateChecksum(int expectedChecksum, ByteBuf data, int offset, int length) {
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
    static int maskChecksum(int checksum) {
        return (checksum >> 15 | checksum << 17) + 0xa282ead8;
    }
}
