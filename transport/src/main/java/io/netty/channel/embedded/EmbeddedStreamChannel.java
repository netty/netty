package io.netty.channel.embedded;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.ChannelBufferHolders;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelType;

public class EmbeddedStreamChannel extends AbstractEmbeddedChannel {

    private final ChannelBuffer lastOutboundBuffer = ChannelBuffers.dynamicBuffer();

    public EmbeddedStreamChannel(ChannelHandler... handlers) {
        super(ChannelBufferHolders.messageBuffer(), handlers);
    }

    @Override
    public ChannelType type() {
        return ChannelType.STREAM;
    }

    public ChannelBuffer inboundBuffer() {
        return pipeline().inboundByteBuffer();
    }

    public ChannelBuffer lastOutboundBuffer() {
        return lastOutboundBuffer;
    }

    public ChannelBuffer readOutbound() {
        if (!lastOutboundBuffer.readable()) {
            return null;
        }
        try {
            return lastOutboundBuffer.readBytes(lastOutboundBuffer.readableBytes());
        } finally {
            lastOutboundBuffer.clear();
        }
    }

    public boolean writeInbound(ChannelBuffer data) {
        inboundBuffer().writeBytes(data);
        pipeline().fireInboundBufferUpdated();
        checkException();
        return lastInboundByteBuffer().readable() || !lastInboundMessageBuffer().isEmpty();
    }

    public boolean writeOutbound(Object msg) {
        write(msg);
        checkException();
        return lastOutboundBuffer().readable();
    }

    @Override
    protected void doFlushByteBuffer(ChannelBuffer buf) throws Exception {
        if (!lastOutboundBuffer.readable()) {
            lastOutboundBuffer.discardReadBytes();
        }
        lastOutboundBuffer.writeBytes(buf);
    }
}
