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
 * Implements Shifting Bit Mask string search algorithm as {@link io.netty.util.ByteProcessor}.
 * @see SearchProcessorFactory
 */
public class ShiftingBitMaskSearchProcessor implements SearchProcessor {

    private final long[] bitMasks;
    private final long successBit;
    private long currentMask;

    ShiftingBitMaskSearchProcessor(long[] bitMasks, long successBit) {
        this.bitMasks = bitMasks;
        this.successBit = successBit;
    }

    @Override
    public boolean process(byte value) {
        currentMask = ((currentMask << 1) | 1) & PlatformDependent.getLong(bitMasks, value & 0xffL);
        return (currentMask & successBit) == 0;
    }

    @Override
    public void reset() {
        currentMask = 0;
    }

}
