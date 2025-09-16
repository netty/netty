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

    static abstract class AbstractZlibDecompressorBuilder extends AbstractDecompressorBuilder {
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
