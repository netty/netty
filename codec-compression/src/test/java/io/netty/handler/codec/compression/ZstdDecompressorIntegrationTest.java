package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;

public class ZstdDecompressorIntegrationTest extends ZstdIntegrationTest {
    @Override
    protected EmbeddedChannel createDecoder() {
        return new EmbeddedChannel(new BackpressureDecompressionHandler(ZstdDecompressor.builder(ByteBufAllocator.DEFAULT).build()));
    }
}
