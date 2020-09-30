package io.netty.handler.codec.compression;

import com.nixxcode.jvmbrotli.enc.BrotliOutputStream;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class BrotliEncoder extends MessageToByteEncoder<ByteBuf> {

    private final int totalLength;
    private int writtenLength;

    public BrotliEncoder(int totalLength) {
        this.totalLength = totalLength;
    }

    private BrotliOutputStream compressorOS;
    private ByteBufOutputStream byteBufOutputStream;

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        if (compressorOS == null) {
            byteBufOutputStream = new ByteBufOutputStream(out);
            compressorOS = new BrotliOutputStream(byteBufOutputStream);
        }

        writtenLength += msg.readableBytes();
        compressorOS.write(ByteBufUtil.getBytes(msg));

        if (writtenLength == totalLength) {
            byteBufOutputStream.close();
            compressorOS.close();
        } else {
            byteBufOutputStream.flush();
            compressorOS.flush();
        }
    }
}
