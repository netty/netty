package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;

public class FastLzDecompressorIntegrationTest extends FastLzIntegrationTest {
    @Override
    protected EmbeddedChannel createDecoder() {
        return new EmbeddedChannel(new BackpressureDecompressionHandler(FastLzFrameDecompressor.builder(ByteBufAllocator.DEFAULT).defaultChecksum().build()));
    }
}
