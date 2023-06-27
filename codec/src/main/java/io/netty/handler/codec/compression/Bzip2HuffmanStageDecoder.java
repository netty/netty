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

import static io.netty.handler.codec.compression.Bzip2Constants.HUFFMAN_DECODE_MAX_CODE_LENGTH;
import static io.netty.handler.codec.compression.Bzip2Constants.HUFFMAN_GROUP_RUN_LENGTH;
import static io.netty.handler.codec.compression.Bzip2Constants.HUFFMAN_MAX_ALPHABET_SIZE;

/**
 * A decoder for the Bzip2 Huffman coding stage.
 */
final class Bzip2HuffmanStageDecoder {
    /**
     * A reader that provides bit-level reads.
     */
    private final Bzip2BitReader reader;

    /**
     * The Huffman table number to use for each group of 50 symbols.
     */
    byte[] selectors;

    /**
     * The minimum code length for each Huffman table.
     */
    private final int[] minimumLengths;

    /**
     * An array of values for each Huffman table that must be subtracted from the numerical value of
     * a Huffman code of a given bit length to give its canonical code index.
     */
    private final int[][] codeBases;

    /**
     * An array of values for each Huffman table that gives the highest numerical value of a Huffman
     * code of a given bit length.
     */
    private final int[][] codeLimits;

    /**
     * A mapping for each Huffman table from canonical code index to output symbol.
     */
    private final int[][] codeSymbols;

    /**
     * The Huffman table for the current group.
     */
    private int currentTable;

    /**
     * The index of the current group within the selectors array.
     */
    private int groupIndex = -1;

    /**
     * The byte position within the current group. A new group is selected every 50 decoded bytes.
     */
    private int groupPosition = -1;

    /**
     * Total number of used Huffman tables in range 2..6.
     */
    final int totalTables;

    /**
     * The total number of codes (uniform for each table).
     */
    final int alphabetSize;

    /**
     * Table for Move To Front transformations.
     */
    final Bzip2MoveToFrontTable tableMTF = new Bzip2MoveToFrontTable();

    // For saving state if end of current ByteBuf was reached
    int currentSelector;

    /**
     * The Canonical Huffman code lengths for each table.
     */
    final byte[][] tableCodeLengths;

    // For saving state if end of current ByteBuf was reached
    int currentGroup;
    int currentLength = -1;
    int currentAlpha;
    boolean modifyLength;

    Bzip2HuffmanStageDecoder(final Bzip2BitReader reader, final int totalTables, final int alphabetSize) {
        this.reader = reader;
        this.totalTables = totalTables;
        this.alphabetSize = alphabetSize;

        minimumLengths = new int[totalTables];
        codeBases = new int[totalTables][HUFFMAN_DECODE_MAX_CODE_LENGTH + 2];
        codeLimits = new int[totalTables][HUFFMAN_DECODE_MAX_CODE_LENGTH + 1];
        codeSymbols = new int[totalTables][HUFFMAN_MAX_ALPHABET_SIZE];
        tableCodeLengths = new byte[totalTables][HUFFMAN_MAX_ALPHABET_SIZE];
    }

    /**
     * Constructs Huffman decoding tables from lists of Canonical Huffman code lengths.
     */
    void createHuffmanDecodingTables() {
        final int alphabetSize = this.alphabetSize;

        for (int table = 0; table < tableCodeLengths.length; table++) {
            final int[] tableBases = codeBases[table];
            final int[] tableLimits = codeLimits[table];
            final int[] tableSymbols = codeSymbols[table];
            final byte[] codeLengths = tableCodeLengths[table];

            int minimumLength = HUFFMAN_DECODE_MAX_CODE_LENGTH;
            int maximumLength = 0;

            // Find the minimum and maximum code length for the table
            for (int i = 0; i < alphabetSize; i++) {
                final byte currLength = codeLengths[i];
                maximumLength = Math.max(currLength, maximumLength);
                minimumLength = Math.min(currLength, minimumLength);
            }
            minimumLengths[table] = minimumLength;

            // Calculate the first output symbol for each code length
            for (int i = 0; i < alphabetSize; i++) {
                tableBases[codeLengths[i] + 1]++;
            }
            for (int i = 1, b = tableBases[0]; i < HUFFMAN_DECODE_MAX_CODE_LENGTH + 2; i++) {
                b += tableBases[i];
                tableBases[i] = b;
            }

            // Calculate the first and last Huffman code for each code length (codes at a given
            // length are sequential in value)
            for (int i = minimumLength, code = 0; i <= maximumLength; i++) {
                int base = code;
                code += tableBases[i + 1] - tableBases[i];
                tableBases[i] = base - tableBases[i];
                tableLimits[i] = code - 1;
                code <<= 1;
            }

            // Populate the mapping from canonical code index to output symbol
            for (int bitLength = minimumLength, codeIndex = 0; bitLength <= maximumLength; bitLength++) {
                for (int symbol = 0; symbol < alphabetSize; symbol++) {
                    if (codeLengths[symbol] == bitLength) {
                        tableSymbols[codeIndex++] = symbol;
                    }
                }
            }
        }

        currentTable = selectors[0];
    }

    /**
     * Decodes and returns the next symbol.
     * @return The decoded symbol
     */
    int nextSymbol() {
        // Move to next group selector if required
        if (++groupPosition % HUFFMAN_GROUP_RUN_LENGTH == 0) {
            groupIndex++;
            if (groupIndex == selectors.length) {
                throw new DecompressionException("error decoding block");
            }
            currentTable = selectors[groupIndex] & 0xff;
        }

        final Bzip2BitReader reader = this.reader;
        final int currentTable = this.currentTable;
        final int[] tableLimits = codeLimits[currentTable];
        final int[] tableBases = codeBases[currentTable];
        final int[] tableSymbols = codeSymbols[currentTable];
        int codeLength = minimumLengths[currentTable];

        // Starting with the minimum bit length for the table, read additional bits one at a time
        // until a complete code is recognised
        int codeBits = reader.readBits(codeLength);
        for (; codeLength <= HUFFMAN_DECODE_MAX_CODE_LENGTH; codeLength++) {
            if (codeBits <= tableLimits[codeLength]) {
                // Convert the code to a symbol index and return
                return tableSymbols[codeBits - tableBases[codeLength]];
            }
            codeBits = codeBits << 1 | reader.readBits(1);
        }

        throw new DecompressionException("a valid code was not recognised");
    }
}
