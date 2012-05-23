package io.netty.channel;


public class ChannelInboundStreamHandlerAdapter extends ChannelInboundHandlerAdapter<Byte> {
    @Override
    public ChannelBufferHolder<Byte> newInboundBuffer(
            ChannelInboundHandlerContext<Byte> ctx) throws Exception {
        return ChannelBufferHolders.byteBuffer();
    }
}
