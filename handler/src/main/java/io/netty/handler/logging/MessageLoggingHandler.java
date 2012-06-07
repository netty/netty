package io.netty.handler.logging;

import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelBufferHolders;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandler;

import java.util.Queue;

public class MessageLoggingHandler
        extends LoggingHandler
        implements ChannelInboundHandler<Byte>, ChannelOutboundHandler<Byte> {

    public MessageLoggingHandler() {
        super();
    }

    public MessageLoggingHandler(Class<?> clazz, LogLevel level) {
        super(clazz, level);
    }

    public MessageLoggingHandler(Class<?> clazz) {
        super(clazz);
    }

    public MessageLoggingHandler(LogLevel level) {
        super(level);
    }

    public MessageLoggingHandler(String name, LogLevel level) {
        super(name, level);
    }

    public MessageLoggingHandler(String name) {
        super(name);
    }
    @Override
    public ChannelBufferHolder<Byte> newOutboundBuffer(ChannelHandlerContext ctx)
            throws Exception {
        return ChannelBufferHolders.messageBuffer();
    }

    @Override
    public ChannelBufferHolder<Byte> newInboundBuffer(ChannelHandlerContext ctx)
            throws Exception {
        return ChannelBufferHolders.messageBuffer();
    }


    @Override
    public void inboundBufferUpdated(ChannelHandlerContext ctx)
            throws Exception {
        Queue<Object> buf = ctx.inboundMessageBuffer();
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, formatBuffer("RECEIVED", buf)));
        }

        Queue<Object> out = ctx.nextInboundMessageBuffer();
        for (;;) {
            Object o = buf.poll();
            if (o == null) {
                break;
            }
            out.add(o);
        }
        ctx.fireInboundBufferUpdated();
    }

    @Override
    public void flush(ChannelHandlerContext ctx, ChannelFuture future)
            throws Exception {
        Queue<Object> buf = ctx.outboundMessageBuffer();
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, format(ctx, formatBuffer("WRITE", buf)));
        }

        Queue<Object> out = ctx.nextOutboundMessageBuffer();
        for (;;) {
            Object o = buf.poll();
            if (o == null) {
                break;
            }
            out.add(o);
        }
        ctx.flush(future);
    }

    protected String formatBuffer(String message, Queue<Object> buf) {
        return message + '(' + buf.size() + "): " + buf;
    }
}
