/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.buffer.search;

import io.netty.util.internal.PlatformDependent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Queue;

/**
 * Implements <a href="https://en.wikipedia.org/wiki/Aho%E2%80%93Corasick_algorithm">Aho–Corasick</a>
 * string search algorithm.
 * Use static {@link AbstractMultiSearchProcessorFactory#newAhoCorasicSearchProcessorFactory}
 * to create an instance of this factory.
 * Use {@link AhoCorasicSearchProcessorFactory#newSearchProcessor} to get an instance of
 * {@link io.netty.util.ByteProcessor} implementation for performing the actual search.
 * @see AbstractMultiSearchProcessorFactory
 */
public class AhoCorasicSearchProcessorFactory extends AbstractMultiSearchProcessorFactory {

    private final int[] jumpTable;
    private final int[] matchForNeedleId;

    static final int BITS_PER_SYMBOL = 8;
    static final int ALPHABET_SIZE = 1 << BITS_PER_SYMBOL;

    private static class Context {
        int[] jumpTable;
        int[] matchForNeedleId;
    }

    public static class Processor implements MultiSearchProcessor {

        private final int[] jumpTable;
        private final int[] matchForNeedleId;
        private long currentPosition;

        Processor(int[] jumpTable, int[] matchForNeedleId) {
            this.jumpTable = jumpTable;
            this.matchForNeedleId = matchForNeedleId;
        }

        @Override
        public boolean process(byte value) {
            currentPosition = PlatformDependent.getInt(jumpTable, currentPosition | (value & 0xffL));
            if (currentPosition < 0) {
                currentPosition = -currentPosition;
                return false;
            }
            return true;
        }

        @Override
        public int getFoundNeedleId() {
            return matchForNeedleId[(int) currentPosition >> AhoCorasicSearchProcessorFactory.BITS_PER_SYMBOL];
        }

        @Override
        public void reset() {
            currentPosition = 0;
        }
    }

    AhoCorasicSearchProcessorFactory(byte[] ...needles) {

        for (byte[] needle: needles) {
            if (needle.length == 0) {
                throw new IllegalArgumentException("Needle must be non empty");
            }
        }

        Context context = buildTrie(needles);
        jumpTable = context.jumpTable;
        matchForNeedleId = context.matchForNeedleId;

        linkSuffixes();

        for (int i = 0; i < jumpTable.length; i++) {
            if (matchForNeedleId[jumpTable[i] >> BITS_PER_SYMBOL] >= 0) {
                jumpTable[i] = -jumpTable[i];
            }
        }
    }

    private static Context buildTrie(byte[][] needles) {

        ArrayList<Integer> jumpTableBuilder = new ArrayList<Integer>(ALPHABET_SIZE);
        for (int i = 0; i < ALPHABET_SIZE; i++) {
            jumpTableBuilder.add(-1);
        }

        ArrayList<Integer> matchForBuilder = new ArrayList<Integer>();
        matchForBuilder.add(-1);

        for (int needleId = 0; needleId < needles.length; needleId++) {
            byte[] needle = needles[needleId];
            int currentPosition = 0;

            for (byte ch0: needle) {

                final int ch = ch0 & 0xff;
                final int next = currentPosition + ch;

                if (jumpTableBuilder.get(next) == -1) {
                    jumpTableBuilder.set(next, jumpTableBuilder.size());
                    for (int i = 0; i < ALPHABET_SIZE; i++) {
                        jumpTableBuilder.add(-1);
                    }
                    matchForBuilder.add(-1);
                }

                currentPosition = jumpTableBuilder.get(next);
            }

            matchForBuilder.set(currentPosition >> BITS_PER_SYMBOL, needleId);
        }

        Context context = new Context();

        context.jumpTable = new int[jumpTableBuilder.size()];
        for (int i = 0; i < jumpTableBuilder.size(); i++) {
            context.jumpTable[i] = jumpTableBuilder.get(i);
        }

        context.matchForNeedleId = new int[matchForBuilder.size()];
        for (int i = 0; i < matchForBuilder.size(); i++) {
            context.matchForNeedleId[i] = matchForBuilder.get(i);
        }

        return context;
    }

    private void linkSuffixes() {

        Queue<Integer> queue = new ArrayDeque<Integer>();
        queue.add(0);

        int[] suffixLinks = new int[matchForNeedleId.length];
        Arrays.fill(suffixLinks, -1);

        while (!queue.isEmpty()) {

            final int v = queue.remove();
            int vPosition = v >> BITS_PER_SYMBOL;
            final int u = suffixLinks[vPosition] == -1 ? 0 : suffixLinks[vPosition];

            if (matchForNeedleId[vPosition] == -1) {
                matchForNeedleId[vPosition] = matchForNeedleId[u >> BITS_PER_SYMBOL];
            }

            for (int ch = 0; ch < ALPHABET_SIZE; ch++) {

                final int vIndex = v | ch;
                final int uIndex = u | ch;

                final int jumpV = jumpTable[vIndex];
                final int jumpU = jumpTable[uIndex];

                if (jumpV != -1) {
                    suffixLinks[jumpV >> BITS_PER_SYMBOL] = v > 0 && jumpU != -1 ? jumpU : 0;
                    queue.add(jumpV);
                } else {
                    jumpTable[vIndex] = jumpU != -1 ? jumpU : 0;
                }
            }
        }
    }

    /**
     * Returns a new {@link Processor}.
     */
    @Override
    public Processor newSearchProcessor() {
        return new Processor(jumpTable, matchForNeedleId);
    }

}
