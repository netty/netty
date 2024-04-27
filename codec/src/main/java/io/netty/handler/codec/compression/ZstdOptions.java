/*
 * Copyright 2021 The Netty Project
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

import io.netty.util.internal.ObjectUtil;

import static io.netty.handler.codec.compression.ZstdConstants.DEFAULT_COMPRESSION_LEVEL;
import static io.netty.handler.codec.compression.ZstdConstants.MIN_COMPRESSION_LEVEL;
import static io.netty.handler.codec.compression.ZstdConstants.MAX_COMPRESSION_LEVEL;
import static io.netty.handler.codec.compression.ZstdConstants.DEFAULT_BLOCK_SIZE;
import static io.netty.handler.codec.compression.ZstdConstants.MAX_BLOCK_SIZE;

/**
 * {@link ZstdOptions} holds compressionLevel for
 * Zstd compression.
 */
public class ZstdOptions implements CompressionOptions {

    private final int blockSize;
    private final int compressionLevel;
    private final int maxEncodeSize;

    /**
     * Default implementation of {@link ZstdOptions} with{compressionLevel(int)} set to
     * {@link ZstdConstants#DEFAULT_COMPRESSION_LEVEL},{@link ZstdConstants#DEFAULT_BLOCK_SIZE},
     * {@link ZstdConstants#MAX_BLOCK_SIZE}
     */
    static final ZstdOptions DEFAULT = new ZstdOptions(DEFAULT_COMPRESSION_LEVEL, DEFAULT_BLOCK_SIZE, MAX_BLOCK_SIZE);

    /**
     * Create a new {@link ZstdOptions}
     *
     * @param  blockSize
     *           is used to calculate the compressionLevel
     * @param  maxEncodeSize
     *           specifies the size of the largest compressed object
     * @param  compressionLevel
     *           specifies the level of the compression
     */
    ZstdOptions(int compressionLevel, int blockSize, int maxEncodeSize) {
        if (!Zstd.isAvailable()) {
            throw new IllegalStateException("zstd-jni is not available", Zstd.cause());
        }

        this.compressionLevel = ObjectUtil.checkInRange(compressionLevel,
                MIN_COMPRESSION_LEVEL, MAX_COMPRESSION_LEVEL, "compressionLevel");
        this.blockSize = ObjectUtil.checkPositive(blockSize, "blockSize");
        this.maxEncodeSize = ObjectUtil.checkPositive(maxEncodeSize, "maxEncodeSize");
    }

    public int compressionLevel() {
        return compressionLevel;
    }

    public int blockSize() {
        return blockSize;
    }

    public int maxEncodeSize() {
        return maxEncodeSize;
    }
}
