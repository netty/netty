package io.netty.handler.codec.compression;

import com.nixxcode.jvmbrotli.dec.BrotliInputStream;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class BrotliDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        ByteBuf decompressed = ctx.alloc().buffer();

        ByteBufOutputStream byteBufOutputStream = new ByteBufOutputStream(decompressed);

        BrotliInputStream decompressorIS = new BrotliInputStream(new ByteBufInputStream(in));
        int read = decompressorIS.read();
        while(read > -1) {
            byteBufOutputStream.write(read);
            read = decompressorIS.read();
        }

        byteBufOutputStream.close();
        decompressorIS.close();

        out.add(byteBufOutputStream.buffer());
    }
}
