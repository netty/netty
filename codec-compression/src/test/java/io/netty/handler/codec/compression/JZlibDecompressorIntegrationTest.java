package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;

public class JZlibDecompressorIntegrationTest extends JZlibIntegrationTest {
    @Override
    protected EmbeddedChannel createDecoder() {
        return new EmbeddedChannel(new BackpressureDecompressionHandler(JZlibDecompressor.builder(ByteBufAllocator.DEFAULT).build()));
    }
}
