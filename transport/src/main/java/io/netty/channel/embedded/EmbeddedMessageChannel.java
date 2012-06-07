package io.netty.channel.embedded;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelType;

import java.util.ArrayDeque;
import java.util.Queue;

public class EmbeddedMessageChannel extends AbstractEmbeddedChannel {

    private final Queue<Object> lastOutboundBuffer = new ArrayDeque<Object>();

    public EmbeddedMessageChannel(ChannelHandler... handlers) {
        super(handlers);
    }

    @Override
    public ChannelType type() {
        return ChannelType.MESSAGE;
    }

    public Queue<Object> inboundBuffer() {
        return pipeline().inboundMessageBuffer();
    }

    public Queue<Object> lastOutboundBuffer() {
        return lastOutboundBuffer;
    }

    public Object readOutbound() {
        return lastOutboundBuffer.poll();
    }

    public boolean writeInbound(Object msg) {
        inboundBuffer().add(msg);
        pipeline().fireInboundBufferUpdated();
        checkException();
        return lastInboundByteBuffer().readable() || !lastInboundMessageBuffer().isEmpty();
    }

    public boolean writeOutbound(Object msg) {
        write(msg);
        checkException();
        return !lastOutboundBuffer().isEmpty();
    }

    public boolean finish() {
        close();
        checkException();
        return lastInboundByteBuffer().readable() || !lastInboundMessageBuffer().isEmpty() ||
               !lastOutboundBuffer().isEmpty();
    }

    @Override
    protected void doFlushMessageBuffer(Queue<Object> buf) throws Exception {
        for (;;) {
            Object o = buf.poll();
            if (o == null) {
                break;
            }
            lastOutboundBuffer.add(o);
        }
    }
}
