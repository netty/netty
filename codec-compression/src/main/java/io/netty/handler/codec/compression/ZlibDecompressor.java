package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBufAllocator;

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
        protected int maxAllocation;

        protected AbstractZlibDecompressorBuilder(ByteBufAllocator allocator) {
            super(allocator);
        }

        public AbstractZlibDecompressorBuilder wrapper(ZlibWrapper wrapper) {
            this.wrapper = wrapper;
            return this;
        }

        public AbstractZlibDecompressorBuilder dictionary(byte[] dictionary) {
            this.dictionary = dictionary;
            return this;
        }

        public AbstractZlibDecompressorBuilder maxAllocation(int maxAllocation) {
            this.maxAllocation = maxAllocation;
            return this;
        }
    }
}
