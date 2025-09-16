package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;

public class Bzip2DecompressorIntegrationTest extends Bzip2IntegrationTest {
    @Override
    protected EmbeddedChannel createDecoder() {
        return new EmbeddedChannel(new BackpressureDecompressionHandler(Bzip2Decompressor.builder(ByteBufAllocator.DEFAULT).build()));
    }
}
