package io.netty.handler.codec.compression;

import com.aayushatharva.brotli4j.decoder.Decoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

public class BrotliEncoderTest extends AbstractEncoderTest {

    @Override
    public void initChannel() {
        channel = new EmbeddedChannel(new BrotliEncoder());
    }

    @Override
    protected ByteBuf decompress(ByteBuf compressed, int originalLength) throws Exception {
        byte[] compressedArray = new byte[compressed.readableBytes()];
        compressed.readBytes(compressedArray);
        compressed.release();

        byte[] decompressed = Decoder.decompress(compressedArray).getDecompressedData();
        return Unpooled.wrappedBuffer(decompressed);
    }
}
