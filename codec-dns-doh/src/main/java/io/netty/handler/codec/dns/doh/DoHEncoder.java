/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.dns.doh;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.base64.Base64Dialect;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsRecordEncoder;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

import java.net.URL;
import java.util.List;

/**
 * Encodes {@link DnsQuery} into {@link FullHttpRequest}
 */
public final class DoHEncoder extends MessageToMessageEncoder<DnsQuery> {

    private final DnsQueryEncoder encoder;
    private final boolean http2;
    private final boolean get;
    private final URL url;

    /**
     * Creates a new encoder which uses {@linkplain DnsRecordEncoder#DEFAULT the default record encoder},
     * HTTP/2 (h2) and HTTP GET method by default.
     *
     * @param url DoH Upstream Server
     * @throws IllegalArgumentException If URL Protocol is not HTTPS
     */
    public DoHEncoder(URL url) throws IllegalArgumentException {
        this(DnsRecordEncoder.DEFAULT, true, true, url);
    }

    /**
     * Creates a new encoder which uses {@linkplain DnsRecordEncoder#DEFAULT the default record encoder},
     * and HTTP/2 (h2) by default.
     *
     * @param httpGET Use HTTP GET method
     * @param url     DoH Upstream Server
     * @throws IllegalArgumentException If URL Protocol is not HTTPS
     */
    public DoHEncoder(boolean httpGET, URL url) throws IllegalArgumentException {
        this(DnsRecordEncoder.DEFAULT, true, httpGET, url);
    }

    /**
     * Creates a new encoder with the specified {@code recordEncoder}, {@code HTTP2},
     * {@code httpGET} and {@code url}
     *
     * @param recordEncoder DNS Record Encoder
     * @param useHTTP2      Use HTTP/2 (h2)
     * @param httpGET       Use HTTP GET method
     * @param url           DoH Upstream Server
     * @throws IllegalArgumentException If URL Protocol is not HTTPS
     */
    public DoHEncoder(DnsRecordEncoder recordEncoder, boolean useHTTP2, boolean httpGET, URL url)
            throws IllegalArgumentException {
        this.encoder = new DnsQueryEncoder(recordEncoder);
        this.http2 = useHTTP2;
        this.get = httpGET;
        this.url = url;

        // DoH queries must be done on top of HTTPS (HTTP + TLS)
        // Reference: https://tools.ietf.org/html/rfc8484#section-5
        if (!url.getProtocol().equalsIgnoreCase("https")) {
            throw new IllegalArgumentException("Only HTTPS Protocol is allowed, but we got: " + url.getProtocol());
        }
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, DnsQuery msg, List<Object> out) throws Exception {
        ByteBuf byteBuf = ctx.alloc().buffer();
        encoder.encode(msg, byteBuf);

        FullHttpRequest fullHttpRequest;
        if (get) {
            ByteBuf dnsQueryBuf = Base64.encode(byteBuf, Base64Dialect.URL_SAFE);

            /*
             * As per RFC 8484, variable "dns" is specified for GET request.
             * [https://tools.ietf.org/html/rfc8484#section-4.1]
             */
            fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                    url.getPath() + "?dns=" + dnsQueryBuf.toString(CharsetUtil.UTF_8));

            dnsQueryBuf.release();
        } else {
            fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, url.getPath(), byteBuf);
            fullHttpRequest.headers()
                    .add(HttpHeaderNames.CONTENT_TYPE, "application/dns-message")
                    .add(HttpHeaderNames.CONTENT_LENGTH, byteBuf.readableBytes());
        }

        // HTTP `HOST` and `ACCEPT` is common in both GET and POST methods.
        fullHttpRequest.headers()
                .add(HttpHeaderNames.HOST, url.getHost())
                .add(HttpHeaderNames.ACCEPT, "application/dns-message")
                .add(HttpHeaderNames.CACHE_CONTROL, "no-cache");

        // If we're using HTTP/2 (h2) then we'll add "x-http2-scheme:https" header
        if (http2) {
            fullHttpRequest.headers().add("x-http2-scheme", "https");
        }

        out.add(fullHttpRequest);
    }
}
