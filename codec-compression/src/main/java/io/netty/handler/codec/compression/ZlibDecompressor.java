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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBufAllocator;
import io.netty.util.internal.ObjectUtil;

import java.util.Objects;

abstract class ZlibDecompressor extends InputBufferingDecompressor {
    protected final int maxAllocation;
    protected final byte[] dictionary;

    ZlibDecompressor(AbstractZlibDecompressorBuilder builder, ByteBufAllocator allocator) {
        super(allocator);
        this.maxAllocation = builder.maxAllocation;
        this.dictionary = builder.dictionary;
    }

    final int outputBufferSize(int compressedSize) {
        long proposedCapacity = Math.max(1L, (long) compressedSize << 1);
        long maximumCapacity = maxAllocation == 0 ? Integer.MAX_VALUE : maxAllocation;
        return (int) Math.min(maximumCapacity, proposedCapacity);
    }

    abstract static class AbstractZlibDecompressorBuilder extends AbstractDecompressorBuilder {
        protected ZlibWrapper wrapper = ZlibWrapper.ZLIB;
        protected byte[] dictionary;
        protected int maxAllocation = 1024 * 1024;

        protected AbstractZlibDecompressorBuilder() {
        }

        /**
         * Set the wrapper format for the deflated data. Defaults to {@link ZlibWrapper#ZLIB}.
         *
         * @param wrapper The wrapper format
         * @return This builder
         */
        public AbstractZlibDecompressorBuilder wrapper(ZlibWrapper wrapper) {
            this.wrapper = Objects.requireNonNull(wrapper, "wrapper");
            return this;
        }

        /**
         * Set the preset dictionary to use. Defaults to no dictionary.
         *
         * @param dictionary The dictionary
         * @return This builder
         */
        public AbstractZlibDecompressorBuilder dictionary(byte[] dictionary) {
            this.dictionary = ObjectUtil.checkNotNull(dictionary, "dictionary").clone();
            return this;
        }

        /**
         * Set the maximum output buffer size. Defaults to 1M.
         *
         * @param maxAllocation The maximum output buffer size.
         * @return This builder
         */
        public AbstractZlibDecompressorBuilder maxAllocation(int maxAllocation) {
            this.maxAllocation = ObjectUtil.checkPositiveOrZero(maxAllocation, "maxAllocation");
            return this;
        }

        final void validate() {
            if (dictionary != null && wrapper != ZlibWrapper.ZLIB) {
                throw new IllegalArgumentException("Dictionary is only supported for ZLIB wrapper");
            }
        }
    }
}
