package io.netty.example.proxy;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerContext;
import io.netty.channel.ChannelInboundStreamHandlerAdapter;

public class HexDumpProxyBackendHandler extends ChannelInboundStreamHandlerAdapter {

    private final Channel inboundChannel;

    public HexDumpProxyBackendHandler(Channel inboundChannel) {
        this.inboundChannel = inboundChannel;
    }

    @Override
    public void channelActive(ChannelInboundHandlerContext<Byte> ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void inboundBufferUpdated(ChannelInboundHandlerContext<Byte> ctx) throws Exception {
        ChannelBuffer in = ctx.inbound().byteBuffer();
        ChannelBuffer out = inboundChannel.outbound().byteBuffer();
        out.discardReadBytes();
        out.writeBytes(in);
        in.clear();
        inboundChannel.flush();
    }

    @Override
    public void channelInactive(ChannelInboundHandlerContext<Byte> ctx) throws Exception {
        HexDumpProxyFrontendHandler.closeOnFlush(inboundChannel);
    }

    @Override
    public void exceptionCaught(ChannelInboundHandlerContext<Byte> ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        HexDumpProxyFrontendHandler.closeOnFlush(ctx.channel());
    }
}