/*
 * Copyright 2026 The Netty Project
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

import java.io.ByteArrayOutputStream;

import static io.netty.handler.codec.compression.Bzip2Constants.BLOCK_HEADER_MAGIC_1;
import static io.netty.handler.codec.compression.Bzip2Constants.BLOCK_HEADER_MAGIC_2;
import static io.netty.handler.codec.compression.Bzip2Constants.MAGIC_NUMBER;

/**
 * Hand-crafted, minimal bzip2 byte streams used to test handling of malformed input.
 */
final class Bzip2MalformedStreams {

    private Bzip2MalformedStreams() {
    }

    /**
     * A stream that declares the minimum number of Huffman tables (2, so the only valid MTF
     * selector indices are 0 and 1) but encodes its single selector as the unary-coded index 9,
     * which is out of range.
     */
    static byte[] selectorIndexOutOfRange() {
        BitWriter bits = new BitWriter();
        bits.writeBit(0);            // randomised = false
        bits.writeBits(0, 24);       // bwtStartPointer
        bits.writeBits(0x0001, 16);  // huffmanInUse16: only the range 0xF0-0xFF is in use
        bits.writeBits(0x8000, 16);  // symbol sub-bitmap: only 0xF0 is used
        bits.writeBits(2, 3);        // totalTables = 2 (minimum allowed -> valid indices 0 or 1)
        bits.writeBits(1, 15);       // totalSelectors = 1
        for (int i = 0; i < 9; i++) {
            bits.writeBit(1);
        }
        bits.writeBit(0);            // unary-coded selector index = 9 (out of range)

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeMedium(out, MAGIC_NUMBER);
        out.write('1');              // block size digit
        writeMedium(out, BLOCK_HEADER_MAGIC_1);
        writeMedium(out, BLOCK_HEADER_MAGIC_2);
        writeInt(out, 0);            // block CRC (irrelevant, the fault triggers earlier)
        byte[] bitBytes = bits.toByteArray();
        out.write(bitBytes, 0, bitBytes.length);
        return out.toByteArray();
    }

    private static void writeMedium(ByteArrayOutputStream out, int value) {
        out.write(value >>> 16);
        out.write(value >>> 8);
        out.write(value);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value >>> 24);
        out.write(value >>> 16);
        out.write(value >>> 8);
        out.write(value);
    }

    /**
     * Packs bits MSB-first into bytes, matching {@link Bzip2BitReader}.
     */
    private static final class BitWriter {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private int currentByte;
        private int bitsInCurrentByte;

        void writeBit(int bit) {
            currentByte = currentByte << 1 | bit & 1;
            if (++bitsInCurrentByte == 8) {
                out.write(currentByte);
                currentByte = 0;
                bitsInCurrentByte = 0;
            }
        }

        void writeBits(int value, int count) {
            for (int i = count - 1; i >= 0; i--) {
                writeBit(value >>> i);
            }
        }

        byte[] toByteArray() {
            while (bitsInCurrentByte != 0) {
                writeBit(0);
            }
            return out.toByteArray();
        }
    }
}
