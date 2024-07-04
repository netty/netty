package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.*;

public class DohRecordEncoder extends ChannelOutboundHandlerAdapter {
    private final DohQueryEncoder dohQueryEncoder = new DohQueryEncoder();

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ByteBuf content = ctx.alloc().heapBuffer();
        dohQueryEncoder.encode(ctx, (DnsQuery) msg, content);

        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/dns-query", content);

        request.headers().set(HttpHeaderNames.HOST, "dns.google");
        request.headers().set(HttpHeaderNames.ACCEPT, "application/dns-message");
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/dns-message");
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());

        super.write(ctx, request, promise);
    }
}
