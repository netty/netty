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

/**
 * Factory that creates {@link ShiftingBitMaskSearchProcessor}.
 * Use static {@link SearchProcessorFactory#newShiftingBitMaskSearchProcessorFactory}
 * to create an instance of this factory.
 * @see SearchProcessorFactory
 */
public class ShiftingBitMaskSearchProcessorFactory extends SearchProcessorFactory {

    private final long[] bitMasks = new long[256];
    private final long successBit;

    ShiftingBitMaskSearchProcessorFactory(byte[] needle) {
        if (needle.length > 64) {
            throw new IllegalArgumentException("Maximum supported search pattern length is 64, got " + needle.length);
        }

        long bit = 1L;
        for (byte c: needle) {
            bitMasks[c & 0xff] |= bit;
            bit <<= 1;
        }

        successBit = 1L << (needle.length - 1);
    }

    /**
     * Returns a new {@link ShiftingBitMaskSearchProcessor}.
     */
    @Override
    public ShiftingBitMaskSearchProcessor newSearchProcessor() {
        return new ShiftingBitMaskSearchProcessor(bitMasks, successBit);
    }

};
