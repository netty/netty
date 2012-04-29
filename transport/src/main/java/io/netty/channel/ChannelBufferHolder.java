package io.netty.channel;

import io.netty.buffer.ChannelBuffer;

import java.util.Queue;

public final class ChannelBufferHolder<E> {

    private final Queue<E> msgBuf;
    private final ChannelBuffer byteBuf;

    ChannelBufferHolder(Queue<E> msgBuf) {
        if (msgBuf == null) {
            throw new NullPointerException("msgBuf");
        }
        this.msgBuf = msgBuf;
        byteBuf = null;

    }

    ChannelBufferHolder(ChannelBuffer byteBuf) {
        if (byteBuf == null) {
            throw new NullPointerException("byteBuf");
        }
        msgBuf = null;
        this.byteBuf = byteBuf;
    }

    public boolean hasMessageBuffer() {
        return msgBuf != null;
    }

    public boolean hasByteBuffer() {
        return byteBuf != null;
    }

    public Queue<E> messageBuffer() {
        if (!hasMessageBuffer()) {
            throw new IllegalStateException("does not have a message buffer");
        }
        return msgBuf;
    }

    public ChannelBuffer byteBuffer() {
        if (!hasByteBuffer()) {
            throw new IllegalStateException("does not have a byte buffer");
        }
        return byteBuf;
    }

    @Override
    public String toString() {
        if (hasMessageBuffer()) {
            return messageBuffer().toString();
        } else {
            return byteBuffer().toString();
        }
    }
}
