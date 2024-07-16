/*
 * Copyright 2024 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.doh;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringEncoder;
import io.netty.util.internal.ObjectUtil;

import java.net.InetSocketAddress;
import java.util.Base64;

public final class DohRecordEncoder extends ChannelOutboundHandlerAdapter {
    private static final String DEFAULT_DOH_PATH = "/dns-query";
    private final DohQueryEncoder dohQueryEncoder = new DohQueryEncoder();

    private final InetSocketAddress dohServer;
    private final boolean useHttpPost;
    private final String uri;

    /**
     * Creates a new instance.
     *
     * @param dohServer the dohServer address
     */
    public DohRecordEncoder(InetSocketAddress dohServer) {
        this(dohServer, true, DEFAULT_DOH_PATH);
    }

    /**
     * Creates a new instance.
     *
     * @param dohServer the dohServer address
     * @param useHttpPost the http request method that can be used to connect to dohServer
     */
    public DohRecordEncoder(InetSocketAddress dohServer, boolean useHttpPost) {
        this(dohServer, useHttpPost, DEFAULT_DOH_PATH);
    }

    /**
     * Creates a new instance.
     *
     * @param dohServer the dohServer address
     * @param uri the http request uri that can be used as address path
     */
    public DohRecordEncoder(InetSocketAddress dohServer, String uri) {
        this(dohServer, true, uri);
    }

    /**
     * Creates a new instance.
     *
     * @param dohServer the dohServer address
     * @param useHttpPost the http request method that can be used to connect to dohServer
     * @param uri the http request uri that can be used as address path
     */
    public DohRecordEncoder(InetSocketAddress dohServer, boolean useHttpPost, String uri) {
        this.dohServer = ObjectUtil.checkNotNull(dohServer, "dohServer");
        this.useHttpPost = useHttpPost;
        this.uri = ObjectUtil.checkNotNull(uri, "uri");
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        DnsQuery query = (DnsQuery) msg
        ByteBuf content = ctx.alloc().buffer();
        try {
            dohQueryEncoder.encode(ctx, query, content);

            HttpRequest request = useHttpPost ? createPostRequest(content, uri) : createGetRequest(content, uri);

            request.headers().set(HttpHeaderNames.HOST, dohServer.getHostName());
            request.headers().set(HttpHeaderNames.ACCEPT, "application/dns-message");
            request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/dns-message");

            if (useHttpPost) {
                request.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
           }

           ctx.write(request, promise);
        } finally {
            content.release();
            query.release();
        }
    }

    private static DefaultFullHttpRequest createPostRequest(ByteBuf content, String uri) {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri, content);
    }

    private static DefaultFullHttpRequest createGetRequest(ByteBuf content, String uri) {
        QueryStringEncoder queryString = new QueryStringEncoder(uri);
        queryString.addParam("dns", Base64.getUrlEncoder().withoutPadding().encodeToString(toByteArray(content)));
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, queryString.toString());
    }

    private static byte[] toByteArray(ByteBuf content) {
        byte[] contentBytes = new byte[content.readableBytes()];
        content.readBytes(contentBytes);
        return contentBytes;
    }
}
