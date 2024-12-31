package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.channel.socket.DatagramPacket;

import java.net.InetSocketAddress;
import java.util.List;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

// TODO Docs
// TODO Example
// TODO Tests
public class DatagramPacketToAddressedEnvelopeDecoder extends MessageToMessageDecoder<DatagramPacket> {

    private final MessageToMessageDecoder<ByteBuf> decoder;

    public DatagramPacketToAddressedEnvelopeDecoder(MessageToMessageDecoder<ByteBuf> decoder) {
        this.decoder = checkNotNull(decoder, "decoder");
    }

    @Override
    public boolean acceptInboundMessage(Object msg) throws Exception {
        if (msg instanceof DatagramPacket) {
            return decoder.acceptInboundMessage(((DatagramPacket) msg).content());
        }
        return false;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) throws Exception {
        CodecOutputList decoderOut = CodecOutputList.newInstance();
        try {
            decoder.decode(ctx, msg.content(), decoderOut);
            for (Object decoded : decoderOut) {
                // TODO Error cases: Unable to create envelope or envelope is null
                out.add(createEnvelope(decoded, msg.recipient(), msg.sender()));
            }
        } finally {
            decoderOut.recycle();
        }
    }

    protected AddressedEnvelope<Object, InetSocketAddress> createEnvelope(Object decoded,
                                                                               InetSocketAddress recipient,
                                                                               InetSocketAddress sender) {
        return new DefaultAddressedEnvelope<Object, InetSocketAddress>(decoded, recipient, sender);
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        decoder.channelRegistered(ctx);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        decoder.channelUnregistered(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        decoder.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        decoder.channelInactive(ctx);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        decoder.channelReadComplete(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        decoder.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        decoder.channelWritabilityChanged(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        decoder.exceptionCaught(ctx, cause);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        decoder.handlerAdded(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        decoder.handlerRemoved(ctx);
    }

    @Override
    public boolean isSharable() {
        return decoder.isSharable();
    }
}
