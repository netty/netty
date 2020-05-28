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

/**
 * Implements
 * <a href="https://en.wikipedia.org/wiki/Knuth%E2%80%93Morris%E2%80%93Pratt_algorithm">Knuth-Morris-Pratt</a>
 * string search algorithm.
 * Use static {@link AbstractSearchProcessorFactory#newKmpSearchProcessorFactory}
 * to create an instance of this factory.
 * Use {@link KmpSearchProcessorFactory#newSearchProcessor} to get an instance of {@link io.netty.util.ByteProcessor}
 * implementation for performing the actual search.
 * @see AbstractSearchProcessorFactory
 */
public class KmpSearchProcessorFactory extends AbstractSearchProcessorFactory {

    private final int[] jumpTable;
    private final byte[] needle;

    public static class Processor implements SearchProcessor {

        private final byte[] needle;
        private final int[] jumpTable;
        private long currentPosition;

        Processor(byte[] needle, int[] jumpTable) {
            this.needle = needle;
            this.jumpTable = jumpTable;
        }

        @Override
        public boolean process(byte value) {
            while (currentPosition > 0 && PlatformDependent.getByte(needle, currentPosition) != value) {
                currentPosition = PlatformDependent.getInt(jumpTable, currentPosition);
            }
            if (PlatformDependent.getByte(needle, currentPosition) == value) {
                currentPosition++;
            }
            if (currentPosition == needle.length) {
                currentPosition = PlatformDependent.getInt(jumpTable, currentPosition);
                return false;
            }

            return true;
        }

        @Override
        public void reset() {
            currentPosition = 0;
        }
    }

    KmpSearchProcessorFactory(byte[] needle) {
        this.needle = needle.clone();
        this.jumpTable = new int[needle.length + 1];

        int j = 0;
        for (int i = 1; i < needle.length; i++) {
            while (j > 0 && needle[j] != needle[i]) {
                j = jumpTable[j];
            }
            if (needle[j] == needle[i]) {
                j++;
            }
            jumpTable[i + 1] = j;
        }
    }

    /**
     * Returns a new {@link Processor}.
     */
    @Override
    public Processor newSearchProcessor() {
        return new Processor(needle, jumpTable);
    }

}
