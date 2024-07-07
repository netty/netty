package io.netty.handler.codec.doh;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.dns.*;
import io.netty.handler.codec.http.FullHttpResponse;

import java.net.SocketAddress;

public class DohResponseDecoder extends SimpleChannelInboundHandler<FullHttpResponse> {

    private final DnsResponseDecoder<SocketAddress> responseDecoder;

    public DohResponseDecoder() {
        responseDecoder = new DnsResponseDecoder<SocketAddress>(DnsRecordDecoder.DEFAULT) {
            @Override
            protected DnsResponse newResponse(SocketAddress sender, SocketAddress recipient, int id, DnsOpCode opCode,
                                              DnsResponseCode responseCode) {
                return new DefaultDnsResponse(id, opCode, responseCode);
            }
        };
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
        ByteBuf content = msg.content();
        DnsResponse dnsResponse = responseDecoder.decode(ctx.channel().remoteAddress(), ctx.channel().localAddress(),
                content);

        ctx.fireChannelRead(dnsResponse);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}