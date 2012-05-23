package io.netty.channel;

import io.netty.buffer.ChannelBuffer;

import java.util.Queue;

public final class ChannelBufferHolder<E> {

    private final ChannelHandlerContext ctx;
    /** 0 - not a bypass, 1 - inbound bypass, 2 - outbound bypass */
    private final int bypassDirection;
    private final Queue<E> msgBuf;
    private final ChannelBuffer byteBuf;

    ChannelBufferHolder(ChannelHandlerContext ctx, boolean inbound) {
        if (ctx == null) {
            throw new NullPointerException("ctx");
        }
        this.ctx = ctx;
        bypassDirection = inbound? 1 : 2;
        msgBuf = null;
        byteBuf = null;
    }

    ChannelBufferHolder(Queue<E> msgBuf) {
        if (msgBuf == null) {
            throw new NullPointerException("msgBuf");
        }
        ctx = null;
        bypassDirection = 0;
        this.msgBuf = msgBuf;
        byteBuf = null;

    }

    ChannelBufferHolder(ChannelBuffer byteBuf) {
        if (byteBuf == null) {
            throw new NullPointerException("byteBuf");
        }
        ctx = null;
        bypassDirection = 0;
        msgBuf = null;
        this.byteBuf = byteBuf;
    }

    ChannelBufferHolder(Queue<E> msgBuf, ChannelBuffer byteBuf) {
        ctx = null;
        bypassDirection = 0;
        this.msgBuf = msgBuf;
        this.byteBuf = byteBuf;
    }

    public boolean isBypass() {
        return bypassDirection != 0;
    }

    public boolean hasMessageBuffer() {
        switch (bypassDirection) {
        case 0:
            return msgBuf != null;
        case 1:
            return ctx.nextIn().hasMessageBuffer();
        case 2:
            return ctx.out().hasMessageBuffer();
        default:
            throw new Error();
        }
    }

    public boolean hasByteBuffer() {
        switch (bypassDirection) {
        case 0:
            return byteBuf != null;
        case 1:
            return ctx.nextIn().hasByteBuffer();
        case 2:
            return ctx.out().hasByteBuffer();
        default:
            throw new Error();
        }
    }

    @SuppressWarnings("unchecked")
    public Queue<E> messageBuffer() {
        switch (bypassDirection) {
        case 0:
            if (!hasMessageBuffer()) {
                throw new IllegalStateException("does not have a message buffer");
            }
            return msgBuf;
        case 1:
            return (Queue<E>) ctx.nextIn().messageBuffer();
        case 2:
            return (Queue<E>) ctx.out().messageBuffer();
        default:
            throw new Error();
        }
    }

    public ChannelBuffer byteBuffer() {
        switch (bypassDirection) {
        case 0:
            if (!hasByteBuffer()) {
                throw new IllegalStateException("does not have a byte buffer");
            }
            return byteBuf;
        case 1:
            return ctx.nextIn().byteBuffer();
        case 2:
            return ctx.out().byteBuffer();
        default:
            throw new Error();
        }
    }

    @Override
    public String toString() {
        switch (bypassDirection) {
        case 0:
            if (hasMessageBuffer()) {
                return messageBuffer().toString();
            } else {
                return byteBuffer().toString();
            }
        case 1:
            return ctx.nextIn().toString();
        case 2:
            return ctx.out().toString();
        default:
            throw new Error();
        }
    }

    public int size() {
        switch (bypassDirection) {
        case 0:
            if (hasMessageBuffer()) {
                return messageBuffer().size();
            } else {
                return byteBuffer().readableBytes();
            }
        case 1:
            return ctx.nextIn().size();
        case 2:
            return ctx.out().size();
        default:
            throw new Error();
        }
    }

    public boolean isEmpty() {
        switch (bypassDirection) {
        case 0:
            if (hasMessageBuffer()) {
                return messageBuffer().isEmpty();
            } else {
                return byteBuffer().readable();
            }
        case 1:
            return ctx.nextIn().isEmpty();
        case 2:
            return ctx.out().isEmpty();
        default:
            throw new Error();
        }
    }
}
