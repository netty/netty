package io.netty.channel;

import io.netty.buffer.ChannelBuffer;

import java.util.Queue;

public final class ChannelBufferHolders {

    public static <E> ChannelBufferHolder<E> messageBuffer(Queue<E> buffer) {
        return new ChannelBufferHolder<E>(buffer);
    }

    public static ChannelBufferHolder<Byte> byteBuffer(ChannelBuffer buffer) {
        return new ChannelBufferHolder<Byte>(buffer);
    }

    public static <E> ChannelBufferHolder<E> inboundBypassBuffer(ChannelHandlerContext ctx) {
        return new ChannelBufferHolder<E>(ctx, true);
    }

    public static <E> ChannelBufferHolder<E> outboundBypassBuffer(ChannelHandlerContext ctx) {
        return new ChannelBufferHolder<E>(ctx, false);
    }

    private ChannelBufferHolders() {
        // Utility class
    }
}
