package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;

public class JdkZlibDecompressorIntegrationTest extends JdkZlibIntegrationTest {
    @Override
    protected EmbeddedChannel createDecoder() {
        return new EmbeddedChannel(new BackpressureDecompressionHandler(JdkZlibDecompressor.builder(ByteBufAllocator.DEFAULT).build()));
    }
}
