package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;

public class Lz4FrameDecompressorIntegrationTest extends Lz4FrameIntegrationTest {
    @Override
    protected EmbeddedChannel createDecoder() {
        return new EmbeddedChannel(new BackpressureDecompressionHandler(Lz4FrameDecompressor.builder(ByteBufAllocator.DEFAULT).defaultChecksum().build()));
    }
}
