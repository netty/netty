package io.netty.handler.codec.doh;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.http.*;

import java.util.Base64;

public class DohRecordEncoder extends ChannelOutboundHandlerAdapter {
    private final DohQueryEncoder dohQueryEncoder = new DohQueryEncoder();

    private final DohProviders.DohProvider dohProvider;

    public DohRecordEncoder(DohProviders.DohProvider dohProvider) {
        this.dohProvider = dohProvider;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ByteBuf content = ctx.alloc().heapBuffer();
        dohQueryEncoder.encode(ctx, (DnsQuery) msg, content);

        HttpRequest request = dohProvider.usePost() ? createPostRequest(content) : createGetRequest(content);

        request.headers().set(HttpHeaderNames.HOST, dohProvider.host());
        request.headers().set(HttpHeaderNames.ACCEPT, "application/dns-message");
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/dns-message");

        if (dohProvider.usePost()) {
            request.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        }

        super.write(ctx, request, promise);
    }

    private DefaultFullHttpRequest createPostRequest(ByteBuf content) {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, dohProvider.uri(), content);
    }

    private DefaultFullHttpRequest createGetRequest(ByteBuf content) {
        QueryStringEncoder queryString = new QueryStringEncoder(dohProvider.uri());
        queryString.addParam("dns", Base64.getUrlEncoder().withoutPadding().encodeToString(toByteArray(content)));
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, queryString.toString());
    }

    private byte[] toByteArray(ByteBuf content) {
        byte[] contentBytes = new byte[content.readableBytes()];
        content.readBytes(contentBytes);
        return contentBytes;
    }
}
