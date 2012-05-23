package io.netty.channel;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;

import java.util.ArrayDeque;
import java.util.Queue;

public final class ChannelBufferHolders {

    public static <E> ChannelBufferHolder<E> messageBuffer() {
        return messageBuffer(new ArrayDeque<E>());
    }

    public static <E> ChannelBufferHolder<E> messageBuffer(Queue<E> buffer) {
        return new ChannelBufferHolder<E>(buffer);
    }

    public static ChannelBufferHolder<Byte> byteBuffer() {
        // TODO: Use more efficient implementation.
        return byteBuffer(ChannelBuffers.dynamicBuffer());
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

    public static <E> ChannelBufferHolder<E> catchAllBuffer() {
        return catchAllBuffer(new ArrayDeque<E>(), ChannelBuffers.dynamicBuffer());
    }

    public static <E> ChannelBufferHolder<E> catchAllBuffer(Queue<E> msgBuf, ChannelBuffer byteBuf) {
        return new ChannelBufferHolder<E>(msgBuf, byteBuf);
    }

    private ChannelBufferHolders() {
        // Utility class
    }
}
