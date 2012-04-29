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

    private ChannelBufferHolders() {
        // Utility class
    }
}
