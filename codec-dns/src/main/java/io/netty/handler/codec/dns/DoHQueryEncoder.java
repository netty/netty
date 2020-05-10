/*
 * Copyright 2015 The Netty Project
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
package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.util.internal.UnstableApi;

import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;

@UnstableApi
public class DoHQueryEncoder extends MessageToMessageEncoder<DefaultDnsQuery> {

    private final DnsQueryEncoder encoder;
    private final boolean http2;
    private final boolean get;
    private final URL url;

    /**
     * Creates a new encoder with {@linkplain DnsRecordEncoder#DEFAULT the default record encoder},
     * uses HTTP/1.1 and HTTP POST method.
     *
     * @param url DoH Upstream Server
     */
    public DoHQueryEncoder(URL url) {
        this(DnsRecordEncoder.DEFAULT, false, false, url);
    }

    /**
     * Creates a new encoder with {@linkplain DnsRecordEncoder#DEFAULT the default record encoder}, uses HTTP POST
     * method and specifies if we're using HTTP/2 (h2).
     *
     * @param http2 Use HTTP/2 (h2)
     * @param url   DoH Upstream Server
     */
    public DoHQueryEncoder(boolean http2, URL url) {
        this(DnsRecordEncoder.DEFAULT, http2, false, url);
    }

    /**
     * Creates a new encoder with the specified {@code recordEncoder}, {@code HTTP2},
     * {@code GET} and {@code url}
     *
     * @param recordEncoder DNS Record Encoder
     * @param http2         Use HTTP/2 (h2)
     * @param get           Use HTTP GET method
     * @param url           DoH Upstream Server
     */
    public DoHQueryEncoder(DnsRecordEncoder recordEncoder, boolean http2, boolean get, URL url) {
        this.encoder = new DnsQueryEncoder(recordEncoder);
        this.http2 = http2;
        this.get = get;
        this.url = url;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, DefaultDnsQuery msg, List<Object> out) throws Exception {
        ByteBuf byteBuf = ctx.alloc().buffer();
        encoder.encode(msg, byteBuf);

        FullHttpRequest fullHttpRequest;
        if (get) {
            /*
             * As per RFC 8484, variable "dns" is specified for GET request.
             * [https://tools.ietf.org/html/rfc8484#section-4.1]
             */
            fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                    url.getPath() + "?dns=" + Base64.encode(byteBuf).toString(Charset.forName("UTF-8")));
        } else {
            fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                    url.getPath(), byteBuf);
            fullHttpRequest.headers().add(HttpHeaderNames.CONTENT_LENGTH, byteBuf.readableBytes());
        }

        fullHttpRequest.headers()
                .add(HttpHeaderNames.HOST, url.getHost())
                .add(HttpHeaderNames.CONTENT_TYPE, "application/dns-message")
                .add(HttpHeaderNames.ACCEPT, "application/dns-message");

        // If we're using HTTP/2 (h2) then we'll add "x-http2-scheme" header
        if (http2) {
            fullHttpRequest.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(),
                    HttpScheme.HTTPS.name());
        }

        out.add(fullHttpRequest);
    }
}
