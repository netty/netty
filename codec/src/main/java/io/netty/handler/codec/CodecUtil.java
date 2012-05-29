package io.netty.handler.codec;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.ChannelHandlerContext;

import java.util.Queue;

class CodecUtil {

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
            Queue<Object> dst = ctx.nextInboundMessageBuffer();
            if (dst != null) {
                dst.add(msg);
                return true;
            } else if (msg instanceof ChannelBuffer) {
                ChannelBuffer altDst = ctx.nextInboundByteBuffer();
                ChannelBuffer src = (ChannelBuffer) msg;
                if (altDst != null) {
                    altDst.writeBytes(src, src.readerIndex(), src.readableBytes());
                    return true;
                }
            }
        } else {
            Queue<Object> dst = ctx.nextOutboundMessageBuffer();
            if (dst != null) {
                dst.add(msg);
                return true;
            } else if (msg instanceof ChannelBuffer) {
                ChannelBuffer altDst = ctx.nextOutboundByteBuffer();
                ChannelBuffer src = (ChannelBuffer) msg;
                if (altDst != null) {
                    altDst.writeBytes(src, src.readerIndex(), src.readableBytes());
                    return true;
                }
            }
        }

        throw new IllegalStateException("no suitable destination buffer found");
    }

    private CodecUtil() {
        // Unused
    }
}
