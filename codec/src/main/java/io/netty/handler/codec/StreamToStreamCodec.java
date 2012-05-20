package io.netty.handler.codec;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelInboundHandlerContext;
import io.netty.channel.ChannelOutboundHandlerContext;

public abstract class StreamToStreamCodec extends ChannelHandlerAdapter<Byte, Byte> {

    private final StreamToStreamEncoder encoder = new StreamToStreamEncoder() {
        @Override
        public void encode(
                ChannelOutboundHandlerContext<Byte> ctx,
                ChannelBuffer in, ChannelBuffer out) throws Exception {
            StreamToStreamCodec.this.encode(ctx, in, out);
        }
    };

    private final StreamToStreamDecoder decoder = new StreamToStreamDecoder() {
        @Override
        public void decode(
                ChannelInboundHandlerContext<Byte> ctx,
                ChannelBuffer in, ChannelBuffer out) throws Exception {
            StreamToStreamCodec.this.decode(ctx, in, out);
        }
    };

    @Override
    public ChannelBufferHolder<Byte> newInboundBuffer(
            ChannelInboundHandlerContext<Byte> ctx) throws Exception {
        return decoder.newInboundBuffer(ctx);
    }

    @Override
    public void inboundBufferUpdated(ChannelInboundHandlerContext<Byte> ctx) throws Exception {
        decoder.inboundBufferUpdated(ctx);
    }

    @Override
    public ChannelBufferHolder<Byte> newOutboundBuffer(
            ChannelOutboundHandlerContext<Byte> ctx) throws Exception {
        return encoder.newOutboundBuffer(ctx);
    }

    @Override
    public void flush(
            ChannelOutboundHandlerContext<Byte> ctx, ChannelFuture future) throws Exception {
        encoder.flush(ctx, future);
    }

    public abstract void encode(
            ChannelOutboundHandlerContext<Byte> ctx,
            ChannelBuffer in, ChannelBuffer out) throws Exception;

    public abstract void decode(
            ChannelInboundHandlerContext<Byte> ctx,
            ChannelBuffer in, ChannelBuffer out) throws Exception;
}
