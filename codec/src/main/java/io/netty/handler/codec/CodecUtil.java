package io.netty.handler.codec;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.NoSuchBufferException;

final class CodecUtil {

    static boolean unfoldAndAdd(
            ChannelHandlerContext ctx, Object msg, boolean inbound) throws Exception {
        if (msg == null) {
            return false;
        }

        // Note we only recognize Object[] because Iterable is often implemented by user messages.
        if (msg instanceof Object[]) {
            Object[] array = (Object[]) msg;
            if (array.length == 0) {
                return false;
            }

            boolean added = false;
            for (Object m: array) {
                if (m == null) {
                    break;
                }
                if (unfoldAndAdd(ctx, m, inbound)) {
                    added = true;
                }
            }
            return added;
        }

        if (inbound) {
            try {
                ctx.nextInboundMessageBuffer().add(msg);
                return true;
            } catch (NoSuchBufferException e) {
                if (msg instanceof ChannelBuffer) {
                    ChannelBuffer altDst = ctx.nextInboundByteBuffer();
                    ChannelBuffer src = (ChannelBuffer) msg;
                    altDst.writeBytes(src, src.readerIndex(), src.readableBytes());
                    return true;
                }
            }
        } else {
            try {
                ctx.nextOutboundMessageBuffer().add(msg);
                return true;
            } catch (NoSuchBufferException e) {
                if (msg instanceof ChannelBuffer) {
                    ChannelBuffer altDst = ctx.nextOutboundByteBuffer();
                    ChannelBuffer src = (ChannelBuffer) msg;
                    altDst.writeBytes(src, src.readerIndex(), src.readableBytes());
                    return true;
                }
            }
        }

        throw new NoSuchBufferException();
    }

    private CodecUtil() {
        // Unused
    }
}
