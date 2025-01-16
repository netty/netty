/*
 * Copyright 2025 The Netty Project
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
package io.netty.channel.uring;

import io.netty.util.internal.ObjectUtil;

public class BufferRingConfig {
    public static final BufferRingConfig DEFAULT_BUFFER_RING_CONFIG = defaultConfig();

    private final short bgId;
    private final short bufferRingSize;
    private final int chunkSize;

    public BufferRingConfig(short bgId, short bufferRingSize, int chunkSize) {
        this.bgId = ObjectUtil.checkPositive(bgId, "bgId");
        this.bufferRingSize = checkBufferRingSize(bufferRingSize);
        this.chunkSize = chunkSize;
    }

    private static BufferRingConfig defaultConfig() {
        return new BufferRingConfig(
                (short) 1, (short) 16, Native.PAGE_SIZE
        );
    }

    public short bufferGroupId() {
        return bgId;
    }

    public short bufferRingSize() {
        return bufferRingSize;
    }

    public int chunkSize() {
        return chunkSize;
    }

    private static short checkBufferRingSize(short bufferRingSize) {
        if (bufferRingSize < 1) {
            throw new IllegalArgumentException("bufferRingSize: " + bufferRingSize + " (expected: > 0)");
        }

        boolean isPowerOfTwo = (bufferRingSize & (bufferRingSize - 1)) == 0;
        if (!isPowerOfTwo) {
            throw new IllegalArgumentException("bufferRingSize: " + bufferRingSize + " (expected: power of 2)");
        }
        return bufferRingSize;
    }
}
