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
import static io.netty.handler.codec.compression.ZstdConstants.MAX_COMPRESSION_LEVEL;

/**
 * {@link ZstdOptions} holds compressionLevel for
 * Zstd compression.
 */
public class ZstdOptions implements CompressionOptions {

    private final int compressionLevel;

    /**
     * Default implementation of {@link ZstdOptions} with{compressionLevel(int)} set to
     * {@link ZstdConstants#DEFAULT_COMPRESSION_LEVEL}
     */
    static final ZstdOptions DEFAULT = new ZstdOptions(DEFAULT_COMPRESSION_LEVEL);

    /**
     * Create a new {@link ZstdOptions}
     *
     * @param  compressionLevel
     *           specifies the level of the compression
     */
    ZstdOptions(int compressionLevel) {
        this.compressionLevel = ObjectUtil.checkInRange(compressionLevel, 0, MAX_COMPRESSION_LEVEL, "compressionLevel");
    }

    public int compressionLevel() {
        return compressionLevel;
    }
}
