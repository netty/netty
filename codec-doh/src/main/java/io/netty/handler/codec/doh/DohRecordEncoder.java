package io.netty.handler.codec.doh;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.http.*;

import java.net.InetSocketAddress;
import java.util.Base64;

public class DohRecordEncoder extends ChannelOutboundHandlerAdapter {
    private static final String DEFAULT_DOH_PATH = "/dns-query";
    private final DohQueryEncoder dohQueryEncoder = new DohQueryEncoder();

    private final InetSocketAddress dohServer;
    private final boolean useHttpPost;
    private final String uri;

    public DohRecordEncoder(InetSocketAddress dohServer) {
        this(dohServer, true, DEFAULT_DOH_PATH);
    }

    public DohRecordEncoder(InetSocketAddress dohServer, boolean useHttpPost) {
        this(dohServer, useHttpPost, DEFAULT_DOH_PATH);
    }

    public DohRecordEncoder(InetSocketAddress dohServer, String uri) {
        this(dohServer, true, uri);
    }

    public DohRecordEncoder(InetSocketAddress dohServer, boolean useHttpPost, String uri) {
        this.dohServer = dohServer;
        this.useHttpPost = useHttpPost;
        this.uri = uri;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ByteBuf content = ctx.alloc().heapBuffer();
        dohQueryEncoder.encode(ctx, (DnsQuery) msg, content);

        HttpRequest request = useHttpPost ? createPostRequest(content) : createGetRequest(content);

        request.headers().set(HttpHeaderNames.HOST, dohServer.getHostName());
        request.headers().set(HttpHeaderNames.ACCEPT, "application/dns-message");
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/dns-message");

        if (useHttpPost) {
            request.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        }

        super.write(ctx, request, promise);
    }

    private DefaultFullHttpRequest createPostRequest(ByteBuf content) {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri, content);
    }

    private DefaultFullHttpRequest createGetRequest(ByteBuf content) {
        QueryStringEncoder queryString = new QueryStringEncoder(uri);
        queryString.addParam("dns", Base64.getUrlEncoder().withoutPadding().encodeToString(toByteArray(content)));
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, queryString.toString());
    }

    private byte[] toByteArray(ByteBuf content) {
        byte[] contentBytes = new byte[content.readableBytes()];
        content.readBytes(contentBytes);
        return contentBytes;
    }
}
