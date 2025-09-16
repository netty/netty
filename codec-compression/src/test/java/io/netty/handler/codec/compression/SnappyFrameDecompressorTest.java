package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandler;

public class SnappyFrameDecompressorTest extends SnappyFrameDecoderTest {
    @Override
    protected ChannelHandler createDecoder() {
        return new BackpressureDecompressionHandler(SnappyFrameDecompressor.builder(ByteBufAllocator.DEFAULT).build());
    }
}
