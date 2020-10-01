package io.netty.handler.codec.compression;

import com.nixxcode.jvmbrotli.common.BrotliLoader;
import com.nixxcode.jvmbrotli.enc.BrotliOutputStream;
import com.nixxcode.jvmbrotli.enc.Encoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

public class BrotliEncoder extends MessageToByteEncoder<ByteBuf> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(BrotliEncoder.class);

    static {
        logger.info("Brotli Loader Status: {}", BrotliLoader.isBrotliAvailable());
    }

    private final Encoder.Parameters params;
    private BrotliOutputStream compressorOS;
    private ByteBufOutputStream byteBufOutputStream;

    public BrotliEncoder(int quality) {
        this.params = new Encoder.Parameters().setQuality(quality);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        if (compressorOS == null) {
            byteBufOutputStream = new ByteBufOutputStream(out);
            compressorOS = new BrotliOutputStream(byteBufOutputStream, params);
        }

        if (msg.hasArray()) {
            compressorOS.write(msg.array());
        } else {
            compressorOS.write(ByteBufUtil.getBytes(msg));
        }

        byteBufOutputStream.flush();
        compressorOS.flush();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
        byteBufOutputStream.close();
        compressorOS.close();
    }
}
