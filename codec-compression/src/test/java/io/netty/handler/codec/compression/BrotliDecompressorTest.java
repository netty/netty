package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;

public class BrotliDecompressorTest extends BrotliDecoderTest {
    @Override
    protected ChannelHandler createDecoder() {
        return new BackpressureDecompressionHandler(BrotliDecompressor.builder(ByteBufAllocator.DEFAULT).build());
    }
}
