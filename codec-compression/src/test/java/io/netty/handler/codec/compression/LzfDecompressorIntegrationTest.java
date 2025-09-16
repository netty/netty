package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;

public class LzfDecompressorIntegrationTest extends LzfIntegrationTest {
    @Override
    protected EmbeddedChannel createDecoder() {
        return new EmbeddedChannel(new BackpressureDecompressionHandler(LzfDecompressor.builder(ByteBufAllocator.DEFAULT).build()));
    }
}
