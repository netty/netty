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

import java.util.Arrays;

import static io.netty.handler.codec.compression.Bzip2Constants.*;

/**
 * An encoder for the Bzip2 Huffman encoding stage.
 */
final class Bzip2HuffmanStageEncoder {
    /**
     * Used in initial Huffman table generation.
     */
    private static final int HUFFMAN_HIGH_SYMBOL_COST = 15;

    /**
     * The {@link Bzip2BitWriter} to which the Huffman tables and data is written.
     */
    private final Bzip2BitWriter writer;

    /**
     * The output of the Move To Front Transform and Run Length Encoding[2] stages.
     */
    private final char[] mtfBlock;

    /**
     * The actual number of values contained in the {@link #mtfBlock} array.
     */
    private final int mtfLength;

    /**
     * The number of unique values in the {@link #mtfBlock} array.
     */
    private final int mtfAlphabetSize;

    /**
     * The global frequencies of values within the {@link #mtfBlock} array.
     */
    private final int[] mtfSymbolFrequencies;

    /**
     * The Canonical Huffman code lengths for each table.
     */
    private final int[][] huffmanCodeLengths;

    /**
     * Merged code symbols for each table. The value at each position is ((code length << 24) | code).
     */
    private final int[][] huffmanMergedCodeSymbols;

    /**
     * The selectors for each segment.
     */
    private final byte[] selectors;

    /**
     * @param writer The {@link Bzip2BitWriter} which provides bit-level writes
     * @param mtfBlock The MTF block data
     * @param mtfLength The actual length of the MTF block
     * @param mtfAlphabetSize The size of the MTF block's alphabet
     * @param mtfSymbolFrequencies The frequencies the MTF block's symbols
     */
    Bzip2HuffmanStageEncoder(final Bzip2BitWriter writer, final char[] mtfBlock,
                             final int mtfLength, final int mtfAlphabetSize, final int[] mtfSymbolFrequencies) {
        this.writer = writer;
        this.mtfBlock = mtfBlock;
        this.mtfLength = mtfLength;
        this.mtfAlphabetSize = mtfAlphabetSize;
        this.mtfSymbolFrequencies = mtfSymbolFrequencies;

        final int totalTables = selectTableCount(mtfLength);

        huffmanCodeLengths = new int[totalTables][mtfAlphabetSize];
        huffmanMergedCodeSymbols = new int[totalTables][mtfAlphabetSize];
        selectors = new byte[(mtfLength + HUFFMAN_GROUP_RUN_LENGTH - 1) / HUFFMAN_GROUP_RUN_LENGTH];
    }

    /**
     * Selects an appropriate table count for a given MTF length.
     * @param mtfLength The length to select a table count for
     * @return The selected table count
     */
    private static int selectTableCount(final int mtfLength) {
        if (mtfLength >= 2400) {
            return 6;
        }
        if (mtfLength >= 1200) {
            return 5;
        }
        if (mtfLength >= 600) {
            return 4;
        }
        if (mtfLength >= 200) {
            return 3;
        }
        return 2;
    }

    /**
     * Generate a Huffman code length table for a given list of symbol frequencies.
     * @param alphabetSize The total number of symbols
     * @param symbolFrequencies The frequencies of the symbols
     * @param codeLengths The array to which the generated code lengths should be written
     */
    private static void generateHuffmanCodeLengths(final int alphabetSize,
                                                   final int[] symbolFrequencies, final int[] codeLengths) {

        final int[] mergedFrequenciesAndIndices = new int[alphabetSize];
        final int[] sortedFrequencies = new int[alphabetSize];

        // The Huffman allocator needs its input symbol frequencies to be sorted, but we need to
        // return code lengths in the same order as the corresponding frequencies are passed in.

        // The symbol frequency and index are merged into a single array of
        // integers - frequency in the high 23 bits, index in the low 9 bits.
        //     2^23 = 8,388,608 which is higher than the maximum possible frequency for one symbol in a block
        //     2^9 = 512 which is higher than the maximum possible alphabet size (== 258)
        // Sorting this array simultaneously sorts the frequencies and
        // leaves a lookup that can be used to cheaply invert the sort.
        for (int i = 0; i < alphabetSize; i++) {
            mergedFrequenciesAndIndices[i] = (symbolFrequencies[i] << 9) | i;
        }
        Arrays.sort(mergedFrequenciesAndIndices);
        for (int i = 0; i < alphabetSize; i++) {
            sortedFrequencies[i] = mergedFrequenciesAndIndices[i] >>> 9;
        }

        // Allocate code lengths - the allocation is in place,
        // so the code lengths will be in the sortedFrequencies array afterwards
        Bzip2HuffmanAllocator.allocateHuffmanCodeLengths(sortedFrequencies, HUFFMAN_ENCODE_MAX_CODE_LENGTH);

        // Reverse the sort to place the code lengths in the same order as the symbols whose frequencies were passed in
        for (int i = 0; i < alphabetSize; i++) {
            codeLengths[mergedFrequenciesAndIndices[i] & 0x1ff] = sortedFrequencies[i];
        }
    }

    /**
     * Generate initial Huffman code length tables, giving each table a different low cost section
     * of the alphabet that is roughly equal in overall cumulative frequency. Note that the initial
     * tables are invalid for actual Huffman code generation, and only serve as the seed for later
     * iterative optimisation in {@link #optimiseSelectorsAndHuffmanTables(boolean)}.
     */
    private void generateHuffmanOptimisationSeeds() {
        final int[][] huffmanCodeLengths = this.huffmanCodeLengths;
        final int[] mtfSymbolFrequencies = this.mtfSymbolFrequencies;
        final int mtfAlphabetSize = this.mtfAlphabetSize;

        final int totalTables = huffmanCodeLengths.length;

        int remainingLength = mtfLength;
        int lowCostEnd = -1;

        for (int i = 0; i < totalTables; i++) {

            final int targetCumulativeFrequency = remainingLength / (totalTables - i);
            final int lowCostStart = lowCostEnd + 1;
            int actualCumulativeFrequency = 0;

            while (actualCumulativeFrequency < targetCumulativeFrequency && lowCostEnd < mtfAlphabetSize - 1) {
                actualCumulativeFrequency += mtfSymbolFrequencies[++lowCostEnd];
            }

            if (lowCostEnd > lowCostStart && i != 0 && i != totalTables - 1 && (totalTables - i & 1) == 0) {
                actualCumulativeFrequency -= mtfSymbolFrequencies[lowCostEnd--];
            }

            final int[] tableCodeLengths = huffmanCodeLengths[i];
            for (int j = 0; j < mtfAlphabetSize; j++) {
                if (j < lowCostStart || j > lowCostEnd) {
                    tableCodeLengths[j] = HUFFMAN_HIGH_SYMBOL_COST;
                }
            }

            remainingLength -= actualCumulativeFrequency;
        }
    }

    /**
     * Co-optimise the selector list and the alternative Huffman table code lengths. This method is
     * called repeatedly in the hope that the total encoded size of the selectors, the Huffman code
     * lengths and the block data encoded with them will converge towards a minimum.<br>
     * If the data is highly incompressible, it is possible that the total encoded size will
     * instead diverge (increase) slightly.<br>
     * @param storeSelectors If {@code true}, write out the (final) chosen selectors
     */
    private void optimiseSelectorsAndHuffmanTables(final boolean storeSelectors) {
        final char[] mtfBlock = this.mtfBlock;
        final byte[] selectors = this.selectors;
        final int[][] huffmanCodeLengths = this.huffmanCodeLengths;
        final int mtfLength = this.mtfLength;
        final int mtfAlphabetSize = this.mtfAlphabetSize;

        final int totalTables = huffmanCodeLengths.length;
        final int[][] tableFrequencies = new int[totalTables][mtfAlphabetSize];

        int selectorIndex = 0;

        // Find the best table for each group of 50 block bytes based on the current Huffman code lengths
        for (int groupStart = 0; groupStart < mtfLength;) {

            final int groupEnd = Math.min(groupStart + HUFFMAN_GROUP_RUN_LENGTH, mtfLength) - 1;

            // Calculate the cost of this group when encoded by each table
            int[] cost = new int[totalTables];
            for (int i = groupStart; i <= groupEnd; i++) {
                final int value = mtfBlock[i];
                for (int j = 0; j < totalTables; j++) {
                    cost[j] += huffmanCodeLengths[j][value];
                }
            }

            // Find the table with the least cost for this group
            byte bestTable = 0;
            int bestCost = cost[0];
            for (byte i = 1 ; i < totalTables; i++) {
                final int tableCost = cost[i];
                if (tableCost < bestCost) {
                    bestCost = tableCost;
                    bestTable = i;
                }
            }

            // Accumulate symbol frequencies for the table chosen for this block
            final int[] bestGroupFrequencies = tableFrequencies[bestTable];
            for (int i = groupStart; i <= groupEnd; i++) {
                bestGroupFrequencies[mtfBlock[i]]++;
            }

            // Store a selector indicating the table chosen for this block
            if (storeSelectors) {
                selectors[selectorIndex++] = bestTable;
            }
            groupStart = groupEnd + 1;
        }

        // Generate new Huffman code lengths based on the frequencies for each table accumulated in this iteration
        for (int i = 0; i < totalTables; i++) {
            generateHuffmanCodeLengths(mtfAlphabetSize, tableFrequencies[i], huffmanCodeLengths[i]);
        }
    }

    /**
     * Assigns Canonical Huffman codes based on the calculated lengths.
     */
    private void assignHuffmanCodeSymbols() {
        final int[][] huffmanMergedCodeSymbols = this.huffmanMergedCodeSymbols;
        final int[][] huffmanCodeLengths = this.huffmanCodeLengths;
        final int mtfAlphabetSize = this.mtfAlphabetSize;

        final int totalTables = huffmanCodeLengths.length;

        for (int i = 0; i < totalTables; i++) {
            final int[] tableLengths = huffmanCodeLengths[i];

            int minimumLength = 32;
            int maximumLength = 0;
            for (int j = 0; j < mtfAlphabetSize; j++) {
                final int length = tableLengths[j];
                if (length > maximumLength) {
                    maximumLength = length;
                }
                if (length < minimumLength) {
                    minimumLength = length;
                }
            }

            int code = 0;
            for (int j = minimumLength; j <= maximumLength; j++) {
                for (int k = 0; k < mtfAlphabetSize; k++) {
                    if ((huffmanCodeLengths[i][k] & 0xff) == j) {
                        huffmanMergedCodeSymbols[i][k] = (j << 24) | code;
                        code++;
                    }
                }
                code <<= 1;
            }
        }
    }

    /**
     * Write out the selector list and Huffman tables.
     */
    private void writeSelectorsAndHuffmanTables(ByteBuf out) {
        final Bzip2BitWriter writer = this.writer;
        final byte[] selectors = this.selectors;
        final int totalSelectors = selectors.length;
        final int[][] huffmanCodeLengths = this.huffmanCodeLengths;
        final int totalTables = huffmanCodeLengths.length;
        final int mtfAlphabetSize = this.mtfAlphabetSize;

        writer.writeBits(out, 3, totalTables);
        writer.writeBits(out, 15, totalSelectors);

        // Write the selectors
        Bzip2MoveToFrontTable selectorMTF = new Bzip2MoveToFrontTable();
        for (byte selector : selectors) {
            writer.writeUnary(out, selectorMTF.valueToFront(selector));
        }

        // Write the Huffman tables
        for (final int[] tableLengths : huffmanCodeLengths) {
            int currentLength = tableLengths[0];

            writer.writeBits(out, 5, currentLength);

            for (int j = 0; j < mtfAlphabetSize; j++) {
                final int codeLength = tableLengths[j];
                final int value = currentLength < codeLength ? 2 : 3;
                int delta = Math.abs(codeLength - currentLength);
                while (delta-- > 0) {
                    writer.writeBits(out, 2, value);
                }
                writer.writeBoolean(out, false);
                currentLength = codeLength;
            }
        }
    }

    /**
     * Writes out the encoded block data.
     */
    private void writeBlockData(ByteBuf out) {
        final Bzip2BitWriter writer = this.writer;
        final int[][] huffmanMergedCodeSymbols = this.huffmanMergedCodeSymbols;
        final byte[] selectors = this.selectors;
        final char[] mtf = mtfBlock;
        final int mtfLength = this.mtfLength;

        int selectorIndex = 0;
        for (int mtfIndex = 0; mtfIndex < mtfLength;) {
            final int groupEnd = Math.min(mtfIndex + HUFFMAN_GROUP_RUN_LENGTH, mtfLength) - 1;
            final int[] tableMergedCodeSymbols = huffmanMergedCodeSymbols[selectors[selectorIndex++]];

            while (mtfIndex <= groupEnd) {
                final int mergedCodeSymbol = tableMergedCodeSymbols[mtf[mtfIndex++]];
                writer.writeBits(out, mergedCodeSymbol >>> 24, mergedCodeSymbol);
            }
        }
    }

    /**
     * Encodes and writes the block data.
     */
    void encode(ByteBuf out) {
        // Create optimised selector list and Huffman tables
        generateHuffmanOptimisationSeeds();
        for (int i = 3; i >= 0; i--) {
            optimiseSelectorsAndHuffmanTables(i == 0);
        }
        assignHuffmanCodeSymbols();

        // Write out the tables and the block data encoded with them
        writeSelectorsAndHuffmanTables(out);
        writeBlockData(out);
    }
}
