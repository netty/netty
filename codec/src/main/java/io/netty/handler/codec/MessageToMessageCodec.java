package io.netty.handler.codec;

import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelInboundHandlerContext;
import io.netty.channel.ChannelOutboundHandlerContext;

public abstract class MessageToMessageCodec<INBOUND_IN, INBOUND_OUT, OUTBOUND_IN, OUTBOUND_OUT>
        extends ChannelHandlerAdapter<INBOUND_IN, OUTBOUND_IN> {

    private final MessageToMessageEncoder<OUTBOUND_IN, OUTBOUND_OUT> encoder =
            new MessageToMessageEncoder<OUTBOUND_IN, OUTBOUND_OUT>() {
        @Override
        public OUTBOUND_OUT encode(ChannelOutboundHandlerContext<OUTBOUND_IN> ctx, OUTBOUND_IN msg) throws Exception {
            return MessageToMessageCodec.this.encode(ctx, msg);
        }
    };

    private final MessageToMessageDecoder<INBOUND_IN, INBOUND_OUT> decoder =
            new MessageToMessageDecoder<INBOUND_IN, INBOUND_OUT>() {
        @Override
        public INBOUND_OUT decode(ChannelInboundHandlerContext<INBOUND_IN> ctx, INBOUND_IN msg) throws Exception {
            return MessageToMessageCodec.this.decode(ctx, msg);
        }
    };

    @Override
    public ChannelBufferHolder<INBOUND_IN> newInboundBuffer(ChannelInboundHandlerContext<INBOUND_IN> ctx) throws Exception {
        return decoder.newInboundBuffer(ctx);
    }

    @Override
    public void inboundBufferUpdated(
            ChannelInboundHandlerContext<INBOUND_IN> ctx) throws Exception {
        decoder.inboundBufferUpdated(ctx);
    }

    @Override
    public ChannelBufferHolder<OUTBOUND_IN> newOutboundBuffer(ChannelOutboundHandlerContext<OUTBOUND_IN> ctx) throws Exception {
        return encoder.newOutboundBuffer(ctx);
    }

    @Override
    public void flush(ChannelOutboundHandlerContext<OUTBOUND_IN> ctx, ChannelFuture future) throws Exception {
        encoder.flush(ctx, future);
    }

    public abstract OUTBOUND_OUT encode(ChannelOutboundHandlerContext<OUTBOUND_IN> ctx, OUTBOUND_IN msg) throws Exception;
    public abstract INBOUND_OUT decode(ChannelInboundHandlerContext<INBOUND_IN> ctx, INBOUND_IN msg) throws Exception;
}
