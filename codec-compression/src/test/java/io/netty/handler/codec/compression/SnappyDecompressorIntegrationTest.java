package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;

public class SnappyDecompressorIntegrationTest extends SnappyIntegrationTest {
    @Override
    protected EmbeddedChannel createDecoder() {
        return new EmbeddedChannel(new BackpressureDecompressionHandler(SnappyFrameDecompressor.builder(ByteBufAllocator.DEFAULT).build()));
    }
}
