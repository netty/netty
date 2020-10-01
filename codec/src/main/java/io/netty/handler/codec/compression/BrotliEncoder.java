package io.netty.handler.codec.compression;

import com.nixxcode.jvmbrotli.common.BrotliLoader;
import com.nixxcode.jvmbrotli.enc.BrotliOutputStream;
import com.nixxcode.jvmbrotli.enc.Encoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

public class BrotliEncoder extends MessageToByteEncoder<ByteBuf> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(BrotliEncoder.class);

    static {
        logger.info("Brotli Loader Status: {}", BrotliLoader.isBrotliAvailable());
    }

    private final Encoder.Parameters parameters;
    private BrotliOutputStream brotliOutputStream;
    private ByteBuf byteBuf;

    public BrotliEncoder(int quality) {
        this.parameters = new Encoder.Parameters().setQuality(quality);
    }

    @Override
    public void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        if (msg.readableBytes() == 0) {
            out.writeBytes(msg);
            return;
        }

        if (brotliOutputStream == null) {
            byteBuf = ctx.alloc().buffer();
            brotliOutputStream = new BrotliOutputStream(new ByteBufOutputStream(byteBuf), parameters);
        }

        if (msg.hasArray()) {
            byte[] inAry = msg.array();
            int offset = msg.arrayOffset() + msg.readerIndex();
            int len = msg.readableBytes();
            brotliOutputStream.write(inAry, offset, len);
        } else {
            brotliOutputStream.write(ByteBufUtil.getBytes(msg));
        }

        brotliOutputStream.flush();
        out.writeBytes(byteBuf);
        byteBuf.clear();

        if (!out.isWritable()) {
            out.ensureWritable(out.writerIndex());
        }
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        if (brotliOutputStream != null) {
            brotliOutputStream.close();
            byteBuf.release();
            promise.setSuccess();
            brotliOutputStream = null;
        }
    }
}
