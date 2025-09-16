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

import java.util.Objects;

abstract class ZlibDecompressor extends InputBufferingDecompressor {
    protected final int maxAllocation;
    protected final byte[] dictionary;

    ZlibDecompressor(AbstractZlibDecompressorBuilder builder) {
        super(builder.allocator);
        this.maxAllocation = builder.maxAllocation;
        this.dictionary = builder.dictionary;
    }

    abstract static class AbstractZlibDecompressorBuilder extends AbstractDecompressorBuilder {
        protected ZlibWrapper wrapper = ZlibWrapper.ZLIB;
        protected byte[] dictionary;
        protected int maxAllocation = 1024 * 1024;

        protected AbstractZlibDecompressorBuilder(ByteBufAllocator allocator) {
            super(allocator);
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
            this.dictionary = dictionary;
            return this;
        }

        /**
         * Set the maximum output buffer size. Defaults to 1M.
         *
         * @param maxAllocation The maximum output buffer size.
         * @return This builder
         */
        public AbstractZlibDecompressorBuilder maxAllocation(int maxAllocation) {
            this.maxAllocation = maxAllocation;
            return this;
        }
    }
}
