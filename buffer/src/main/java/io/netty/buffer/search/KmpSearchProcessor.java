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
 * string search algorithm as {@link io.netty.util.ByteProcessor}.
 * @see SearchProcessorFactory
 */
public class KmpSearchProcessor implements SearchProcessor {

    private final byte[] needle;
    private final int[] jumpTable;
    private int currentPosition;

    KmpSearchProcessor(byte[] needle, int[] jumpTable) {
        this.needle = needle;
        this.jumpTable = jumpTable;
    }

    @Override
    public boolean process(byte value) {
        while (currentPosition > 0 && needle[currentPosition] != value) {
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
